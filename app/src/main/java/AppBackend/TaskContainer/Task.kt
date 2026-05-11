package AppBackend.TaskContainer

import java.time.LocalDate
import java.util.Date

interface Task: Task_ModelParams, Task_DataParams{
    var task_Id: String
    var taskType: TaskType
    var task_expire_date: Date
    var task_completion_status: Boolean
    var training_type: List<String>
    var CKPT_FILENAME: String
    var architecture: String
    var reward_rate: Double

    fun save_data(): Boolean
}