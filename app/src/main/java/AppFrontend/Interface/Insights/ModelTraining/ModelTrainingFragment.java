//package AppFrontend.Interface.Insights.ModelTraining;
//
//import android.os.Bundle;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.fragment.app.Fragment;
//import androidx.lifecycle.ViewModelProvider;
//import com.example.fractal.databinding.FragmentModelBinding;
//import AppFrontend.Interface.Home.HomeViewModel; // Import the shared ViewModel
//
//public class ModelTrainingFragment extends Fragment {
//
//    private FragmentModelBinding binding;
//    private HomeViewModel sharedViewModel;
//
//    @Override
//
//    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//        binding = FragmentModelBinding.inflate(inflater, container, false);
//        sharedViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
//
//        sharedViewModel.getDetailedStats().observe(getViewLifecycleOwner(), stats -> {
//            binding.tvOverallPerf.setText("Overall Performance: " + stats.getOverallPerformance());
//            binding.tvEstTime.setText("Estimated Time Left: " + stats.getEstimatedTimeLeft());
//            binding.tvEpochs.setText("Epochs Completed: " + stats.getEpochsCompleted());
//            binding.tvInference.setText("Inference Testing: " + stats.getInferenceTesting());
//        });
//
//        return binding.getRoot();
//    }
//
//    @Override
//    public void onDestroyView() {
//        super.onDestroyView();
//        binding = null;
//    }
//}
package AppFrontend.Interface.Insights.ModelTraining;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.fractal.databinding.FragmentModelBinding;
import AppFrontend.Interface.Home.HomeViewModel;

public class ModelTrainingFragment extends Fragment {

    private FragmentModelBinding binding;
    private HomeViewModel sharedViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentModelBinding.inflate(inflater, container, false);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        // 1. Observe Stats
        sharedViewModel.getDetailedStats().observe(getViewLifecycleOwner(), stats -> {
//            binding.tvOverallPerf.setText("Overall Performance: " + stats.getOverallPerformance());
//            binding.tvEstTime.setText("Estimated Time Left: " + stats.getEstimatedTimeLeft());
//            binding.tvEpochs.setText("Epochs Completed: " + stats.getEpochsCompleted());
//            binding.tvInference.setText("Inference Testing: " + stats.getInferenceTesting());

            binding.tvOverallPerf.setText("Generation Rate: " + stats.getOverallPerformance());
            binding.tvEstTime.setText("Estimated Time Left: " + stats.getEstimatedTimeLeft());
            binding.tvEpochs.setText("Chunks Digested: " + stats.getEpochsCompleted());
            binding.tvInference.setText("Verifying Output: " + stats.getInferenceTesting());
        });

        // 2. Observe State to control the Wave Animation
        sharedViewModel.getStatusMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && binding.waveVisualization != null) {

                // FIX: Expanded the checks to catch Paused, Cancelled, and Aborted states!
                boolean isTrainingActive = !msg.equals("inactive")
                        && !msg.equals("Process Complete")
                        && !msg.equals("Process Cancelled")
                        && !msg.contains("Paused")
                        && !msg.contains("Aborted")
                        && !msg.startsWith("Error");

                binding.waveVisualization.setActive(isTrainingActive);
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}