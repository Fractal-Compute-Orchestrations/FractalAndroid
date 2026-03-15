package AppFrontend.Interface.RewardBank

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.fractal.R
import com.example.fractal.databinding.FragmentRewardBankBinding

class RewardBank_Fragment : Fragment() {

    private var _binding: FragmentRewardBankBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: RewardBank_ViewModel
    private var simulationAnimator: ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRewardBankBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[RewardBank_ViewModel::class.java]

        setupObservers()
        setupClickListeners()

        binding.root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val safeBinding = _binding ?: return
                safeBinding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // Start simulation only after layout is measured
                if (simulationAnimator == null && safeBinding.root.height > 0) {
                    startVisualSimulation()
                }
            }
        })

        return binding.root
    }

    private fun setupObservers() {
        viewModel.displayGBs.observe(viewLifecycleOwner) { gbs ->
            _binding?.tvValueBlack?.text = gbs
            _binding?.tvValueWhite?.text = gbs
        }

        viewModel.validityDate.observe(viewLifecycleOwner) { dateText ->
            _binding?.tvValidityBlack?.text = dateText
            _binding?.tvValidityWhite?.text = dateText
        }

        viewModel.fillPercentage.observe(viewLifecycleOwner) { percentage ->
            // Passes percentage to the custom View's internal physics engine
            _binding?.liquidContainer?.fillPercentage = percentage
        }
    }

    private fun setupClickListeners() {
        val openDrawer = View.OnClickListener {
            activity?.findViewById<DrawerLayout>(R.id.drawer_layout)?.openDrawer(GravityCompat.START)
        }
        binding.btnMenuBlack.setOnClickListener(openDrawer)
        binding.btnMenuWhite.setOnClickListener(openDrawer)
    }

    private fun startVisualSimulation() {
        // Start from whatever value was persisted in the ViewModel
        val startValue = viewModel.currentMBs.value ?: 0f

        simulationAnimator = ValueAnimator.ofFloat(startValue, 2048f).apply {
            duration = 100000 // Adjust based on your preferred speed
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                val currentMBs = animator.animatedValue as Float
                viewModel.setSimulatedMBs(currentMBs)
            }
            start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        simulationAnimator?.cancel()
        simulationAnimator = null
        _binding = null
    }
}