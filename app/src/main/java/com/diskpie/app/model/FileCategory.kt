package com.diskpie.app.model

enum class FileCategory(val label: String) {
    IMAGE("이미지"),
    VIDEO("동영상"),
    AUDIO("오디오"),
    DOCUMENT("문서"),
    ARCHIVE("압축 파일"),
    APP("앱·설치 파일"),
    OTHER("기타"),
}

object FileClassifier {
    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "raw", "dng",
    )
    private val videoExtensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "ts", "wmv", "flv",
    )
    private val audioExtensions = setOf(
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "mid", "midi",
    )
    private val documentExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf",
        "epub", "hwp", "hwpx", "odt", "ods", "odp", "json", "xml",
    )
    private val archiveExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "zst",
    )
    private val appExtensions = setOf(
        "apk", "apks", "aab", "xapk", "dex", "jar",
    )

    fun classify(name: String, mimeType: String?): FileCategory {
        val normalizedMimeType = mimeType?.lowercase()
        when {
            normalizedMimeType?.startsWith("image/") == true -> return FileCategory.IMAGE
            normalizedMimeType?.startsWith("video/") == true -> return FileCategory.VIDEO
            normalizedMimeType?.startsWith("audio/") == true -> return FileCategory.AUDIO
            normalizedMimeType in archiveMimeTypes -> return FileCategory.ARCHIVE
            normalizedMimeType in appMimeTypes -> return FileCategory.APP
            normalizedMimeType in documentMimeTypes || normalizedMimeType?.startsWith("text/") == true -> {
                return FileCategory.DOCUMENT
            }
        }

        return when (name.substringAfterLast('.', "").lowercase()) {
            in imageExtensions -> FileCategory.IMAGE
            in videoExtensions -> FileCategory.VIDEO
            in audioExtensions -> FileCategory.AUDIO
            in documentExtensions -> FileCategory.DOCUMENT
            in archiveExtensions -> FileCategory.ARCHIVE
            in appExtensions -> FileCategory.APP
            else -> FileCategory.OTHER
        }
    }

    private val archiveMimeTypes = setOf(
        "application/zip",
        "application/gzip",
        "application/x-7z-compressed",
        "application/x-rar-compressed",
        "application/vnd.rar",
        "application/x-tar",
    )
    private val appMimeTypes = setOf(
        "application/vnd.android.package-archive",
        "application/java-archive",
    )
    private val documentMimeTypes = setOf(
        "application/pdf",
        "application/json",
        "application/xml",
        "application/msword",
        "application/vnd.ms-excel",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )
}
