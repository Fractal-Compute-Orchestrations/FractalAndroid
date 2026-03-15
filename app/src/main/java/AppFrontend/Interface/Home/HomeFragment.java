package AppFrontend.Interface.Home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.fractal.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {
    private static final String TAG = "FRACTAL_HOME";
    private FragmentHomeBinding binding;
    private HomeViewModel homeViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Attach the training lifecycle to the whole Activity to survive tab switches
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);

        homeViewModel.getLiveStats().observe(getViewLifecycleOwner(), stats -> {
            binding.computationUsagePercentage.setText("Computation Usage: " + stats.getGpuPercentage() + "%");
            binding.processorUsagePercentage.setText("Processor Usage: " + stats.getCpuPercentage() + "%");
            binding.ramUsagePercentage.setText("Ram Usage: " + stats.getRamPercentage() + "%");
            binding.systemTempratureDegree.setText("System Temperature: " + stats.getTemperature() + "°C");
        });

        homeViewModel.getTrainingProgress().observe(getViewLifecycleOwner(), percent -> {
            binding.diamondToggleButton.setProgress(percent);
        });

        homeViewModel.getStatusMessage().observe(getViewLifecycleOwner(), msg -> {
            binding.textView.setText(msg);
        });

        binding.diamondToggleButton.setOnClickListener(v -> {
            homeViewModel.toggleAILifecycle();
        });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}