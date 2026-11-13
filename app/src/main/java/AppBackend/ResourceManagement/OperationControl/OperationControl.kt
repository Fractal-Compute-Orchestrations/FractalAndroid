package AppBackend.ResourceManagement.OperationControl

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import AppGlobal.app_config
import AppBackend.LocalTrainingModule.TrainingExecutor.TrainingCallback
import com.example.fractal.FractalApplication
import java.util.Calendar

class OperationControl(private val context: Context) {

    private val globalState = (context.applicationContext as FractalApplication).globalState

    @RequiresApi(Build.VERSION_CODES.M)
    fun waitForOptimalConditions(callback: TrainingCallback?): Boolean {
        callback?.onWaitingStateChanged(true)

        while (callback?.isCancelled() == false) {
            val (allowed, smartMessage) = evaluateDeviceState()

            if (allowed) {
                callback?.onWaitingStateChanged(false)
                return true
            } else {
                callback?.onStatusUpdate(smartMessage)
                Thread.sleep(3000) // Re-check every 3 seconds
            }
        }

        callback?.onWaitingStateChanged(false)
        callback?.onStatusUpdate("Process Aborted by User")
        return false
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun waitForNetworkToUpload(callback: TrainingCallback?): Boolean {
        callback?.onWaitingStateChanged(true)

        while (callback?.isCancelled() == false) {
            val (networkAllowed, networkMessage) = evaluateNetworkOnly()

            if (networkAllowed) {
                callback?.onWaitingStateChanged(false)
                return true
            } else {
                callback?.onStatusUpdate("Upload Paused: $networkMessage")
                Thread.sleep(3000)
            }
        }

        callback?.onWaitingStateChanged(false)
        callback?.onStatusUpdate("Upload Aborted by User")
        return false
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun evaluateNetworkOnly(): Pair<Boolean, String> {
        val config = globalState.appConfig ?: AppGlobal.app_config()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        // Case 2: On Wifi
        if (config.onWifi && !config.onData && !isWifi) {
            return Pair(false, "Awaiting Wi-Fi...")
        }
        // Case 3: On Data
        if (config.onData && !config.onWifi && !isCellular) {
            return Pair(false, "Awaiting Cellular Data...")
        }
        // Case 4: Both Wifi and Data
        if (config.onWifi && config.onData && !isWifi && !isCellular) {
            return Pair(false, "Awaiting Network Connection...")
        }
        // Fallback for Upload Phase: If both are false, we STILL need some internet to upload to the server
        if (!config.onWifi && !config.onData && !isWifi && !isCellular) {
            return Pair(false, "Offline. Waiting for Network...")
        }

        return Pair(true, "Network available.")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun evaluateDeviceState(): Pair<Boolean, String> {
        val config = globalState.appConfig ?: AppGlobal.app_config()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isInteractive = if (config.idleTimeUtilization) powerManager.isInteractive else false

        if (config.idleTimeUtilization && isInteractive) {
            return Pair(false, "Standby: Waiting for device to be idle (Screen Off)")
        }

        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryStatus == null) return Pair(true, "Optimal conditions met.")
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (scale > 0) (level * 100 / scale) else 0
        val batteryTempTenthsC = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val isThermalSevere = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        } else {
            false
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        return evaluateConditions(
            config = config,
            isInteractive = isInteractive,
            batteryPct = batteryPct,
            isCharging = isCharging,
            batteryTempTenthsC = batteryTempTenthsC,
            isThermalSevere = isThermalSevere,
            isWifi = isWifi,
            isCellular = isCellular,
        )
    }

    // Add this to the bottom of OperationControl.kt
    @RequiresApi(Build.VERSION_CODES.M)
    fun getViolationMessage(): String? {
        val (allowed, msg) = evaluateDeviceState()
        return if (allowed) null else msg
    }

    companion object {
        const val MAX_BATTERY_TEMP_TENTHS_C = 400

        @JvmStatic
        fun evaluateConditions(
            config: app_config,
            isInteractive: Boolean,
            batteryPct: Int,
            isCharging: Boolean,
            batteryTempTenthsC: Int,
            isThermalSevere: Boolean,
            isWifi: Boolean,
            isCellular: Boolean,
        ): Pair<Boolean, String> {
            if (config.idleTimeUtilization && isInteractive) {
                return Pair(false, "Standby: Waiting for device to be idle (Screen Off)")
            }
            if (batteryTempTenthsC > MAX_BATTERY_TEMP_TENTHS_C) {
                val tempC = batteryTempTenthsC / 10.0
                return Pair(false, "Standby: Battery temperature elevated ($tempC C > 40.0 C)")
            }
            if (isThermalSevere) {
                return Pair(false, "Standby: Device thermal throttling active")
            }
            if (batteryPct < config.minChargeLimit) {
                return Pair(false, "Standby: Battery too low (Need ${config.minChargeLimit}%)")
            }
            if (config.onChargingExclusive && !isCharging) {
                return Pair(false, "Standby: Awaiting charger connection...")
            }
            if (config.onWifi && !config.onData && !isWifi) {
                return Pair(false, "Standby: Awaiting stable Wi-Fi...")
            }
            if (config.onData && !config.onWifi && !isCellular) {
                return Pair(false, "Standby: Awaiting Cellular Data...")
            }
            if (config.onWifi && config.onData && !isWifi && !isCellular) {
                return Pair(false, "Standby: Awaiting Network Connection...")
            }
            return Pair(true, "Optimal conditions met.")
        }
    }
}