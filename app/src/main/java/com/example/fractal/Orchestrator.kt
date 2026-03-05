package com.example.fractal

import android.content.Context
import android.util.Log
import AppBackend.ResourceManagement.DataDownloader_naf
import AppBackend.LocalTrainingModule.TrainingExecutor.TrainingCallback
import AppBackend.TaskContainer.Image_Task
import AppBackend.TaskContainer.Task
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.util.concurrent.CountDownLatch

class Orchestrator(private val context: Context) {

    private val TAG = "FractalOrchestrator"

    /**
     * Executes the full pipeline: Fetch -> Download -> Build -> Train -> Upload -> Flush
     * The callback allows real-time UI updates across different fragments.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun executeTrainingPipeline(callback: TrainingCallback? = null) {

        val globalState = (context.applicationContext as FractalApplication).globalState

        val serverIp = globalState.server?.networkConfig?.SERVER_IP ?: "192.168.43.76"
        val serverPort = globalState.server?.networkConfig?.SERVER_PORT ?: "5001"
        val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_device"

        Log.i(TAG, "=========================================")
        Log.i(TAG, " STARTING FRACTAL TRAINING PIPELINE")
        Log.i(TAG, "=========================================")

        // =================================================================================
        // THE MASTER LOOP: Keeps fetching and training until the user explicitly cancels
        // =================================================================================
        while (callback?.isCancelled() == false) {
            try {
                // Step 0: Operational Control Gatekeeper (Waits for Battery/Network)
                val opControl = AppBackend.ResourceManagement.OperationControl.OperationControl(context)
                callback.onStatusUpdate("Evaluating device state...")

                if (!opControl.waitForOptimalConditions(callback)) {
                    return // Exit the thread completely if user cancelled
                }

                // Step 1: Infinite Task Fetch Loop
                var task: Task? = null
                var attemptCount = 1

                while (task == null && callback.isCancelled() == false) {
                    // Pause Trap
                    while (callback.isPaused() == true) {
                        if (callback.isCancelled()) return
                        callback.onStatusUpdate("Task Search Paused")
                        Thread.sleep(500)
                    }
                    if (callback.isCancelled() == true) return

                    callback.onStatusUpdate("Checking network for task fetch...")
                    if (!opControl.waitForNetworkToUpload(callback)) return

                    callback.onStatusUpdate(if (attemptCount == 1) "Fetching task from server..." else "Searching for task (Attempt $attemptCount)...")
                    task = globalState.server?.GET_Task(true, deviceId)

                    if (task != null) {
                        Log.i(TAG, "Task ${task.task_Id} acquired.")
                        break
                    } else {
                        // Server offline or 403 (No tasks available for this device)
                        callback.onStatusUpdate("Server offline or no task\nRetrying in 10s...")
                        for (i in 0 until 100) {
                            if (callback.isCancelled() == true) return
                            if (callback.isPaused() == true) break
                            Thread.sleep(100)
                        }
                        attemptCount++
                    }
                }

                if (task == null || callback.isCancelled() == true) return

                // Step 2: Smart Download Resources
                var downloadSuccess = false
                var needsDownload = true

                if (task is Image_Task) {
                    val modelFile = File(context.filesDir, task.MODEL_FILENAME)
                    val imagesFile = File(context.filesDir, task.TRAIN_IMAGES_FILENAME)
                    val labelsFile = File(context.filesDir, task.TRAIN_LABELS_FILENAME)

                    if (modelFile.exists() && modelFile.length() > 0 &&
                        imagesFile.exists() && imagesFile.length() > 0 &&
                        labelsFile.exists() && labelsFile.length() > 0) {
                        callback.onStatusUpdate("Local files found. Skipping download...")
                        needsDownload = false
                        downloadSuccess = true
                    }
                }

                if (needsDownload) {
                    callback.onStatusUpdate("Downloading training resources...")
                    val latch = CountDownLatch(1)

                    if (task is Image_Task) {
                        DataDownloader_naf.downloadFiles(
                            context,
                            serverIp,
                            serverPort,
                            task.TRAIN_IMAGES_FILENAME,
                            task.TRAIN_LABELS_FILENAME,
                            task.MODEL_FILENAME,
                            object : DataDownloader_naf.DownloadListener {
                                override fun onDownloadFinished() {
                                    downloadSuccess = true
                                    latch.countDown()
                                }
                                override fun onError(error: String) {
                                    Log.e(TAG, "Download failed: $error")
                                    downloadSuccess = false
                                    latch.countDown()
                                }
                            }
                        )
                        latch.await()
                    } else {
                        Log.e(TAG, "Task is not an Image_Task. Cannot proceed with download.")
                        downloadSuccess = false
                    }
                }

                if (!downloadSuccess) {
                    callback.onStatusUpdate("Error: Download failed")
                    // Instead of killing the master loop, we break this iteration and wait 10s before trying again
                    Thread.sleep(10000)
                    continue
                }

                // Step 3: Assembling the Engine
                callback.onStatusUpdate("Assembling training engine...")
                val trainingPreferences = task.training_type.toTypedArray()
                val packageTypeTrainer = globalState.packageTypeTrainerBuilder?.make(context, trainingPreferences)

                if (packageTypeTrainer == null) {
                    callback.onStatusUpdate("Error: Engine build failed")
                    Thread.sleep(10000)
                    continue
                }

                // Step 4: Execute Pipeline (Train -> Validate -> Upload -> Flush)
                packageTypeTrainer.run(task, callback)

                // =======================================================================
                // Step 5: The Cooldown Phase
                // If we reach here, a full task was completed (or gracefully failed).
                // We rest for 30 seconds before pinging the server for the NEXT task.
                // =======================================================================
                if (callback.isCancelled() == false) {
                    Log.i(TAG, "Task ${task.task_Id} complete. Entering cooldown phase.")
                    callback.onStatusUpdate("Process Complete\nCooling down (15s)...")

                    // Reset the diamond progress visually for the next task
                    callback.onProgress(0)

                    // 30-Second Smart Sleep
                    for (i in 0 until 100) {
                        if (callback.isCancelled() == true) return
                        if (callback.isPaused() == true) {
                            // If user pauses during cooldown, hold here until they resume
                            while(callback.isPaused()) {
                                if (callback.isCancelled()) return
                                Thread.sleep(500)
                            }
                        }
                        Thread.sleep(100)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Pipeline encountered an error: ${e.message}")
                callback?.onStatusUpdate("Error: ${e.message}")
                // If the whole pipeline crashes, wait 10 seconds before automatically restarting the master loop
                Thread.sleep(10000)
            }
        }

        Log.i(TAG, "Master Pipeline gracefully terminated by user.")
    }
}