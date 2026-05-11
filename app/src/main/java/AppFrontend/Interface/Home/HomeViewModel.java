//package AppFrontend.Interface.Home;
//
//import android.app.Application;
//import android.content.Intent;
//import android.os.Build;
//import android.util.Log;
//import androidx.annotation.NonNull;
//import androidx.core.content.ContextCompat;
//import androidx.lifecycle.AndroidViewModel;
//import androidx.lifecycle.LiveData;
//import androidx.lifecycle.MutableLiveData;
//import AppFrontend.Interface.RewardBank.RewardBank_ViewModel;
//
//import AppBackend.ResourceManagement.ResourceManager.ResourceManager_Live_DTO;
//import AppBackend.ResourceManagement.ResourceManager.ResourceStatistics;
//import AppBackend.LocalTrainingModule.TrainingExecutor.TrainingCallback;
//import AppBackend.ResourceManagement.OperationControl.OperationControl;
//import AppBackend.TaskContainer.Task;
//import com.example.fractal.FractalTrainingService;
//import com.example.fractal.Orchestrator;
//
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//
//public class HomeViewModel extends AndroidViewModel {
//
//    private final TrainingStateRepository repository = TrainingStateRepository.getInstance();
//
//    private final MutableLiveData<ResourceManager_Live_DTO> liveStats = new MutableLiveData<>();
//    private final ResourceManager_Live_DTO resourceManager;
//    private static final String TAG = "FRACTAL_VM";
//
//    private final MutableLiveData<String> architectureModel = new MutableLiveData<>("Connecting to grid...");
//    private volatile boolean isCurrentlyDownloading = false;
//
//    public HomeViewModel(@NonNull Application application) {
//        super(application);
//        resourceManager = new ResourceManager_Live_DTO(application);
//
//        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
//            resourceManager.updateStatistics(application);
//            liveStats.postValue(resourceManager);
//        }, 0, 500, TimeUnit.MILLISECONDS);
//    }
//
//    public LiveData<String> getArchitectureModel() {
//        return architectureModel;
//    }
//
//    public void setArchitectureModel(String architecture) {
//        if (architecture != null && !architecture.isEmpty() && !architecture.equals("Unknown")) {
//            architectureModel.postValue("Fractal Engine: " + architecture);
//        } else {
//            architectureModel.postValue("Fractal Engine: Standard");
//        }
//    }
//
//    public void toggleAILifecycle() {
//        if (!repository.isActive) {
//            repository.isActive = true;
//            repository.isWaiting = true;
//            repository.isPaused = false;
//            isCurrentlyDownloading = false;
//            repository.statusMessage.postValue("Initializing...");
//            architectureModel.postValue("Connecting to grid...");
//            startPipelineThread();
//
//        } else if (repository.isWaiting) {
//            repository.isActive = false;
//            repository.isWaiting = false;
//            isCurrentlyDownloading = false;
//            repository.statusMessage.postValue("Process Cancelled");
//            architectureModel.postValue("Engine Offline");
//            stopForegroundService();
//
//        } else if (!repository.isPaused) {
//            repository.isPaused = true;
//
//            if (isCurrentlyDownloading) {
//                repository.statusMessage.postValue("Gulping Paused");
//                Intent pausedIntent = new Intent(getApplication(), FractalTrainingService.class);
//                pausedIntent.putExtra("STATUS_TEXT", "Download Paused"); // MUST match Service string exactly
//                pausedIntent.putExtra("PROGRESS",
//                        repository.trainingProgress.getValue() != null
//                                ? repository.trainingProgress.getValue() : 0);
//                getApplication().startService(pausedIntent);
//            } else {
//                repository.statusMessage.postValue("Synthesis Paused");
//                stopForegroundService();
//            }
//
//        } else {
//            repository.isPaused = false;
//
//            if (isCurrentlyDownloading) {
//                repository.statusMessage.postValue("Resuming gulping...");
//            } else {
//                repository.statusMessage.postValue("Synthesis Resumed...");
//                Intent startIntent = new Intent(getApplication(), FractalTrainingService.class);
//                startIntent.putExtra("PROGRESS",
//                        repository.trainingProgress.getValue() != null
//                                ? repository.trainingProgress.getValue() : 0);
//                startIntent.putExtra("STATUS_TEXT", "Synthesizing Data");
//                ContextCompat.startForegroundService(getApplication(), startIntent);
//            }
//        }
//    }
//
//    private void stopForegroundService() {
//        Intent stopIntent = new Intent(getApplication(), FractalTrainingService.class);
//        stopIntent.setAction("STOP_SERVICE");
//        getApplication().startService(stopIntent);
//    }
//
//    private void startPipelineThread() {
//        new Thread(() -> {
//            try {
//                Intent startIntent = new Intent(getApplication(), FractalTrainingService.class);
//                startIntent.putExtra("PROGRESS", 0);
//                ContextCompat.startForegroundService(getApplication(), startIntent);
//
//                Orchestrator orchestrator = new Orchestrator(getApplication());
//
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                    orchestrator.executeTrainingPipeline(new TrainingCallback() {
//
//                        private double currentTaskRewardRate = 0.0;
//                        private int currentTaskTotalEpochs = 1;
//
//                        @Override
//                        public void onTaskAcquired(@NonNull Task task) {
//                            setArchitectureModel(task.getArchitecture());
//                            currentTaskRewardRate = task.getReward_rate();
//                            currentTaskTotalEpochs = task.getNUM_EPOCHS() > 0 ? task.getNUM_EPOCHS() : 1;
//                        }
//
//                        @Override
//                        public void onEpochUpdate(int completedEpochs, int totalEpochs, float loss, @NonNull String timeLeft) {
//                            ResourceStatistics currentStats = repository.detailedStats.getValue();
//                            if (currentStats != null) {
//                                currentStats.setEpochsCompleted(completedEpochs + " / " + totalEpochs);
//                                currentStats.setEstimatedTimeLeft(timeLeft);
//                                int perf = Math.max(0, 100 - (int)(loss * 100));
//                                currentStats.setOverallPerformance(perf + "%");
//                                repository.detailedStats.postValue(currentStats);
//                            }
//
//                            float mbsPerEpoch = (float) (currentTaskRewardRate / currentTaskTotalEpochs);
//                            RewardBank_ViewModel.Companion.getInstance(getApplication()).addRealVapor(mbsPerEpoch);
//                        }
//
//                        @Override
//                        public void onUploadSuccess() {
//                            RewardBank_ViewModel.Companion.getInstance(getApplication()).verifyVaporToLiquid();
//                        }
//
//                        @Override
//                        public void onProgress(int percentage) {
//                            isCurrentlyDownloading = false;
//                            repository.trainingProgress.postValue(percentage);
//
//                            Intent updateIntent = new Intent(getApplication(), FractalTrainingService.class);
//                            updateIntent.putExtra("PROGRESS", percentage);
//                            updateIntent.putExtra("STATUS_TEXT", "Synthesizing Data");
//                            getApplication().startService(updateIntent);
//                        }
//
//                        @Override
//                        public void onValidationUpdate(@NonNull String result) {
//                            ResourceStatistics stats = repository.detailedStats.getValue();
//                            if (stats != null) {
//                                stats.setInferenceTesting(result);
//                                repository.detailedStats.postValue(stats);
//                            }
//                        }
//
//                        @Override
//                        public void onStatusUpdate(@NonNull String message) {
//                            repository.statusMessage.postValue(message);
//
//                            // 1. Smarter check: Catches ANY string containing "Gulping" and a "%"
//                            if (message.toLowerCase().contains("gulping") && message.contains("%")) {
//                                isCurrentlyDownloading = true;
//                                try {
//                                    String numericOnly = message.replaceAll("[^0-9]", "");
//                                    if (!numericOnly.isEmpty()) {
//                                        int percent = Integer.parseInt(numericOnly);
//                                        Intent downloadIntent = new Intent(getApplication(), FractalTrainingService.class);
//                                        downloadIntent.putExtra("PROGRESS", percent);
//
//                                        if (message.toLowerCase().contains("paused")) {
//                                            downloadIntent.putExtra("STATUS_TEXT", "Download Paused"); // Exact string match for the Service
//                                        } else {
//                                            downloadIntent.putExtra("STATUS_TEXT", "Downloading");
//                                        }
//                                        getApplication().startService(downloadIntent);
//                                    }
//                                } catch (Exception e) {
//                                    Log.w(TAG, "Could not parse gulping percentage from: " + message);
//                                }
//
//                            } else if (message.contains("Resuming")) {
//                                // Just wait for the next percent tick
//                                isCurrentlyDownloading = true;
//
//                            } else if (message.contains("Generating Internet:")) {
//                                // Handled safely by onProgress() right above this, ignore it here
//                                isCurrentlyDownloading = false;
//
//                            } else {
//                                // 2. THE MISSING LINK: Send ALL other states directly to the Service!
//                                isCurrentlyDownloading = false;
//
//                                Intent generalIntent = new Intent(getApplication(), FractalTrainingService.class);
//                                generalIntent.putExtra("STATUS_TEXT", message);
//
//                                Integer currentProg = repository.trainingProgress.getValue();
//                                generalIntent.putExtra("PROGRESS", currentProg != null ? currentProg : 0);
//
//                                getApplication().startService(generalIntent);
//                            }
//                        }
//
//                        @Override
//                        public boolean isPaused() { return repository.isPaused; }
//
//                        @Override
//                        public boolean isCancelled() { return !repository.isActive; }
//
//                        @Override
//                        public void onWaitingStateChanged(boolean isWaiting) { repository.isWaiting = isWaiting; }
//
//                        @Override
//                        public String checkLiveConditions() {
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                                OperationControl opControl = new OperationControl(getApplication());
//                                return opControl.getViolationMessage();
//                            }
//                            return null;
//                        }
//                    });
//                }
//
//            } catch (Exception e) {
//                Log.e(TAG, "Lifecycle Error: " + e.getMessage());
//                repository.statusMessage.postValue("Error: " + e.getMessage());
//            } finally {
//                repository.isActive = false;
//                repository.isWaiting = false;
//                repository.isPaused = false;
//                isCurrentlyDownloading = false;
//                architectureModel.postValue("Engine Offline");
//
//                Intent stopIntent = new Intent(getApplication(), FractalTrainingService.class);
//                stopIntent.setAction("STOP_SERVICE");
//                getApplication().startService(stopIntent);
//            }
//        }).start();
//    }
//
//    public MutableLiveData<Integer> getTrainingProgress() { return repository.trainingProgress; }
//    public MutableLiveData<String> getStatusMessage()     { return repository.statusMessage; }
//    public MutableLiveData<ResourceStatistics> getDetailedStats() { return repository.detailedStats; }
//    public MutableLiveData<ResourceManager_Live_DTO> getLiveStats() { return liveStats; }
//}

package AppFrontend.Interface.Home;

import android.app.Application;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import AppBackend.ResourceManagement.ResourceManager.ResourceManager_Live_DTO;
import AppBackend.ResourceManagement.ResourceManager.ResourceStatistics;
import com.example.fractal.FractalTrainingService;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HomeViewModel extends AndroidViewModel {

    private final TrainingStateRepository repository = TrainingStateRepository.getInstance();

    private final MutableLiveData<ResourceManager_Live_DTO> liveStats = new MutableLiveData<>();
    private final ResourceManager_Live_DTO resourceManager;
    private static final String TAG = "FRACTAL_VM";

    private final MutableLiveData<String> architectureModel = new MutableLiveData<>("Connecting to grid...");

    public HomeViewModel(@NonNull Application application) {
        super(application);
        resourceManager = new ResourceManager_Live_DTO(application);

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            resourceManager.updateStatistics(application);
            liveStats.postValue(resourceManager);
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    public LiveData<String> getArchitectureModel() {
        return architectureModel;
    }

    public void setArchitectureModel(String architecture) {
        if (architecture != null && !architecture.isEmpty() && !architecture.equals("Unknown")) {
            architectureModel.postValue("Fractal Engine: " + architecture);
        } else {
            architectureModel.postValue("Fractal Engine: Standard");
        }
    }

    // ── THE REMOTE CONTROL LOGIC ──
    public void toggleAILifecycle() {
        if (!repository.isActive) {
            // START TRAINING
            repository.isActive = true;
            repository.isWaiting = true;
            repository.isPaused = false;
            repository.statusMessage.postValue("Initializing...");
            architectureModel.postValue("Connecting to grid...");

            Intent startIntent = new Intent(getApplication(), FractalTrainingService.class);
            startIntent.setAction("ACTION_START_TRAINING");
            ContextCompat.startForegroundService(getApplication(), startIntent);

        } else if (repository.isWaiting) {
            // CANCEL TRAINING
            repository.isActive = false;
            repository.isWaiting = false;
            repository.statusMessage.postValue("Process Cancelled");
            architectureModel.postValue("Engine Offline");

            Intent stopIntent = new Intent(getApplication(), FractalTrainingService.class);
            stopIntent.setAction("STOP_SERVICE");
            getApplication().startService(stopIntent);

        } else if (!repository.isPaused) {
            // PAUSE TRAINING
            repository.isPaused = true;
            repository.statusMessage.postValue("Command Sent: Pausing...");

            Intent pauseIntent = new Intent(getApplication(), FractalTrainingService.class);
            pauseIntent.setAction("PAUSE_SERVICE");
            getApplication().startService(pauseIntent);

        } else {
            // RESUME TRAINING
            repository.isPaused = false;
            repository.statusMessage.postValue("Command Sent: Resuming...");

            Intent resumeIntent = new Intent(getApplication(), FractalTrainingService.class);
            resumeIntent.setAction("RESUME_SERVICE");
            getApplication().startService(resumeIntent);
        }
    }

    // Getters for UI observing
    public MutableLiveData<Integer> getTrainingProgress() { return repository.trainingProgress; }
    public MutableLiveData<String> getStatusMessage()     { return repository.statusMessage; }
    public MutableLiveData<ResourceStatistics> getDetailedStats() { return repository.detailedStats; }
    public MutableLiveData<ResourceManager_Live_DTO> getLiveStats() { return liveStats; }
}