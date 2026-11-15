package com.drivecoach.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * DRIVECOACH 3.0 — "AUTOMOTIVE PRECISION" Typography Scale (Section 06)
 *
 * Font Investigation & Selection:
 * - Pleos Connect Vehicle SDK (`ai.pleos.playground:Vehicle:2.3.2`) does not bundle a redistributable TTF/OTF font file.
 * - Per Section 06 license compliance rules, unverified external font binaries are not embedded.
 * - Uses Android's native `FontFamily.SansSerif` (Noto Sans KR / Roboto on Android 14 & Pleos Connect)
 *   with a driver-distance-optimized automotive type scale:
 *   - Large Screen Title: 32sp / SemiBold
 *   - Major Action / Guidance: 28sp / SemiBold
 *   - Menu / Category Title: 26sp / SemiBold
 *   - Course Title: 22sp / SemiBold
 *   - Body Copy: 18sp / Medium
 *   - Supporting Info: 16sp / Medium
 *   - No 11sp/12sp micro-text on driver-facing screens; natural Korean letter spacing.
 */
val Typography = Typography(
    // 대형 화면 타이틀 (30~34sp / SemiBold)
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
    ),
    // 주요 조작 안내 (26~32sp / SemiBold)
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
    ),
    // 메뉴 또는 카테고리 제목 (25~28sp / SemiBold)
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp,
    ),
    // 코스 제목 (21~24sp / SemiBold)
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.1).sp,
    ),
    // 버튼 및 강조 항목 (19~20sp / SemiBold)
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
    ),
    // 일반 본문 (17~20sp / Regular or Medium)
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    // 보조 정보 (15~17sp / Medium)
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
    ),
    // 최소 보조 텍스트 (15sp / Medium)
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    ),
    // 주요 버튼 라벨 (17~18sp / SemiBold)
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    // 보조 라벨 (15sp / Medium)
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
)
