package AppBackend.LocalTrainingModule.TrainingExecutor

import android.util.Log
import AppBackend.LocalTrainingModule.TrainingStateManager.CheckpointManager
import AppBackend.TaskContainer.Image_Task
import AppBackend.TaskContainer.Task
import AppFrontend.Interface.TrainingLogsDisplay.TrainingLogger // NEW IMPORT
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ImageTrainer : Trainer {

    override lateinit var task: Task
    override lateinit var trainingData: Any
    override lateinit var interpreter: Interpreter
    override var currentEpoch: Int = 0

    private val checkpointManager = CheckpointManager()
    private val TAG = "ImageTrainer"

    override fun setUpTrainer(pTrainingData: Any?, pTask: Task) {
        Log.d(TAG, "--> setUpTrainer(): Initializing...")
        this.task = pTask

        if (pTrainingData !is Pair<*, *> || pTrainingData.first !is FloatBuffer || pTrainingData.second !is FloatBuffer) {
            Log.e(TAG, "Invalid training data format! Expected Pair<FloatBuffer, FloatBuffer>")
            throw IllegalArgumentException("Invalid training data format")
        }

        this.trainingData = pTrainingData
        Log.d(TAG, "--> setUpTrainer(): Loading TFLite model...")
        this.interpreter = loadModel()

        val availableSignatures = interpreter.signatureKeys.joinToString()
        Log.e(TAG, "=====================================================")
        Log.e(TAG, "AVAILABLE SIGNATURES IN THIS MODEL: [$availableSignatures]")
        Log.e(TAG, "=====================================================")

        Log.i(TAG, "Trainer successfully set up. Checking for previous checkpoints...")
        initializeWeights()
    }

    override fun loadModelFile(): MappedByteBuffer {
        val imageTask = task as Image_Task
        val modelFile = File("/data/data/org.fractal.app/files/", imageTask.MODEL_FILENAME)
        Log.d(TAG, "--> loadModelFile(): Looking for model at ${modelFile.absolutePath}")

        if (!modelFile.exists()) {
            throw Exception("Model file not found in internal storage: ${modelFile.absolutePath}")
        }

        val inputStream = FileInputStream(modelFile)
        val buffer = inputStream.channel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
        inputStream.close()
        Log.d(TAG, "--> loadModelFile(): Model mapped to memory successfully.")

        return buffer
    }

    override fun loadModel(): Interpreter {
        val buffer = loadModelFile()
        return Interpreter(buffer)
    }

    override fun initializeWeights() {
        Log.d(TAG, "--> initializeWeights(): Calling CheckpointManager...")
        currentEpoch = checkpointManager.loadCheckpoint(task, interpreter)
        Log.d(TAG, "--> initializeWeights(): Returned from CheckpointManager. Current Epoch: $currentEpoch")
    }

    override fun trainModel(callback: TrainingCallback?) {
        Log.d(TAG, "========== TRAIN MODEL STARTED ==========")
        TrainingLogger.currentStatus.postValue("Status: Training in Progress...")

        val imageTask = task as Image_Task

        @Suppress("UNCHECKED_CAST")
        val data = trainingData as Pair<FloatBuffer, FloatBuffer>
        val imageBatch = data.first
        val labelBatch = data.second

        // --- SAFE EXTRACTION START ---
        val shapeArray = imageTask.INPUT_SHAPE
        val imgHeight: Int
        val imgWidth: Int

        if (shapeArray.size == 2) {
            imgHeight = shapeArray[0]
            imgWidth = shapeArray[1]
        } else if (shapeArray.size >= 3) {
            imgHeight = shapeArray[1]
            imgWidth = shapeArray[2]
        } else {
            imgHeight = 28
            imgWidth = 28
        }
        // --- SAFE EXTRACTION END ---

        val numClasses = imageTask.NUM_CLASSES
        val numTrainings = imageTask.NUM_TRAININGS
        val numEpochs = imageTask.NUM_EPOCHS

        if (currentEpoch >= numEpochs) {
            Log.i(TAG, "Model is already fully trained for $numEpochs epochs. Skipping training phase.")
            callback?.onProgress(100)
            callback?.onStatusUpdate("Training Already Completed")
            TrainingLogger.currentStatus.postValue("Status: Training Already Completed")
            return
        }

        // --- TIME TRACKING SETUP ---
        val trainingStartTime = System.currentTimeMillis()
        var timeLeftStr = "Calculating..."

        // Calculate absolute total steps for accurate UI progress calculation
        val totalSteps = numEpochs * numTrainings
        var currentStep = currentEpoch * numTrainings

        // PRIME UI: Send 4 arguments
        callback?.onEpochUpdate(currentEpoch, numEpochs, 0f, timeLeftStr)

        val singleImageBuffer = ByteBuffer.allocateDirect(imgHeight * imgWidth * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val singleLabelBuffer = ByteBuffer.allocateDirect(numClasses * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val lossBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        val inputs = mutableMapOf<String, Any>("x" to singleImageBuffer, "y" to singleLabelBuffer)
        val outputs = mutableMapOf<String, Any>("loss" to lossBuffer)

        Log.d(TAG, "--> Entering Epoch Loop...")
        for (epoch in currentEpoch until numEpochs) {
            var lastLoss = 0f

            for (sampleIdx in 0 until numTrainings) {

                // --- NEW: SMART HARDWARE & PAUSE TRAP ---
                // We only do a deep hardware check every 50 steps to prevent battery drain and lag
                if (currentStep % 50 == 0) {
                    var hardwareIssue = callback?.checkLiveConditions()

                    // If hardware violates rules OR user paused, lock the thread
                    while (hardwareIssue != null || callback?.isPaused() == true) {
                        if (callback?.isCancelled() == true) {
                            Log.i(TAG, "Training cancelled by user during pause/hardware wait.")
                            TrainingLogger.currentStatus.postValue("Status: Cancelled by User")
                            return
                        }

                        if (hardwareIssue != null) {
                            callback?.onStatusUpdate(hardwareIssue) // e.g. "Standby: Awaiting Wi-Fi"
                            TrainingLogger.currentStatus.postValue("Status: $hardwareIssue")
                        } else {
                            callback?.onStatusUpdate("Training Paused")
                            TrainingLogger.currentStatus.postValue("Status: Training Paused")
                        }

                        Thread.sleep(3000) // Sleep 3 seconds before re-evaluating
                        hardwareIssue = callback?.checkLiveConditions()
                    }
                } else {
                    // For the 49 steps in between, do a super lightweight check just for manual pauses
                    while (callback?.isPaused() == true) {
                        if (callback?.isCancelled() == true) {
                            Log.i(TAG, "Training cancelled by user during pause.")
                            return
                        }
                        Thread.sleep(500)
                    }
                }

                // Final safety catch
                if (callback?.isCancelled() == true) {
                    Log.i(TAG, "Training cancelled by user.")
                    return
                }
                // ----------------------------------------

                try {
                    // 1. EXTRACT IMAGE
                    val imgPos = sampleIdx * imgHeight * imgWidth
                    imageBatch.position(imgPos)
                    val imgArray = FloatArray(imgHeight * imgWidth)
                    imageBatch.get(imgArray)
                    singleImageBuffer.put(imgArray).rewind()

                    // 2. EXTRACT LABEL
                    val lblPos = sampleIdx * numClasses
                    labelBatch.position(lblPos)
                    val lblArray = FloatArray(numClasses)
                    labelBatch.get(lblArray)
                    singleLabelBuffer.put(lblArray).rewind()

                    // 3. RUN INFERENCE
                    interpreter.runSignature(inputs, outputs, "train")

                    lossBuffer.rewind()
                    lastLoss = lossBuffer.get(0)
                    currentStep++

                    // Push percentage update to the UI diamond AND text every 100 steps
                    if (currentStep % 100 == 0) {
                        val percent = ((currentStep.toFloat() / totalSteps.toFloat()) * 100).toInt()

                        callback?.onProgress(percent)
                        callback?.onStatusUpdate("Training: $percent%")

                        // Build the log string exactly like your original Log.d
                        val logMessage = "Step $currentStep/$totalSteps | Epoch: $epoch | Loss: $lastLoss"
                        Log.d(TAG, logMessage)

                        // Push data dynamically to the UI Fragment
                        TrainingLogger.appendLog(logMessage)
                        TrainingLogger.stepsText.postValue("Steps Completed: $currentStep / $totalSteps")
                        TrainingLogger.lossText.postValue("Current Loss: $lastLoss")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "================ FATAL CRASH IN LOOP ================")
                    Log.e(TAG, "Failed at Sample Index: $sampleIdx")
                    throw e
                }
            }

            // --- CALCULATE TIME REMAINING ---
            val completedInSession = (epoch + 1) - currentEpoch
            val timeElapsed = System.currentTimeMillis() - trainingStartTime
            val avgTimePerEpoch = timeElapsed / completedInSession
            val remainingEpochs = numEpochs - (epoch + 1)
            val remainingMillis = avgTimePerEpoch * remainingEpochs

            if (remainingEpochs > 0) {
                val seconds = (remainingMillis / 1000) % 60
                val minutes = (remainingMillis / (1000 * 60)) % 60
                timeLeftStr = String.format("%dm %ds", minutes, seconds)
            } else {
                timeLeftStr = "Done"
            }

            Log.d(TAG, "--> Epoch $epoch complete. Saving checkpoint...")
            val completedEpochs = epoch + 1
            checkpointManager.createCheckpoint(task, interpreter, completedEpochs)

            TrainingLogger.appendLog("--> Epoch $epoch complete. Checkpoint saved.")

            // UPDATE UI: Send 4 arguments including the new Time Left string
            callback?.onEpochUpdate(completedEpochs, numEpochs, lastLoss, timeLeftStr)
        }

        // Guarantee the UI hits 100% when finished
        callback?.onProgress(100)
        callback?.onStatusUpdate("Training: 100%")
        TrainingLogger.currentStatus.postValue("Status: Training Completed Successfully")
        Log.i(TAG, "Training cycle completely finished! All $numEpochs epochs done.")
    }
}