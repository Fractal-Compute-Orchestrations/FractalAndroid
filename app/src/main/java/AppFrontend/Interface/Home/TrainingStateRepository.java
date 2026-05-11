package AppFrontend.Interface.Home;

import androidx.lifecycle.MutableLiveData;
import AppBackend.ResourceManagement.ResourceManager.ResourceStatistics;

public class TrainingStateRepository {
    private static TrainingStateRepository instance;

    // --- NEW STATE MACHINE TRACKERS ---
    public boolean isActive = false;  // Is the overall pipeline thread alive?
    public boolean isWaiting = false; // Is OperationControl waiting for settings?
    public boolean isPaused = false;  // Has the user paused active training?

    public final MutableLiveData<Integer> trainingProgress = new MutableLiveData<>(0);
    public final MutableLiveData<String> statusMessage = new MutableLiveData<>("inactive");
    public final MutableLiveData<ResourceStatistics> detailedStats = new MutableLiveData<>(
            new ResourceStatistics("0%", "Calculating...", "0 / 0", "Pending")
    );

    private TrainingStateRepository() {}

    public static synchronized TrainingStateRepository getInstance() {
        if (instance == null) {
            instance = new TrainingStateRepository();
        }
        return instance;
    }
}