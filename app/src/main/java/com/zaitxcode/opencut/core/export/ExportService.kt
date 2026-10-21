package com.zaitxcode.opencut.core.export

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Foreground service that keeps an export alive while the app is in the background. */
class ExportService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
