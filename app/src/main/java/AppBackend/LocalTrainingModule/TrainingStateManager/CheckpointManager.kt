package AppBackend.LocalTrainingModule.TrainingStateManager

import android.util.Log
import AppBackend.TaskContainer.Image_Task
import AppBackend.TaskContainer.Task
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File

class CheckpointManager {
    private val TAG = "CheckpointManager"

    fun createCheckpoint(task: Task, interpreter: Interpreter, completedEpochs: Int): Boolean {
        val imageTask = task as Image_Task
        val ckptFile = File("/data/data/org.fractal.app/files/", imageTask.CKPT_FILENAME)
        // We create a tiny metadata file next to the weights to remember the epoch number
        val metaFile = File("/data/data/org.fractal.app/files/", "${imageTask.task_Id}_meta.json")

        return try {
            // 1. Save Weights (TFLite automatically OVERWRITES the old checkpoint file)
            val inputs = mutableMapOf<String, Any>("checkpoint_path" to ckptFile.absolutePath)
            interpreter.runSignature(inputs, mutableMapOf<String, Any>(), "save")

            // 2. Save Metadata (Overwrites old metadata)
            val json = JSONObject()
            json.put("task_Id", imageTask.task_Id)
            json.put("lastEpoch", completedEpochs)
            json.put("checkpointTimestamp", System.currentTimeMillis())
            metaFile.writeText(json.toString())

            Log.i(TAG, "Checkpoint & Metadata saved successfully. Completed Epochs: $completedEpochs")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save checkpoint: ${e.message}")
            false
        }
    }

    fun loadCheckpoint(task: Task, interpreter: Interpreter): Int {
        val imageTask = task as Image_Task
        val ckptFile = File("/data/data/org.fractal.app/files/", imageTask.CKPT_FILENAME)
        val metaFile = File("/data/data/org.fractal.app/files/", "${imageTask.task_Id}_meta.json")

        // If either file is missing, we must start from scratch at Epoch 0
        if (!ckptFile.exists() || !metaFile.exists()) {
            Log.i(TAG, "No valid checkpoint found. Starting fresh from Epoch 0.")
            return 0
        }

        return try {
            // 1. Restore Weights
            val inputs = mutableMapOf<String, Any>("checkpoint_path" to ckptFile.absolutePath)
            interpreter.runSignature(inputs, mutableMapOf<String, Any>(), "restore")

            // 2. Read Metadata to find out exactly where we left off
            val jsonText = metaFile.readText()
            val json = JSONObject(jsonText)
            val lastEpoch = json.optInt("lastEpoch", 0)

            Log.i(TAG, "Checkpoint restored successfully. Resuming from Epoch $lastEpoch")
            lastEpoch
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore checkpoint: ${e.message}")
            0 // If corrupted, safely fallback to starting over
        }
    }
}