package AppBackend.Network

import com.google.firebase.remoteconfig.FirebaseRemoteConfig

class networkConfig_ini {
    var API_KEY: String = ""

//    var API_KEY: String = "",
//    var SERVER_IP: String = "fractal-grid.duckdns.org",
//    var SERVER_PORT: String = "5000"

    // Dynamically pulls from Firebase, falls back to default if Firebase hasn't loaded yet
    val SERVER_IP: String
        get() = FirebaseRemoteConfig.getInstance().getString("server_ip").takeIf { it.isNotEmpty() } ?: "fractal-grid.duckdns.org"

    val SERVER_PROTOCOL: String
        get() = FirebaseRemoteConfig.getInstance().getString("server_protocol").takeIf { it.isNotEmpty() } ?: "https"

    val SERVER_PORT: String
        get() = FirebaseRemoteConfig.getInstance().getString("server_port")

    // Smart helper that perfectly formats the URL for your DAO
    fun getBaseUrl(): String {
        val portStr = if (SERVER_PORT.isNotEmpty()) ":$SERVER_PORT" else ""
        return "$SERVER_PROTOCOL://$SERVER_IP$portStr"
    }
}