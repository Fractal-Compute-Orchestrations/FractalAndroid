package AppBackend.Network.RegisteredInfo

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.net.NetworkInterface
import java.util.Collections

class RegistrationManager(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var liveUpdateRunnable: Runnable? = null

    // Call this from your Fragment to get live real-time updates of the data
    fun startLiveUpdates(onUpdate: (Registered_DTO) -> Unit) {
        liveUpdateRunnable = object : Runnable {
            override fun run() {
                onUpdate(generateNewRegistrationData())
                handler.postDelayed(this, 2000) // Re-fetch and update every 2 seconds
            }
        }
        handler.post(liveUpdateRunnable!!)
    }

    // Call this in your Fragment's onDestroy() to prevent memory leaks
    fun stopLiveUpdates() {
        liveUpdateRunnable?.let { handler.removeCallbacks(it) }
    }

    fun generateNewRegistrationData(): Registered_DTO {
        return Registered_DTO(
            username = "Loading...",
            email = "Loading...",
            phoneNumber = "Unknown",
            carrier = "Unknown",
            timeZone = java.util.TimeZone.getDefault().id, // <-- Captures local timezone (e.g., "Asia/Karachi")
            joinedOn = "Loading...",
            platform = "Android",
            hardwareID = getHardwareId(),
            serialNumber = getSerial(),
            processor = getProcessorName(),
            storage = getTotalInternalMemorySize(),
            totalRam = getTotalRAM(),
            androidVersion = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            macAddress = getMacAddress()
        )
    }

    // --- HELPER FUNCTIONS ---

    private fun getHardwareId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            "Unknown ID"
        }
    }

    private fun getSerial(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.MODEL
            } else {
                Build.SERIAL
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getTotalRAM(): String {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        return formatSize(memInfo.availMem) + " Free / " + formatSize(memInfo.totalMem)
    }

    private fun getTotalInternalMemorySize(): String {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong

        return formatSize(availableBlocks * blockSize) + " Free"
    }

    private fun getProcessorName(): String {
        try {
            val br = BufferedReader(FileReader("/proc/cpuinfo"))
            var line: String?
            while (br.readLine().also { line = it } != null) {
                if (line!!.contains("Hardware")) {
                    return line!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }[1].trim()
                }
            }
            br.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return Build.BOARD
    }

    private fun getMacAddress(): String {
        try {
            val all = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in all) {
                if (!nif.name.equals("wlan0", ignoreCase = true)) continue
                val macBytes = nif.hardwareAddress ?: return "Unavailable"
                val res1 = StringBuilder()
                for (b in macBytes) {
                    res1.append(String.format("%02X:", b))
                }
                if (res1.isNotEmpty()) {
                    return res1.deleteCharAt(res1.length - 1).toString()
                }
            }
        } catch (ex: Exception) {
            return "Unavailable"
        }
        return "02:00:00:00:00:00"
    }

    private fun formatSize(size: Long): String {
        val kb = 1024L
        val mb = kb * 1024L
        val gb = mb * 1024L
        return when {
            size >= gb -> String.format("%.1f GB", size.toDouble() / gb)
            size >= mb -> String.format("%.1f MB", size.toDouble() / mb)
            else -> String.format("%.1f KB", size.toDouble() / kb)
        }
    }

    fun loadRegistrationData(): Registered_DTO {
        return generateNewRegistrationData()
    }

    fun saveRegistrationData() {}
}