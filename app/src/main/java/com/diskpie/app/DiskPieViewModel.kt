package com.diskpie.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.diskpie.app.data.DeviceStorageReader
import com.diskpie.app.data.StorageFileManager
import com.diskpie.app.data.StorageScanner
import com.diskpie.app.model.DeviceStorage
import com.diskpie.app.model.ScanProgress
import com.diskpie.app.model.ScanResult
import com.diskpie.app.model.StorageNode
import com.diskpie.app.model.StorageTree
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiskPieUiState(
    val selectedTreeUri: String? = null,
    val deviceStorage: DeviceStorage? = null,
    val isScanning: Boolean = false,
    val progress: ScanProgress = ScanProgress(),
    val result: ScanResult? = null,
    val canDeleteFiles: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
)

class DiskPieViewModel(application: Application) : AndroidViewModel(application) {
    private val scanner = StorageScanner(application)
    private val fileManager = StorageFileManager(application)
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val savedTreeUri = preferences.getString(KEY_TREE_URI, null)
    private val _uiState = MutableStateFlow(
        DiskPieUiState(
            selectedTreeUri = savedTreeUri,
            canDeleteFiles = hasPersistedWritePermission(savedTreeUri),
        ),
    )
    val uiState: StateFlow<DiskPieUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        refreshDeviceStorage()
        _uiState.value.selectedTreeUri?.let(::startScan)
    }

    fun selectTree(uri: Uri) {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        var writePermissionGranted = true
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            writePermissionGranted = false
            try {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                _uiState.update {
                    it.copy(errorMessage = "이 폴더의 읽기 권한을 유지할 수 없습니다. 다른 폴더를 선택해 주세요.")
                }
                return
            }
        }

        val uriString = uri.toString()
        val canDelete = writePermissionGranted && hasPersistedWritePermission(uriString)
        preferences.edit { putString(KEY_TREE_URI, uriString) }
        _uiState.update {
            it.copy(
                selectedTreeUri = uriString,
                result = null,
                canDeleteFiles = canDelete,
                errorMessage = null,
                actionMessage = if (canDelete) null else "이 위치는 읽기 전용입니다. 분석은 가능하지만 삭제할 수 없습니다.",
            )
        }
        startScan(uriString)
    }

    fun rescan() {
        _uiState.value.selectedTreeUri?.let(::startScan)
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun deleteFile(file: StorageNode) {
        val state = _uiState.value
        val validationError = when {
            file.isDirectory -> "폴더 전체 삭제는 지원하지 않습니다. 개별 파일을 선택해 주세요."
            state.isScanning -> "분석이 끝난 뒤 파일을 삭제해 주세요."
            state.isDeleting -> "다른 파일을 삭제하고 있습니다. 잠시 기다려 주세요."
            !state.canDeleteFiles -> "삭제 권한이 없습니다. 같은 폴더를 다시 선택해 쓰기 권한을 허용해 주세요."
            state.result?.let { !StorageTree.containsFile(it.root, file.uri) } != false -> {
                "현재 분석 결과에 없는 파일입니다. 다시 분석한 뒤 시도해 주세요."
            }
            else -> null
        }
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        _uiState.update { it.copy(isDeleting = true, errorMessage = null, actionMessage = null) }
        viewModelScope.launch {
            try {
                val deleted = fileManager.deleteFile(file.uri.toUri())
                if (!deleted) error("저장소 공급자가 삭제 요청을 거부했습니다.")

                val selectedTreeUri = _uiState.value.selectedTreeUri
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        actionMessage = "‘${file.name}’ 파일을 삭제했습니다.",
                    )
                }
                selectedTreeUri?.let(::startScan)
            } catch (_: SecurityException) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        canDeleteFiles = false,
                        errorMessage = "삭제 권한이 없거나 만료되었습니다. 폴더를 다시 선택해 주세요.",
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = error.message ?: "파일을 삭제하지 못했습니다.",
                    )
                }
            }
        }
    }

    fun refreshDeviceStorage() {
        _uiState.update {
            it.copy(deviceStorage = runCatching(DeviceStorageReader::readInternalStorage).getOrNull())
        }
    }

    private fun startScan(uriString: String) {
        scanJob?.cancel()
        _uiState.update {
            it.copy(
                isScanning = true,
                progress = ScanProgress(),
                errorMessage = null,
            )
        }

        scanJob = viewModelScope.launch {
            try {
                val result = scanner.scan(uriString.toUri()) { progress ->
                    _uiState.update { current -> current.copy(progress = progress) }
                }
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        progress = ScanProgress(),
                        result = result,
                    )
                }
                refreshDeviceStorage()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        canDeleteFiles = false,
                        errorMessage = "폴더 접근 권한이 만료되었습니다. 폴더를 다시 선택해 주세요.",
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = error.message ?: "저장공간을 분석하는 중 문제가 발생했습니다.",
                    )
                }
            }
        }
    }

    private fun hasPersistedWritePermission(uriString: String?): Boolean {
        if (uriString == null) return false
        return getApplication<Application>().contentResolver.persistedUriPermissions.any { permission ->
            permission.uri.toString() == uriString && permission.isWritePermission
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "diskpie_preferences"
        const val KEY_TREE_URI = "selected_tree_uri"
    }
}
