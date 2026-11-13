package com.example.fractal

import AppGlobal.Utils.FractalPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FractalPathsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun resolvesValidFilesInsideBaseDirectory() {
        val baseDir = tempFolder.newFolder("fractal_files")
        val resolved = FractalPaths.resolveFile("checkpoint.ckpt", baseDir)
        assertEquals(File(baseDir.canonicalFile, "checkpoint.ckpt"), resolved)
        assertTrue(resolved.canonicalPath.startsWith(baseDir.canonicalPath))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blocksParentDirectoryTraversal() {
        val baseDir = tempFolder.newFolder("fractal_files")
        FractalPaths.resolveFile("../escape.ckpt", baseDir)
    }
}
