package com.example.fractal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class FractalTrainingService : Service() {

    private val CHANNEL_ID = "Fractal_TrainingChannel"
    private val NOTIFICATION_ID = 69420

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == "STOP_SERVICE") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val progress = intent?.getIntExtra("PROGRESS", 0) ?: 0
        val notification = createNotification(progress)

        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun createNotification(progress: Int): Notification {
        // 1. Create a "Click to Open App" Intent
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Load your beautiful full-color Fractal Logo for the right side
        val largeIconBitmap = BitmapFactory.decodeResource(resources, R.drawable.fractal_logo)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            // Beautiful, clean text formatting
            .setContentTitle("Fractal AI Engine")
            .setContentText("Training Model: $progress% Complete")

            // SMALL ICON: This sits in the status bar at the very top of the phone.
            // *IMPORTANT NOTE:* Android forces small icons to be pure white and transparent.
            // If your fractal_logo is a solid color image, change this to an outline vector (like R.drawable.ic_diamond_indicator_selected)
            .setSmallIcon(R.drawable.fractal_logo)

            // LARGE ICON: This is where your full-color logo shines inside the notification body!
            .setLargeIcon(largeIconBitmap)

            // Color themes the progress bar and the small icon
            .setColor(Color.parseColor("#181818"))

            // Makes the notification clickable
            .setContentIntent(pendingIntent)

            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Training Process",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                // UPDATED: This prevents the app icon badge (the dot) from showing up
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}