package AppFrontend.Interface.Insights.DeviceInsights;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.fractal.R;
import com.example.fractal.databinding.FragmentDeviceBinding;

public class DeviceInsightsFragment extends Fragment {

    private FragmentDeviceBinding binding;
    private DeviceInsightsViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        viewModel = new ViewModelProvider(this).get(DeviceInsightsViewModel.class);
        binding = FragmentDeviceBinding.inflate(inflater, container, false);

        setupObservers();

        binding.btnOptimize.setOnClickListener(v -> viewModel.button_optimize());
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        return binding.getRoot();
    }

    private void setupObservers() {
        // --- 1. DYNAMIC BATTERY BAR (Text Inversion) ---
        viewModel.getBatteryPercent().observe(getViewLifecycleOwner(), percent -> {
            String percentText = percent + "%";

            binding.tvBatteryPercentBlack.setText(percentText);
            binding.tvBatteryPercentWhite.setText(percentText);

            ConstraintLayout.LayoutParams maskParams = (ConstraintLayout.LayoutParams) binding.flBatteryMask.getLayoutParams();
            maskParams.matchConstraintPercentWidth = percent / 100f;
            binding.flBatteryMask.setLayoutParams(maskParams);

            binding.batteryBarContainer.post(() -> {
                int fullWidth = binding.batteryBarContainer.getWidth();
                if (fullWidth > 0) {
                    ViewGroup.LayoutParams innerParams = binding.clBatteryInner.getLayoutParams();
                    innerParams.width = fullWidth;
                    binding.clBatteryInner.setLayoutParams(innerParams);
                }
            });
        });

        // --- 2. DYNAMIC HARDWARE INDICATORS ---
        viewModel.getIsCharging().observe(getViewLifecycleOwner(), active ->
                binding.icCharging.setImageResource(active ? R.drawable.ic_diamond_indicator_selected : R.drawable.ic_diamond_indicator_deselected)
        );

        viewModel.getIsOnline().observe(getViewLifecycleOwner(), active ->
                binding.icOnline.setImageResource(active ? R.drawable.ic_diamond_indicator_selected : R.drawable.ic_diamond_indicator_deselected)
        );

        viewModel.getIsWifi().observe(getViewLifecycleOwner(), active ->
                binding.icWifi.setImageResource(active ? R.drawable.ic_diamond_indicator_selected : R.drawable.ic_diamond_indicator_deselected)
        );

        viewModel.getIsCellular().observe(getViewLifecycleOwner(), active ->
                binding.icCellular.setImageResource(active ? R.drawable.ic_diamond_indicator_selected : R.drawable.ic_diamond_indicator_deselected)
        );

        // --- 3. TEXT STATS ---
        viewModel.getDeviceStandby().observe(getViewLifecycleOwner(), val ->
                binding.tvDeviceStandby.setText("Device Standby time: " + val + " hrs")
        );

        viewModel.getNetworkStandby().observe(getViewLifecycleOwner(), val ->
                binding.tvNetworkStandby.setText("Network Standby Time: " + val + " hrs")
        );

        viewModel.getPowerConsumption().observe(getViewLifecycleOwner(), val ->
                binding.tvPowerConsumption.setText("Power Consumption: " + val + " mW")
        );

        viewModel.getTempStorage().observe(getViewLifecycleOwner(), val ->
                binding.tvTempStorage.setText("Temp Storage: " + val + " GBs")
        );

        viewModel.getBgTasks().observe(getViewLifecycleOwner(), val ->
                binding.tvBgTasks.setText("Background Tasks Running: " + val)
        );
    }

    // NOTE: onResume() is intentionally removed here.
    // The ViewModel handles continuous real-time updates natively now!

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}