package AppBackend.LocalTrainingModule.TrainingExecutor

import AppBackend.TaskContainer.Task

interface TrainingCallback {
    fun onProgress(percentage: Int)
    fun onStatusUpdate(message: String)
    fun onValidationUpdate(result: String)
    fun onEpochUpdate(completedEpochs: Int, totalEpochs: Int, loss: Float, timeLeft: String)

    fun isPaused(): Boolean
    fun isCancelled(): Boolean
    fun onWaitingStateChanged(isWaiting: Boolean)
    fun checkLiveConditions(): String?
    fun onTaskAcquired(task: Task) {}

    // NEW: Triggered when the final model is safely on the server
    fun onUploadSuccess() {}
}