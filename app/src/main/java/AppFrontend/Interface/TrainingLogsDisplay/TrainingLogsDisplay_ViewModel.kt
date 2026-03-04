package AppFrontend.Interface.TrainingLogsDisplay

import androidx.lifecycle.ViewModel

class TrainingLogsDisplay_ViewModel : ViewModel() {
    // Connect directly to the global logger
    val consoleLogs = TrainingLogger.consoleLogs
    val currentStatus = TrainingLogger.currentStatus
    val stepsText = TrainingLogger.stepsText
    val lossText = TrainingLogger.lossText
}