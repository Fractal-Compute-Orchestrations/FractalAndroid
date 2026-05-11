package AppFrontend.Interface.Settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.fractal.R
import com.example.fractal.databinding.FragmentSettingsBinding

class Settings_Fragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: Settings_ViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[Settings_ViewModel::class.java]
        setupListeners()
    }

    // ── THE FIX: Move to onResume so it refreshes after notification clicks ──
    override fun onResume() {
        super.onResume()
        setupInitialState()
    }

    private fun setupInitialState() {
        val config = viewModel.getConfig()

        // 1. Set Icons
        updateIcon(binding.btnWifi, config.onWifi)
        updateIcon(binding.btnData, config.onData)
        updateIcon(binding.btnOvernight, config.overNightUtilization)
        updateIcon(binding.btnIdle, config.idleTimeUtilization)
        updateIcon(binding.btnChargingExclusive, config.onChargingExclusive)

        // 2. Set Slider & Text
        binding.sliderCharge.progress = config.minChargeLimit
        updateLabelText(config.minChargeLimit)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.btnWifi.setOnClickListener {
            viewModel.toggleWifi()
            updateIcon(binding.btnWifi, viewModel.getConfig().onWifi)
        }

        binding.btnData.setOnClickListener {
            viewModel.toggleData()
            updateIcon(binding.btnData, viewModel.getConfig().onData)
        }

        binding.btnOvernight.setOnClickListener {
            viewModel.toggleOvernight()
            updateIcon(binding.btnOvernight, viewModel.getConfig().overNightUtilization)
        }

        binding.btnIdle.setOnClickListener {
            viewModel.toggleIdle()
            updateIcon(binding.btnIdle, viewModel.getConfig().idleTimeUtilization)
        }

        binding.btnChargingExclusive.setOnClickListener {
            viewModel.toggleChargingExclusive()
            updateIcon(binding.btnChargingExclusive, viewModel.getConfig().onChargingExclusive)
        }

        binding.sliderCharge.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateLabelText(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let { viewModel.updateChargeLimit(it.progress) }
            }
        })
    }

    private fun updateLabelText(value: Int) {
        binding.tvChargeLimitLabel.text = "Only train when charging is\nabove: $value%"
    }

    private fun updateIcon(view: ImageView, isActive: Boolean) {
        if (isActive) {
            view.setImageResource(R.drawable.leaf_checked)
            view.alpha = 1.0f
        } else {
            view.setImageResource(R.drawable.leaf_unchecked)
            view.alpha = 0.5f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun event_updateSettings(){}
}