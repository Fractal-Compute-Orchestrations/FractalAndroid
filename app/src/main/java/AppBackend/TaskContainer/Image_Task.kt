package AppBackend.TaskContainer

import java.util.Date

class Image_Task(
    override var task_Id: String = "-1",
    override var taskType: TaskType = TaskType.ActiveTask,

    override var task_expire_date: Date = Date(),

    override var task_completion_status: Boolean = false,
    override var training_type: List<String> = listOf(),
    override var CKPT_FILENAME: String = "checkpoint.ckpt",

    // --- NEW REQUIRED OVERRIDES ---
    override var architecture: String = "Unknown",
    override var reward_rate: Double = 0.0,
    // ------------------------------

    override var NUM_EPOCHS: Int = 20,
    override var input_tensor_name: Map<List<String>, Any> = mutableMapOf(),
    override var output_tensor_name: Map<List<String>, Any> = mutableMapOf(),
    override var MODEL_FILENAME: String = "model_server.tflite",

    override var BATCH_SIZE: Int = 100,
    override var NUM_TRAININGS: Int = 6000,
    override var INPUT_SHAPE: Array<Int> = arrayOf(1, 28, 28, 1),
    override var NUM_CLASSES: Int = 10,
    override var TRAIN_IMAGES_FILENAME: String = "train_images_server.bin",
    override var TRAIN_LABELS_FILENAME: String = "train_labels_server.bin"
) : Task, Image_Task_ModelParams, Image_Task_DataParams {

    override fun save_data(): Boolean {
        return true
    }
}