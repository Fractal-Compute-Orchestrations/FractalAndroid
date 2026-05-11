package com.example.fractal

import android.app.Application
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
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import AppBackend.LocalTrainingModule.TrainingExecutor.TrainingCallback
import AppBackend.ResourceManagement.OperationControl.OperationControl
import AppBackend.ResourceManagement.ResourceManager.ResourceStatistics
import AppBackend.TaskContainer.Task
import AppFrontend.Interface.Home.TrainingStateRepository
import AppFrontend.Interface.RewardBank.RewardBank_ViewModel
import kotlin.math.max

class FractalTrainingService : Service() {

    private val CHANNEL_ID      = "Fractal_Training_Channel"
    private val NOTIFICATION_ID = 69420
    private val TAG             = "FractalService"

    @Volatile private var currentStatusText: String = "Training Model"
    @Volatile private var lastProgress: Int         = 0
    @Volatile private var isCurrentlyDownloading: Boolean = false

    private var trainingThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun isSystemInDarkMode(): Boolean {
        val systemUiMode = android.content.res.Resources.getSystem().configuration.uiMode
        return (systemUiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = TrainingStateRepository.getInstance()

        when (intent?.action) {
            "ACTION_START_TRAINING" -> {
                // ── THE DEAD THREAD FIX ──
                repo.isActive = true
                repo.isPaused = false

                if (trainingThread?.isAlive == true) {
                    currentStatusText = if (isCurrentlyDownloading) "Downloading" else "Synthesizing Data"
                    repo.statusMessage.postValue(currentStatusText)
                    updateNotification(lastProgress)
                } else {
                    currentStatusText = "Initializing..."
                    startForeground(NOTIFICATION_ID, createNotification(lastProgress))
                    startPipelineThread(repo) // Force starts the thread if OS killed it
                }
                return START_STICKY
            }
            "STOP_SERVICE" -> {
                Log.i(TAG, "Stopping Service via Intent")
                repo.isActive = false
                repo.isWaiting = false
                trainingThread?.interrupt()
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            "PAUSE_SERVICE" -> {
                if (repo.isActive) {
                    repo.isPaused = true
                    currentStatusText = if (isCurrentlyDownloading) "Download Paused" else "Synthesis Paused"
                    repo.statusMessage.postValue(currentStatusText)
                    updateNotification(lastProgress)
                }
                return START_STICKY
            }
            "RESUME_SERVICE" -> {
                // ── THE DEAD THREAD FIX (RESUME) ──
                repo.isActive = true
                repo.isPaused = false

                if (trainingThread?.isAlive == true) {
                    currentStatusText = if (isCurrentlyDownloading) "Downloading" else "Synthesizing Data"
                    repo.statusMessage.postValue(currentStatusText)
                    updateNotification(lastProgress)
                } else {
                    currentStatusText = "Resuming Initialization..."
                    startForeground(NOTIFICATION_ID, createNotification(lastProgress))
                    startPipelineThread(repo) // Force starts the thread if OS killed it
                }
                return START_STICKY
            }
        }

        val progress = intent?.getIntExtra("PROGRESS", lastProgress) ?: lastProgress
        val incomingText = intent?.getStringExtra("STATUS_TEXT")

        lastProgress = progress
        if (!incomingText.isNullOrEmpty()) {
            currentStatusText = incomingText
        }

        updateNotification(progress)
        return START_STICKY
    }

    private fun startPipelineThread(repository: TrainingStateRepository) {
        if (trainingThread?.isAlive == true) return

        trainingThread = Thread {
            try {
                val orchestrator = Orchestrator(applicationContext)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    orchestrator.executeTrainingPipeline(object : TrainingCallback {

                        private var currentTaskRewardRate: Double = 0.0
                        private var currentTaskTotalEpochs: Int = 1

                        override fun onTaskAcquired(task: Task) {
                            currentTaskRewardRate = task.reward_rate
                            currentTaskTotalEpochs = if (task.NUM_EPOCHS > 0) task.NUM_EPOCHS else 1
                        }

                        override fun onEpochUpdate(completedEpochs: Int, totalEpochs: Int, loss: Float, timeLeft: String) {
                            // 1. Always update the text UI (Even at 0 epochs)
                            val currentStats = repository.detailedStats.value ?: ResourceStatistics()
                            currentStats.epochsCompleted = "$completedEpochs / $totalEpochs"
                            currentStats.estimatedTimeLeft = timeLeft
                            val perf = max(0, 100 - (loss * 100).toInt())
                            currentStats.overallPerformance = "$perf%"
                            repository.detailedStats.postValue(currentStats)

                            // 2. THE FIX: Only synthesize Vapor if actual work was done!
                            if (completedEpochs > 0) {
                                val mbsPerEpoch = (currentTaskRewardRate / currentTaskTotalEpochs).toFloat()
                                val app = applicationContext as Application
                                RewardBank_ViewModel.getInstance(app).addRealVapor(mbsPerEpoch)
                            }
                        }

                        override fun onUploadSuccess() {
                            val app = applicationContext as Application
                            RewardBank_ViewModel.getInstance(app).verifyVaporToLiquid()
                        }

                        override fun onProgress(percentage: Int) {
                            isCurrentlyDownloading = false
                            lastProgress = percentage
                            repository.trainingProgress.postValue(percentage)
                            currentStatusText = "Synthesizing Data"
                            updateNotification(percentage)
                        }

                        override fun onValidationUpdate(result: String) {
                            val stats = repository.detailedStats.value ?: ResourceStatistics()
                            stats.inferenceTesting = result
                            repository.detailedStats.postValue(stats)
                        }

                        override fun onStatusUpdate(message: String) {
                            repository.statusMessage.postValue(message)

                            if (message.lowercase().contains("gulping") && message.contains("%")) {
                                isCurrentlyDownloading = true
                                try {
                                    val numericOnly = message.replace("[^0-9]".toRegex(), "")
                                    if (numericOnly.isNotEmpty()) {
                                        lastProgress = numericOnly.toInt()
                                        currentStatusText = if (message.lowercase().contains("paused")) "Gulping Paused" else "Gulping Internet Chunk"
                                        updateNotification(lastProgress)
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Could not parse gulping percentage from: $message")
                                }
                            } else if (message.contains("Resuming")) {
                                isCurrentlyDownloading = true
                            } else if (message.contains("Generating Internet:")) {
                                isCurrentlyDownloading = false
                            } else {
                                isCurrentlyDownloading = false
                                currentStatusText = message
                                updateNotification(lastProgress)
                            }
                        }

                        override fun isPaused(): Boolean = repository.isPaused
                        override fun isCancelled(): Boolean = !repository.isActive
                        override fun onWaitingStateChanged(isWaiting: Boolean) { repository.isWaiting = isWaiting }

                        override fun checkLiveConditions(): String? {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val opControl = OperationControl(applicationContext)
                                val violation = opControl.getViolationMessage()

                                if (violation != null && (
                                            violation.contains("network", ignoreCase = true) ||
                                                    violation.contains("wi-fi", ignoreCase = true) ||
                                                    violation.contains("wifi", ignoreCase = true) ||
                                                    violation.contains("internet", ignoreCase = true)
                                            )) {
                                    return null
                                }

                                return violation
                            }
                            return null
                        }
                    })
                }

            } catch (e: Exception) {
                Log.e(TAG, "Lifecycle Error: ${e.message}")
                repository.statusMessage.postValue("Error: ${e.message}")
            } finally {
                repository.isActive = false
                repository.isWaiting = false
                repository.isPaused = false
                isCurrentlyDownloading = false

                stopForeground(true)
                stopSelf()
            }
        }
        trainingThread?.start()
    }

    private fun updateNotification(progress: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, createNotification(progress))
    }

    private fun createNotification(progress: Int): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val accentColor = if (isSystemInDarkMode()) Color.WHITE else Color.parseColor("#181818")

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.fractal_logo)
            .setContentTitle(currentStatusText)
            .setContentText("$progress% Complete")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .setColor(accentColor)

        if (currentStatusText == "Download Paused" || currentStatusText == "Synthesis Paused") {
            val resumeIntent = Intent(this, FractalTrainingService::class.java).apply { action = "RESUME_SERVICE" }
            val resumePendingIntent = PendingIntent.getService(this, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(R.drawable.ic_play_filled, "Resume", resumePendingIntent)

        } else if (currentStatusText.startsWith("Downloading") || currentStatusText.startsWith("Synthesizing")) {
            val pauseIntent = Intent(this, FractalTrainingService::class.java).apply { action = "PAUSE_SERVICE" }
            val pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(R.drawable.ic_pause_filled, "Pause", pausePendingIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Fractal Orchestration", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}