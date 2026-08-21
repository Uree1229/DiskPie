package com.diskpie.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diskpie.app.model.StorageNode
import com.diskpie.app.ui.DiskPieScreen
import com.diskpie.app.ui.theme.DiskPieTheme

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
        viewModel.refreshDeviceStorage()
    }

    private fun openFile(file: StorageNode) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri.toUri(), file.mimeType ?: "*/*")
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
}
