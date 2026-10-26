package com.drdev.faphousepro.ui.license

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdev.faphousepro.AppGraph
import com.drdev.faphousepro.BuildConfig
import com.drdev.faphousepro.R
import com.drdev.faphousepro.ui.components.AboutDevDialog
import com.drdev.faphousepro.ui.components.BlockedDialog
import com.drdev.faphousepro.ui.components.UpdateDialog
import com.drdev.faphousepro.ui.main.MainActivity
import com.drdev.faphousepro.ui.theme.FapHouseProTheme
import com.drdev.faphousepro.ui.theme.FhBg
import com.drdev.faphousepro.ui.theme.FhBorder
import com.drdev.faphousepro.ui.theme.FhCard
import com.drdev.faphousepro.ui.theme.FhDanger
import com.drdev.faphousepro.ui.theme.FhGold
import com.drdev.faphousepro.ui.theme.FhMuted
import com.drdev.faphousepro.ui.theme.FhText
import com.drdev.faphousepro.ui.theme.FhWarning

class LicenseActivity : ComponentActivity() {

    private val vm: LicenseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FapHouseProTheme {
                LicenseScreen(
                    vm = vm,
                    onActivated = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    telegramUrl = BuildConfig.TELEGRAM_URL,
                    botUrl = BuildConfig.BOT_URL
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseScreen(
    vm: LicenseViewModel,
    onActivated: () -> Unit,
    telegramUrl: String,
    botUrl: String
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val runtime by AppGraph.appState.state.collectAsStateWithLifecycle()

    var showAbout by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }

    LaunchedEffect(runtime.license) {
        (runtime.license as? com.drdev.faphousepro.data.LicenseState.Blocked)?.let {
            showBlocked = true
        }
    }
    LaunchedEffect(ui.blockedUntilSec) {
        if (ui.blockedUntilSec != null) showBlocked = true
    }
    LaunchedEffect(ui.success) { if (ui.success) onActivated() }

    fun openUrl(url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Throwable) { }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(FhBg, Color(0xFF0E1016))))) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = FhGold,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("F", fontWeight = FontWeight.Black, fontSize = 40.sp, color = FhBg)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FAPHOUSE", fontWeight = FontWeight.Black, fontSize = 24.sp, color = FhText)
                Spacer(Modifier.width(8.dp))
                Surface(color = FhGold, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "PRO",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        color = FhBg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(40.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = FhCard),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text("Activate License", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = FhText)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Enter your license key to unlock unlimited premium access.",
                        fontSize = 13.sp,
                        color = FhMuted
                    )
                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = ui.key,
                        onValueChange = vm::onKeyChange,
                        singleLine = true,
                        enabled = !ui.loading && ui.blockedUntilSec == null,
                        label = { Text("License key") },
                        placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = FhMuted) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val pasted = clipboard.getText()?.text ?: return@IconButton
                                vm.onKeyChange(pasted)
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = FhGold)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { vm.activate() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = FhBg,
                            unfocusedContainerColor = FhBg,
                            disabledContainerColor = FhBg,
                            focusedBorderColor = FhGold,
                            unfocusedBorderColor = FhBorder,
                            focusedTextColor = FhText,
                            unfocusedTextColor = FhText,
                            disabledTextColor = FhMuted,
                            focusedLabelColor = FhGold,
                            unfocusedLabelColor = FhMuted,
                            cursorColor = FhGold
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (ui.errorMessage != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            ui.errorMessage!!,
                            color = if (ui.expired) FhWarning else FhDanger,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = { vm.activate() },
                        enabled = !ui.loading && ui.blockedUntilSec == null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FhGold,
                            contentColor = FhBg,
                            disabledContainerColor = FhBorder,
                            disabledContentColor = FhMuted
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (ui.loading) {
                            CircularProgressIndicator(
                                color = FhBg, strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Verifying…", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Activate", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Don't have a license key?", color = FhMuted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { openUrl(telegramUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = FhGold, contentColor = FhBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Purchase from Telegram", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = { openUrl(botUrl) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FhText),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FhBorder)
                    ) {
                        Text("Bot", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { showAbout = true }) {
                Text("About Developer", color = FhMuted, fontSize = 12.sp)
            }
            Text(
                "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = FhMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    if (showBlocked && (ui.blockedUntilSec ?: (runtime.license as? com.drdev.faphousepro.data.LicenseState.Blocked)?.untilSec) != null) {
        val until = ui.blockedUntilSec
            ?: (runtime.license as? com.drdev.faphousepro.data.LicenseState.Blocked)?.untilSec
            ?: 0L
        BlockedDialog(
            untilSec = until,
            message = ui.errorMessage ?: (runtime.license as? com.drdev.faphousepro.data.LicenseState.Blocked)?.reason,
            onDismiss = { showBlocked = false },
            onTelegram = { openUrl(telegramUrl) },
            onBot = { openUrl(botUrl) }
        )
    }

    runtime.update?.let { cfg ->
        if (cfg.enabled) {
            UpdateDialog(
                cfg = cfg,
                onUpdate = { openUrl(cfg.updateUrl) },
                onLater = { AppGraph.appState.setUpdate(null) }
            )
        }
    }

    if (showAbout) {
        AboutDevDialog(
            onDismiss = { showAbout = false },
            onTelegram = { openUrl(telegramUrl) }
        )
    }
}