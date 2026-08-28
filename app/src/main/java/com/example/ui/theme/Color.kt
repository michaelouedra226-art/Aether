package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Premium Aether Palette - Cosmic Dark (#0A0A0F) & OLED Pure Black
val CosmicBlack = Color(0xFF0A0A0F)
val SpotifyBlack = Color(0xFF0A0A0F)
val SpotifyPureBlack = Color(0xFF000000)
val SpotifyDarkSurface = Color(0xFF12121A)
val SpotifySurfaceVariant = Color(0xFF181824)
val SpotifySurfaceElevated = Color(0xFF202030)
val SpotifySurfaceHighlight = Color(0xFF2A2A3E)
val SpotifyBorder = Color(0xFF2F2F48)

// Signature Accents - Electric Violet & Cyber Cyan
val ElectricViolet = Color(0xFF8B5CF6)
val ElectricVioletLight = Color(0xFFA78BFA)
val ElectricVioletDark = Color(0xFF6D28D9)
val ElectricVioletGlow = Color(0x668B5CF6)

val CyberCyan = Color(0xFF22D3EE)
val CyberCyanLight = Color(0xFF67E8F9)
val CyberCyanDark = Color(0xFF0891B2)
val CyberCyanGlow = Color(0x6622D3EE)

// Standard Aliases (Redirected to Electric Violet & Cyan for high cohesion)
val SpotifyGreen = Color(0xFF8B5CF6)
val SpotifyGreenLight = Color(0xFFA78BFA)
val SpotifyGreenGlow = Color(0x668B5CF6)
val SpotifyGreenDark = Color(0xFF6D28D9)

// Vibrant Accent Palette
val NeonCyan = Color(0xFF22D3EE)
val NeonViolet = Color(0xFF8B5CF6)
val NeonPurpleGlow = Color(0x668B5CF6)
val NeonMagenta = Color(0xFFF43F5E)
val NeonEmerald = Color(0xFF10B981)
val NeonAmber = Color(0xFFF59E0B)

// Premium Gradient Brushes
val PremiumAccentGradient = Brush.horizontalGradient(
    listOf(ElectricViolet, CyberCyan)
)
val PremiumVerticalGradient = Brush.verticalGradient(
    listOf(ElectricViolet, CyberCyan)
)
val CosmicCardGradient = Brush.verticalGradient(
    listOf(Color(0xFF181826), Color(0xFF101018))
)

// Text Hierarchy
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextTertiary = Color(0xFF64748B)
val TextMuted = Color(0xFF475569)

// Glass & Surface Styling
val CyberBlack = CosmicBlack
val CyberDarkSurface = SpotifyDarkSurface
val CyberSurfaceVariant = SpotifySurfaceVariant
val CyberBorderGlow = Color(0x448B5CF6)
val NeonCyanDark = CyberCyanDark
val GlassBackground = Color(0xEE12121A)
val GlassSurface = Color(0xCC181824)
val GlassSurfaceHighlight = Color(0x338B5CF6)
val GlassBorder = Color(0x338B5CF6)

