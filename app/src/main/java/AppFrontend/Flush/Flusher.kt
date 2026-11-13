package AppFrontend.Flush

import android.util.Log
import AppBackend.TaskContainer.Image_Task
import AppBackend.TaskContainer.Task
import AppGlobal.Utils.FractalPaths
import java.io.File

class Flusher(
    private val baseFilesDir: File? = null,
) {
    private val TAG = "Flusher"
    private val resolvedFilesDir: File
        get() = FractalPaths.resolveFilesDir(baseFilesDir)

    fun flushCheckpoint(task: Task) {
        val imageTask = task as Image_Task
        val ckptFile = FractalPaths.resolveFile(imageTask.CKPT_FILENAME, baseFilesDir)
        val metaFile = FractalPaths.resolveFile("${imageTask.task_Id}_meta.json", baseFilesDir)

        if (ckptFile.exists() && ckptFile.delete()) {
            Log.d(TAG, "--> Flushed checkpoint weights: ${ckptFile.name}")
        }
        if (metaFile.exists() && metaFile.delete()) {
            Log.d(TAG, "--> Flushed checkpoint metadata: ${metaFile.name}")
        }
    }

    fun flushCurrentTask(task: Task) {
        val imageTask = task as Image_Task
        val imgFile = FractalPaths.resolveFile(imageTask.TRAIN_IMAGES_FILENAME, baseFilesDir)
        val lblFile = FractalPaths.resolveFile(imageTask.TRAIN_LABELS_FILENAME, baseFilesDir)
        val modelFile = FractalPaths.resolveFile(imageTask.MODEL_FILENAME, baseFilesDir)

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
            val directory = resolvedFilesDir
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