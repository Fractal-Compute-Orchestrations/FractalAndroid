package AppGlobal.Utils

import android.content.Context
import java.io.File

/**
 * Centralized internal storage path resolver for FractalAndroid.
 *
 * Captures `context.filesDir` at application initialization while permitting
 * explicit directory overrides for JVM unit tests and enforcing canonical
 * path-traversal checks on server-supplied filenames.
 */
object FractalPaths {
    @Volatile
    private var appFilesDir: File? = null

    fun init(context: Context) {
        appFilesDir = context.filesDir
    }

    fun resolveFilesDir(overrideDir: File? = null): File {
        return overrideDir ?: appFilesDir ?: File("/data/data/org.fractal.app/files")
    }

    fun resolveFile(fileName: String, overrideDir: File? = null): File {
        val baseDir = resolveFilesDir(overrideDir)
        val cleanName = fileName.trim()
        require(cleanName.isNotEmpty()) { "Filename must not be empty." }
        val baseCanonical = baseDir.canonicalFile
        val candidate = File(baseCanonical, cleanName).canonicalFile
        val basePath = baseCanonical.path
        val candidatePath = candidate.path
        require(
            candidatePath.startsWith(basePath + File.separator) || candidatePath == basePath
        ) {
            "Path traversal blocked for filename: $fileName"
        }
        return candidate
    }
}
