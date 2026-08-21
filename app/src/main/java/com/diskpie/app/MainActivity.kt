package com.diskpie.app

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diskpie.app.model.StorageNode
import com.diskpie.app.ui.DiskPieScreen
import com.diskpie.app.ui.theme.DiskPieTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: DiskPieViewModel by viewModels()

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let(viewModel::selectTree)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            DiskPieTheme {
                DiskPieScreen(
                    uiState = uiState,
                    onChooseFolder = { folderPicker.launch(null) },
                    onRequestAllFilesAccess = ::openAllFilesAccessSettings,
                    onRequestUsageAccess = ::openUsageAccessSettings,
                    onRefreshAppStorage = viewModel::refreshAppStorage,
                    onToggleFolder = viewModel::setFolderSelected,
                    onSelectAllFolders = viewModel::selectAllFolders,
                    onDeselectAllFolders = viewModel::deselectAllFolders,
                    onRemoveFolder = viewModel::removeFolder,
                    onRescan = viewModel::rescan,
                    onCancelScan = viewModel::cancelScan,
                    onDismissError = viewModel::dismissError,
                    onDismissActionMessage = viewModel::dismissActionMessage,
                    onOpenFile = ::openFile,
                    onDeleteFile = viewModel::deleteFile,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAllFilesAccess()
        viewModel.refreshDeviceStorage()
        viewModel.refreshAppStorageAccess()
    }

    private fun openFile(file: StorageNode) {
        val sourceUri = file.uri.toUri()
        val openUri = if (sourceUri.scheme == ContentResolver.SCHEME_FILE) {
            val path = sourceUri.path
            if (path == null) {
                Toast.makeText(this, "파일 경로가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    File(path),
                )
            } catch (_: IllegalArgumentException) {
                Toast.makeText(this, "이 파일을 안전하게 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            sourceUri
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(openUri, file.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "이 파일을 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "파일 접근 권한이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(
                this,
                "이 Android 버전에서는 폴더 선택 방식으로 접근 범위를 추가해 주세요.",
                Toast.LENGTH_LONG,
            ).show()
            folderPicker.launch(null)
            return
        }

        val appSettings = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            "package:$packageName".toUri(),
        )
        try {
            startActivity(appSettings)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "모든 파일 접근 설정을 열 수 없습니다.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openUsageAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "사용정보 접근 설정을 열 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }
}
