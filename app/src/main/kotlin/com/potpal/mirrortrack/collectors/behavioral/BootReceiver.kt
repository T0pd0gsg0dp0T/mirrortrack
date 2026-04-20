package com.potpal.mirrortrack.collectors.behavioral

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.potpal.mirrortrack.scheduling.CollectionForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        CollectionForegroundService.startIfEnabled(context)
    }
}
