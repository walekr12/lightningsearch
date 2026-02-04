package com.example.lightningsearch.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: SearchViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            onPermissionGranted()
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            onPermissionGranted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[SearchViewModel::class.java]

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

        if (hasStoragePermission()) {
            onPermissionGranted()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                manageStorageLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (e: Exception) {
                manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun onPermissionGranted() {
        viewModel.setPermissionGranted(true)
        viewModel.checkAndStartIndexing { getStoragePaths() }
    }

    private fun getStoragePaths(): List<String> {
        val paths = mutableListOf<String>()
        Environment.getExternalStorageDirectory()?.absolutePath?.let { paths.add(it) }
        getExternalFilesDirs(null).forEach { file ->
            file?.absolutePath?.substringBefore("/Android/")?.let { rootPath ->
                if (rootPath !in paths) paths.add(rootPath)
            }
        }
        return paths
    }
}
