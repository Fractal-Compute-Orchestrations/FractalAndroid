package AppFrontend.Interface.Insights.ModelTraining;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ModelTrainingViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public ModelTrainingViewModel() {
        mText = new MutableLiveData<>();
//        mText.setValue("Model Training Details");
        mText.setValue("Internet Synthesis");
    }

    public LiveData<String> getText() {
        return mText;
    }

    // You can use this later to pass data to your TrainingWaveView
    public void updateModelTrainingGraph(float lossValue) {
        // Logic to update your animated graph goes here
    }
}