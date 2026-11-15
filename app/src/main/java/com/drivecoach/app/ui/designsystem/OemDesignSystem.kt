package com.drivecoach.app.ui.designsystem

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecoach.app.ui.theme.OemColors
import kotlin.math.abs

// ============================================================================
// DRIVECOACH 3.0 — "AUTOMOTIVE PRECISION" Design System
// - Minimal, high-contrast charcoal/graphite surfaces (#08090B / #101114 / #17191D)
// - Three strict button tiers (PRIMARY / SECONDARY / TEXT-ICON) with large touch targets
// - Restrained status indication (subtle neutral dot when normal; #EF6666 only on error)
// - Sculpted top-down vehicle rendering + optional WebP asset slot loader
// ============================================================================

object OemSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp

    val screenHorizontal: Dp = 28.dp
    val screenVertical: Dp = 20.dp
}

object OemRadius {
    val chip: RoundedCornerShape = RoundedCornerShape(6.dp)
    val button: RoundedCornerShape = RoundedCornerShape(8.dp)
    val panel: RoundedCornerShape = RoundedCornerShape(10.dp)
    val card: RoundedCornerShape = RoundedCornerShape(12.dp)
    val largeCanvas: RoundedCornerShape = RoundedCornerShape(12.dp)
}

enum class OemSemanticTone {
    NEUTRAL,
    ACCENT,
    NORMAL,
    WARNING,
    ALERT,
}

enum class OemButtonVariant {
    PRIMARY,
    SECONDARY,
    GHOST,
    ALERT,
}

/**
 * Optional static asset slot resolver (Section 09 & Section 19):
 * Checks if a drawable resource (`parking_hero`, `driving_hero`, `vehicle_topview`, `parking_environment`)
 * has been placed in `app/src/main/res/drawable/`. Returns 0 if not present so the bespoke
 * Compose vector artwork renders seamlessly.
 */
@SuppressLint("DiscouragedApi")
@Composable
fun rememberOptionalDrawableId(resourceName: String): Int {
    val context = LocalContext.current
    return remember(resourceName, context) {
        context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    }
}

@Composable
fun OptionalHeroAssetOrFallback(
    drawableName: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit,
) {
    val resId = rememberOptionalDrawableId(drawableName)
    if (resId != 0) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        fallback()
    }
}

/**
 * Minimal Automotive Header Bar (`OemTopBar`) — Section 08, 15, 16
 * - No cluttered chip parades or heavy box borders
 * - Clean wordmark / screen title on the left, subtle status & text/icon actions on the right
 */
@Composable
fun OemTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "← 이전",
    trailingContent: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (onBack != null) {
                    OemButton(
                        text = backLabel,
                        onClick = onBack,
                        variant = OemButtonVariant.GHOST,
                        minHeight = 48.dp,
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(OemColors.Divider),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = OemColors.TextPrimary,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                            color = OemColors.TextSecondary,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                content = trailingContent,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OemColors.Divider.copy(alpha = 0.65f)),
        )
    }
}

/**
 * Three-Tier Automotive Button (`OemButton`) — Section 15
 * - PRIMARY: #5D8EFF fill, #08090B dark label, prominent sizing (default 56~64dp)
 * - SECONDARY: #22252B elevated panel, #F5F4F0 label, 1dp #343840 divider border
 * - GHOST (TEXT / ICON ACTION): Minimal surface for back/settings navigation
 */
