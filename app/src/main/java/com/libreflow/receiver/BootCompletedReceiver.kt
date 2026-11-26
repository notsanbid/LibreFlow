package com.libreflow.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.libreflow.LibreFlowApplication
import com.libreflow.service.FlowOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (!Settings.canDrawOverlays(context)) return
            val app = context.applicationContext as? LibreFlowApplication ?: return
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (app.preferences.overlayEnabled.first()) {
                        runCatching { FlowOverlayService.start(context) }
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
