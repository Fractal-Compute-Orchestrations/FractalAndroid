package AppFrontend.Interface.TrainingLogsDisplay

import androidx.lifecycle.MutableLiveData

object TrainingLogger {
    val consoleLogs = MutableLiveData("========== TRAIN MODEL STARTED ==========\nWaiting for data injection...")
    val currentStatus = MutableLiveData("Status: Training in Progress...")
    val stepsText = MutableLiveData("Steps Completed: 0 / 0")
    val lossText = MutableLiveData("Current Loss: Calculating...")

    fun appendLog(logText: String) {
        val current = consoleLogs.value ?: ""

        // Safety: Keep only the last 150 lines so the app doesn't run out of memory over long training sessions
        val lines = current.split("\n")
        val safeLines = if (lines.size > 150) lines.takeLast(150) else lines

        consoleLogs.postValue(safeLines.joinToString("\n") + "\n$logText")
    }
}