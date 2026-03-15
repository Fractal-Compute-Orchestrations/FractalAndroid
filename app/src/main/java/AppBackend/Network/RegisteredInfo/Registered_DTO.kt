package AppBackend.Network.RegisteredInfo

data class Registered_DTO(
    var username: String = "Loading...",
    var email: String = "Loading...",
    var joinedOn: String = "Loading...",
    var platform: String = "Android",
    var hardwareID: String = "Unknown",
    var serialNumber: String = "Unknown",
    var processor: String = "Unknown",
    var storage: String = "0 GB",
    var totalRam: String = "0 GB",
    var androidVersion: String = "Unknown",
    var macAddress: String = "Unavailable",
    // "not_registered" | "registered" | "unregistered"
    var status: String = "not_registered"
)