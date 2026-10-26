package com.drdev.faphousepro.data.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class DownloadState { QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

@Serializable
data class DownloadEntity(
    val id: String,
    @SerialName("video_id") val videoId: String,
    val title: String,
    val studio: String,
    val thumbnail: String,
    @SerialName("duration_formatted") val durationFormatted: String,
    val qualityLabel: String,
    val qualityUrl: String,
    val isHls: Boolean,
    @SerialName("file_path") val filePath: String = "",
    @SerialName("playback_path") val playbackPath: String = "",
    @SerialName("share_path") val sharePath: String = "",
    @SerialName("bytes_total") val bytesTotal: Long = 0L,
    @SerialName("bytes_downloaded") val bytesDownloaded: Long = 0L,
    val state: DownloadState = DownloadState.QUEUED,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("completed_at") val completedAt: Long? = null,
    @SerialName("subtitles_json") val subtitlesJson: String = "[]"
) {
    val progressFraction: Float
        get() {
            if (state == DownloadState.COMPLETED) return 1f
            if (bytesTotal <= 0L) return 0f
            return (bytesDownloaded.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f)
        }

    val displaySizeMb: String
        get() = if (bytesTotal > 0) "%.1f MB".format(bytesTotal / 1024.0 / 1024.0) else "—"
}