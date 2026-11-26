# Sherpa-ONNX JNI
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# Llama.cpp Android JNI
-keep class dev.ffmpegkit.** { *; }
-keep class com.llama.** { *; }
-dontwarn dev.ffmpegkit.**

# Room entities & DAOs
-keep class com.libreflow.data.local.entity.** { *; }
-keep class * extends androidx.room.RoomDatabase

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
