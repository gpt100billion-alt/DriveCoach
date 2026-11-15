package com.drivecoach.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// DRIVECOACH 3.0 — "AUTOMOTIVE PRECISION" Color System (Section 05)
// - Pure charcoal-black & neutral graphite foundation (no navy/green/pastel cast)
// - Warm off-white primary text (#F5F4F0) for high contrast on dark displays
// - Single precision cobalt-blue accent (#5D8EFF) for selection & trajectory
// - Critical warning red (#EF6666) reserved strictly for collision & errors
// ============================================================================

object OemColors {
    // 1. Core Surface Hierarchy (Section 05 exact specification)
    val BackgroundPrimary = Color(0xFF08090B)      // MAIN BACKGROUND (#08090B)
    val SurfaceViewport = Color(0xFF101114)        // SECONDARY BACKGROUND (#101114)
    val SurfacePrimary = Color(0xFF17191D)         // PRIMARY PANEL (#17191D)
    val SurfaceSecondary = Color(0xFF101114)       // SECONDARY BACKGROUND (#101114)
    val SurfaceElevated = Color(0xFF22252B)        // ELEVATED PANEL (#22252B)
    val SurfaceDarkRestricted = Color(0xFF08090B)  // Parked-only lock screen background
    val SurfaceDarkCard = Color(0xFF17191D)        // Parked-only lock screen panel

    // 2. Structural Dividers & Borders
    val Divider = Color(0xFF343840)                // DIVIDER (#343840)
    val BorderSubtle = Color(0xFF262930)           // Subtle structural border
    val BorderStrong = Color(0xFF343840)           // Emphasized panel border
    val GridLine = Color(0xFF1A1C20)               // Minimal auxiliary reference line

    // 3. Typography Hierarchy (Section 05 exact specification)
    val TextPrimary = Color(0xFFF5F4F0)            // PRIMARY TEXT (#F5F4F0)
    val TextSecondary = Color(0xFFB7BBC4)          // SECONDARY TEXT (#B7BBC4)
    val TextTertiary = Color(0xFF777C85)           // DISABLED / MUTED TEXT (#777C85)
    val TextOnAccent = Color(0xFF08090B)           // High-contrast dark label on #5D8EFF
    val TextOnDark = Color(0xFFF5F4F0)
    val TextOnDarkSecondary = Color(0xFFB7BBC4)

    // 4. Single Primary Accent (#5D8EFF)
    val AccentPrimary = Color(0xFF5D8EFF)          // PRIMARY ACCENT (#5D8EFF)
    val AccentPrimarySoft = Color(0xFF182238)      // Restrained accent surface tint
    val AccentPrimaryBorder = Color(0xFF3B5998)    // Selected outline
    val GuideCyan = Color(0xFF5D8EFF)              // Unified with Primary Accent (#5D8EFF)

    // 5. Semantic Status Tokens (No decorative green/pastel badges)
    val StatusNormal = Color(0xFFF5F4F0)           // Normal state uses calm neutral off-white
    val StatusNormalSoft = Color(0xFF17191D)
    val StatusWarning = Color(0xFFD6A85A)          // Restrained amber for P-gear lock notice
    val StatusWarningSoft = Color(0xFF241E14)
    val StatusAlert = Color(0xFFEF6666)            // CRITICAL WARNING (#EF6666)
    val StatusAlertSoft = Color(0xFF2A1416)
}

// ============================================================================
// Simulator Canvas & Screen Tokens (Mapped to AUTOMOTIVE PRECISION)
// ============================================================================
val SimMainBackground = OemColors.BackgroundPrimary
val SimMainSurface = OemColors.SurfacePrimary
val SimSecondarySurface = OemColors.SurfaceSecondary
val SimPrimaryText = OemColors.TextPrimary
val SimSecondaryText = OemColors.TextSecondary
val SimPrimaryAccent = OemColors.AccentPrimary
val SimPrimaryAccentSoft = OemColors.AccentPrimarySoft
val SimBorder = OemColors.Divider
val SimSuccess = OemColors.AccentPrimary
val SimSuccessSoft = OemColors.AccentPrimarySoft
val SimWarning = OemColors.StatusWarning
val SimWarningSoft = OemColors.StatusWarningSoft
val SimCollision = OemColors.StatusAlert
val SimCollisionSoft = OemColors.StatusAlertSoft

// Automotive Precision Top-Down Environment & Vehicle Palette
val SimRoadSurface = Color(0xFF14161A)
val SimAsphaltDark = Color(0xFF0E1013)
val SimCurbFill = Color(0xFF1E2127)
val SimCurbStroke = Color(0xFF343840)
val SimParkingLine = Color(0xFFD8D7D2)
val SimTargetZoneFill = Color(0x185D8EFF)
val SimTargetZoneActive = Color(0x2E5D8EFF)

// Sculpted Ego Vehicle (Metallic Titanium Silver-White & Deep Glasshouse)
val SimEgoVehicleBody = Color(0xFFE6E8EC)
val SimEgoVehicleRoof = Color(0xFFC8CDD6)
val SimEgoVehicleGlass = Color(0xFF181C24)

// Surrounding Parked Obstacle Vehicles (Refined Dark Graphite Metallic)
val SimObstacleVehicleBody = Color(0xFF282C34)
val SimObstacleVehicleGlass = Color(0xFF14171C)

// Trajectory Lines (Differentiated by stroke pattern & opacity, single accent family)
val SimPathPredicted = Color(0xFF5D8EFF)
val SimPathHistory = Color(0x99B7BBC4)
val SimPathReference = Color(0x775D8EFF)

// ============================================================================
// Legacy Package Compatibility Aliases
// ============================================================================
val MainBackground = OemColors.BackgroundPrimary
val SecondaryBackground = OemColors.SurfacePrimary
val ElevatedSurface = OemColors.SurfaceElevated
val CardBorder = OemColors.Divider

val PrimaryAccent = OemColors.AccentPrimary
val PrimaryAccentContainer = OemColors.AccentPrimarySoft
val SecondaryAccent = OemColors.AccentPrimary
val SecondaryAccentContainer = OemColors.AccentPrimarySoft

val TextPrimary = OemColors.TextPrimary
val TextSecondary = OemColors.TextSecondary
val TextMuted = OemColors.TextTertiary

val StatusSuccess = OemColors.AccentPrimary
val StatusWarning = OemColors.StatusWarning
val StatusCritical = OemColors.StatusAlert

val GaugeTrack = OemColors.SurfaceSecondary

val CockpitBackground = MainBackground
val CockpitSurface = SecondaryBackground
val CockpitSurfaceElevated = ElevatedSurface
val CockpitCardBorder = CardBorder
val PleosCyan = PrimaryAccent
val PleosCyanContainer = PrimaryAccentContainer
val PleosMint = SecondaryAccent
val PleosAmber = StatusWarning
val PleosBlueDeep = Color(0xFF3B66CC)
