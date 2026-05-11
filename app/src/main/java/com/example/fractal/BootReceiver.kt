package com.example.fractal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import AppGlobal.app_config
import AppGlobal.Utils.FileOperations

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        android.util.Log.i("BootReceiver", "Device booted. Checking Fractal config...")

        val fileOps = FileOperations(context)
        val config = fileOps.readJson<app_config>("app_config.json") ?: return

        if (config.overNightUtilization) {
            android.util.Log.i("BootReceiver", "Overnight Utilization is ON. Starting Supervisor.")
            val serviceIntent = Intent(context, OvernightManagerService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}