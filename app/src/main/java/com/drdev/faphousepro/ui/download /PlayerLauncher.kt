package com.drdev.faphousepro.ui.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.drdev.faphousepro.ui.player.PlayerActivity
import java.io.File

object PlayerLauncher {
    fun launch(ctx: Context, filePath: String, title: String, subtitlesJson: String) {
        val intent = Intent(ctx, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_PATH, filePath)
            putExtra(PlayerActivity.EXTRA_TITLE, title)
            putExtra(PlayerActivity.EXTRA_SUBTITLES_JSON, subtitlesJson)
        }
        ctx.startActivity(intent)
    }
}

object ShareLauncher {
    fun share(ctx: Context, filePath: String, mime: String, title: String) {
        try {
            val file = File(filePath)
            val uri: Uri = FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.fileprovider", file
            )
            val i = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, "Share video"))
        } catch (_: Throwable) { }
    }
}