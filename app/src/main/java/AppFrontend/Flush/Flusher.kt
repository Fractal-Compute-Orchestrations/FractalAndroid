package AppFrontend.Flush

import android.util.Log
import AppBackend.TaskContainer.Image_Task
import AppBackend.TaskContainer.Task
import java.io.File

class Flusher {
    private val TAG = "Flusher"
    private val filesDir = "/data/data/org.fractal.app/files/"

    fun flushCheckpoint(task: Task) {
        val imageTask = task as Image_Task
        val ckptFile = File(filesDir, imageTask.CKPT_FILENAME)
        val metaFile = File(filesDir, "${imageTask.task_Id}_meta.json")

        if (ckptFile.exists() && ckptFile.delete()) {
            Log.d(TAG, "--> Flushed checkpoint weights: ${ckptFile.name}")
        }
        if (metaFile.exists() && metaFile.delete()) {
            Log.d(TAG, "--> Flushed checkpoint metadata: ${metaFile.name}")
        }
    }

    fun flushCurrentTask(task: Task) {
        val imageTask = task as Image_Task
        val imgFile = File(filesDir, imageTask.TRAIN_IMAGES_FILENAME)
        val lblFile = File(filesDir, imageTask.TRAIN_LABELS_FILENAME)
        val modelFile = File(filesDir, imageTask.MODEL_FILENAME)

        if (imgFile.exists() && imgFile.delete()) Log.d(TAG, "--> Flushed training images: ${imgFile.name}")
        if (lblFile.exists() && lblFile.delete()) Log.d(TAG, "--> Flushed training labels: ${lblFile.name}")
        if (modelFile.exists() && modelFile.delete()) Log.d(TAG, "--> Flushed server model: ${modelFile.name}")
    }

    fun flushAll(task: Task): Boolean {
        try {
            Log.i(TAG, "Initiating full storage flush for Task ID: ${task.task_Id}...")

            // 1. Delete the explicitly named files
            flushCurrentTask(task)
            flushCheckpoint(task)

            // 2. Directory Sweeper: Look for ANY leftover files with the task prefix
            val directory = File(filesDir)
            if (directory.exists() && directory.isDirectory) {
                val prefix = "${task.task_Id}_"
                directory.listFiles()?.forEach { file ->
                    if (file.name.startsWith(prefix)) {
                        if (file.delete()) {
                            Log.d(TAG, "--> Sweeper deleted orphaned file: ${file.name}")
                        }
                    }
                }
            }

            Log.i(TAG, "Flush complete! Device storage is clean and ready for the next task.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to completely flush files: ${e.message}")
            return false
        }
    }
}