package com.example.fractal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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

    private var isManagingCurrentWindow = false
    private var isFirstRun = true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val app = applicationContext as? FractalApplication
            val config = app?.globalState?.appConfig ?: FileOperations(this).readJson<app_config>("app_config.json")

            if (config != null) {
                config.overNightUtilization = false
                FileOperations(this).writeJson("app_config.json", config)
                app?.globalState?.appConfig = config
            }
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        // VISIBILITY: Friendly, clear system state
        startForeground(NOTIFICATION_ID, buildNotification("Overnight Mode: Enabled", "Waiting for the scheduled time window..."))
        scope.launch { runOvernightLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runOvernightLoop() {
        while (scope.isActive) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

            if (isFirstRun) {
                isManagingCurrentWindow = false
                isFirstRun = false
            }

            when {
                // ── Active Window: Midnight → 8 AM ──────────────────────
                hour in 0..7 -> {
                    if (!isManagingCurrentWindow) {
                        if (!repo.isActive) {
                            startGeneration()
                        } else if (repo.isPaused) {
                            resumeGeneration()
                        }
                        isManagingCurrentWindow = true
                    }

                    // FEEDBACK: Simple "What is it doing right now?" language
                    if (repo.isActive && !repo.isPaused) {
                        updateNotification("Overnight Mode: Active", "Status: Generating internet rewards for you.")
                    } else {
                        updateNotification("Overnight Mode: Paused", "Status: Generation paused. Tap 'Resume' in the app to continue.")
                    }
                }

                // ── Standby Window: 8 AM → Midnight ──────────────────────
                else -> {
                    if (isManagingCurrentWindow) {
                        pauseForDaytime()
                        isManagingCurrentWindow = false
                    }

                    // MAPPING: Clear countdown to when the "Work" starts again
                    val minutesUntilMidnight = minutesUntil(0)
                    updateNotification("Overnight Mode: Standby", "Next generation window starts in ${formatCountdown(minutesUntilMidnight)}.")
                }
            }
            delay(10_000L)
        }
    }

    // ── Generation control (Friendly Language) ────────────────────────────────

    private fun startGeneration() {
        repo.isActive = true
        repo.isWaiting = true
        repo.isPaused = false
        repo.statusMessage.postValue("Overnight Mode: Starting Reward Generation...")

        val startIntent = Intent(this, FractalTrainingService::class.java).apply {
            action = "ACTION_START_TRAINING"
        }
        ContextCompat.startForegroundService(this, startIntent)
    }

    private fun resumeGeneration() {
        repo.isPaused = false
        repo.statusMessage.postValue("Overnight Mode: Resuming Reward Generation...")

        val resumeIntent = Intent(this, FractalTrainingService::class.java).apply {
            action = "RESUME_SERVICE"
        }
        startService(resumeIntent)
    }

    private fun pauseForDaytime() {
        if (!repo.isActive) return
        repo.isPaused = true
        repo.statusMessage.postValue("Overnight Mode: Window Closed (08:00 AM)")

        val pauseIntent = Intent(this, FractalTrainingService::class.java).apply {
            action = "PAUSE_SERVICE"
        }
        startService(pauseIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overnight Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps your internet reward generation on schedule."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OvernightManagerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.fractal_logo)
            .setContentIntent(openAppPending)
            .setOngoing(true)
            .setSilent(true)
            .setColor(Color.parseColor("#181818"))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Turn Off Overnight Mode", stopPendingIntent)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

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