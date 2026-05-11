package AppFrontend.Interface.Auth.DeviceAuthorization

data class LoginRegister_DTO (
    var username: String,
    var password: String,
    var email: String,
    var phoneNumber: String,
    var carrier: String
)