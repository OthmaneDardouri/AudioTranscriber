package com.othmane.vocaleai.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Teal = Color(0xFF00A884)
private val TealDark = Color(0xFF00805F)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Teal, secondary = Teal)
        else -> lightColorScheme(primary = TealDark, secondary = Teal)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun languageName(code: String): String = when (code.lowercase()) {
    "it" -> "Italiano"
    "ar" -> "Arabo"
    "en" -> "Inglese"
    "fr" -> "Francese"
    "es" -> "Spagnolo"
    "de" -> "Tedesco"
    "nl" -> "Olandese"
    "pt" -> "Portoghese"
    "auto" -> "Automatica"
    "" -> "?"
    else -> code.uppercase()
}

fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
