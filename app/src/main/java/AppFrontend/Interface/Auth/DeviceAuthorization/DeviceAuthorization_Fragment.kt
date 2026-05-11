package AppFrontend.Interface.Auth.DeviceAuthorization

import android.graphics.Paint
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.fractal.R
import com.example.fractal.databinding.FragmentDeviceAuthorizationBinding
import com.google.firebase.auth.FirebaseAuth

class DeviceAuthorization_Fragment : Fragment() {

    private var _binding: FragmentDeviceAuthorizationBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DeviceAuthorization_ViewModel
    private var isPasswordVisible = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceAuthorizationBinding.inflate(inflater, container, false)

        binding.tvForgetPassword.paintFlags = binding.tvForgetPassword.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvTermsLink.paintFlags = binding.tvTermsLink.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        fixPasswordFont()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. SECURE AUTO-LOGIN BYPASS
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            Toast.makeText(requireContext(), "Welcome back!", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.navigation_home)
            return
            // NOTE: We no longer sign out unverified users here.
            // It was killing the session while they were in the Gmail app!
        }

        // 2. USE requireActivity() TO PREVENT SCREEN RESET
        // This keeps the ViewModel alive even if they background the app or visit the Forget Password screen.
        viewModel = ViewModelProvider(requireActivity())[DeviceAuthorization_ViewModel::class.java]

        setupClickListeners()
        setupObservers()
    }

    // 3. THE INSTANT REFRESH TRIGGER
    // This fires the exact millisecond the user returns to your app from their email.
    override fun onResume() {
        super.onResume()
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && !currentUser.isEmailVerified) {
            currentUser.reload().addOnSuccessListener {
                if (currentUser.isEmailVerified) {
                    // Call the secure helper function instead!
                    viewModel.updateAuthStatus("Success")
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.authStatus.observe(viewLifecycleOwner) { status ->
            when {
                status == "Processing..." -> {
                    binding.btnLoginRegister.text = "Authenticating..."
                    binding.btnLoginRegister.isEnabled = false
                }
                status == "Success" -> {
                    Toast.makeText(context, "Authentication Successful!", Toast.LENGTH_SHORT).show()

                    // Call the secure helper function to clear the status
                    viewModel.updateAuthStatus("")
                    findNavController().navigate(R.id.navigation_home)
                }
                status.startsWith("AwaitingVerification:") -> {
                    val msg = status.removePrefix("AwaitingVerification:")
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

                    binding.btnLoginRegister.text = "Waiting for Verification..."
                    binding.btnLoginRegister.isEnabled = false
                }
                status.startsWith("Error:") -> {
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                    binding.btnLoginRegister.text = "Login/Register"
                    binding.btnLoginRegister.isEnabled = true
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnTogglePassword.setOnClickListener { togglePasswordVisibility() }
        binding.btnLoginRegister.setOnClickListener { button_LoginRegister() }
        binding.tvTermsLink.setOnClickListener { binding.cbTerms.isChecked = !binding.cbTerms.isChecked }
        binding.tvForgetPassword.setOnClickListener { findNavController().navigate(R.id.navigation_forget_password) }
    }

    private fun togglePasswordVisibility() {
        if (isPasswordVisible) {
            binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            binding.btnTogglePassword.setImageResource(R.drawable.ic_eye_hidden)
            isPasswordVisible = false
        } else {
            binding.etPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            binding.btnTogglePassword.setImageResource(R.drawable.ic_eye_visibility)
            isPasswordVisible = true
        }
        fixPasswordFont()
        binding.etPassword.setSelection(binding.etPassword.text.length)
    }

    private fun fixPasswordFont() {
        val customTypeface = ResourcesCompat.getFont(requireContext(), R.font.genos_regular)
        binding.etPassword.typeface = customTypeface
    }

    fun button_LoginRegister() {
        val username = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()     // <-- NEW
        val carrier = binding.etCarrier.text.toString().trim() // <-- NEW
        val password = binding.etPassword.text.toString().trim()
        val agreedToTerms = binding.cbTerms.isChecked

        if (username.isEmpty() || email.isEmpty() || phone.isEmpty() || carrier.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!agreedToTerms) {
            Toast.makeText(context, "You must agree to the Terms and Conditions", Toast.LENGTH_SHORT).show()
            return
        }

        // Pass the new fields into the DTO
        val loginData = LoginRegister_DTO(username, password, email, phone, carrier)
        viewModel.loginRegister(loginData)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}