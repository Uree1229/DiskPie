package com.diskpie.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diskpie.app.DiskPieUiState
import com.diskpie.app.SelectedFolder
import com.diskpie.app.model.AppStorageSummary
import com.diskpie.app.model.AppStorageUsage
import com.diskpie.app.model.CategoryUsage
import com.diskpie.app.model.DeviceStorage
import com.diskpie.app.model.FileCategory
import com.diskpie.app.model.ScanProgress
import com.diskpie.app.model.ScanResult
import com.diskpie.app.model.StorageNode
import com.diskpie.app.model.StorageTree
import com.diskpie.app.ui.theme.Cream
import com.diskpie.app.ui.theme.Leaf
import com.diskpie.app.ui.theme.Muted
import com.diskpie.app.ui.theme.Outline
import com.diskpie.app.ui.theme.RaisedInk
import com.diskpie.app.ui.theme.SoftInk
import com.diskpie.app.ui.theme.Warning
import com.diskpie.app.ui.treemap.StorageTreemap
import com.diskpie.app.ui.treemap.TreemapEntry
import com.diskpie.app.ui.treemap.appPaletteColor
import com.diskpie.app.ui.treemap.categoryColor
import com.diskpie.app.util.ByteFormatter
import java.util.Locale

@Composable
fun DiskPieScreen(
    uiState: DiskPieUiState,
    onChooseFolder: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRefreshAppStorage: () -> Unit,
    onToggleFolder: (String, Boolean) -> Unit,
    onSelectAllFolders: () -> Unit,
    onDeselectAllFolders: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRescan: () -> Unit,
    onCancelScan: () -> Unit,
    onDismissError: () -> Unit,
    onDismissActionMessage: () -> Unit,
    onOpenFile: (StorageNode) -> Unit,
    onDeleteFile: (StorageNode) -> Unit,
) {
    var searchQuery by remember(uiState.result) { mutableStateOf("") }
    var filePendingDeletion by remember { mutableStateOf<StorageNode?>(null) }
    var showAllFilesDisclosure by remember { mutableStateOf(false) }
    var showUsageAccessDisclosure by remember { mutableStateOf(false) }
    val displayedFiles: List<StorageNode> = remember(uiState.result, searchQuery) {
        val result = uiState.result ?: return@remember emptyList()
        if (searchQuery.isBlank()) {
            result.largestFiles
        } else {
            StorageTree.searchFiles(result.root, searchQuery, FILE_SEARCH_LIMIT)
        }
    }
    val deletionEnabled = uiState.canDeleteFiles && !uiState.isScanning && !uiState.isDeleting
    val selectedFolderCount = uiState.selectedFolders.count(SelectedFolder::isSelected)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                top = 18.dp,
                end = 20.dp,
                bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { AppHeader() }

            uiState.deviceStorage?.let { storage ->
                item { DeviceStorageCard(storage) }
            }

            if (uiState.supportsAllFilesAccess) {
                item {
                    AllFilesAccessCard(
                        hasAccess = uiState.hasAllFilesAccess,
                        onRequestAccess = {
                            if (uiState.hasAllFilesAccess) {
                                onRequestAllFilesAccess()
                            } else {
                                showAllFilesDisclosure = true
                            }
                        },
                    )
                }
            }

            item {
                AppStorageSection(
                    deviceStorage = uiState.deviceStorage,
                    scannedBytes = uiState.result?.totalBytes,
                    hasAccess = uiState.hasUsageStatsAccess,
                    isLoading = uiState.isLoadingAppStorage,
                    summary = uiState.appStorage,
                    errorMessage = uiState.appStorageError,
                    onRequestAccess = {
                        if (uiState.hasUsageStatsAccess) {
                            onRequestUsageAccess()
                        } else {
                            showUsageAccessDisclosure = true
                        }
                    },
                    onRefresh = onRefreshAppStorage,
                )
            }

            uiState.errorMessage?.let { message ->
                item { ErrorCard(message, onDismissError) }
            }

            uiState.actionMessage?.let { message ->
                item { ActionMessageCard(message, onDismissActionMessage) }
            }

            if (uiState.selectedFolders.isEmpty()) {
                item { EmptyState(onChooseFolder) }
            } else {
                item {
                    FolderSelectionCard(
                        folders = uiState.selectedFolders,
                        onChooseFolder = onChooseFolder,
                        onToggleFolder = onToggleFolder,
                        onSelectAllFolders = onSelectAllFolders,
                        onDeselectAllFolders = onDeselectAllFolders,
                        onRemoveFolder = onRemoveFolder,
                    )
                }
                if (selectedFolderCount == 0) {
                    item { NoFolderSelectedCard() }
                }
            }

            if (uiState.isScanning) {
                item { ScanningCard(uiState.progress, onCancelScan) }
            }

            if (uiState.isDeleting) {
                item { DeletingCard() }
            }

            uiState.result?.let { result ->
                item {
                    SelectionSummaryCard(
                        result = result,
                        isScanning = uiState.isScanning,
                        onRescan = onRescan,
                        onChooseFolder = onChooseFolder,
                    )
                }
                item {
                    TreemapSection(
                        result = result,
                        deletionEnabled = deletionEnabled,
                        onRequestDelete = { filePendingDeletion = it },
                    )
                }
                item { CategorySection(result.categories, result.totalBytes) }
                item {
                    FileSearchSection(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        canDeleteFiles = uiState.canDeleteFiles,
                        onChooseFolder = onChooseFolder,
                    )
                }
                items(displayedFiles, key = StorageNode::uri) { file ->
                    FileRow(
                        file = file,
                        deletionEnabled = deletionEnabled,
                        onOpen = { onOpenFile(file) },
                        onDelete = { filePendingDeletion = file },
                    )
                }
                if (searchQuery.isNotBlank() && displayedFiles.isEmpty()) {
                    item {
                        Text("일치하는 파일이 없습니다.", color = Muted, fontSize = 13.sp)
                    }
                }
                if (result.inaccessibleCount > 0) {
                    item {
                        Text(
                            text = "접근할 수 없거나 중복된 항목 ${result.inaccessibleCount}개는 집계에서 제외했습니다.",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }

    filePendingDeletion?.let { file ->
        DeleteFileDialog(
            file = file,
            onDismiss = { filePendingDeletion = null },
            onConfirm = {
                filePendingDeletion = null
                onDeleteFile(file)
            },
        )
    }

    if (showAllFilesDisclosure) {
        AllFilesAccessDisclosureDialog(
            onDismiss = { showAllFilesDisclosure = false },
            onAgree = {
                showAllFilesDisclosure = false
                onRequestAllFilesAccess()
            },
        )
    }


    if (showUsageAccessDisclosure) {
        UsageAccessDisclosureDialog(
            onDismiss = { showUsageAccessDisclosure = false },
            onAgree = {
                showUsageAccessDisclosure = false
                onRequestUsageAccess()
            },
        )
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Leaf),
            contentAlignment = Alignment.Center,
        ) {
            Text("◔", color = Color(0xFF101311), fontSize = 29.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "DiskPie",
                color = Cream,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
            )
            Text("내 저장공간을 한눈에", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DeviceStorageCard(storage: DeviceStorage) {
    DashboardCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { storage.usedFraction },
                    modifier = Modifier.size(92.dp),
                    color = Leaf,
                    trackColor = Outline,
                    strokeWidth = 9.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(storage.usedFraction * 100).toInt()}%",
                        color = Cream,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("사용", color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("현재 사용 중인 공간", color = Muted, fontSize = 13.sp)
                Text(
                    ByteFormatter.format(storage.usedBytes),
                    color = Cream,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "전체 ${ByteFormatter.format(storage.totalBytes)}  ·  여유 ${ByteFormatter.format(storage.availableBytes)}",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun AllFilesAccessCard(
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
) {
    DashboardCard(containerColor = if (hasAccess) Color(0xFF1D2A1C) else SoftInk) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(
                "전체 저장공간 탐색",
                if (hasAccess) "허용됨" else "추가 동의 필요",
            )
            Text(
                text = if (hasAccess) {
                    "공유 저장공간과 연결된 SD·USB의 접근 가능한 폴더를 한 번에 분석합니다."
                } else {
                    "Android의 앱별 ‘모든 파일 접근’을 허용하면 폴더를 하나씩 고르지 않고 공유 저장공간 전체를 분석할 수 있습니다."
                },
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            if (hasAccess) {
                Text(
                    "다른 앱의 Android/data 전용 영역은 운영체제 정책상 계속 제외됩니다.",
                    color = Warning,
                    fontSize = 12.sp,
                )
            }
            Button(
                onClick = onRequestAccess,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Leaf,
                    contentColor = Color(0xFF101311),
                ),
            ) {
                Text(if (hasAccess) "권한 설정 열기" else "전체 접근 동의 및 설정", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AllFilesAccessDisclosureDialog(
    onDismiss: () -> Unit,
    onAgree: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("전체 파일 접근 동의") },
        text = {
            Text(
                "DiskPie가 저장공간 사용량을 계산하고 파일 열기·삭제 기능을 제공하기 위해 공유 저장공간의 파일 이름, 크기와 폴더 구조를 기기 안에서 읽습니다. 분석 정보는 외부로 전송하지 않습니다. 동의 후 Android 시스템 설정에서도 이 앱의 ‘모든 파일 접근’을 허용해 주세요. 다른 앱의 보호된 Android/data 영역은 허용 후에도 접근할 수 없습니다.",
            )
        },
        confirmButton = {
            Button(onClick = onAgree) { Text("동의하고 설정 열기") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun AppStorageSection(
    deviceStorage: DeviceStorage?,
    scannedBytes: Long?,
    hasAccess: Boolean,
    isLoading: Boolean,
    summary: AppStorageSummary?,
    errorMessage: String?,
    onRequestAccess: () -> Unit,
    onRefresh: () -> Unit,
) {
    var selectedPackage by remember(summary) {
        mutableStateOf(summary?.apps?.firstOrNull()?.packageName)
    }
    val selectedApp = summary?.apps?.firstOrNull { it.packageName == selectedPackage }

    DashboardCard(containerColor = if (hasAccess) RaisedInk else SoftInk) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                SectionTitle(
                    title = "앱별 저장공간",
                    subtitle = when {
                        !hasAccess -> "별도 사용정보 접근 동의 필요"
                        isLoading -> "설치 앱 용량을 계산하는 중"
                        summary != null -> {
                            "${formatCount(summary.apps.size)}개 앱 · ${formatElapsed(summary.elapsedMillis)}"
                        }
                        else -> "권한 허용됨"
                    },
                )
                Spacer(Modifier.weight(1f))
                if (hasAccess) {
                    TextButton(onClick = onRefresh, enabled = !isLoading) { Text("새로고침") }
                }
            }

            Text(
                "상단 기기 사용량에는 공유 파일뿐 아니라 앱 코드·비공개 데이터·캐시와 Android 시스템 영역도 포함됩니다.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )

            deviceStorage?.let { storage ->
                CoverageRow(
                    label = "기기 전체 사용",
                    bytes = storage.usedBytes,
                    denominator = storage.usedBytes,
                    color = Cream,
                )
                CoverageRow(
                    label = "직접 읽은 파일",
                    bytes = scannedBytes,
                    denominator = storage.usedBytes,
                    color = Leaf,
                    pendingLabel = "폴더 분석 전",
                )
                CoverageRow(
                    label = "앱별 Android 통계",
                    bytes = summary?.totalBytes,
                    denominator = storage.usedBytes,
                    color = appPaletteColor(0),
                    pendingLabel = when {
                        !hasAccess -> "권한 필요"
                        isLoading -> "계산 중"
                        else -> "측정 전"
                    },
                )
                Text(
                    "두 분석에는 겹치는 항목이 있을 수 있어 합산하지 않습니다.",
                    color = Warning,
                    fontSize = 11.sp,
                )
            }

            if (!hasAccess) {
                Button(
                    onClick = onRequestAccess,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Leaf,
                        contentColor = Color(0xFF101311),
                    ),
                ) {
                    Text("앱 용량 분석 동의 및 설정", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onRequestAccess) { Text("사용정보 권한 설정 열기") }
            }

            errorMessage?.let { message ->
                Text(message, color = Warning, fontSize = 12.sp)
                if (hasAccess && !isLoading) {
                    OutlinedButton(onClick = onRefresh) { Text("다시 시도") }
                }
            }

            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Leaf,
                        trackColor = Outline,
                        strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.width(11.dp))
                    Text("앱 수에 따라 몇 초 걸릴 수 있습니다.", color = Muted, fontSize = 12.sp)
                }
            }

            summary?.takeIf { it.apps.isNotEmpty() }?.let { appSummary ->
                AppStorageVisualization(
                    summary = appSummary,
                    selectedApp = selectedApp,
                    onSelect = { selectedPackage = it.packageName },
                )
            }

            if (summary != null && summary.apps.isEmpty() && !isLoading) {
                Text("표시할 앱 저장공간 통계가 없습니다.", color = Muted, fontSize = 13.sp)
            }

            summary?.takeIf { it.inaccessibleAppCount > 0 }?.let { appSummary ->
                Text(
                    "측정할 수 없는 앱 ${formatCount(appSummary.inaccessibleAppCount)}개는 제외했습니다.",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Android가 보고한 전체 사용량입니다. 아래의 직접 읽은 파일 용량과는 집계 범위가 다릅니다.",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun CoverageRow(
    label: String,
    bytes: Long?,
    denominator: Long,
    color: Color,
    pendingLabel: String = "측정 전",
) {
    val fraction = if (bytes != null && denominator > 0L) {
        (bytes.toDouble() / denominator.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Cream, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                text = bytes?.let {
                    "${ByteFormatter.format(it)} · ${(fraction * 100).toInt()}%"
                } ?: pendingLabel,
                color = if (bytes == null) Muted else color,
                fontSize = 11.sp,
                fontWeight = if (bytes == null) FontWeight.Normal else FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape),
            color = color,
            trackColor = Outline,
        )
    }
}

@Composable
private fun AppStorageVisualization(
    summary: AppStorageSummary,
    selectedApp: AppStorageUsage?,
    onSelect: (AppStorageUsage) -> Unit,
) {
    val entries = remember(summary) { summary.toAppTreemapEntries() }
    val topApps = remember(summary) { summary.apps.take(APP_LIST_LIMIT) }
    val largestBytes = topApps.firstOrNull()?.totalBytes ?: 0L

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "앱 용량 지도 · ${ByteFormatter.format(summary.totalBytes)}",
            color = Cream,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        StorageTreemap(
            entries = entries,
            selectedId = selectedApp?.packageName,
            onSelect = { entry ->
                summary.apps.firstOrNull { it.packageName == entry.id }?.let(onSelect)
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0B0D0C)),
        )
        Text("사각형의 넓이가 앱의 코드·데이터·캐시 합계에 비례합니다.", color = Muted, fontSize = 11.sp)

        selectedApp?.let { app ->
            AppStorageDetail(
                app = app,
                color = appPaletteColor(summary.apps.indexOf(app)),
            )
        }

        Text("용량이 큰 앱", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        topApps.forEachIndexed { index, app ->
            val fraction = if (largestBytes > 0L) {
                (app.totalBytes.toDouble() / largestBytes.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(app) }
                    .padding(vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(appPaletteColor(index)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        app.label,
                        color = Cream,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(ByteFormatter.format(app.totalBytes), color = Muted, fontSize = 11.sp)
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = appPaletteColor(index),
                    trackColor = Outline,
                )
            }
        }
    }
}

@Composable
private fun AppStorageDetail(app: AppStorageUsage, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SoftInk)
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    color = Cream,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.packageName,
                    color = Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(ByteFormatter.format(app.totalBytes), color = Cream, fontWeight = FontWeight.Bold)
                Text(if (app.isSystemApp) "시스템 앱" else "설치 앱", color = Muted, fontSize = 10.sp)
            }
        }
        AppStorageBreakdownRow("앱 코드", app.codeBytes, app.totalBytes, appPaletteColor(0))
        AppStorageBreakdownRow("데이터", app.dataBytes, app.totalBytes, Leaf)
        AppStorageBreakdownRow("캐시", app.cacheBytes, app.totalBytes, Warning)
    }
}

@Composable
private fun AppStorageBreakdownRow(
    label: String,
    bytes: Long,
    totalBytes: Long,
    color: Color,
) {
    val fraction = if (totalBytes > 0L) {
        (bytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(ByteFormatter.format(bytes), color = Muted, fontSize = 11.sp)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = color,
            trackColor = Outline,
        )
    }
}

@Composable
private fun UsageAccessDisclosureDialog(
    onDismiss: () -> Unit,
    onAgree: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("앱별 용량 분석 동의") },
        text = {
            Text(
                "DiskPie는 설치된 앱의 이름과 패키지 식별자, 앱 코드·데이터·캐시의 용량 통계를 기기 안에서 읽습니다. 파일 내용과 앱 사용기록은 읽지 않으며 분석 정보는 외부로 전송하지 않습니다. 동의 후 Android 시스템 설정에서 DiskPie의 ‘사용정보 접근 허용’을 켜 주세요.",
            )
        },
        confirmButton = {
            Button(onClick = onAgree) { Text("동의하고 설정 열기") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun EmptyState(onChooseFolder: () -> Unit) {
    DashboardCard(containerColor = SoftInk) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("어디가 공간을 차지할까요?", color = Cream, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                "폴더를 목록에 여러 개 추가하면 선택한 모든 위치를 한 번에 분석합니다. 추가한 폴더는 개별 또는 전체 선택·해제할 수 있습니다.",
                color = Muted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            Button(
                onClick = onChooseFolder,
                colors = ButtonDefaults.buttonColors(containerColor = Leaf, contentColor = Color(0xFF101311)),
            ) {
                Text("첫 폴더 선택", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FolderSelectionCard(
    folders: List<SelectedFolder>,
    onChooseFolder: () -> Unit,
    onToggleFolder: (String, Boolean) -> Unit,
    onSelectAllFolders: () -> Unit,
    onDeselectAllFolders: () -> Unit,
    onRemoveFolder: (String) -> Unit,
) {
    val selectedCount = folders.count(SelectedFolder::isSelected)
    val sharedStorageRoots = folders.filter(SelectedFolder::isSharedStorageRoot)
    val allTargetFoldersSelected = if (sharedStorageRoots.isNotEmpty()) {
        sharedStorageRoots.all(SelectedFolder::isSelected) &&
            folders.filterNot(SelectedFolder::isSharedStorageRoot).none(SelectedFolder::isSelected)
    } else {
        folders.all(SelectedFolder::isSelected)
    }
    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("분석 폴더", "${selectedCount}/${folders.size}개 선택")
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onChooseFolder) { Text("폴더 추가") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onSelectAllFolders,
                    enabled = !allTargetFoldersSelected,
                ) {
                    Text(if (sharedStorageRoots.isNotEmpty()) "전체 저장공간 선택" else "전체 폴더 선택")
                }
                TextButton(
                    onClick = onDeselectAllFolders,
                    enabled = selectedCount > 0,
                ) { Text("전체 폴더 해제") }
            }
            folders.forEach { folder ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = folder.isSelected,
                        onCheckedChange = { checked -> onToggleFolder(folder.uri, checked) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            color = Cream,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when {
                                folder.isSharedStorageRoot -> "전체 접근 · 읽기·삭제 가능"
                                folder.canWrite -> "읽기·삭제 가능"
                                else -> "읽기 전용"
                            },
                            color = if (folder.canWrite) Muted else Warning,
                            fontSize = 11.sp,
                        )
                    }
                    if (folder.isSharedStorageRoot) {
                        Text("시스템", color = Muted, fontSize = 11.sp)
                    } else {
                        TextButton(onClick = { onRemoveFolder(folder.uri) }) { Text("제거") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoFolderSelectedCard() {
    DashboardCard(containerColor = SoftInk) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("분석할 폴더가 선택되지 않았습니다.", color = Cream, fontWeight = FontWeight.Bold)
            Text(
                "위 목록에서 폴더를 체크하거나 ‘전체 폴더 선택’을 눌러 주세요.",
                color = Muted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ScanningCard(progress: ScanProgress, onCancel: () -> Unit) {
    DashboardCard(containerColor = Color(0xFF1D2A1C)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(42.dp),
                color = Leaf,
                trackColor = Outline,
                strokeWidth = 4.dp,
            )
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text("저장공간 분석 중", color = Cream, fontWeight = FontWeight.Bold)
                Text(
                    "파일 ${formatCount(progress.fileCount)}개  ·  ${ByteFormatter.format(progress.bytesSeen)} 확인",
                    color = Muted,
                    fontSize = 12.sp,
                )
                if (progress.currentName.isNotBlank()) {
                    Text(
                        progress.currentName,
                        color = Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onCancel) { Text("중지") }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2421)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("!", color = Warning, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(12.dp))
            Text(message, color = Cream, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    }
}

@Composable
private fun ActionMessageCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D3420)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✓", color = Leaf, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(12.dp))
            Text(message, color = Cream, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("확인") }
        }
    }
}

@Composable
private fun DeletingCard() {
    DashboardCard(containerColor = Color(0xFF352321)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.error,
                trackColor = Outline,
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("파일 삭제 중", color = Cream, fontWeight = FontWeight.Bold)
                Text("저장소 공급자의 응답을 기다리고 있습니다.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SelectionSummaryCard(
    result: ScanResult,
    isScanning: Boolean,
    onRescan: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        result.root.name,
                        color = Cream,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("선택 영역", color = Muted, fontSize = 12.sp)
                }
                Text(
                    ByteFormatter.format(result.totalBytes),
                    color = Leaf,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Metric("파일", "${formatCount(result.fileCount)}개")
                Metric("폴더", "${formatCount(result.directoryCount)}개")
                Metric("분석 시간", formatElapsed(result.elapsedMillis))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChooseFolder) { Text("폴더 추가") }
                Button(onClick = onRescan, enabled = !isScanning) { Text("다시 분석") }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, color = Cream, fontWeight = FontWeight.SemiBold)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun TreemapSection(
    result: ScanResult,
    deletionEnabled: Boolean,
    onRequestDelete: (StorageNode) -> Unit,
) {
    var mapRoot by remember(result) { mutableStateOf(result.root) }
    var selectedNode by remember(result) { mutableStateOf<StorageNode?>(null) }
    val entries = remember(mapRoot) { mapRoot.toTreemapEntries() }

    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("용량 지도", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        mapRoot.name,
                        color = Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (mapRoot.uri != result.root.uri) {
                    TextButton(
                        onClick = {
                            mapRoot = result.root
                            selectedNode = null
                        },
                    ) { Text("처음으로") }
                }
            }

            StorageTreemap(
                entries = entries,
                selectedId = selectedNode?.uri,
                onSelect = { entry ->
                    selectedNode = mapRoot.children.firstOrNull { it.uri == entry.id }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0B0D0C)),
            )

            selectedNode?.let { node ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftInk)
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(categoryColor(node.category)),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            node.name,
                            color = Cream,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${node.category.label}  ·  ${ByteFormatter.format(node.sizeBytes)}",
                            color = Muted,
                            fontSize = 11.sp,
                        )
                    }
                    if (node.isDirectory && node.children.any { it.sizeBytes > 0L }) {
                        TextButton(
                            onClick = {
                                mapRoot = node
                                selectedNode = null
                            },
                        ) { Text("확대") }
                    } else if (!node.isDirectory) {
                        TextButton(
                            onClick = { onRequestDelete(node) },
                            enabled = deletionEnabled,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text("삭제") }
                    }
                }
            }
            Text("사각형의 넓이가 실제 용량에 비례합니다. 폴더를 선택한 뒤 확대할 수 있습니다.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CategorySection(categories: List<CategoryUsage>, totalBytes: Long) {
    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("파일 유형", "선택 영역의 구성")
            if (categories.isEmpty()) {
                Text("분류할 파일이 없습니다.", color = Muted, fontSize = 13.sp)
            }
            categories.forEach { usage ->
                val fraction = if (totalBytes > 0L) {
                    (usage.sizeBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(categoryColor(usage.category)),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(usage.category.label, color = Cream, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            "${ByteFormatter.format(usage.sizeBytes)} · ${(fraction * 100).toInt()}%",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape),
                        color = categoryColor(usage.category),
                        trackColor = Outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileSearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    canDeleteFiles: Boolean,
    onChooseFolder: () -> Unit,
) {
    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(
                "파일 찾기·삭제",
                if (query.isBlank()) "공간을 많이 차지하는 파일 20개" else "검색 결과 최대 ${FILE_SEARCH_LIMIT}개",
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("파일 이름 검색") },
                placeholder = { Text("예: video, apk, 보고서") },
                singleLine = true,
            )
            if (!canDeleteFiles) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "삭제하려면 선택한 모든 폴더에 쓰기 권한이 필요합니다. 읽기 전용 폴더를 다시 선택해 주세요.",
                        color = Warning,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onChooseFolder) { Text("다시 선택") }
                }
            } else {
                Text(
                    "삭제 버튼을 누른 뒤 파일명과 용량을 다시 확인해야 삭제됩니다.",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun FileRow(
    file: StorageNode,
    deletionEnabled: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RaisedInk),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 10.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(categoryColor(file.category).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = categoryGlyph(file.category),
                        color = categoryColor(file.category),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        color = Cream,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        file.relativePath,
                        color = Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(file.category.label, color = Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    ByteFormatter.format(file.sizeBytes),
                    color = Cream,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpen) { Text("열기") }
                TextButton(
                    onClick = onDelete,
                    enabled = deletionEnabled,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("삭제") }
            }
        }
    }
}

@Composable
private fun DeleteFileDialog(
    file: StorageNode,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("파일을 삭제할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    file.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${file.category.label} · ${ByteFormatter.format(file.sizeBytes)}")
                Text(
                    file.relativePath,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "저장소 공급자에 따라 휴지통을 거치지 않고 즉시 삭제될 수 있으며, 복구하지 못할 수 있습니다.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("삭제") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun DashboardCard(
    containerColor: Color = RaisedInk,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content,
        )
    }
}

private fun StorageNode.toTreemapEntries(): List<TreemapEntry> {
    val nonEmpty = children.filter { it.sizeBytes > 0L }.sortedByDescending(StorageNode::sizeBytes)
    val visible = nonEmpty.take(MAX_TREEMAP_TILES)
    val entries = visible.map { node ->
        TreemapEntry(
            id = node.uri,
            label = node.name,
            sizeBytes = node.sizeBytes,
            category = node.category,
            isDirectory = node.isDirectory,
        )
    }.toMutableList()

    if (nonEmpty.size > visible.size) {
        entries += TreemapEntry(
            id = "__other__:$uri",
            label = "기타 ${nonEmpty.size - visible.size}개",
            sizeBytes = nonEmpty.drop(visible.size).fold(0L) { sum, node -> safeAdd(sum, node.sizeBytes) },
            category = FileCategory.OTHER,
            isDirectory = false,
        )
    }
    return entries
}

private fun AppStorageSummary.toAppTreemapEntries(): List<TreemapEntry> {
    val visibleApps = apps.take(MAX_TREEMAP_TILES)
    val entries = visibleApps.mapIndexed { index, app ->
        TreemapEntry(
            id = app.packageName,
            label = app.label,
            sizeBytes = app.totalBytes,
            category = FileCategory.APP,
            isDirectory = false,
            paletteIndex = index,
        )
    }.toMutableList()

    if (apps.size > visibleApps.size) {
        entries += TreemapEntry(
            id = "__other_apps__",
            label = "기타 ${apps.size - visibleApps.size}개",
            sizeBytes = apps.drop(visibleApps.size).fold(0L) { total, app ->
                safeAdd(total, app.totalBytes)
            },
            category = FileCategory.OTHER,
            isDirectory = false,
        )
    }
    return entries
}

private fun safeAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

private fun categoryGlyph(category: FileCategory): String = when (category) {
    FileCategory.IMAGE -> "IMG"
    FileCategory.VIDEO -> "VID"
    FileCategory.AUDIO -> "AUD"
    FileCategory.DOCUMENT -> "DOC"
    FileCategory.ARCHIVE -> "ZIP"
    FileCategory.APP -> "APK"
    FileCategory.OTHER -> "•••"
}

private fun formatCount(value: Int): String = String.format(Locale.KOREA, "%,d", value)

private fun formatElapsed(milliseconds: Long): String = when {
    milliseconds < 1_000L -> "${milliseconds}ms"
    milliseconds < 60_000L -> String.format(Locale.KOREA, "%.1f초", milliseconds / 1_000.0)
    else -> "${milliseconds / 60_000}분 ${(milliseconds % 60_000) / 1_000}초"
}

private const val MAX_TREEMAP_TILES = 35
private const val APP_LIST_LIMIT = 10
private const val FILE_SEARCH_LIMIT = 100
