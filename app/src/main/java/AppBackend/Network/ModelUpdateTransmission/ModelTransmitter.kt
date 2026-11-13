package AppBackend.Network.ModelUpdateTransmission

import android.util.Log
import AppBackend.Network.Server_DAO.Server_DAO
import AppBackend.TaskContainer.Image_Task
import java.io.File

class ModelTransmitter(
    private val baseFilesDir: File? = null,
) {
    private val TAG = "ModelTransmitter"

    fun transmitModel(modeltransmissionDto: ModelTransmission_DTO): Boolean {
        Log.i(TAG, "Preparing to transmit model for Task ID: ${modeltransmissionDto.task.task_Id}")

        val serverDao = Server_DAO(baseFilesDir = baseFilesDir)
        return serverDao.POST_UploadModelToServer(modeltransmissionDto)
    }

    fun transmitQuantizedState(
        modeltransmissionDto: ModelTransmission_DTO,
        tensors: Map<String, Pair<FloatArray, IntArray>>,
        blockSize: Int = Int4BlockCodec.DEFAULT_BLOCK_SIZE,
    ): Boolean {
        val (frqdBundle, stats) = Int4BlockCodec.encodeStateDict(tensors, blockSize = blockSize)
        val rawName = (modeltransmissionDto.task as? Image_Task)?.CKPT_FILENAME ?: "model_update.ckpt"
        val wireFilename = if (rawName.endsWith(".frqd")) rawName else "$rawName.frqd"
        Log.i(
            TAG,
            "Compressed FRQD update for Task ${modeltransmissionDto.task.task_Id}: " +
                "${stats.originalBytes} B -> ${stats.compressedBytes} B " +
                "(${String.format("%.2fx", stats.compressionRatio)}, cos=${String.format("%.4f", stats.cosineSimilarity)})"
        )
        val serverDao = Server_DAO(baseFilesDir = baseFilesDir)
        return serverDao.uploadBytesToServer(modeltransmissionDto.task.task_Id, wireFilename, frqdBundle)
    }
}