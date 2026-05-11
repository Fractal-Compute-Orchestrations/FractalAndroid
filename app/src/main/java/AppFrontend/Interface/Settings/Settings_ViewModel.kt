package AppFrontend.Interface.Settings

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import AppGlobal.Utils.FileOperations
import com.example.fractal.FractalApplication
import com.example.fractal.OvernightManagerService

class Settings_ViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FractalApplication
    private val globalState = app.globalState
    private val fileOps = FileOperations(application)

    // Helper to get current config safely
    fun getConfig() = globalState.appConfig ?: AppGlobal.app_config()

    private fun saveConfig() {
        globalState.appConfig = getConfig()
        fileOps.writeJson("app_config.json", globalState.appConfig)
    }

    // --- Actions ---

    fun toggleWifi() {
        val config = getConfig()
        if (config.onWifi) {
            // Trying to turn Wifi OFF. Only allow if Data is ON.
            if (config.onData) config.onWifi = false
        } else {
            // Turning Wifi ON is always allowed.
            config.onWifi = true
        }
        saveConfig()
    }

    fun toggleData() {
        val config = getConfig()
        if (config.onData) {
            // Trying to turn Data OFF. Only allow if Wifi is ON.
            if (config.onWifi) config.onData = false
        } else {
            // Turning Data ON is always allowed.
            config.onData = true
        }
        saveConfig()
    }

    fun toggleOvernight() {
        val config = getConfig()
        config.overNightUtilization = !config.overNightUtilization
        saveConfig()

        // ── LIVE SERVICE CONTROL ──
        // Instantly starts or stops the Supervisor when the user taps the toggle
        val serviceIntent = Intent(app, OvernightManagerService::class.java)

        if (config.overNightUtilization) {
            ContextCompat.startForegroundService(app, serviceIntent)
        } else {
            app.stopService(serviceIntent)
        }
    }

    fun toggleIdle() {
        val config = getConfig()
        config.idleTimeUtilization = !config.idleTimeUtilization
        saveConfig()
    }

    fun toggleChargingExclusive() {
        val config = getConfig()
        config.onChargingExclusive = !config.onChargingExclusive
        saveConfig()
    }

    fun updateChargeLimit(value: Int) {
        val config = getConfig()
        config.minChargeLimit = value
        saveConfig()
    }
}