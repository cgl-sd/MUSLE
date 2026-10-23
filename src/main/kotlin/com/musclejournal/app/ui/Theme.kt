package com.musclejournal.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Ink = Color(0xFF080808)
internal val Panel = Color(0xFF151515)
internal val Accent = Color(0xFFF05252)
internal val Muted = Color(0xFFB0B0B0)
internal val Outline = Color(0xFF383838)
private val AppColors = darkColorScheme(primary=Color(0xFFF5F5F5),onPrimary=Ink,secondary=Accent,secondaryContainer=Color(0xFF303030),onSecondaryContainer=Color.White,background=Ink,onBackground=Color(0xFFF5F5F5),surface=Panel,onSurface=Color(0xFFF5F5F5),onSurfaceVariant=Muted,outline=Outline,error=Color(0xFFFFB4AB))
@Composable fun MuscleJournalTheme(content:@Composable ()->Unit) { MaterialTheme(colorScheme=AppColors,typography=Typography(),content=content) }
