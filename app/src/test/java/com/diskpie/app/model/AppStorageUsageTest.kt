package com.diskpie.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppStorageUsageTest {
    @Test
    fun `platform cache is split from data without double counting`() {
        val usage = AppStorageUsage.fromPlatformStats(
            packageName = "com.example.app",
            label = "Example",
            codeBytes = 100L,
            dataBytesIncludingCache = 900L,
            cacheBytes = 200L,
            isSystemApp = false,
        )

        assertEquals(100L, usage.codeBytes)
        assertEquals(700L, usage.dataBytes)
        assertEquals(200L, usage.cacheBytes)
        assertEquals(1_000L, usage.totalBytes)
    }

    @Test
    fun `cache is capped at the platform data total`() {
        val usage = AppStorageUsage.fromPlatformStats(
            packageName = "com.example.app",
            label = "Example",
            codeBytes = 10L,
            dataBytesIncludingCache = 100L,
            cacheBytes = 150L,
            isSystemApp = false,
        )

        assertEquals(0L, usage.dataBytes)
        assertEquals(100L, usage.cacheBytes)
        assertEquals(110L, usage.totalBytes)
    }

    @Test
    fun `total saturates instead of overflowing`() {
        val usage = AppStorageUsage.fromPlatformStats(
            packageName = "com.example.app",
            label = "Example",
            codeBytes = Long.MAX_VALUE,
            dataBytesIncludingCache = Long.MAX_VALUE,
            cacheBytes = 0L,
            isSystemApp = false,
        )

        assertEquals(Long.MAX_VALUE, usage.totalBytes)
    }
}
