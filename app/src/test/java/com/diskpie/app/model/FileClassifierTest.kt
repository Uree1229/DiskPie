package com.diskpie.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FileClassifierTest {
    @Test
    fun `mime type takes precedence over extension`() {
        assertEquals(
            FileCategory.IMAGE,
            FileClassifier.classify("download.bin", "image/jpeg"),
        )
    }

    @Test
    fun `classifies common Korean document and package extensions`() {
        assertEquals(FileCategory.DOCUMENT, FileClassifier.classify("보고서.hwpx", null))
        assertEquals(FileCategory.APP, FileClassifier.classify("release.APK", null))
        assertEquals(FileCategory.ARCHIVE, FileClassifier.classify("backup.tar", null))
    }

    @Test
    fun `classifies archive and app mime types without extensions`() {
        assertEquals(FileCategory.ARCHIVE, FileClassifier.classify("download", "application/zip"))
        assertEquals(
            FileCategory.APP,
            FileClassifier.classify("download", "application/vnd.android.package-archive"),
        )
    }

    @Test
    fun `unknown file is categorized as other`() {
        assertEquals(FileCategory.OTHER, FileClassifier.classify("mystery.data", null))
    }
}
