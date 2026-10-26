package com.drdev.faphousepro.ui.player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.drdev.faphousepro.AppGraph
import com.drdev.faphousepro.network.SubtitleDto
import com.drdev.faphousepro.ui.theme.FapHouseProTheme
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLES_JSON = "extra_subtitles_json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val subsJson = intent.getStringExtra(EXTRA_SUBTITLES_JSON).orEmpty()
        val subtitles: List<SubtitleDto> = if (subsJson.isBlank()) emptyList() else try {
            Json { ignoreUnknownKeys = true }
                .decodeFromString(ListSerializer(SubtitleDto.serializer()), subsJson)
        } catch (_: Throwable) { emptyList() }

        if (path.isBlank() || !File(path).exists()) {
            finish()
            return
        }

        setContent {
            FapHouseProTheme {
                PlayerScreen(path = path, title = title, subtitles = subtitles)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@androidx.compose.runtime.Composable
private fun PlayerScreen(path: String, title: String, subtitles: List<SubtitleDto>) {
    val ctx = LocalContext.current
    val client = remember { AppGraph.apiClient.streamingClient() }

    val exoPlayer = remember {
        ExoPlayer.Builder(ctx).build().apply {
            playWhenReady = true

            val dataSource = OkHttpDataSource.Factory(client)
            val mediaSource: MediaSource = if (path.endsWith(".m3u8", ignoreCase = true)) {
                HlsMediaSource.Factory(dataSource)
                    .createMediaSource(MediaItem.fromUri(File(path).toURI().toString()))
            } else {
                ProgressiveMediaSource.Factory(dataSource)
                    .createMediaSource(MediaItem.fromUri(File(path).toURI().toString()))
            }
            setMediaSource(mediaSource)
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose { try { exoPlayer.release() } catch (_: Throwable) {} }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            }
        )
    }
}