@Composable
fun OemButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OemButtonVariant = OemButtonVariant.PRIMARY,
    enabled: Boolean = true,
    minHeight: Dp = when (variant) {
        OemButtonVariant.PRIMARY -> 58.dp
        OemButtonVariant.SECONDARY -> 54.dp
        OemButtonVariant.GHOST -> 48.dp
        OemButtonVariant.ALERT -> 56.dp
    },
) {
    val bgColor = when {
        !enabled -> OemColors.SurfacePrimary
        variant == OemButtonVariant.PRIMARY -> OemColors.AccentPrimary
        variant == OemButtonVariant.SECONDARY -> OemColors.SurfaceElevated
        variant == OemButtonVariant.GHOST -> Color.Transparent
        variant == OemButtonVariant.ALERT -> OemColors.StatusAlert
        else -> OemColors.SurfaceElevated
    }

    val contentColor = when {
        !enabled -> OemColors.TextTertiary
        variant == OemButtonVariant.PRIMARY -> OemColors.TextOnAccent
        variant == OemButtonVariant.SECONDARY -> OemColors.TextPrimary
        variant == OemButtonVariant.GHOST -> OemColors.TextSecondary
        variant == OemButtonVariant.ALERT -> OemColors.TextPrimary
        else -> OemColors.TextPrimary
    }

    val borderStroke = when {
        !enabled -> BorderStroke(1.dp, OemColors.BorderSubtle)
        variant == OemButtonVariant.SECONDARY -> BorderStroke(1.dp, OemColors.Divider)
        variant == OemButtonVariant.GHOST -> BorderStroke(1.dp, OemColors.BorderSubtle)
        else -> null
    }

    Surface(
        modifier = modifier
            .heightIn(min = minHeight)
            .widthIn(min = 64.dp)
            .clip(OemRadius.button)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        color = bgColor,
        shape = OemRadius.button,
        border = borderStroke,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (variant == OemButtonVariant.PRIMARY) 19.sp else 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

/**
 * Restrained Status Readout (`OemStatusChip`) — Section 16
 * - Normal / Neutral states: Quiet indicator dot + clean text without flashy green/blue pill boxes
 * - Alert / Warning states: Emphasized with #EF6666 / #D6A85A only when an issue requires driver attention
 */
@Composable
fun OemStatusChip(
    text: String,
    tone: OemSemanticTone = OemSemanticTone.NEUTRAL,
    showDot: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isProblem = tone == OemSemanticTone.ALERT || tone == OemSemanticTone.WARNING
    val dotColor = when (tone) {
        OemSemanticTone.ALERT -> OemColors.StatusAlert
        OemSemanticTone.WARNING -> OemColors.StatusWarning
        OemSemanticTone.ACCENT -> OemColors.AccentPrimary
        else -> OemColors.TextSecondary
    }
    val textColor = when (tone) {
        OemSemanticTone.ALERT -> OemColors.StatusAlert
        OemSemanticTone.WARNING -> OemColors.StatusWarning
        else -> OemColors.TextSecondary
    }

    Surface(
        modifier = modifier,
        color = if (isProblem) OemColors.StatusAlertSoft else Color.Transparent,
        shape = OemRadius.chip,
        border = if (isProblem) BorderStroke(1.dp, dotColor.copy(alpha = 0.55f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isProblem) 12.dp else 6.dp,
                vertical = 6.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = if (isProblem) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Graphical Steering State Indicator (`OemSteeringCuePanel`) — Section 13
 * - Replaces engineering angle numbers and arbitrary text commands with a clean
 *   visual front-wheel / steering-bar graphic and concise direction label ("좌회전" / "중앙" / "우회전").
 */
@Composable
fun OemSteeringCuePanel(
    phaseLabel: String,
    steeringActionCue: String,
    currentDirectionLabel: String,
    currentFrontWheelDeg: Float,
    targetFrontWheelDeg: Float?,
    maxFrontWheelDeg: Float,
    isSteeringConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = OemColors.SurfaceSecondary,
        shape = OemRadius.panel,
        border = BorderStroke(1.dp, OemColors.BorderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "조향 상태",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                    color = OemColors.TextSecondary,
                )
                Text(
                    text = if (isSteeringConnected) currentDirectionLabel else "핸들 신호 없음",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (isSteeringConnected) OemColors.TextPrimary else OemColors.StatusAlert,
                )
            }

            OemSteeringAlignmentBar(
                currentDeg = currentFrontWheelDeg,
                targetDeg = targetFrontWheelDeg,
                maxDeg = maxFrontWheelDeg.coerceAtLeast(25f),
                isConnected = isSteeringConnected,
            )
        }
    }
}

@Composable
private fun OemSteeringAlignmentBar(
    currentDeg: Float,
    targetDeg: Float?,
    maxDeg: Float,
    isConnected: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(OemRadius.chip)
                .background(OemColors.BackgroundPrimary),
        ) {
            val w = size.width
            val h = size.height
            val midX = w * 0.5f

            // Center reference line
            drawLine(
                color = OemColors.Divider,
                start = Offset(midX, 4f),
                end = Offset(midX, h - 4f),
                strokeWidth = 2.dp.toPx(),
            )

            // Verified target steering window (only rendered when reference path provides a verified target)
            if (targetDeg != null) {
                val normTarget = (targetDeg / maxDeg).coerceIn(-1f, 1f)
                val targetX = midX + normTarget * (w * 0.42f)
                val bandHalfW = w * 0.06f
                drawRoundRect(
                    color = OemColors.AccentPrimary.copy(alpha = 0.22f),
                    topLeft = Offset((targetX - bandHalfW).coerceAtLeast(2f), 3f),
                    size = Size(bandHalfW * 2f, h - 6f),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }

            // Active steering bar from center
            if (isConnected) {
                val normCurr = (currentDeg / maxDeg).coerceIn(-1f, 1f)
                val currX = midX + normCurr * (w * 0.42f)
                val barLeft = minOf(midX, currX)
                val barWidth = abs(currX - midX).coerceAtLeast(4.dp.toPx())

                drawRoundRect(
                    color = OemColors.AccentPrimary.copy(alpha = 0.45f),
                    topLeft = Offset(barLeft, h * 0.32f),
                    size = Size(barWidth, h * 0.36f),
                    cornerRadius = CornerRadius(3f, 3f),
                )

                drawRoundRect(
                    color = OemColors.TextPrimary,
                    topLeft = Offset(currX - 5.dp.toPx(), 4.dp.toPx()),
                    size = Size(10.dp.toPx(), h - 8.dp.toPx()),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("좌", style = MaterialTheme.typography.bodySmall, color = OemColors.TextTertiary)
            Text("중앙", style = MaterialTheme.typography.bodySmall, color = OemColors.TextTertiary)
            Text("우", style = MaterialTheme.typography.bodySmall, color = OemColors.TextTertiary)
        }
    }
}

/**
 * Clean Canvas Overlay Banner (`OemPracticeGuidanceBanner`)
 */
@Composable
fun OemPracticeGuidanceBanner(
    headline: String,
    detail: String,
    tone: OemSemanticTone = OemSemanticTone.NEUTRAL,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isAlert = tone == OemSemanticTone.ALERT
    val borderColor = if (isAlert) OemColors.StatusAlert else OemColors.Divider
    val headlineColor = if (isAlert) OemColors.StatusAlert else OemColors.TextPrimary

    Surface(
        modifier = modifier,
        color = OemColors.SurfacePrimary.copy(alpha = 0.96f),
        shape = OemRadius.panel,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = headlineColor,
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                        color = OemColors.TextSecondary,
                    )
                }
            }

            if (actionLabel != null && onActionClick != null) {
                OemButton(
                    text = actionLabel,
                    onClick = onActionClick,
                    variant = if (isAlert) OemButtonVariant.ALERT else OemButtonVariant.PRIMARY,
                    minHeight = 52.dp,
                )
            }
        }
    }
}

