package AppBackend.Network.Server_DAO

import android.util.Log
import AppBackend.Network.ModelUpdateTransmission.ModelTransmission_DTO
import AppBackend.Network.RegisteredInfo.Registered_DTO
import AppBackend.Network.networkConfig_ini
import AppBackend.TaskContainer.Image_Task
import AppBackend.TaskContainer.Task
import AppBackend.TaskContainer.TaskType
import AppFrontend.Interface.Auth.DeviceAuthorization.LoginRegister_DTO
import AppFrontend.Interface.Auth.DeviceUnregister.Unregister_DTO
import AppFrontend.Interface.Auth.ForgetPassword.ForgetPassword_DTO
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

import android.util.Base64

class Server_DAO(var networkConfig: networkConfig_ini = networkConfig_ini()) : Auth, ModelTransmission, TaskPopulate {

    private val TAG = "Server_DAO"

    fun POST_Ping(taskID: String, pingStatus: Boolean){ }

    // --- NEW: Added deviceId parameter to pass to the server ---
    override fun GET_Task(flushPrevious: Boolean, deviceId: String): Task? {
        try {
            val serverIp = networkConfig.SERVER_IP

            val url = URL("http://$serverIp:5000/api/task/current?device_id=$deviceId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                Log.i(TAG, "================ RAW TASK JSON ================")
                Log.i(TAG, response)
                Log.i(TAG, "===============================================")

                val json = JSONObject(response)
                val task = Image_Task()


                task.task_Id = json.optString("task_Id", "-1")

                // 2. Task Type
                task.taskType = if (json.optString("taskType") == "ActiveTask") TaskType.ActiveTask else TaskType.PassiveTask

                // 3. Expiration Date
                val dateString = json.optString("task_expire_date", "")
                if (dateString.isNotEmpty()) {
                    try {
                        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        task.task_expire_date = format.parse(dateString) ?: Date()
                    } catch (e: Exception) {
                        task.task_expire_date = Date()
                    }
                } else {
                    task.task_expire_date = Date()
                }

                // 4. Bools and Strings
                task.task_completion_status = json.optBoolean("task_completion_status", false)
                task.CKPT_FILENAME = json.optString("CKPT_FILENAME", "checkpoint.ckpt")
                task.MODEL_FILENAME = json.optString("MODEL_FILENAME", "model_server.tflite")
                task.TRAIN_IMAGES_FILENAME = json.optString("TRAIN_IMAGES_FILENAME", "train_images_server.bin")
                task.TRAIN_LABELS_FILENAME = json.optString("TRAIN_LABELS_FILENAME", "train_labels_server.bin")

                // 5. Integers
                task.NUM_EPOCHS = json.optInt("NUM_EPOCHS", 20)
                task.BATCH_SIZE = json.optInt("BATCH_SIZE", 100)
                task.NUM_TRAININGS = json.optInt("NUM_TRAININGS", 6000)
                task.NUM_CLASSES = json.optInt("NUM_CLASSES", 10)

                // 6. JSON Arrays
                val trainingTypeJsonArray = json.optJSONArray("training_type")
                val trainingTypeList = mutableListOf<String>()
                if (trainingTypeJsonArray != null) {
                    for (i in 0 until trainingTypeJsonArray.length()) {
                        trainingTypeList.add(trainingTypeJsonArray.getString(i))
                    }
                }
                task.training_type = trainingTypeList

                val shapeJsonArray = json.optJSONArray("INPUT_SHAPE")
                if (shapeJsonArray != null) {
                    val shapeList = Array(shapeJsonArray.length()) { 0 }
                    for (i in 0 until shapeJsonArray.length()) {
                        shapeList[i] = shapeJsonArray.getInt(i)
                    }
                    task.INPUT_SHAPE = shapeList
                }

                // 7. JSON Objects (Tensors)
                val inputTensorJson = json.optJSONObject("input_tensor_name")
                val inputMap = mutableMapOf<List<String>, Any>()
                inputTensorJson?.keys()?.forEach { key ->
                    inputMap[listOf(key)] = inputTensorJson.getString(key)
                }
                task.input_tensor_name = inputMap

                val outputTensorJson = json.optJSONObject("output_tensor_name")
                val outputMap = mutableMapOf<List<String>, Any>()
                outputTensorJson?.keys()?.forEach { key ->
                    outputMap[listOf(key)] = outputTensorJson.getString(key)
                }
                task.output_tensor_name = outputMap

                // ==========================================================
                // COMPREHENSIVE FIELD LOGGING
                // ==========================================================
                Log.i(TAG, "================ POPULATED TASK DETAILS ================")
                Log.i(TAG, "task_Id:                  ${task.task_Id}")
                Log.i(TAG, "taskType:                 ${task.taskType}")
                Log.i(TAG, "task_expire_date:         ${task.task_expire_date}")
                Log.i(TAG, "task_completion_status:   ${task.task_completion_status}")
                Log.i(TAG, "CKPT_FILENAME:            ${task.CKPT_FILENAME}")
                Log.i(TAG, "training_type:            ${task.training_type}")
                Log.i(TAG, "NUM_EPOCHS:               ${task.NUM_EPOCHS}")
                Log.i(TAG, "MODEL_FILENAME:           ${task.MODEL_FILENAME}")
                Log.i(TAG, "input_tensor_name:        ${task.input_tensor_name}")
                Log.i(TAG, "output_tensor_name:       ${task.output_tensor_name}")
                Log.i(TAG, "BATCH_SIZE:               ${task.BATCH_SIZE}")
                Log.i(TAG, "NUM_TRAININGS:            ${task.NUM_TRAININGS}")
                Log.i(TAG, "NUM_CLASSES:              ${task.NUM_CLASSES}")
                Log.i(TAG, "TRAIN_IMAGES_FILENAME:    ${task.TRAIN_IMAGES_FILENAME}")
                Log.i(TAG, "TRAIN_LABELS_FILENAME:    ${task.TRAIN_LABELS_FILENAME}")
                Log.i(TAG, "INPUT_SHAPE:              ${task.INPUT_SHAPE.joinToString(", ", "[", "]")}")
                Log.i(TAG, "========================================================")

                return task
            } else {
                Log.e(TAG, "Failed to fetch task. HTTP Code: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching task: ${e.message}")
        }
        return null
    }

    // ... (POST_UploadModelToServer and other methods remain exactly the same) ...
    override fun POST_UploadModelToServer(modeltransmissionDto: ModelTransmission_DTO): Boolean {
        val serverIp = networkConfig.SERVER_IP
        val imageTask = modeltransmissionDto.task as Image_Task
        val taskId = imageTask.task_Id
        val ckptFilename = imageTask.CKPT_FILENAME
        val uploadFile = File("/data/data/com.example.fractal/files/", ckptFilename)

        if (!uploadFile.exists()) {
            Log.e(TAG, "Upload Failed: Checkpoint file does not exist at ${uploadFile.absolutePath}")
            return false
        }

        val taskJson = JSONObject().apply {
            put("task_Id", taskId)
            put("taskType", if (imageTask.taskType == TaskType.ActiveTask) "ActiveTask" else "PassiveTask")

            val trainingArray = org.json.JSONArray()
            imageTask.training_type.forEach { trainingArray.put(it) }
            put("training_type", trainingArray)

            put("CKPT_FILENAME", imageTask.CKPT_FILENAME)
            put("MODEL_FILENAME", imageTask.MODEL_FILENAME)
            put("NUM_EPOCHS", imageTask.NUM_EPOCHS)
            put("BATCH_SIZE", imageTask.BATCH_SIZE)
            put("NUM_TRAININGS", imageTask.NUM_TRAININGS)
            put("NUM_CLASSES", imageTask.NUM_CLASSES)

            val shapeArray = org.json.JSONArray()
            imageTask.INPUT_SHAPE.forEach { shapeArray.put(it) }
            put("INPUT_SHAPE", shapeArray)
        }

        val boundary = "FormBoundary" + System.currentTimeMillis()
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        try {
            val url = URL("http://$serverIp:5000/api/model/upload")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doInput = true
            conn.doOutput = true
            conn.useCaches = false
            conn.setRequestProperty("Connection", "Keep-Alive")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val dos = DataOutputStream(conn.outputStream)

            fun writeTextPart(text: String) {
                dos.write(text.toByteArray(Charsets.UTF_8))
            }

            writeTextPart(twoHyphens + boundary + lineEnd)
            writeTextPart("Content-Disposition: form-data; name=\"task_Id\"" + lineEnd)
            writeTextPart(lineEnd)
            writeTextPart(taskId.toString() + lineEnd)

            writeTextPart(twoHyphens + boundary + lineEnd)
            writeTextPart("Content-Disposition: form-data; name=\"task_json\"" + lineEnd)
            writeTextPart(lineEnd)
            writeTextPart(taskJson.toString() + lineEnd)

            writeTextPart(twoHyphens + boundary + lineEnd)
            writeTextPart("Content-Disposition: form-data; name=\"model_file\"; filename=\"$ckptFilename\"" + lineEnd)
            writeTextPart("Content-Type: application/octet-stream" + lineEnd)
            writeTextPart(lineEnd)

            val fileInputStream = FileInputStream(uploadFile)
            val bufferSize = 1024 * 1024
            val buffer = ByteArray(bufferSize)
            var bytesRead = fileInputStream.read(buffer, 0, bufferSize)

            while (bytesRead > 0) {
                dos.write(buffer, 0, bytesRead)
                bytesRead = fileInputStream.read(buffer, 0, bufferSize)
            }
            fileInputStream.close()

            writeTextPart(lineEnd)
            writeTextPart(twoHyphens + boundary + twoHyphens + lineEnd)
            dos.flush()
            dos.close()

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.i(TAG, "Server accepted the uploaded model and JSON successfully!")
                return true
            } else {
                Log.e(TAG, "Server rejected the upload. HTTP Response Code: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during model upload: ${e.message}")
        }
        return false
    }

    override fun POST_RegisterLogin(registeredDto: Registered_DTO, loginregisterDto: LoginRegister_DTO): Boolean {
        val auth = FirebaseAuth.getInstance()
        val email = loginregisterDto.email
        val password = loginregisterDto.password
        val username = loginregisterDto.username

        try {
            // 1. ATTEMPT LOGIN FIRST
            val loginTask = auth.signInWithEmailAndPassword(email, password)
            Tasks.await(loginTask)

            val user = auth.currentUser
            user?.reload()?.let { Tasks.await(it) }

            if (user != null && user.isEmailVerified) {
                Log.i(TAG, "Login Successful and Email is Verified!")
                return true
            } else {
                try {
                    user?.sendEmailVerification()?.let { Tasks.await(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Email already sent recently, skipping resend.")
                }
                throw Exception("AWAITING_VERIFICATION:Please verify your email to continue. Link sent to $email.")
            }

        } catch (loginException: Exception) {

            if (loginException.message?.startsWith("AWAITING_VERIFICATION:") == true) {
                throw loginException
            }

            // 2. REGISTRATION FALLBACK
            try {
                val regTask = auth.createUserWithEmailAndPassword(email, password)
                Tasks.await(regTask)

                val user = auth.currentUser
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(username)
                    .build()
                user?.updateProfile(profileUpdates)?.let { Tasks.await(it) }

                // ── Save user profile to Firestore keyed by email ─────────────────
                try {
                    val firestore = FirebaseFirestore.getInstance()
                    val userData = hashMapOf(
                        "username" to username,
                        "email"    to email
                    )
                    val firestoreTask = firestore
                        .collection("users")
                        .document(email)
                        .set(userData)
                    Tasks.await(firestoreTask)
                    Log.i(TAG, "User profile saved to Firestore for: $email")

                    // ── Send full registered DTO now that Firebase fields are known ─
                    registeredDto.username = username
                    registeredDto.email    = email
                    registeredDto.joinedOn = user?.metadata?.creationTimestamp?.let { timestamp ->
                        java.text.SimpleDateFormat("dd MMM, yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(timestamp))
                    } ?: "N/A"
                    POST_SendRegisteredInfo(registeredDto)
                    // ──────────────────────────────────────────────────────────────

                } catch (e: Exception) {
                    // Non-fatal — auth still succeeded, don't block registration
                    Log.w(TAG, "Firestore profile save failed (non-fatal): ${e.message}")
                }

                try {
                    user?.sendEmailVerification()?.let { Tasks.await(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send initial verification email: ${e.message}")
                }

                throw Exception("AWAITING_VERIFICATION:Account created! Check inbox ($email) to verify.")

            } catch (regException: Exception) {
                if (regException.message?.startsWith("AWAITING_VERIFICATION:") == true) {
                    throw regException
                }

                if (regException is FirebaseAuthUserCollisionException || regException.message?.contains("email address is already in use") == true) {
                    throw Exception("Incorrect password for existing account.")
                } else {
                    throw Exception(regException.localizedMessage ?: "Registration Failed.")
                }
            }
        }
    }

    override fun GET_RegisteredInfo(registeredDto: Registered_DTO): Registered_DTO {
        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            // User is logged in, pull their data
            registeredDto.username = if (!user.displayName.isNullOrEmpty()) user.displayName!! else "Authorized User"
            registeredDto.email = user.email ?: "Unknown Email"

            registeredDto.joinedOn = user.metadata?.creationTimestamp?.let { timestamp ->
                val sdf = java.text.SimpleDateFormat("dd MMM, yyyy", java.util.Locale.getDefault())
                sdf.format(java.util.Date(timestamp))
            } ?: "N/A"
        } else {
            // User is NOT logged in, provide clean fallback text
            registeredDto.username = "Unregistered Device"
            registeredDto.email = "Not Authenticated"
            registeredDto.joinedOn = "N/A"
        }

        return registeredDto
    }

    override fun POST_Forgetpassword_verifyEmail(registeredDto: Registered_DTO, forgetpasswordDto: ForgetPassword_DTO): Boolean = false

    override fun POST_ForgetPassword_sendEmail(registeredDto: Registered_DTO): Boolean {
        val auth = FirebaseAuth.getInstance()
        val targetEmail = registeredDto.email

        if (targetEmail.isEmpty() || targetEmail == "Loading...") {
            throw Exception("Email address is missing.")
        }

        // Notice we removed the try-catch here.
        // If it fails, it will throw the exact Firebase error straight to the ViewModel!
        Log.i(TAG, "Requesting Firebase to send password reset link to: $targetEmail")

        val resetTask = auth.sendPasswordResetEmail(targetEmail)
        com.google.android.gms.tasks.Tasks.await(resetTask)

        Log.i(TAG, "Reset email sent successfully!")
        return true
    }

    override fun POST_Unregister(registeredDto: Registered_DTO, feedbackDto: Unregister_DTO): Boolean {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        try {
            val firestore = FirebaseFirestore.getInstance()

            // Fallback to "Anonymous" if they aren't logged in
            val userEmail = user?.email ?: registeredDto.email
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

            // 1. Payload Size Guardrail
            val base64Images = mutableListOf<String>()
            var currentPayloadSize = 0
            val maxSafePayloadSize = 800_000 // 800KB safe zone

            for (imageBytes in feedbackDto.screenshots) {
                val base64String = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)

                if (currentPayloadSize + base64String.length < maxSafePayloadSize) {
                    base64Images.add(base64String)
                    currentPayloadSize += base64String.length
                } else {
                    Log.w(TAG, "Image truncated for safety. Current Size: $currentPayloadSize")
                    break
                }
            }

            // 2. Prepare the document
            val feedbackData = hashMapOf(
                "email" to userEmail,
                "submissionDate" to currentDate,
                "problemTitle" to feedbackDto.problemTitle,
                "description" to feedbackDto.description,
                "screenshotsBase64" to base64Images,
                "hardwareId" to registeredDto.hardwareID,
                "username" to registeredDto.username,
                "requestedUnregister" to feedbackDto.wantsToUnregister
            )

            // 3. DYNAMIC COLLECTION ROUTING
            val targetCollection = if (feedbackDto.wantsToUnregister) {
                "unregistered_feedback"
            } else {
                "app_feedback"
            }

            Log.i(TAG, "Attempting upload to $targetCollection...")
            val firestoreTask = firestore.collection(targetCollection).add(feedbackData)
            com.google.android.gms.tasks.Tasks.await(firestoreTask)
            Log.i(TAG, "Firestore upload successful.")

            // 4. CONDITIONAL SIGN OUT
            if (user != null && feedbackDto.wantsToUnregister) {
                Log.i(TAG, "User requested unregistration. Proceeding to sign out...")
                auth.signOut()
            }

            return true

        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Upload failed. Error: ${e.message}")
            return false
        }
    }

    fun POST_SendRegisteredInfo(registeredDto: Registered_DTO): Boolean {
        return try {
            val firestore = FirebaseFirestore.getInstance()

            val data = hashMapOf(
                "username"       to registeredDto.username,
                "email"          to registeredDto.email,
                "joinedOn"       to registeredDto.joinedOn,
                "platform"       to registeredDto.platform,
                "hardwareId"     to registeredDto.hardwareID,
                "serialNumber"   to registeredDto.serialNumber,
                "processor"      to registeredDto.processor,
                "storage"        to registeredDto.storage,
                "totalRam"       to registeredDto.totalRam,
                "androidVersion" to registeredDto.androidVersion,
                "macAddress"     to registeredDto.macAddress,
                "sentAt"         to java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.getDefault()
                ).format(java.util.Date())
            )

            val task = firestore
                .collection("registered_devices")
                .document(registeredDto.hardwareID)
                .set(data)

            com.google.android.gms.tasks.Tasks.await(task)
            Log.i(TAG, "POST_SendRegisteredInfo → Firestore write successful.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "POST_SendRegisteredInfo failed: ${e.message}")
            false
        }
    }
}