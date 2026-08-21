package com.diskpie.app.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.diskpie.app.model.AppStorageSummary
import com.diskpie.app.model.AppStorageUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AppStorageReader(private val context: Context) {
    @Suppress("DEPRECATION")
    fun hasUsageStatsAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun read(): AppStorageSummary = withContext(Dispatchers.IO) {
        if (!hasUsageStatsAccess()) {
            throw SecurityException("앱 사용정보 접근 권한이 필요합니다.")
        }

        val startedAt = System.currentTimeMillis()
        val packageManager = context.packageManager
        val storageStatsManager = context.getSystemService(StorageStatsManager::class.java)
        val applications = installedApplications(packageManager)
        val usages = ArrayList<AppStorageUsage>(applications.size)
        var inaccessibleAppCount = 0

        applications.forEach { application ->
            currentCoroutineContext().ensureActive()
            try {
                val stats = storageStatsManager.queryStatsForPackage(
                    application.storageUuid,
                    application.packageName,
                    Process.myUserHandle(),
                )
                val usage = AppStorageUsage.fromPlatformStats(
                    packageName = application.packageName,
                    label = packageManager.getApplicationLabel(application)
                        .toString()
                        .ifBlank { application.packageName },
                    codeBytes = stats.appBytes.coerceAtLeast(0L),
                    dataBytesIncludingCache = stats.dataBytes,
                    cacheBytes = stats.cacheBytes,
                    isSystemApp = application.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
                if (usage.totalBytes > 0L) usages += usage
            } catch (_: PackageManager.NameNotFoundException) {
                inaccessibleAppCount++
            } catch (error: SecurityException) {
                if (!hasUsageStatsAccess()) throw error
                inaccessibleAppCount++
            } catch (_: java.io.IOException) {
                inaccessibleAppCount++
            } catch (_: RuntimeException) {
                inaccessibleAppCount++
            }
        }

        val sortedUsages = usages.sortedWith(
            compareByDescending<AppStorageUsage>(AppStorageUsage::totalBytes)
                .thenBy(String.CASE_INSENSITIVE_ORDER, AppStorageUsage::label),
        )
        AppStorageSummary(
            apps = sortedUsages,
            totalBytes = sortedUsages.fold(0L) { total, usage ->
                safeAdd(total, usage.totalBytes)
            },
            inaccessibleAppCount = inaccessibleAppCount,
            elapsedMillis = System.currentTimeMillis() - startedAt,
        )
    }

    private fun installedApplications(packageManager: PackageManager): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(0)
        }

    private fun safeAdd(left: Long, right: Long): Long =
        if (right > 0L && Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
