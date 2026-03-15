package AppFrontend.Interface.RewardBank

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RewardBank_ViewModel(application: Application) : AndroidViewModel(application) {

    private val PREFS_NAME = "reward_bank_prefs"
    private val KEY_CURRENT_MB = "current_mbs"
    private val MAX_MB_LIMIT = 2048f // 2 GB limit

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Initialize with the last saved value from storage
    private val _currentMBs = MutableLiveData<Float>(prefs.getFloat(KEY_CURRENT_MB, 0f))
    val currentMBs: LiveData<Float> get() = _currentMBs

    // These hold the processed data for the UI
    private val _fillPercentage = MutableLiveData<Float>()
    val fillPercentage: LiveData<Float> get() = _fillPercentage

    private val _displayGBs = MutableLiveData<String>()
    val displayGBs: LiveData<String> get() = _displayGBs

    init {
        // Run an initial update so the UI isn't empty on first frame
        updateDependentValues(_currentMBs.value ?: 0f)
    }

    val validityDate: LiveData<String>
        get() = MutableLiveData<String>().apply {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            value = "Valid till ${sdf.format(Calendar.getInstance().time)}"
        }

    fun setSimulatedMBs(mbs: Float) {
        _currentMBs.value = mbs
        updateDependentValues(mbs)

        // Save to persistent storage asynchronously
        prefs.edit().putFloat(KEY_CURRENT_MB, mbs).apply()
    }

    private fun updateDependentValues(mbs: Float) {
        _fillPercentage.value = (mbs / MAX_MB_LIMIT).coerceIn(0f, 1f)
        val gbs = mbs / 1024f
        _displayGBs.value = String.format(Locale.getDefault(), "%.1f", gbs)
    }
}