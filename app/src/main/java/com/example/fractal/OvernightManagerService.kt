package com.example.fractal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import AppFrontend.Interface.Home.TrainingStateRepository
import AppGlobal.app_config
import AppGlobal.Utils.FileOperations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

class OvernightManagerService : Service() {

    companion object {
        const val CHANNEL_ID = "overnight_manager_channel"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "ACTION_STOP_OVERNIGHT_MANAGER"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repo get() = TrainingStateRepository.getInstance()

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Update Config to reflect the user turning it off via the notification
            val fileOps = FileOperations(this)
            val config = fileOps.readJson<app_config>("app_config.json")
            if (config != null) {
                config.overNightUtilization = false
                fileOps.writeJson("app_config.json", config)
            }
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        // Immediately promote to foreground so Android cannot kill us
        startForeground(NOTIFICATION_ID, buildNotification("Overnight Supervisor Active", "Calculating schedule..."))

        // Start the monitoring loop
        scope.launch { runOvernightLoop() }

        return START_STICKY // Android restarts the service if it ever dies
    }

    override fun onDestroy() {
        scope.cancel()
        pauseTrainingIfRunning() // Clean up on intentional stop
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Core loop ────────────────────────────────────────────────────────────

    private suspend fun runOvernightLoop() {
        while (isActive) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

            when {
                // ── Overnight window: midnight → 8 AM ──────────────────────
                hour in 0..7 -> {
                    if (!repo.isActive && !repo.isPaused) {
                        startTraining()
                        updateNotification("Overnight Supervisor Active", "🌙 Training window is open and running.")
                    } else if (repo.isActive) {
                        updateNotification("Overnight Supervisor Active", "🌙 Training is currently running.")
                    } else {
                        // Manually paused by user — respect it
                        updateNotification("Overnight Supervisor Active", "⏸ Training paused by user. Will not auto-resume.")
                    }
                }

                // ── Past 8 AM: pause if still running ──────────────────────
                hour >= 8 && repo.isActive && !repo.isPaused -> {
                    pauseTrainingIfRunning()
                    updateNotification("Overnight Supervisor Active", "☀️ Training paused (Outside 00:00 - 08:00 window).")
                }

                // ── Daytime idle ───────────────────────────────────────────
                else -> {
                    val minutesUntilMidnight = minutesUntil(0)
                    updateNotification("Overnight Supervisor Active", "⏰ Next window opens in ${formatCountdown(minutesUntilMidnight)}")
                }
            }

            delay(60_000L) // Re-check every 60 seconds
        }
    }

    // ── Training control ─────────────────────────────────────────────────────

    private fun startTraining() {
        repo.isActive = true
        repo.isWaiting = true
        repo.isPaused = false
        repo.statusMessage.postValue("Overnight Training Initiated...")

        val startIntent = Intent(this, FractalTrainingService::class.java).apply {
            action = "ACTION_START_TRAINING"
        }
        ContextCompat.startForegroundService(this, startIntent)
        android.util.Log.i("OvernightManager", "Training started by Supervisor")
    }

    private fun pauseTrainingIfRunning() {
        if (!repo.isActive) return
        repo.isPaused = true
        repo.statusMessage.postValue("Overnight Training Paused (8 AM limit)")

        val pauseIntent = Intent(this, FractalTrainingService::class.java).apply {
            action = "PAUSE_SERVICE"
        }
        startService(pauseIntent)
        android.util.Log.i("OvernightManager", "Training paused by Supervisor")
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overnight Training Manager",
                NotificationManager.IMPORTANCE_LOW // Silent, but visible
            ).apply {
                description = "Keeps overnight training on schedule"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        // Tap opens the app
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Turn off" action in the notification itself
        val stopIntent = Intent(this, OvernightManagerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val accentColor = Color.parseColor("#181818") // Use your theme color

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.fractal_logo) // Ensure this is your vector logo
            .setContentIntent(openAppPending)
            .setOngoing(true)
            .setSilent(true)
            .setColor(accentColor)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disable Supervisor", stopPendingIntent)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    // ── Time utilities ────────────────────────────────────────────────────────

    private fun minutesUntil(targetHour: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_MONTH, 1)
        }
        return (target.timeInMillis - now.timeInMillis) / 60_000
    }

    private fun formatCountdown(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}