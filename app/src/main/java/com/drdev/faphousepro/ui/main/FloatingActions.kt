package com.drdev.faphousepro.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.drdev.faphousepro.ui.theme.FhBg
import com.drdev.faphousepro.ui.theme.FhCard
import com.drdev.faphousepro.ui.theme.FhGold
import com.drdev.faphousepro.ui.theme.FhText

@Composable
fun FloatingActions(
    modifier: Modifier = Modifier,
    onWatchAndDownloadClick: () -> Unit,
    onMyDownloadsClick: () -> Unit
) {
    Row(
        modifier = modifier.padding(end = 12.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleFab(Icons.Filled.Download, "Watch & Download", onWatchAndDownloadClick, true)
        CircleFab(Icons.Outlined.FileDownload, "My Downloads", onMyDownloadsClick, false)
    }
}

@Composable
private fun CircleFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean
) {
    val container = if (highlighted) FhGold else FhCard
    val content = if (highlighted) FhBg else FhText
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = container,
        contentColor = content,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
        modifier = Modifier.size(48.dp)
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = content, modifier = Modifier.size(24.dp))
    }
}