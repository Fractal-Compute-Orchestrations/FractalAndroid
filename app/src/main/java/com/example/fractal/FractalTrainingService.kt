package com.example.fractal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import AppFrontend.Interface.Home.TrainingStateRepository

class FractalTrainingService : Service() {

    private val CHANNEL_ID      = "Fractal_TrainingChannel"
    private val NOTIFICATION_ID = 69420

    private var currentStatusText: String = "Training Model"
    private var lastProgress: Int         = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    // ── SAFELY CHECK TRUE OS THEME ──
    private fun isSystemInDarkMode(): Boolean {
        val systemUiMode = android.content.res.Resources.getSystem().configuration.uiMode
        return (systemUiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_SERVICE" -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            "PAUSE_SERVICE" -> {
                val repo = TrainingStateRepository.getInstance()
                if (repo != null && repo.isActive) {
                    repo.isPaused = true
                    repo.statusMessage.postValue("Training Paused via Notification")
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            "PAUSE_DOWNLOAD" -> {
                val repo = TrainingStateRepository.getInstance()
                if (repo != null && repo.isActive) {
                    repo.isPaused = true
                    repo.statusMessage.postValue("Download Paused: $lastProgress%")
                }
                currentStatusText = "Download Paused"
                startForeground(NOTIFICATION_ID, createNotification(lastProgress))
                return START_STICKY
            }
            "RESUME_DOWNLOAD" -> {
                val repo = TrainingStateRepository.getInstance()
                if (repo != null && repo.isActive) {
                    repo.isPaused = false
                    repo.statusMessage.postValue("Resuming download...")
                }
                currentStatusText = "Downloading"
                startForeground(NOTIFICATION_ID, createNotification(lastProgress))
                return START_STICKY
            }
        }

        val progress     = intent?.getIntExtra("PROGRESS", 0) ?: 0
        val incomingText = intent?.getStringExtra("STATUS_TEXT")

        lastProgress = progress
        if (!incomingText.isNullOrEmpty()) {
            currentStatusText = incomingText
        }

        startForeground(NOTIFICATION_ID, createNotification(progress))
        return START_STICKY
    }

    private fun createNotification(progress: Int): Notification {

        // 1. Base Tap intent (Opens App)
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── THE FIX: Dynamically set the Accent Color ──
        // If the OS is in Dark Mode (dark background), tint the logo/app name WHITE.
        // If the OS is in Light Mode (white background), tint the logo/app name DARK.
        val accentColor = if (isSystemInDarkMode()) Color.WHITE else Color.parseColor("#181818")

        // 2. Native Notification Builder
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.fractal_logo)
            .setContentTitle(currentStatusText)
            .setContentText("$progress% Complete")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .setColor(accentColor) // <--- Magic happens here!

        // 3. Attach Native Action Buttons conditionally
        if (currentStatusText == "Download Paused") {
            val resumeIntent = Intent(this, FractalTrainingService::class.java).apply { action = "RESUME_DOWNLOAD" }
            val resumePendingIntent = PendingIntent.getService(
                this, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_play_filled, "Resume", resumePendingIntent)

        } else if (currentStatusText == "Downloading" || currentStatusText.startsWith("Downloading")) {
            val pauseIntent = Intent(this, FractalTrainingService::class.java).apply { action = "PAUSE_DOWNLOAD" }
            val pausePendingIntent = PendingIntent.getService(
                this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_pause_filled, "Pause", pausePendingIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Training Process",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}