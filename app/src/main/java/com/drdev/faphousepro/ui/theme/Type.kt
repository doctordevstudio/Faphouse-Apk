package com.drdev.faphousepro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

val FapTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = base.displayMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = base.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.bodySmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = base.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = base.labelMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)