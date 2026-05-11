package AppFrontend.Interface.GetStarted

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.fractal.databinding.FragmentGetStartedBinding
import com.example.fractal.R

class GetStarted_Fragment : Fragment() {

    private var _binding: FragmentGetStartedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGetStartedBinding.inflate(inflater, container, false)

        binding.btnGetStarted.setOnClickListener {
            // 1. Mark onboarding as complete
            val prefs = requireContext().getSharedPreferences("app_run_stats", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_first_run", false).apply()

            // 2. Navigate to Home and REMOVE GetStarted from the history (backstack)
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.navigation_get_started, true)
                .build()

            findNavController().navigate(R.id.navigation_home, null, navOptions)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}