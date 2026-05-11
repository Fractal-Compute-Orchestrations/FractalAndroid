package AppFrontend.Interface.RewardBank

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import AppBackend.Network.Server_DAO.Server_DAO
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class RewardBank_ViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        @Volatile
        private var INSTANCE: RewardBank_ViewModel? = null
        fun getInstance(app: Application): RewardBank_ViewModel {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RewardBank_ViewModel(app).also { INSTANCE = it }
            }
        }
    }

    private val PREFS_NAME = "fractal_bank_storage"
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val auth = FirebaseAuth.getInstance()
    private var firestoreListener: ListenerRegistration? = null

    private val MAX_MB_LIMIT = 2048f

    private var vaporMBs = prefs.getFloat("vapor_mbs", 0f)
    private var liquidMBs = prefs.getFloat("liquid_mbs", 0f)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var lastActiveDate = prefs.getString("last_active_date", getCurrentDateStr())

    private var isFlushing = false

    val vaporPercentage = MutableLiveData<Float>((vaporMBs / MAX_MB_LIMIT).coerceIn(0f, 1f))
    val liquidPercentage = MutableLiveData<Float>((liquidMBs / MAX_MB_LIMIT).coerceIn(0f, 1f))
    val displayGBs = MutableLiveData<String>(String.format(Locale.getDefault(), "%.1f", vaporMBs / 1024f))
    val statusText = MutableLiveData<String>("System Ready")

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (!isFlushing) {
            when {
                user == null -> {
                    statusText.postValue("Grid Offline: Login Required")
                    stopLiveSync()
                }
                !user.isEmailVerified -> {
                    statusText.postValue("Grid Error: Email Not Verified")
                    stopLiveSync()
                }
                else -> {
                    statusText.postValue("Grid Online: ${user.displayName ?: "Authorized"}")
                    startLiveSync()
                }
            }
        }
    }

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            checkAndResetIfNewDay()
        }
    }

    init {
        auth.addAuthStateListener(authListener)
        checkAndResetIfNewDay()
        refreshUI()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        application.registerReceiver(timeTickReceiver, filter)
    }

    // ─── LIVE SYNC ENGINE (Firestore Snapshot) ───
    private fun startLiveSync() {
        val user = auth.currentUser ?: return
        if (!user.isEmailVerified || firestoreListener != null) return

        val db = FirebaseFirestore.getInstance()
        firestoreListener = db.collection("users").document(user.email!!)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                if (snapshot != null && snapshot.exists() && !isFlushing) {
                    val serverLiquid = snapshot.getDouble("liquid_mbs")?.toFloat() ?: 0f

                    // Only trigger the "Woah" if the value actually increased
                    if (serverLiquid > liquidMBs) {
                        viewModelScope.launch(Dispatchers.Main) {
                            statusText.postValue("Grid Update Detected...") // The "Woah" trigger
                            animateToNewValues(serverLiquid)
                            delay(1000)
                            statusText.postValue("Vapor Condensation Complete") // The payoff
                            delay(4000)
                            statusText.postValue("Fractal Core: Internet Generation")
                        }
                    } else {
                        animateToNewValues(serverLiquid)
                    }
                }
            }
    }

    private fun stopLiveSync() {
        firestoreListener?.remove()
        firestoreListener = null
    }

    private fun animateToNewValues(targetLiquid: Float) {
        viewModelScope.launch(Dispatchers.Main) {
            val validatedTarget = targetLiquid.coerceAtMost(MAX_MB_LIMIT)
            val startLiquid = liquidMBs
            val startVapor = vaporMBs

            // Calculate Target Vapor based on the "Drop Sync" logic
            var targetVapor = startVapor
            if (validatedTarget < startLiquid) {
                val drop = startLiquid - validatedTarget
                targetVapor = (startVapor - drop).coerceAtLeast(0f)
            }
            if (targetVapor < validatedTarget) targetVapor = validatedTarget

            // Smooth 1-second transition
            val duration = 1000L
            val frameDelay = 16L
            val totalFrames = duration / frameDelay

            for (i in 1..totalFrames.toInt()) {
                val p = i.toFloat() / totalFrames
                liquidMBs = startLiquid + (validatedTarget - startLiquid) * p
                vaporMBs = startVapor + (targetVapor - startVapor) * p
                refreshUI()
                delay(frameDelay)
            }

            // Persistence
            prefs.edit()
                .putFloat("liquid_mbs", liquidMBs)
                .putFloat("vapor_mbs", vaporMBs)
                .apply()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveSync()
        auth.removeAuthStateListener(authListener)
        try {
            getApplication<Application>().unregisterReceiver(timeTickReceiver)
        } catch (e: Exception) {
            Log.e("RewardBank", "Unregister failed: ${e.message}")
        }
    }

    private fun getCurrentDateStr(): String = dateFormat.format(Calendar.getInstance().time)

    private fun checkAndResetIfNewDay() {
        val today = getCurrentDateStr()
        if (lastActiveDate != today && !isFlushing) {
            isFlushing = true
            animateFlushToZero(today)
        }
    }

    // ─── THE SMOOTH DRAIN ENGINE (Midnight Reset) ───
    private fun animateFlushToZero(today: String) {
        val startVapor = vaporMBs
        val startLiquid = liquidMBs

        if (startVapor <= 0f && startLiquid <= 0f) {
            finalizeReset(today)
            return
        }

        statusText.postValue("Midnight Reset Sequence...")

        viewModelScope.launch(Dispatchers.Main) {
            val totalDuration = 1500L
            val frameDelay = 16L
            val totalFrames = totalDuration / frameDelay

            for (i in totalFrames downTo 0) {
                val linearProgress = i.toFloat() / totalFrames
                val easedProgress = 1f - Math.pow((1f - linearProgress).toDouble(), 2.0).toFloat()

                vaporMBs = startVapor * linearProgress
                liquidMBs = startLiquid * linearProgress

                vaporPercentage.postValue((vaporMBs / MAX_MB_LIMIT) * easedProgress)
                liquidPercentage.postValue((liquidMBs / MAX_MB_LIMIT) * easedProgress)
                displayGBs.postValue(String.format(Locale.getDefault(), "%.1f", vaporMBs / 1024f))

                delay(frameDelay)
            }
            finalizeReset(today)
        }
    }

    private fun finalizeReset(today: String) {
        vaporMBs = 0f
        liquidMBs = 0f
        lastActiveDate = today

        prefs.edit()
            .putFloat("vapor_mbs", 0f)
            .putFloat("liquid_mbs", 0f)
            .putString("last_active_date", today)
            .apply()

        refreshUI()
        statusText.postValue("Midnight Reset: Bank Flushed.")
        isFlushing = false
    }

    fun addRealVapor(amount: Float) {
        if (isFlushing) return
        checkAndResetIfNewDay()

        if (vaporMBs >= MAX_MB_LIMIT) {
            statusText.postValue("Bank Full! Max Capacity Reached.")
            return
        }

        vaporMBs = (vaporMBs + amount).coerceAtMost(MAX_MB_LIMIT)
        prefs.edit().putFloat("vapor_mbs", vaporMBs).apply()
        refreshUI()

        if (vaporMBs >= MAX_MB_LIMIT) {
            statusText.postValue("Bank Full! Max Capacity Reached.")
        } else {
            statusText.postValue("Vapor Synthesized: +${String.format("%.2f", amount)} MB")
        }
    }

    /**
     * Manual trigger for UI refresh or force-sync.
     * Now primarily handled by the LiveSync listener.
     */
    fun verifyVaporToLiquid() {
        if (isFlushing) return
        checkAndResetIfNewDay()

        val user = auth.currentUser ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // 1. The Build-up (The user feels the work happening)
//            statusText.postValue("Synchronizing...")
            delay(1200)

            val serverDao = Server_DAO()
            val serverLiquid = serverDao.GET_VerifiedLiquidMBs(user.email!!)

            if (serverLiquid >= 0f) {
                withContext(Dispatchers.Main) {
                    // 2. The Payoff (Smooth liquid animation starts)
                    animateToNewValues(serverLiquid)

//                    // 3. The "Woah" Moment (High-impact success message)
//                    statusText.postValue("Internet Verified")
//
//                    delay(3500)
//                    if (!isFlushing) {
//                        statusText.postValue("Internet Downloaded")
//                    }
                }
            } else {
                statusText.postValue("Grid Sync Delayed: Retrying...")
                delay(3000)
                statusText.postValue("Fractal Grid: Standby")
            }
        }
    }

    private fun refreshUI() {
        vaporPercentage.postValue((vaporMBs / MAX_MB_LIMIT).coerceIn(0f, 1f))
        liquidPercentage.postValue((liquidMBs / MAX_MB_LIMIT).coerceIn(0f, 1f))
        val totalGBs = vaporMBs / 1024f
        displayGBs.postValue(String.format(Locale.getDefault(), "%.1f", totalGBs))
    }

    val validityDate: LiveData<String> = MutableLiveData<String>().apply {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        postValue("Valid till ${sdf.format(Calendar.getInstance().time)}")
    }
}