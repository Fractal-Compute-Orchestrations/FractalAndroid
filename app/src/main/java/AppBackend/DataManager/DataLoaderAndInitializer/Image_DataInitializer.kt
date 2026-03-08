package AppBackend.DataManager.DataLoaderAndInitializer

import android.util.Log
import AppBackend.TaskContainer.Image_Task
import AppBackend.TaskContainer.Task
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Image_DataInitializer : DataInitializer {

    private val TAG = "Image_DataInit"
    private var imageStream: InputStream? = null
    private var labelStream: InputStream? = null

    override fun locadBatch(task: Task): Pair<InputStream, InputStream> {
        Log.d(TAG, "--> locadBatch(): Entering method.")
        val imageTask = task as Image_Task

        val filesDir = "/data/data/org.fractal.app/files/"
        val imgFile = File(filesDir, imageTask.TRAIN_IMAGES_FILENAME)
        val lblFile = File(filesDir, imageTask.TRAIN_LABELS_FILENAME)

        Log.d(TAG, "--> locadBatch(): Looking for Images: ${imgFile.absolutePath}")
        Log.d(TAG, "--> locadBatch(): Looking for Labels: ${lblFile.absolutePath}")

        if (!imgFile.exists() || !lblFile.exists()) {
            Log.e(TAG, "--> locadBatch(): Missing training .bin files in local storage!")
            throw Exception("Data Initializer Error: Missing training .bin files in local storage!")
        }

        Log.d(TAG, "--> locadBatch(): Files found. Opening streams...")
        imageStream = FileInputStream(imgFile)
        labelStream = FileInputStream(lblFile)

        Log.d(TAG, "--> locadBatch(): Streams opened successfully.")
        return Pair(imageStream!!, labelStream!!)
    }

    override fun preprocess(task: Task): Pair<FloatBuffer, FloatBuffer> {
        Log.d(TAG, "--> preprocess(): Entering method.")
        val imageTask = task as Image_Task

        if (imageStream == null || labelStream == null) {
            Log.d(TAG, "--> preprocess(): Streams are null. Calling locadBatch()...")
            locadBatch(task)
        }

        try {
            Log.d(TAG, "--> preprocess(): Extracting task dimensions...")
            val numTrainings = imageTask.NUM_TRAININGS
            val numClasses = imageTask.NUM_CLASSES
            val shapeArray = imageTask.INPUT_SHAPE

            Log.d(TAG, "--> preprocess(): INPUT_SHAPE received: ${shapeArray.contentToString()} (Length: ${shapeArray.size})")

            // SAFE EXTRACTION: Dynamically adapt to the array size sent by the server
            val imgHeight: Int
            val imgWidth: Int
            if (shapeArray.size == 2) {
                // Server sent [28, 28]
                imgHeight = shapeArray[0]
                imgWidth = shapeArray[1]
            } else if (shapeArray.size >= 3) {
                // Server sent [1, 28, 28, 1] or [28, 28, 1]
                imgHeight = shapeArray[1]
                imgWidth = shapeArray[2]
            } else {
                // Fallback
                imgHeight = 28
                imgWidth = 28
            }

            Log.d(TAG, "--> preprocess(): Parsed Dimensions - H:$imgHeight, W:$imgWidth, Trainings:$numTrainings, Classes:$numClasses")

            Log.d(TAG, "--> preprocess(): Calculating exact byte sizes...")
            val imageBytes = numTrainings * imgHeight * imgWidth * 4
            val labelBytes = numTrainings * numClasses * 4
            Log.d(TAG, "--> preprocess(): Image bytes required: $imageBytes | Label bytes required: $labelBytes")

            Log.d(TAG, "--> preprocess(): Allocating Direct Buffers...")
            val imageBatch = ByteBuffer.allocateDirect(imageBytes).order(ByteOrder.nativeOrder()).asFloatBuffer()
            val labelBatch = ByteBuffer.allocateDirect(labelBytes).order(ByteOrder.nativeOrder()).asFloatBuffer()

            Log.d(TAG, "--> preprocess(): Creating temporary byte arrays...")
            val imageData = ByteArray(imageBytes)
            val labelData = ByteArray(labelBytes)

            Log.d(TAG, "--> preprocess(): Reading file streams into memory...")
            val imgRead = imageStream!!.read(imageData)
            val lblRead = labelStream!!.read(labelData)
            Log.d(TAG, "--> preprocess(): Bytes read into memory - Images: $imgRead, Labels: $lblRead")

            Log.d(TAG, "--> preprocess(): Wrapping arrays and converting to Floats...")
            val imageFloatBuffer = ByteBuffer.wrap(imageData).order(ByteOrder.nativeOrder()).asFloatBuffer()
            val labelFloatBuffer = ByteBuffer.wrap(labelData).order(ByteOrder.nativeOrder()).asFloatBuffer()

            val imageFloats = FloatArray(numTrainings * imgHeight * imgWidth)
            val labelFloats = FloatArray(numTrainings * numClasses)

            imageFloatBuffer.get(imageFloats)
            labelFloatBuffer.get(labelFloats)

            Log.d(TAG, "--> preprocess(): Putting floats into final Direct Buffers...")
            imageBatch.put(imageFloats).rewind()
            labelBatch.put(labelFloats).rewind()

            Log.d(TAG, "--> preprocess(): Memory cleanup - closing streams...")
            imageStream!!.close()
            labelStream!!.close()

            Log.d(TAG, "--> preprocess(): SUCCESS. Returning processed pairs.")
            return Pair(imageBatch, labelBatch)

        } catch (e: Exception) {
            Log.e(TAG, "--> preprocess(): FATAL ERROR CAUGHT: ${e.message}")
            e.printStackTrace()
            throw e // Rethrow to be caught by PackageTypeTrainer
        }
    }
}