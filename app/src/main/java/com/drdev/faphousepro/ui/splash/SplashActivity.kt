package com.drdev.faphousepro.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdev.faphousepro.BuildConfig
import com.drdev.faphousepro.R
import com.drdev.faphousepro.ui.license.LicenseActivity
import com.drdev.faphousepro.ui.main.MainActivity
import com.drdev.faphousepro.ui.theme.FapHouseProTheme
import com.drdev.faphousepro.ui.theme.FhBg
import com.drdev.faphousepro.ui.theme.FhGold
import com.drdev.faphousepro.ui.theme.FhMuted
import com.drdev.faphousepro.ui.theme.FhText

class SplashActivity : ComponentActivity() {

    private val vm: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { false }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FapHouseProTheme {
                val dest by vm.dest.collectAsStateWithLifecycle()
                LaunchedEffect(dest) {
                    when (dest) {
                        SplashDestination.ToMain -> {
                            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                            finish()
                        }
                        SplashDestination.ToLicense -> {
                            startActivity(Intent(this@SplashActivity, LicenseActivity::class.java))
                            finish()
                        }
                        SplashDestination.Loading -> Unit
                    }
                }
                SplashContent()
            }
        }
    }
}

@Composable
private fun SplashContent() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(if (visible) 1f else 0.85f, tween(600), label = "scale")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(600), label = "alpha")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FhBg, FhBg))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha).scale(scale).padding(horizontal = 32.dp)
        ) {
            Surface(
                color = FhGold,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("F", fontWeight = FontWeight.Black, fontSize = 56.sp, color = FhBg)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_brand),
                fontWeight = FontWeight.Black,
                fontSize = 32.sp,
                letterSpacing = (-0.5).sp,
                color = FhText
            )
            Text(
                text = stringResource(R.string.app_brand_pro),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 8.sp,
                color = FhGold
            )

            Spacer(Modifier.height(40.dp))
            CircularProgressIndicator(
                color = FhGold,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Starting secure session…",
                fontSize = 13.sp,
                color = FhMuted,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            fontSize = 11.sp,
            color = FhMuted,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        )
    }
}