package com.drdev.faphousepro.ui.download

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.drdev.faphousepro.ui.theme.FapHouseProTheme

class WatchDownloadActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_MODE = "extra_mode"
        const val MODE_NEW = "new"
        const val MODE_DOWNLOADS = "downloads"
    }

    private val vm: WatchDownloadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialUrl = intent.getStringExtra(EXTRA_VIDEO_URL).orEmpty()
        val initialMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_NEW

        if (initialUrl.isNotBlank()) {
            vm.onUrlChange(initialUrl)
            vm.fetch()
        }

        setContent {
            FapHouseProTheme {
                WatchDownloadScreen(
                    vm = vm,
                    initialMode = initialMode,
                    onClose = { finish() },
                    onOpenPlayer = { path, title, subsJson ->
                        PlayerLauncher.launch(this, path, title, subsJson)
                    },
                    onShare = { path, mime, title ->
                        ShareLauncher.share(this, path, mime, title)
                    }
                )
            }
        }
    }
}