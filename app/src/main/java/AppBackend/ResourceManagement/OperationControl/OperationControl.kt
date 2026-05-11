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

        // --- CASE 6: Idle-Time Utilization (Screen is OFF) ---
        if (config.idleTimeUtilization) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isInteractive) { // isInteractive == true means the screen is ON
                return Pair(false, "Standby: Waiting for device to be idle (Screen Off)")
            }
        }

        // --- Get Battery Info ---
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryStatus == null) return Pair(true, "Optimal conditions met.") // Default to true if battery check fails
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (scale > 0) (level * 100 / scale) else 0

        // --- CASE 7: Minimum Charge Limit ---
        if (batteryPct < config.minChargeLimit) {
            return Pair(false, "Standby: Battery too low (Need ${config.minChargeLimit}%)")
        }

        // --- CASE 8: On-Charging Exclusive ---
        if (config.onChargingExclusive && !isCharging) {
            return Pair(false, "Standby: Awaiting charger connection...")
        }

        // --- CASE 1, 2, 3, 4: Network Rules for Training ---
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

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

    // Add this to the bottom of OperationControl.kt
    @RequiresApi(Build.VERSION_CODES.M)
    fun getViolationMessage(): String? {
        val (allowed, msg) = evaluateDeviceState()
        return if (allowed) null else msg
    }
}