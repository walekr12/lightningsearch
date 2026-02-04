package com.example.lightningsearch.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.lightningsearch.ui.theme.LightningSearchTheme
import dagger.hilt.android.AndroidEntryPoint

private const val TAG = "LightningSearch"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: SearchViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permission result: $permissions")
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onPermissionGranted()
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "Manage storage result")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                onPermissionGranted()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        try {
            enableEdgeToEdge()
            Log.d(TAG, "enableEdgeToEdge done")

            viewModel = ViewModelProvider(this)[SearchViewModel::class.java]
            Log.d(TAG, "ViewModel created")

            setContent {
                LightningSearchTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        SearchScreen(
                            onRequestPermission = { requestStoragePermission() },
                            viewModel = viewModel
                        )
                    }
                }
            }
            Log.d(TAG, "setContent done")

            checkAndRequestPermission()
            Log.d(TAG, "onCreate completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            throw e
        }
    }

    private fun checkAndRequestPermission() {
        Log.d(TAG, "checkAndRequestPermission")
        try {
            if (hasStoragePermission()) {
                Log.d(TAG, "Has permission, calling onPermissionGranted")
                onPermissionGranted()
            } else {
                Log.d(TAG, "No permission yet")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkAndRequestPermission", e)
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        Log.d(TAG, "requestStoragePermission")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error launching manage storage", e)
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun onPermissionGranted() {
        Log.d(TAG, "onPermissionGranted called")
        try {
            viewModel.setPermissionGranted(true)
            Log.d(TAG, "setPermissionGranted(true) done")

            // Only start indexing if not already indexed
            val state = viewModel.state.value
            Log.d(TAG, "Current state: totalIndexed=${state.totalIndexed}, isIndexing=${state.isIndexing}")

            if (state.totalIndexed == 0 && !state.isIndexing) {
                // Get all storage paths
                val storagePaths = mutableListOf<String>()

                // Internal storage
                try {
                    Environment.getExternalStorageDirectory()?.absolutePath?.let {
                        Log.d(TAG, "Adding storage path: $it")
                        storagePaths.add(it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting external storage", e)
                }

                // External SD cards
                try {
                    getExternalFilesDirs(null).forEach { file ->
                        file?.absolutePath?.let { path ->
                            // Extract root path from app-specific path
                            val rootPath = path.substringBefore("/Android/")
                            if (rootPath !in storagePaths) {
                                Log.d(TAG, "Adding SD card path: $rootPath")
                                storagePaths.add(rootPath)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting external file dirs", e)
                }

                Log.d(TAG, "Starting indexing with paths: $storagePaths")
                viewModel.startIndexing(storagePaths)
            } else {
                Log.d(TAG, "Skipping indexing - already indexed or in progress")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onPermissionGranted", e)
        }
    }
}
