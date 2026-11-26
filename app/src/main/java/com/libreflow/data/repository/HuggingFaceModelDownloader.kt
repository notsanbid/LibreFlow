package com.libreflow.data.repository

import android.content.Context
import android.os.Environment
import com.libreflow.data.local.dao.ModelDao
import com.libreflow.data.local.entity.ModelDescriptorEntity
import com.libreflow.data.local.entity.ModelDownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ModelFileSpec(val name: String, val url: String)

class HuggingFaceModelDownloader(
    private val context: Context,
    private val modelDao: ModelDao
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getModelsRootDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, "LibreFlowModels")
        if (!dir.exists() && !dir.mkdirs()) {
            val fallback = File(context.filesDir, "LibreFlowModels")
            fallback.mkdirs()
            return fallback
        }
        return dir
    }

    fun getModelDir(modelId: String): File = File(getModelsRootDir(), modelId).apply { mkdirs() }

    fun parseFiles(filesJson: String): List<ModelFileSpec> {
        val array = JSONArray(filesJson)
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(ModelFileSpec(obj.getString("name"), obj.getString("url")))
            }
        }
    }

    fun isModelPresentOnDisk(model: ModelDescriptorEntity): Boolean {
        val dir = getModelDir(model.id)
        val specs = parseFiles(model.filesJson)
        return specs.isNotEmpty() && specs.all { spec ->
            val f = File(dir, spec.name)
            f.exists() && f.length() > 0L
        }
    }

    suspend fun downloadModel(model: ModelDescriptorEntity): Result<File> = withContext(Dispatchers.IO) {
        val specs = parseFiles(model.filesJson)
        val modelDir = getModelDir(model.id)

        try {
            modelDao.updateState(model.id, ModelDownloadState.DOWNLOADING.name, 0, modelDir.absolutePath, null)
            val totalFiles = specs.size.coerceAtLeast(1)

            specs.forEachIndexed { index, spec ->
                val targetFile = File(modelDir, spec.name)
                val tempFile = File(modelDir, "${spec.name}.part")
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    val request = Request.Builder().url(spec.url).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            error("HTTP ${response.code} downloading ${spec.name}")
                        }
                        val body = response.body ?: error("Empty body for ${spec.name}")
                        val contentLen = body.contentLength()
                        var copied = 0L
                        var lastReported = -1
                        body.byteStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val read = input.read(buf)
                                    if (read <= 0) break
                                    output.write(buf, 0, read)
                                    copied += read
                                    val fileFrac = if (contentLen > 0) copied.toFloat() / contentLen else 0.5f
                                    val overall = (((index + fileFrac) / totalFiles) * 100f).toInt().coerceIn(0, 99)
                                    if (overall != lastReported) {
                                        lastReported = overall
                                        modelDao.updateState(
                                            model.id,
                                            ModelDownloadState.DOWNLOADING.name,
                                            overall,
                                            modelDir.absolutePath,
                                            null
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                }
            }

            // Coordinate batch completion: mark DOWNLOADED only after all constituent files succeed
            modelDao.updateState(
                model.id,
                ModelDownloadState.DOWNLOADED.name,
                100,
                modelDir.absolutePath,
                null
            )
            Result.success(modelDir)
        } catch (t: Throwable) {
            modelDao.updateState(
                model.id,
                ModelDownloadState.ERROR.name,
                0,
                modelDir.absolutePath,
                t.message ?: "Download failed"
            )
            Result.failure(t)
        }
    }

    suspend fun deleteModel(model: ModelDescriptorEntity) = withContext(Dispatchers.IO) {
        val dir = getModelDir(model.id)
        if (dir.exists()) dir.deleteRecursively()
        modelDao.updateState(
            model.id,
            ModelDownloadState.NOT_DOWNLOADED.name,
            0,
            "",
            null
        )
    }
}
