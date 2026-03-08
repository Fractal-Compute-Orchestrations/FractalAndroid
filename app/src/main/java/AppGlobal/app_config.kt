package AppGlobal

data class app_config(
    var onWifi: Boolean = true,
    var onData: Boolean = true,
    var overNightUtilization: Boolean = false,
    var idleTimeUtilization: Boolean = false, // Added to match screenshot
    var minChargeLimit: Int = 20,            // Changed Boolean -> Int for Slider
    var maxChargeLimit: Boolean = false,
    var onChargingExclusive: Boolean = false,
    var isLoggedIn: Boolean = false
)