/**
 * Practice Completion Summary Card (`OemResultSummaryCard`)
 */
@Composable
fun OemResultSummaryCard(
    scenarioTitle: String,
    categoryTitle: String,
    isCompletedSuccess: Boolean,
    statusSummary: String,
    keyTakeaways: List<String>,
    onRetry: () -> Unit,
    onNextPractice: () -> Unit,
    onBackToCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = OemColors.SurfacePrimary,
        shape = OemRadius.card,
        border = BorderStroke(1.dp, OemColors.Divider),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = categoryTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OemColors.TextSecondary,
                        )
                        Text(
                            text = scenarioTitle,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
                            color = OemColors.TextPrimary,
                        )
                    }

                    OemStatusChip(
                        text = if (isCompletedSuccess) "주차 완료" else "다시 시도",
                        tone = if (isCompletedSuccess) OemSemanticTone.ACCENT else OemSemanticTone.ALERT,
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = OemColors.SurfaceSecondary,
                    shape = OemRadius.panel,
                    border = BorderStroke(1.dp, OemColors.BorderSubtle),
                ) {
                    Text(
                        text = statusSummary,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp),
                        color = OemColors.TextPrimary,
                        modifier = Modifier.padding(20.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    keyTakeaways.take(2).forEach { point ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(OemColors.AccentPrimary),
                            )
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                                color = OemColors.TextSecondary,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OemButton(
                    text = "목록으로",
                    onClick = onBackToCategory,
                    variant = OemButtonVariant.GHOST,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OemButton(
                        text = "다시 시작",
                        onClick = onRetry,
                        variant = OemButtonVariant.SECONDARY,
                        minHeight = 64.dp,
                    )
                    OemButton(
                        text = "다음 코스",
                        onClick = onNextPractice,
                        variant = OemButtonVariant.PRIMARY,
                        minHeight = 64.dp,
                    )
                }
            }
        }
    }
}

