package com.drdev.faphousepro.ui.download

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.drdev.faphousepro.AppGraph
import com.drdev.faphousepro.data.download.DownloadEntity
import com.drdev.faphousepro.data.download.DownloadState
import com.drdev.faphousepro.data.download.DownloadWorker
import com.drdev.faphousepro.network.QualityDto
import com.drdev.faphousepro.network.SubtitleDto
import com.drdev.faphousepro.network.VideoInfoDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

data class WatchUiState(
    val url: String = "",
    val loading: Boolean = false,
    val videoInfo: VideoInfoDto? = null,
    val error: String? = null
)

data class ActiveDownload(
    val entity: DownloadEntity,
    val progressPct: Int = 0,
    val bytesDownloaded: Long = 0L,
    val bytesTotal: Long = 0L,
    val state: DownloadState = DownloadState.QUEUED,
    val error: String? = null,
    val workId: UUID? = null
)

class WatchDownloadViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "FH-WDVM"
        private const val TAG_WORK = "fh_downloads"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val subsSer = ListSerializer(SubtitleDto.serializer())

    private val _ui = MutableStateFlow(WatchUiState())
    val ui: StateFlow<WatchUiState> = _ui.asStateFlow()

    private val _active = MutableStateFlow<List<ActiveDownload>>(emptyList())
    val active: StateFlow<List<ActiveDownload>> = _active.asStateFlow()

    private val _persistedList = MutableStateFlow<List<DownloadEntity>>(emptyList())
    val persistedList: StateFlow<List<DownloadEntity>> = _persistedList.asStateFlow()

    private val wm: WorkManager = WorkManager.getInstance(app)

    init {
        viewModelScope.launch(Dispatchers.IO) { AppGraph.downloads.load() }
        viewModelScope.launch {
            AppGraph.downloads.persisted.collectLatest { _persistedList.value = it }
        }
        viewModelScope.launch {
            wm.getWorkInfosByTagFlow(TAG_WORK).collectLatest { infos ->
                _active.value = infos.mapNotNull { it.toActive() }
            }
        }
    }

    fun onUrlChange(v: String) {
        _ui.update { it.copy(url = v.trim(), error = null) }
    }

    fun fetch() {
        val url = _ui.value.url
        if (url.isBlank()) {
            _ui.update { it.copy(error = "Paste a video URL first.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, videoInfo = null) }
            try {
                val key = withContext(Dispatchers.IO) { AppGraph.storage.getLicenseKey() }
                if (key.isNullOrBlank()) {
                    _ui.update { it.copy(loading = false, error = "No license key.") }
                    return@launch
                }
                val resp = withContext(Dispatchers.IO) { AppGraph.apiClient.fetchVideo(key, url) }
                if (resp.success && resp.video != null) {
                    _ui.update { it.copy(loading = false, videoInfo = resp.video) }
                } else {
                    _ui.update {
                        it.copy(loading = false, error = resp.message ?: "Fetch failed (${resp.code})")
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _ui.update { it.copy(loading = false, error = t.message ?: "Network error") }
            }
        }
    }

    fun startDownload(quality: QualityDto) {
        val info = _ui.value.videoInfo ?: return
        viewModelScope.launch {
            val key = withContext(Dispatchers.IO) { AppGraph.storage.getLicenseKey() }
            if (key.isNullOrBlank()) {
                _ui.update { it.copy(error = "No license key.") }
                return@launch
            }
            val videoId = info.videoUrl.substringBeforeLast('/').substringAfterLast('/')
                .substringBefore('?').take(32)
            val id = UUID.randomUUID().toString()
            val subsJson = json.encodeToString(subsSer, info.subtitles)

            val entity = DownloadEntity(
                id = id,
                videoId = videoId,
                title = info.title,
                studio = info.studio,
                thumbnail = info.thumbnail,
                durationFormatted = info.durationFormatted,
                qualityLabel = quality.quality,
                qualityUrl = quality.url,
                isHls = quality.type == "m3u8",
                state = DownloadState.QUEUED,
                subtitlesJson = subsJson
            )

            val entityJson = json.encodeToString(DownloadEntity.serializer(), entity)

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .addTag(TAG_WORK)
                .addTag("download_$id")
                .setInputData(
                    workDataOf(
                        DownloadWorker.KEY_DOWNLOAD_ID to id,
                        DownloadWorker.KEY_TITLE to info.title,
                        "entity" to entityJson
                    )
                )
                .build()

            withContext(Dispatchers.IO) { AppGraph.downloads.upsert(entity) }
            wm.enqueueUniqueWork("download_$id", ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun cancelDownload(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                wm.getWorkInfosByTag("download_$id").firstOrNull()?.let { wm.cancelWorkById(it.id) }
            } catch (t: Throwable) {
                Log.w(TAG, "cancel failed: ${t.message}")
            }
        }
    }

    fun deleteDownload(id: String) {
        viewModelScope.launch(Dispatchers.IO) { AppGraph.downloads.remove(id) }
    }

    fun clearError() = _ui.update { it.copy(error = null) }

    private fun WorkInfo.toActive(): ActiveDownload? {
        val id = tags.firstOrNull { it.startsWith("download_") }?.removePrefix("download_")
            ?: return null
        val entity = _persistedList.value.firstOrNull { it.id == id } ?: return null
        val pct = progress.getInt(DownloadWorker.PROGRESS_PCT, 0)
        val bytes = progress.getLong(DownloadWorker.PROGRESS_BYTES, 0L)
        val total = progress.getLong(DownloadWorker.PROGRESS_TOTAL, entity.bytesTotal)
        val stateStr = progress.getString(DownloadWorker.STATE)
        val err = progress.getString(DownloadWorker.ERROR)
        val state = when {
            this.state == WorkInfo.State.FAILED || this.state == WorkInfo.State.CANCELLED -> DownloadState.FAILED
            this.state == WorkInfo.State.SUCCEEDED -> DownloadState.COMPLETED
            stateStr != null -> runCatching { DownloadState.valueOf(stateStr) }.getOrDefault(DownloadState.DOWNLOADING)
            else -> DownloadState.QUEUED
        }
        return ActiveDownload(
            entity = entity,
            progressPct = pct,
            bytesDownloaded = bytes,
            bytesTotal = total,
            state = state,
            error = err,
            workId = this.id
        )
    }
}