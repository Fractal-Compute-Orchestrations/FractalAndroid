package com.example.fractal

import AppBackend.Factory.PackageTypeTrainer.PackageTypeTrainerBuilder
import android.app.Application
import android.util.Log
import AppBackend.Network.Server_DAO.Server_DAO
import AppGlobal.GlobalState
import AppGlobal.app_config
import AppGlobal.Utils.FileOperations
import AppGlobal.Utils.GlobalUtils
import androidx.appcompat.app.AppCompatDelegate
import java.util.concurrent.Executors // Add this

class FractalApplication : Application() {

    // Use 'volatile' or proper synchronization if these are accessed immediately by other threads,
    // but usually, by the time the user clicks anything, this background thread has finished.
    lateinit var globalState: GlobalState
    lateinit var globalUtils: GlobalUtils
    lateinit var appConfig: app_config

    // A fast, single-thread executor for startup tasks
    private val startupExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()

        // 1. MUST BE ON MAIN THREAD: Lock Theme Instantly
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // 2. MUST BE ON MAIN THREAD: Utils (Super lightweight, safe here)
        globalUtils = GlobalUtils()

        // 3. OFFLOAD HEAVY TASKS: Disk I/O and JSON parsing to background
        startupExecutor.execute {
            initializeHeavyComponents()
        }
    }

    private fun initializeHeavyComponents() {
        try {
            val fileOps = FileOperations(this)
            val fileName = "app_config.json"
            val defaultAsset = "app_config_default.json"

            // Disk I/O: Copy default JSON if needed
            fileOps.copyDefaultFromAssets(defaultAsset, fileName)

            // Disk I/O: Load config file
            val loadedConfig = fileOps.readJson<app_config>(fileName)
            appConfig = loadedConfig ?: app_config()
            if (loadedConfig == null) {
                fileOps.writeJson(fileName, appConfig)
            }

            // Setup Server & Engines
            val serverDao = Server_DAO()
            val trainerBuilder = PackageTypeTrainerBuilder()

            // Setup GlobalState
            val state = GlobalState()
            state.server = serverDao
            state.appConfig = appConfig
            state.packageTypeTrainerBuilder = trainerBuilder

            // Assign to the public variable once fully constructed
            globalState = state

            Log.d("FractalApp", "FractalApplication background init finished. Config Loaded: $appConfig")
        } catch (e: Exception) {
            Log.e("FractalApp", "Error during background initialization", e)
        }
    }
}