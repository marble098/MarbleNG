package com.marbleng.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun colors(theme:String)=when(theme.lowercase()){
    "ocean"->darkColorScheme(primary=Color(0xFF23B7FF),secondary=Color(0xFF00F5D4),tertiary=Color(0xFF6C8CFF),background=Color(0xFF07141E),surface=Color(0xFF102432),surfaceVariant=Color(0xFF193546))
    "sunset"->darkColorScheme(primary=Color(0xFFFF6B8A),secondary=Color(0xFFFFC857),tertiary=Color(0xFFC77DFF),background=Color(0xFF1B1018),surface=Color(0xFF2B1825),surfaceVariant=Color(0xFF3A2031))
    "matrix"->darkColorScheme(primary=Color(0xFF55FF88),secondary=Color(0xFF00D66B),tertiary=Color(0xFFA4FFB9),background=Color(0xFF07110A),surface=Color(0xFF0D1C12),surfaceVariant=Color(0xFF163020))
    "mono"->darkColorScheme(primary=Color(0xFFE7E7EF),secondary=Color(0xFFB9BBC7),tertiary=Color(0xFFFFFFFF),background=Color(0xFF0D0E12),surface=Color(0xFF18191F),surfaceVariant=Color(0xFF262832))
    else->darkColorScheme(primary=Color(0xFF8B7CFF),secondary=Color(0xFF00D8FF),tertiary=Color(0xFFFF4FC3),background=Color(0xFF0E101B),surface=Color(0xFF171A29),surfaceVariant=Color(0xFF22263A))
}
@Composable fun MarbleTheme(theme:String="aurora",content: @Composable () -> Unit)=MaterialTheme(colorScheme=colors(theme),typography=Typography(),content=content)
