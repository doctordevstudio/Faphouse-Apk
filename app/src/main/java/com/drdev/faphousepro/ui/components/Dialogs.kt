package com.drdev.faphousepro.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.drdev.faphousepro.BuildConfig
import com.drdev.faphousepro.network.UpdateConfigDto
import com.drdev.faphousepro.ui.theme.FhBorder
import com.drdev.faphousepro.ui.theme.FhCard
import com.drdev.faphousepro.ui.theme.FhGold
import com.drdev.faphousepro.ui.theme.FhMuted
import com.drdev.faphousepro.ui.theme.FhText

@Composable
fun BlockedDialog(
    untilSec: Long,
    message: String?,
    onDismiss: () -> Unit,
    onTelegram: () -> Unit,
    onBot: () -> Unit
) {
    val remaining = (untilSec - System.currentTimeMillis() / 1000L).coerceAtLeast(0L)
    val h = remaining / 3600
    val m = (remaining % 3600) / 60
    val s = remaining % 60
    val label = buildString {
        if (h > 0) append("${h}h ")
        if (h > 0 || m > 0) append("${m}m ")
        append("${s}s")
    }

    AlertDialog(
        onDismissRequest = { },
        containerColor = FhCard,
        titleContentColor = FhText,
        textContentColor = FhText,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Device Temporarily Blocked", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column {
                Text(
                    message ?: "Too many failed activation attempts. Please wait before trying again.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Unlocks in ", color = FhMuted, fontSize = 13.sp)
                    Text(label, color = FhGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Don't have a license key? Purchase from Telegram starting from ₹9/day.",
                    fontSize = 13.sp,
                    color = FhMuted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onTelegram) {
                Text("Purchase", color = FhGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onBot) { Text("Bot", color = FhMuted) }
        }
    )
}

@Composable
fun UpdateDialog(
    cfg: UpdateConfigDto,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!cfg.forceUpdate) onLater() },
        containerColor = FhCard,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Update Available", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = FhText) },
        text = {
            Column {
                Text("v${cfg.versionName} is available. Update now to continue.", fontSize = 14.sp, color = FhText)
                if (cfg.features.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(cfg.features, fontSize = 13.sp, color = FhMuted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text("Update Now", color = FhGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = if (!cfg.forceUpdate) {
            { TextButton(onClick = onLater) { Text("Later", color = FhMuted) } }
        } else null
    )
}

@Composable
fun AboutDevDialog(
    onDismiss: () -> Unit,
    onTelegram: () -> Unit
) {
    val ctx = LocalContext.current
    val devResId = remember(ctx) {
        ctx.resources.getIdentifier("dev", "drawable", ctx.packageName)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FhCard,
        shape = RoundedCornerShape(16.dp),
        title = { Text("About the Developer", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = FhText) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(88.dp).clip(CircleShape).background(FhBorder),
                    contentAlignment = Alignment.Center
                ) {
                    if (devResId != 0) {
                        val bmp = ContextCompat.getDrawable(ctx, devResId)?.let {
                            toBitmap(it).asImageBitmap()
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "Developer",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = FhMuted,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Dr. Hamza (Dr. Dev)", fontWeight = FontWeight.Bold, color = FhText, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Android systems engineer and reverse-engineering hobbyist. Built FapHouse Pro to be a clean, ad-free window into premium content — proper player, proper downloader, no telemetry.",
                    color = FhMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onTelegram) {
                    Text("@drdevstudio", color = FhGold, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    color = FhMuted,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = FhGold, fontWeight = FontWeight.Bold) }
        }
    )
}