// ============================================================================
// SCULPTED AUTOMOTIVE TOP-DOWN VEHICLE RENDERER (Section 09 & Section 14)
// - Replaces simple rectangular cars with realistic automotive proportions:
//   tapered front nose, wheel-arch flares, sculpted hood creases, A/B/C pillars,
//   panoramic dark glasshouse with subtle specular reflection, side mirrors,
//   front LED DRL light bars, and rear tail-lamp bars.
// ============================================================================

fun DrawScope.drawSculptedTopDownVehicle(
    center: Offset,
    lengthPx: Float,
    widthPx: Float,
    headingDegreesScreen: Float,
    frontWheelSteerDegreesScreen: Float = 0f,
    isEgoVehicle: Boolean = true,
    isColliding: Boolean = false,
) {
    rotate(degrees = headingDegreesScreen, pivot = center) {
        val halfL = lengthPx * 0.5f
        val halfW = widthPx * 0.5f

        // 1. Soft multi-layered drop shadow on the road surface
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.42f),
            topLeft = Offset(center.x - halfL * 1.04f, center.y - halfW * 1.08f + 4.dp.toPx()),
            size = Size(lengthPx * 1.08f, widthPx * 1.16f),
            cornerRadius = CornerRadius(widthPx * 0.28f, widthPx * 0.28f),
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.25f),
            topLeft = Offset(center.x - halfL * 1.01f, center.y - halfW * 1.03f + 2.dp.toPx()),
            size = Size(lengthPx * 1.02f, widthPx * 1.06f),
            cornerRadius = CornerRadius(widthPx * 0.24f, widthPx * 0.24f),
        )

        // 2. Four wheels (Front wheels rotate with steering angle)
        val wheelLen = lengthPx * 0.16f
        val wheelWid = widthPx * 0.13f
        val frontAxleX = center.x + halfL * 0.56f
        val rearAxleX = center.x - halfL * 0.56f
        val wheelOffsetY = halfW * 0.90f

        val wheelCenters = listOf(
            Triple(frontAxleX, center.y - wheelOffsetY, frontWheelSteerDegreesScreen),
            Triple(frontAxleX, center.y + wheelOffsetY, frontWheelSteerDegreesScreen),
            Triple(rearAxleX, center.y - wheelOffsetY, 0f),
            Triple(rearAxleX, center.y + wheelOffsetY, 0f),
        )
        wheelCenters.forEach { (wx, wy, steerDeg) ->
            val wPivot = Offset(wx, wy)
            rotate(degrees = steerDeg, pivot = wPivot) {
                drawRoundRect(
                    color = Color(0xFF0A0B0E),
                    topLeft = Offset(wx - wheelLen * 0.5f, wy - wheelWid * 0.5f),
                    size = Size(wheelLen, wheelWid),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
                drawRoundRect(
                    color = Color(0xFF3E434D),
                    topLeft = Offset(wx - wheelLen * 0.5f, wy - wheelWid * 0.5f),
                    size = Size(wheelLen, wheelWid),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        // 3. Sculpted aerodynamic body contour (+X is forward, -X is rear)
        val bodyPath = Path().apply {
            val frontX = center.x + halfL
            val rearX = center.x - halfL
            // Start at front center nose
            moveTo(frontX, center.y - halfW * 0.52f)
            // Front bumper corner -> front fender flare
            quadraticTo(
                frontX - lengthPx * 0.02f,
                center.y - halfW * 0.92f,
                center.x + halfL * 0.65f,
                center.y - halfW,
            )
            // Side sill to rear fender
            lineTo(center.x - halfL * 0.68f, center.y - halfW * 0.98f)
            // Rear quarter taper -> rear bumper
            quadraticTo(
                rearX + lengthPx * 0.02f,
                center.y - halfW * 0.88f,
                rearX,
                center.y - halfW * 0.56f,
            )
            // Rear tail across
            lineTo(rearX, center.y + halfW * 0.56f)
            // Rear right quarter
            quadraticTo(
                rearX + lengthPx * 0.02f,
                center.y + halfW * 0.88f,
                center.x - halfL * 0.68f,
                center.y + halfW * 0.98f,
            )
            // Right sill to front right fender
            lineTo(center.x + halfL * 0.65f, center.y + halfW)
            // Front right bumper corner
            quadraticTo(
                frontX - lengthPx * 0.02f,
                center.y + halfW * 0.92f,
                frontX,
                center.y + halfW * 0.52f,
            )
            close()
        }

        val bodyBrush = when {
            isColliding -> Brush.linearGradient(
                colors = listOf( Color(0xFFEF6666), Color(0xFFB83B3B) ),
                start = Offset(center.x + halfL, center.y - halfW),
                end = Offset(center.x - halfL, center.y + halfW),
            )
            isEgoVehicle -> Brush.linearGradient(
                colors = listOf(
                    Color(0xFFF2F3F5),
                    Color(0xFFDCE0E8),
                    Color(0xFFBFC5D0),
                ),
                start = Offset(center.x + halfL, center.y - halfW),
                end = Offset(center.x - halfL, center.y + halfW),
            )
            else -> Brush.linearGradient(
                colors = listOf(
                    Color(0xFF343942),
                    Color(0xFF252930),
                    Color(0xFF1C1F25),
                ),
                start = Offset(center.x + halfL, center.y - halfW),
                end = Offset(center.x - halfL, center.y + halfW),
            )
        }

        drawPath(path = bodyPath, brush = bodyBrush, style = Fill)
        drawPath(
            path = bodyPath,
            color = if (isEgoVehicle) Color(0xFFFFFFFF).copy(alpha = 0.7f) else Color(0xFF484E5A),
            style = Stroke(width = 1.4.dp.toPx(), join = StrokeJoin.Round),
        )

        // 4. Side mirrors (A-pillar position)
        val mirrorX = center.x + halfL * 0.28f
        val mirrorLen = lengthPx * 0.065f
        val mirrorOut = widthPx * 0.09f
        val mirrorColor = if (isEgoVehicle) Color(0xFFDCE0E8) else Color(0xFF2D323B)
        // Left mirror
        drawRoundRect(
            color = mirrorColor,
            topLeft = Offset(mirrorX - mirrorLen * 0.5f, center.y - halfW - mirrorOut * 0.85f),
            size = Size(mirrorLen, mirrorOut),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        )
        // Right mirror
        drawRoundRect(
            color = mirrorColor,
            topLeft = Offset(mirrorX - mirrorLen * 0.5f, center.y + halfW - mirrorOut * 0.15f),
            size = Size(mirrorLen, mirrorOut),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        )

        // 5. Panoramic Glasshouse (Windshield + Roof + Rear Glass)
        val greenhousePath = Path().apply {
            val wsFrontX = center.x + halfL * 0.36f
            val rearGlassX = center.x - halfL * 0.64f
            moveTo(wsFrontX, center.y - halfW * 0.68f)
            lineTo(wsFrontX, center.y + halfW * 0.68f)
            lineTo(center.x + halfL * 0.10f, center.y + halfW * 0.78f)
            lineTo(rearGlassX, center.y + halfW * 0.66f)
            lineTo(rearGlassX, center.y - halfW * 0.66f)
            lineTo(center.x + halfL * 0.10f, center.y - halfW * 0.78f)
            close()
        }
        drawPath(
            path = greenhousePath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF141820), Color(0xFF202632), Color(0xFF101319)),
                start = Offset(center.x + halfL * 0.3f, center.y - halfW * 0.7f),
                end = Offset(center.x - halfL * 0.6f, center.y + halfW * 0.7f),
            ),
        )

        // Roof panel inset between windshield and rear glass
        val roofLeft = center.x - halfL * 0.42f
        val roofRight = center.x + halfL * 0.08f
        val roofHalfW = halfW * 0.64f
        val roofColor = if (isEgoVehicle) Color(0xFFCFD4DE) else Color(0xFF232730)
        drawRoundRect(
            color = roofColor,
            topLeft = Offset(roofLeft, center.y - roofHalfW),
            size = Size(roofRight - roofLeft, roofHalfW * 2f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        )

        // Subtle windshield specular reflection line
        drawLine(
            color = Color.White.copy(alpha = if (isEgoVehicle) 0.22f else 0.10f),
            start = Offset(center.x + halfL * 0.28f, center.y - halfW * 0.52f),
            end = Offset(center.x + halfL * 0.16f, center.y + halfW * 0.30f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // 6. Sculpted Hood Crease Lines
        val creaseColor = if (isEgoVehicle) Color(0xFF9EA6B5) else Color(0xFF16181D)
        drawLine(
            color = creaseColor,
            start = Offset(center.x + halfL * 0.38f, center.y - halfW * 0.56f),
            end = Offset(center.x + halfL * 0.90f, center.y - halfW * 0.40f),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = creaseColor,
            start = Offset(center.x + halfL * 0.38f, center.y + halfW * 0.56f),
            end = Offset(center.x + halfL * 0.90f, center.y + halfW * 0.40f),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // 7. Front LED DRL headlamps & Rear Taillamps (clear front vs. rear distinction)
        if (isEgoVehicle) {
            val frontLampX = center.x + halfL * 0.96f
            drawLine(
                color = Color(0xFFFFFFFF),
                start = Offset(frontLampX, center.y - halfW * 0.72f),
                end = Offset(frontLampX, center.y - halfW * 0.34f),
                strokeWidth = 2.8.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xFFFFFFFF),
                start = Offset(frontLampX, center.y + halfW * 0.34f),
                end = Offset(frontLampX, center.y + halfW * 0.72f),
                strokeWidth = 2.8.dp.toPx(),
                cap = StrokeCap.Round,
            )

            val rearLampX = center.x - halfL * 0.96f
            drawLine(
                color = Color(0xFFD94A4A),
                start = Offset(rearLampX, center.y - halfW * 0.70f),
                end = Offset(rearLampX, center.y + halfW * 0.70f),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
