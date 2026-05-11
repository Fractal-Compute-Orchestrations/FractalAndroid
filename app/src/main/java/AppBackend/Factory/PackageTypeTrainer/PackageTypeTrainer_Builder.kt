package AppBackend.Factory.PackageTypeTrainer

import android.content.Context
import android.util.Log
import AppBackend.DataManager.DataLoaderAndInitializer.DataInitializer_Factory
import AppBackend.DataManager.DataLoaderAndInitializer.Image_DataInitializer_Factory
import AppBackend.LocalTrainingModule.TrainingExecutor.Image_Trainer_Factory
import AppBackend.LocalTrainingModule.TrainingExecutor.Trainer_Factory
import AppBackend.TaskContainer.Image_Task_Factory
import AppBackend.TaskContainer.Task_Factory
import AppBackend.Validator.ModelInferenceValidator.Image_InferenceValidator_Factory
import AppBackend.Validator.ModelInferenceValidator.InferenceValidator_Factory

class PackageTypeTrainerBuilder : Builder {

    private val TAG = "TrainerBuilder"

    // Placeholders for the generalized factories of each category
    private var taskFactory: Task_Factory? = null
    private var dataInitializerFactory: DataInitializer_Factory? = null
    private var trainerFactory: Trainer_Factory? = null
    private var validatorFactory: InferenceValidator_Factory? = null

    // We store the context temporarily during the build chain
    private var buildContext: Context? = null

    override fun build(
        taskName: String,
        dataInitializerName: String,
        trainerName: String,
        validatorName: String
    ): PackageTypeTrainer {
        Log.e(TAG, "Legacy build() method called without Context! Use make(context, preferences) instead.")
        throw UnsupportedOperationException("Legacy build without Context is no longer supported.")
    }


    override fun make(context: Context, packageTypeTrainer_preferences: Array<String>): PackageTypeTrainer {
        if (packageTypeTrainer_preferences.size != 4) {
            Log.e(TAG, "make() failed: Expected exactly 4 preferences, but got ${packageTypeTrainer_preferences.size}")
            require(packageTypeTrainer_preferences.size == 4) { "Expected exactly 4 preferences" }
        }

        Log.d(TAG, "make() called with preferences: ${packageTypeTrainer_preferences.joinToString()}")

        this.buildContext = context

        return this.addTask(packageTypeTrainer_preferences[0])
            .addDataInitializer(packageTypeTrainer_preferences[1])
            .addTrainer(packageTypeTrainer_preferences[2])
            .addValidator(packageTypeTrainer_preferences[3])
            .buildFinal()
    }

    fun addTask(task_name: String): PackageTypeTrainerBuilder {
        taskFactory = when (task_name) {
            "Image_Task" -> {
                Log.d(TAG, "Successfully initialized Image_Task_Factory")
                Image_Task_Factory()
            }
            else -> {
                Log.e(TAG, "Failed to add Task: Unknown configuration '$task_name'")
                throw IllegalArgumentException("Unknown Task configuration: $task_name")
            }
        }
        return this
    }

    fun addDataInitializer(dataInitializer_name: String): PackageTypeTrainerBuilder {
        dataInitializerFactory = when (dataInitializer_name) {
            "Image_DataInitializer" -> {
                Log.d(TAG, "Successfully initialized Image_DataInitializer_Factory")
                Image_DataInitializer_Factory()
            }
            else -> {
                Log.e(TAG, "Failed to add DataInitializer: Unknown configuration '$dataInitializer_name'")
                throw IllegalArgumentException("Unknown DataInitializer configuration: $dataInitializer_name")
            }
        }
        return this
    }

    fun addTrainer(trainer_name: String): PackageTypeTrainerBuilder {
        trainerFactory = when (trainer_name) {
            "Image_Trainer" -> {
                Log.d(TAG, "Successfully initialized Image_Trainer_Factory")
                Image_Trainer_Factory()
            }
            else -> {
                Log.e(TAG, "Failed to add Trainer: Unknown configuration '$trainer_name'")
                throw IllegalArgumentException("Unknown Trainer configuration: $trainer_name")
            }
        }
        return this
    }

    fun addValidator(validator_name: String): PackageTypeTrainerBuilder {
        validatorFactory = when (validator_name) {
            "Image_InferenceValidator" -> {
                Log.d(TAG, "Successfully initialized Image_InferenceValidator_Factory")
                Image_InferenceValidator_Factory()
            }
            else -> {
                Log.e(TAG, "Failed to add Validator: Unknown configuration '$validator_name'")
                throw IllegalArgumentException("Unknown Validator configuration: $validator_name")
            }
        }
        return this
    }

    // --- FINAL ASSEMBLY ---
    fun buildFinal(): PackageTypeTrainer {
        Log.d(TAG, "Validating factories before final assembly...")

        val ctx = buildContext ?: run {
            Log.e(TAG, "buildFinal() failed: Context not provided")
            error("Context not provided")
        }

        // --- STEP 1: TASK ---
        val tFactory = taskFactory ?: run {
            Log.e(TAG, "build() failed: TaskFactory not initialized")
            error("TaskFactory not initialized")
        }
        val task = tFactory.createTask()

        // --- STEP 2: DATA INITIALIZER ---
        val dFactory = dataInitializerFactory ?: run {
            Log.e(TAG, "build() failed: DataInitializerFactory not initialized")
            error("DataInitializerFactory not initialized")
        }
        val dataInitializer = dFactory.createDataInitializer()

        // --- STEP 3: TRAINER ---
        val trFactory = trainerFactory ?: run {
            Log.e(TAG, "build() failed: TrainerFactory not initialized")
            error("TrainerFactory not initialized")
        }
        val trainer = trFactory.createTrainer()

        // --- STEP 4: VALIDATOR ---
        val vFactory = validatorFactory ?: run {
            Log.e(TAG, "build() failed: ValidatorFactory not initialized")
            error("ValidatorFactory not initialized")
        }
        val validator = vFactory.createInferenceValidator()

        Log.i(TAG, "PackageTypeTrainer successfully assembled and ready to run!")

        // Pass the context into the trainer class
        return PackageTypeTrainer(
            ctx,
            task,
            dataInitializer,
            trainer,
            validator
        )
    }
}