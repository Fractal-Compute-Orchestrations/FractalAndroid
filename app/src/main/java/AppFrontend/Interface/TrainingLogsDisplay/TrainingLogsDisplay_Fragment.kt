package AppFrontend.Interface.TrainingLogsDisplay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.fractal.databinding.FragmentTrainingLogsDisplayBinding

class TrainingLogsDisplay_Fragment : Fragment() {

    private var _binding: FragmentTrainingLogsDisplayBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TrainingLogsDisplay_ViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainingLogsDisplayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Back Button
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // 2. Observe the Live Terminal Logs
        viewModel.consoleLogs.observe(viewLifecycleOwner) { logs ->
            binding.tvConsoleOutput.text = logs

            // Auto-scroll to the bottom whenever a new log arrives
            binding.svLogContainer.post {
                binding.svLogContainer.fullScroll(View.FOCUS_DOWN)
            }
        }

        // 3. Observe the Stats
        viewModel.currentStatus.observe(viewLifecycleOwner) { status ->
            binding.tvCurrentStatus.text = status
        }

        viewModel.stepsText.observe(viewLifecycleOwner) { steps ->
            binding.tvLogSteps.text = steps
        }

        viewModel.lossText.observe(viewLifecycleOwner) { loss ->
            binding.tvLogLoss.text = loss
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}