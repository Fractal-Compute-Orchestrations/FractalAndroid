//package AppFrontend.Interface.RewardBank
//
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import androidx.core.view.GravityCompat
//import androidx.drawerlayout.widget.DrawerLayout
//import androidx.fragment.app.Fragment
//import com.example.fractal.R
//import com.example.fractal.databinding.FragmentRewardBankBinding
//
//class RewardBank_Fragment : Fragment() {
//
//    private var _binding: FragmentRewardBankBinding? = null
//    private val binding get() = _binding!!
//
//    private lateinit var viewModel: RewardBank_ViewModel
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        _binding = FragmentRewardBankBinding.inflate(inflater, container, false)
//
//        // Binds directly to the shared background state
//        viewModel = RewardBank_ViewModel.getInstance(requireActivity().application)
//
//        setupObservers()
//        setupClickListeners()
//
//        return binding.root
//    }
//
//    private fun setupObservers() {
//        viewModel.displayGBs.observe(viewLifecycleOwner) { gbs ->
//            binding.tvValueBlack.text = gbs
//            binding.tvValueWhite.text = gbs
//        }
//
//        viewModel.validityDate.observe(viewLifecycleOwner) { dateText ->
//            binding.tvValidityBlack.text = dateText
//            binding.tvValidityWhite.text = dateText
//        }
//
//        viewModel.statusText.observe(viewLifecycleOwner) { status ->
//            binding.tvStatusBlack.text = status
//            binding.tvStatusWhite.text = status
//        }
//
//        // --- Passes the data straight to the custom view's Tsunami Engine ---
//        viewModel.vaporPercentage.observe(viewLifecycleOwner) { targetPercentage ->
//            binding.liquidContainer.targetVaporPercentage = targetPercentage
//        }
//
//        viewModel.liquidPercentage.observe(viewLifecycleOwner) { targetPercentage ->
//            binding.liquidContainer.targetLiquidPercentage = targetPercentage
//        }
//    }
//
//    private fun setupClickListeners() {
//        val openDrawer = View.OnClickListener {
//            activity?.findViewById<DrawerLayout>(R.id.drawer_layout)?.openDrawer(GravityCompat.START)
//        }
//        binding.btnMenuBlack.setOnClickListener(openDrawer)
//        binding.btnMenuWhite.setOnClickListener(openDrawer)
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
//}
package AppFrontend.Interface.RewardBank

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.fractal.R
import com.example.fractal.databinding.FragmentRewardBankBinding
import com.google.firebase.auth.FirebaseAuth

class RewardBank_Fragment : Fragment() {

    private var _binding: FragmentRewardBankBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: RewardBank_ViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRewardBankBinding.inflate(inflater, container, false)

        // Binds directly to the shared background state
        viewModel = RewardBank_ViewModel.getInstance(requireActivity().application)

        setupObservers()
        setupClickListeners()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // 1. Refresh visual warning based on current Firebase state
        checkAuthStatus()

        // 2. Automatically trigger a grid sync if the user is authenticated
        // This ensures the "Grid Error" message clears if they just verified
        viewModel.verifyVaporToLiquid()
    }

    /**
     * Handles the visibility and animation of the "Login Required" warnings.
     */
    private fun checkAuthStatus() {
        val user = FirebaseAuth.getInstance().currentUser

        // We show the warning if user is null OR logged in but not verified
        if (user == null || !user.isEmailVerified) {
            binding.tvAuthWarningBlack.visibility = View.VISIBLE
            binding.tvAuthWarningWhite.visibility = View.VISIBLE

            // Breathing Animation Logic
            val pulse = AlphaAnimation(0.4f, 1.0f).apply {
                duration = 1000
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            binding.tvAuthWarningBlack.startAnimation(pulse)
            binding.tvAuthWarningWhite.startAnimation(pulse)

        } else {
            // User is verified, kill animations and hide warnings
            binding.tvAuthWarningBlack.clearAnimation()
            binding.tvAuthWarningWhite.clearAnimation()
            binding.tvAuthWarningBlack.visibility = View.GONE
            binding.tvAuthWarningWhite.visibility = View.GONE
        }
    }

    private fun setupObservers() {
        // Observers GB values (0.0 - 2.0 range)
        viewModel.displayGBs.observe(viewLifecycleOwner) { gbs ->
            binding.tvValueBlack.text = gbs
            binding.tvValueWhite.text = gbs
        }

        // Validity text (Valid till date)
        viewModel.validityDate.observe(viewLifecycleOwner) { dateText ->
            binding.tvValidityBlack.text = dateText
            binding.tvValidityWhite.text = dateText
        }

        // The central status message (Grid Online / Error messages)
        viewModel.statusText.observe(viewLifecycleOwner) { status ->
            binding.tvStatusBlack.text = status
            binding.tvStatusWhite.text = status
        }

        // Vapor (Synthesized but unverified data)
        viewModel.vaporPercentage.observe(viewLifecycleOwner) { targetPercentage ->
            binding.liquidContainer.targetVaporPercentage = targetPercentage
        }

        // Liquid (Verified and secured data)
        viewModel.liquidPercentage.observe(viewLifecycleOwner) { targetPercentage ->
            binding.liquidContainer.targetLiquidPercentage = targetPercentage
        }
    }

    private fun setupClickListeners() {
        // Navigation Drawer
        val openDrawer = View.OnClickListener {
            activity?.findViewById<DrawerLayout>(R.id.drawer_layout)?.openDrawer(GravityCompat.START)
        }
        binding.btnMenuBlack.setOnClickListener(openDrawer)
        binding.btnMenuWhite.setOnClickListener(openDrawer)

        // Navigation to Auth Fragment (Login/Register)
        val goToLogin = View.OnClickListener {
            findNavController().navigate(R.id.navigation_device_auth)
        }
        binding.tvAuthWarningBlack.setOnClickListener(goToLogin)
        binding.tvAuthWarningWhite.setOnClickListener(goToLogin)

        // Optional: Tapping the status text triggers a manual sync attempt
        binding.tvStatusBlack.setOnClickListener { viewModel.verifyVaporToLiquid() }
        binding.tvStatusWhite.setOnClickListener { viewModel.verifyVaporToLiquid() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Prevent memory leaks by clearing animations and binding
        binding.tvAuthWarningBlack.clearAnimation()
        binding.tvAuthWarningWhite.clearAnimation()
        _binding = null
    }
}