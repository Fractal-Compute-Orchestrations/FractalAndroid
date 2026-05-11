package AppBackend.Factory.PackageTypeTrainer

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import AppBackend.DataManager.DataLoaderAndInitializer.DataInitializer
import AppBackend.LocalTrainingModule.TrainingExecutor.Trainer
import AppBackend.Network.ModelUpdateTransmission.ModelTransmission_DTO
import AppBackend.Network.ModelUpdateTransmission.ModelTransmitter
import AppBackend.ResourceManagement.OperationControl.OperationControl
import AppBackend.TaskContainer.Task
import AppBackend.Validator.ModelInferenceValidator.InferenceValidator
import AppFrontend.Flush.Flusher
import android.util.Log

class PackageTypeTrainer(
    private val context: Context,
    val task: Task?,
    val dataInitializer: DataInitializer?,
    val trainer: Trainer?,
    val validator: InferenceValidator?
) {
    private val TAG = "PackageTypeTrainer"

    @RequiresApi(Build.VERSION_CODES.M)
    fun run(task: Task, callback: AppBackend.LocalTrainingModule.TrainingExecutor.TrainingCallback? = null) {
        if (dataInitializer == null || trainer == null || validator == null) {
            callback?.onStatusUpdate("Error: Pipeline components missing.")
            return
        }

        try {
            // Step 1: Preprocess
            callback?.onStatusUpdate("Preprocessing Local Data...")
            val data = dataInitializer.preprocess(task)

            // Step 2: Train Model
            callback?.onStatusUpdate("Setting up AI Engine...")
            trainer.setUpTrainer(data, task)

            // Training loop with internal Pause/Cancel checks
            trainer.trainModel(callback)

            // Check if user cancelled during training before proceeding
            if (callback?.isCancelled() == true) return

            // Step 3: Validate
            callback?.onStatusUpdate("Running Sanity Check...")
            val validationResult = validator.infer(data, trainer.interpreter, task)
            callback?.onValidationUpdate(validationResult)

            if (callback?.isCancelled() == true) return

            // --- Step 4: Transmit with INFINITE AUTO-RETRY & PAUSE CONTROL ---
            val opControl = OperationControl(context)
            val transmissionDTO = ModelTransmission_DTO(task)
            val transmitter = ModelTransmitter()
            val flusher = Flusher()

            var uploadSuccess = false
            var attemptCount = 1

            // Keep trying indefinitely as long as upload fails AND user hasn't hit cancel
            while (!uploadSuccess && callback?.isCancelled() == false) {

                // 4A. PAUSE TRAP: Wait here indefinitely if the user has paused the app
                while (callback?.isPaused() == true) {
                    if (callback.isCancelled()) return
                    callback.onStatusUpdate("Upload Paused")
                    Thread.sleep(500)
                }
                if (callback?.isCancelled() == true) return

                // 4B. Wait for Network to physically exist before even trying
                callback?.onStatusUpdate("Checking network for upload...")
                if (!opControl.waitForNetworkToUpload(callback)) {
                    return // User explicitly canceled while waiting for network
                }

                // Double check pause/cancel right before firing the network request
                if (callback?.isCancelled() == true) return
                if (callback?.isPaused() == true) continue // Loop back to the top into the pause trap

                // 4C. Attempt the Upload
                callback?.onStatusUpdate(if (attemptCount == 1) "Uploading Model to Server..." else "Retrying Upload (Attempt $attemptCount)...")
                uploadSuccess = transmitter.transmitModel(transmissionDTO)

                if (uploadSuccess) {
                    // Success! Flush and exit loop cleanly.
                    callback?.onStatusUpdate("Flushing Temporary Storage...")
                    flusher.flushAll(task)

                    // --- NEW: Tell the HomeViewModel to turn Vapor into Liquid! ---
                    callback?.onUploadSuccess()
                    // --------------------------------------------------------------

                    callback?.onStatusUpdate("Process Complete")
                    break
                } else {
                    // Failure! Show error, then smart-sleep for 10 seconds before retrying
                    callback?.onStatusUpdate("Upload Failed. Retrying in 10s...")

                    // SMART SLEEP: Sleep 10 seconds total, check for user cancellation OR pause every 100ms
                    for (i in 0 until 50) {
                        if (callback?.isCancelled() == true) {
                            return // User hit the Diamond to cancel during the countdown
                        }
                        if (callback?.isPaused() == true) {
                            break // Break the 10s sleep instantly, loop back to the top into the pause trap
                        }
                        Thread.sleep(100)
                    }
                    attemptCount++
                }
            }

        } catch (e: Exception) {
            callback?.onStatusUpdate("Error: ${e.message}")
            Log.e(TAG, "Training error", e)
        }
    }
}