package AppGlobal.Utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File

class FileOperations(val context: Context) {

    val gson = Gson()
    val TAG = "FileOperations"

    // ----------------------------------------------------
    // WRITE JSON
    // ----------------------------------------------------
    fun <T> writeJson(fileName: String, data: T) {
        val file = File(context.filesDir, fileName)
        val json = gson.toJson(data)

        Log.d(TAG, "writeJson(): Writing to $fileName -> $json")

        file.writeText(json)

        Log.d(TAG, "writeJson(): Successfully wrote to $fileName")
    }

    // Java-callable version (reified inline functions are invisible to Java)
    fun <T> readJson(fileName: String, clazz: Class<T>): T? {
        val file = File(context.filesDir, fileName)
        Log.d(TAG, "readJson(java): Checking if $fileName exists...")
        if (!file.exists()) {
            Log.w(TAG, "readJson(java): File $fileName does NOT exist.")
            return null
        }
        return try {
            val json = file.readText()
            Log.d(TAG, "readJson(java): Raw JSON -> $json")
            val obj = gson.fromJson(json, clazz)
            Log.d(TAG, "readJson(java): Parsed -> $obj")
            obj
        } catch (e: Exception) {
            Log.e(TAG, "readJson(java): ERROR reading $fileName", e)
            null
        }
    }

    // ----------------------------------------------------
    // READ JSON
    // ----------------------------------------------------
    inline fun <reified T> readJson(fileName: String): T? {
        val file = File(context.filesDir, fileName)

        Log.d(TAG, "readJson(): Checking if $fileName exists...")

        if (!file.exists()) {
            Log.w(TAG, "readJson(): File $fileName does NOT exist.")
            return null
        }

        return try {
            val json = file.readText()
            Log.d(TAG, "readJson(): Raw JSON from $fileName -> $json")

            val obj = gson.fromJson(json, T::class.java)

            Log.d(TAG, "readJson(): Parsed object -> $obj")

            obj
        } catch (e: Exception) {
            Log.e(TAG, "readJson(): ERROR reading $fileName", e)
            null
        }
    }

    // ----------------------------------------------------
    // UPDATE JSON
    // ----------------------------------------------------
    inline fun <reified T> updateJson(fileName: String, updateAction: (T) -> T) {
        Log.d(TAG, "updateJson(): Attempting to update $fileName")

        val existing: T? = readJson(fileName)

        if (existing == null) {
            Log.w(TAG, "updateJson(): Cannot update $fileName because it does NOT exist.")
            return
        }

        Log.d(TAG, "updateJson(): Current object -> $existing")

        val updated = updateAction(existing)

        Log.d(TAG, "updateJson(): Updated object -> $updated")

        writeJson(fileName, updated)

        Log.d(TAG, "updateJson(): Successfully updated $fileName")
    }

    // ----------------------------------------------------
    // DELETE FILE
    // ----------------------------------------------------
    fun deleteFile(fileName: String): Boolean {
        val file = File(context.filesDir, fileName)
        Log.d(TAG, "deleteFile(): Trying to delete $fileName")

        return if (file.exists()) {
            val result = file.delete()
            Log.d(TAG, "deleteFile(): Delete result for $fileName -> $result")
            result
        } else {
            Log.w(TAG, "deleteFile(): File $fileName does NOT exist.")
            false
        }
    }

    // ----------------------------------------------------
    // FILE EXISTS
    // ----------------------------------------------------
    fun fileExists(fileName: String): Boolean {
        val exists = File(context.filesDir, fileName).exists()
        Log.d(TAG, "fileExists(): $fileName exists -> $exists")
        return exists
    }

    // ----------------------------------------------------
    // COPY DEFAULT FROM ASSETS
    // ----------------------------------------------------
    fun copyDefaultFromAssets(assetFileName: String, destFileName: String) {
        val destFile = File(context.filesDir, destFileName)

        Log.d(TAG, "copyDefaultFromAssets(): Checking destination file $destFileName")

        if (destFile.exists()) {
            Log.d(TAG, "copyDefaultFromAssets(): File already exists. Skipping.")
            return
        }

        try {
            Log.d(TAG, "copyDefaultFromAssets(): Copying $assetFileName → $destFileName")

            context.assets.open(assetFileName).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "copyDefaultFromAssets(): Successfully copied asset file.")
        } catch (e: Exception) {
            Log.e(TAG, "copyDefaultFromAssets(): ERROR copying asset $assetFileName", e)
        }
    }
}
