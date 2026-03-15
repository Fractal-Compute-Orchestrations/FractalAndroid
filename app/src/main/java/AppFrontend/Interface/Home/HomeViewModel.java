package AppFrontend.Interface.Home;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import AppBackend.ResourceManagement.ResourceManager.ResourceManager_Live_DTO;
import AppBackend.ResourceManagement.ResourceManager.ResourceStatistics;
import AppBackend.LocalTrainingModule.TrainingExecutor.TrainingCallback;
import AppBackend.ResourceManagement.OperationControl.OperationControl;
import com.example.fractal.FractalTrainingService;
import com.example.fractal.Orchestrator;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HomeViewModel extends AndroidViewModel {

    private final TrainingStateRepository repository = TrainingStateRepository.getInstance();

    private final MutableLiveData<ResourceManager_Live_DTO> liveStats = new MutableLiveData<>();
    private final ResourceManager_Live_DTO resourceManager;
    private static final String TAG = "FRACTAL_VM";

    // Tracks whether the pipeline is currently in the gulping phase.
    // Stays true across paused/resuming sub-states so pause/resume
    // behaviour routes correctly from the diamond button.
    private volatile boolean isCurrentlyDownloading = false;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        resourceManager = new ResourceManager_Live_DTO(application);

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            resourceManager.updateStatistics(application);
            liveStats.postValue(resourceManager);
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    public void toggleAILifecycle() {
        if (!repository.isActive) {
            // 1. Turn ON
            repository.isActive = true;
            repository.isWaiting = true;
            repository.isPaused = false;
            isCurrentlyDownloading = false;
            repository.statusMessage.postValue("Initializing...");
            startPipelineThread();

        } else if (repository.isWaiting) {
            // 2. Cancel while WAITING
            repository.isActive = false;
            repository.isWaiting = false;
            isCurrentlyDownloading = false;
            // DO NOT CHANGE: Tied to wave animation logic in Fragment
            repository.statusMessage.postValue("Process Cancelled");
            stopForegroundService();

        } else if (!repository.isPaused) {
            // 3. Pause — behaviour differs by phase
            repository.isPaused = true;

            if (isCurrentlyDownloading) {
                // ── GULPING PHASE: keep the notification, swap to play icon ──
                repository.statusMessage.postValue("Gulping Paused");
                Intent pausedIntent = new Intent(getApplication(), FractalTrainingService.class);
                pausedIntent.putExtra("STATUS_TEXT", "Gulping Paused");
                pausedIntent.putExtra("PROGRESS",
                        repository.trainingProgress.getValue() != null
                                ? repository.trainingProgress.getValue() : 0);
                getApplication().startService(pausedIntent);
            } else {
                // ── SYNTHESIS PHASE: dismiss the notification entirely ──────────
                repository.statusMessage.postValue("Synthesis Paused");
                stopForegroundService();
            }

        } else {
            // 4. Resume while PAUSED
            repository.isPaused = false;

            if (isCurrentlyDownloading) {
                // The pause trap in DataDownloader_naf unblocks automatically
                // because isPaused is now false. The next onProgressUpdate call
                // will fire onStatusUpdate("Gulping data chunk... X%") which
                // restores the notification normally. No extra intent needed.
                repository.statusMessage.postValue("Resuming gulping...");
            } else {
                // Synthesis resume — restart the notification
                repository.statusMessage.postValue("Synthesis Resumed...");
                Intent startIntent = new Intent(getApplication(), FractalTrainingService.class);
                startIntent.putExtra("PROGRESS",
                        repository.trainingProgress.getValue() != null
                                ? repository.trainingProgress.getValue() : 0);
                startIntent.putExtra("STATUS_TEXT", "Synthesizing Data");
                ContextCompat.startForegroundService(getApplication(), startIntent);
            }
        }
    }

    private void stopForegroundService() {
        Intent stopIntent = new Intent(getApplication(), FractalTrainingService.class);
        stopIntent.setAction("STOP_SERVICE");
        getApplication().startService(stopIntent);
    }

    private void startPipelineThread() {
        new Thread(() -> {
            try {
                Intent startIntent = new Intent(getApplication(), FractalTrainingService.class);
                startIntent.putExtra("PROGRESS", 0);
                ContextCompat.startForegroundService(getApplication(), startIntent);

                Orchestrator orchestrator = new Orchestrator(getApplication());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    orchestrator.executeTrainingPipeline(new TrainingCallback() {

                        @Override
                        public void onProgress(int percentage) {
                            // onProgress is only fired during synthesis
                            isCurrentlyDownloading = false;
                            repository.trainingProgress.postValue(percentage);

                            Intent updateIntent = new Intent(getApplication(), FractalTrainingService.class);
                            updateIntent.putExtra("PROGRESS", percentage);
                            updateIntent.putExtra("STATUS_TEXT", "Synthesizing Data");
                            getApplication().startService(updateIntent);
                        }

                        @Override
                        public void onValidationUpdate(@NonNull String result) {
                            ResourceStatistics stats = repository.detailedStats.getValue();
                            if (stats != null) {
                                stats.setInferenceTesting(result);
                                repository.detailedStats.postValue(stats);
                            }
                        }

                        @Override
                        public void onStatusUpdate(@NonNull String message) {
                            repository.statusMessage.postValue(message);

                            // ── All Gulping-phase messages ───────────────────────────
                            // We explicitly check for the phrases sent by Orchestrator
                            if (message.startsWith("Gulping data chunk") || message.equals("Gulping Internet Chunk...")) {
                                isCurrentlyDownloading = true;
                                try {
                                    String numericOnly = message.replaceAll("[^0-9]", "");
                                    if (!numericOnly.isEmpty()) {
                                        int percent = Integer.parseInt(numericOnly);
                                        Intent downloadIntent = new Intent(getApplication(), FractalTrainingService.class);
                                        downloadIntent.putExtra("PROGRESS", percent);
                                        downloadIntent.putExtra("STATUS_TEXT", "Gulping");
                                        getApplication().startService(downloadIntent);
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Could not parse gulping percentage from: " + message);
                                }

                            } else if (message.startsWith("Gulping Paused")) {
                                // Covers both "Gulping Paused" and "Gulping Paused: X%"
                                try {
                                    String numericOnly = message.replaceAll("[^0-9]", "");
                                    if (!numericOnly.isEmpty()) {
                                        int percent = Integer.parseInt(numericOnly);
                                        Intent pausedIntent = new Intent(getApplication(), FractalTrainingService.class);
                                        pausedIntent.putExtra("PROGRESS", percent);
                                        pausedIntent.putExtra("STATUS_TEXT", "Gulping Paused");
                                        getApplication().startService(pausedIntent);
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Could not parse paused percentage from: " + message);
                                }

                            } else if (message.equals("Resuming gulping...")) {
                                // Still in gulp phase — do not clear isCurrentlyDownloading.

                            } else {
                                // Any other message means we have left the gulping phase
                                isCurrentlyDownloading = false;
                            }
                        }

                        @Override
                        public void onEpochUpdate(int completedEpochs, int totalEpochs, float loss, @NonNull String timeLeft) {
                            ResourceStatistics currentStats = repository.detailedStats.getValue();
                            if (currentStats != null) {
                                currentStats.setEpochsCompleted(completedEpochs + " / " + totalEpochs);
                                currentStats.setEstimatedTimeLeft(timeLeft);
                                int perf = Math.max(0, 100 - (int)(loss * 100));
                                currentStats.setOverallPerformance(perf + "%");
                                repository.detailedStats.postValue(currentStats);
                            }
                        }

                        @Override
                        public boolean isPaused() {
                            return repository.isPaused;
                        }

                        @Override
                        public boolean isCancelled() {
                            return !repository.isActive;
                        }

                        @Override
                        public void onWaitingStateChanged(boolean isWaiting) {
                            repository.isWaiting = isWaiting;
                        }

                        @Override
                        public String checkLiveConditions() {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                OperationControl opControl = new OperationControl(getApplication());
                                return opControl.getViolationMessage();
                            }
                            return null;
                        }
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Lifecycle Error: " + e.getMessage());
                repository.statusMessage.postValue("Error: " + e.getMessage());
            } finally {
                repository.isActive = false;
                repository.isWaiting = false;
                repository.isPaused = false;
                isCurrentlyDownloading = false;

                Intent stopIntent = new Intent(getApplication(), FractalTrainingService.class);
                stopIntent.setAction("STOP_SERVICE");
                getApplication().startService(stopIntent);
            }
        }).start();
    }

    public MutableLiveData<Integer> getTrainingProgress() { return repository.trainingProgress; }
    public MutableLiveData<String> getStatusMessage()     { return repository.statusMessage; }
    public MutableLiveData<ResourceStatistics> getDetailedStats() { return repository.detailedStats; }
    public MutableLiveData<ResourceManager_Live_DTO> getLiveStats() { return liveStats; }
}