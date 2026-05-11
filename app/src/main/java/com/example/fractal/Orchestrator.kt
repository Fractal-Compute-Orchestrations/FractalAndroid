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

        val deviceId = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"

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
                    return
                }

                // Step 1: Infinite Task Fetch Loop
                var task: Task? = null
                var attemptCount = 1

                while (task == null && callback.isCancelled() == false) {
                    // Pause Trap
                    while (callback.isPaused() == true) {
                        if (callback.isCancelled()) return
                        callback.onStatusUpdate("Grid Search Paused")
                        Thread.sleep(500)
                    }
                    if (callback.isCancelled() == true) return

                    callback.onStatusUpdate("Checking for internet payload...")
                    if (!opControl.waitForNetworkToUpload(callback)) return

                    callback.onStatusUpdate(
                        if (attemptCount == 1) "Locating optimal Internet chunk..."
                        else "Scanning grid for payload (Attempt $attemptCount)..."
                    )
                    task = globalState.server?.GET_Task(true, deviceId)

                    if (task != null) {
                        if (task.task_expire_date != null && task.task_expire_date.before(java.util.Date())) {
                            Log.w(TAG, "Task ${task.task_Id} has expired. Flushing and skipping.")
                            callback.onStatusUpdate("Payload Expired. Flushing...")
                            AppFrontend.Flush.Flusher().flushAll(task)
                            task = null
                            for (i in 0 until 100) {
                                if (callback.isCancelled() == true) return
                                if (callback.isPaused() == true) break
                                Thread.sleep(100)
                            }
                            attemptCount++
                            continue
                        }

                        Log.i(TAG, "Task ${task.task_Id} acquired.")
                        Log.i(TAG, "Engine Architecture: ${task.architecture} | Reward Rate: ${task.reward_rate} MB")

                        // --- NEW: Pass the full task back to the caller immediately ---
                        // This allows the UI to update the Architecture text before downloading even starts
                        callback.onTaskAcquired(task)

                        break
                    } else {
                        callback.onStatusUpdate("Grid quiet. Re-scanning...")
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
                    val modelFile  = File(context.filesDir, task.MODEL_FILENAME)
                    val imagesFile = File(context.filesDir, task.TRAIN_IMAGES_FILENAME)
                    val labelsFile = File(context.filesDir, task.TRAIN_LABELS_FILENAME)

                    if (modelFile.exists()  && modelFile.length()  > 0 &&
                        imagesFile.exists() && imagesFile.length() > 0 &&
                        labelsFile.exists() && labelsFile.length() > 0) {
                        callback?.onStatusUpdate("Local cache found. Preparing synthesis...")
                        needsDownload = false
                        downloadSuccess = true
                    }
                }

                if (needsDownload) {
                    callback?.onStatusUpdate("Gulping Internet Chunk...")
                    val latch = CountDownLatch(1)

                    if (task is Image_Task) {

                        // ── Wire the callback's pause/cancel state into the downloader ──
                        val pauseController = object : DataDownloader_naf.PauseController {
                            override fun isPaused(): Boolean   = callback?.isPaused()   == true
                            override fun isCancelled(): Boolean = callback?.isCancelled() == true
                        }

                        DataDownloader_naf.downloadFiles(
                            context,
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
                                override fun onProgressUpdate(percentage: Int) {
                                    callback?.onStatusUpdate("Gulping Internet chunk... $percentage%")
                                }
                                // ── Forward the paused status to the UI ──────────────
                                override fun onStatusMessage(message: String) {
                                    callback?.onStatusUpdate(message)
                                }
                            },
                            pauseController  // ← pass the controller
                        )
                        latch.await()

                        // ── Instant exit if the user cancelled during the download ──
                        if (callback?.isCancelled() == true) return

                    } else {
                        Log.e(TAG, "Task is not an Image_Task. Cannot proceed with download.")
                        downloadSuccess = false
                    }
                }

                if (!downloadSuccess) {
                    callback?.onStatusUpdate("Error: Payload retrieval failed")
                    Thread.sleep(10000)
                    continue
                }

                // Step 3: Assembling the Engine
                callback.onStatusUpdate("Assembling synthesis engine...")
                val trainingPreferences = task.training_type.toTypedArray()
                val packageTypeTrainer = globalState.packageTypeTrainerBuilder?.make(context, trainingPreferences)

                if (packageTypeTrainer == null) {
                    callback.onStatusUpdate("Error: Engine assembly failed")
                    Thread.sleep(10000)
                    continue
                }

                // Step 4: Execute Pipeline (Train -> Validate -> Upload -> Flush)
                // The task object passed here now contains your reward_rate!
                packageTypeTrainer.run(task, callback)

                // Step 5: The Cooldown Phase
                if (callback.isCancelled() == false) {
                    Log.i(TAG, "Task ${task.task_Id} complete. Entering cooldown phase.")
                    callback.onStatusUpdate("Process Complete\nCooling down (15s)...")
                    callback.onProgress(0)

                    for (i in 0 until 100) {
                        if (callback.isCancelled() == true) return
                        if (callback.isPaused() == true) {
                            while (callback.isPaused()) {
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
                Thread.sleep(10000)
            }
        }

        Log.i(TAG, "Master Pipeline gracefully terminated by user.")
    }
}