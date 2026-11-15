package com.drivecoach.app.ui.simulator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecoach.app.data.vehicle.PleosSdkDiagnosticState
import com.drivecoach.app.simulation.collision.EvaluationStatus
import com.drivecoach.app.simulation.geometry.ObstacleRole
import com.drivecoach.app.simulation.geometry.Vec2
import com.drivecoach.app.simulation.geometry.WorldViewportTransform
import com.drivecoach.app.simulation.scenarios.PracticeCategory
import com.drivecoach.app.simulation.scenarios.ReferencePathResult
import com.drivecoach.app.simulation.scenarios.ScenarioDefinition
import com.drivecoach.app.simulation.scenarios.ScenarioSpaceOptions
import com.drivecoach.app.simulation.scenarios.ScenarioType
import com.drivecoach.app.simulation.scenarios.SimulatorScenarios
import com.drivecoach.app.simulation.vehicle.VehicleConfig
import com.drivecoach.app.ui.designsystem.OemButton
import com.drivecoach.app.ui.designsystem.OemButtonVariant
import com.drivecoach.app.ui.designsystem.OemRadius
import com.drivecoach.app.ui.designsystem.OemResultSummaryCard
import com.drivecoach.app.ui.designsystem.OemSemanticTone
import com.drivecoach.app.ui.designsystem.OemSpacing
import com.drivecoach.app.ui.designsystem.OemStatusChip
import com.drivecoach.app.ui.designsystem.OemTopBar
import com.drivecoach.app.ui.designsystem.OptionalHeroAssetOrFallback
import com.drivecoach.app.ui.designsystem.drawSculptedTopDownVehicle
import com.drivecoach.app.ui.theme.DriveCoachTheme
import com.drivecoach.app.ui.theme.OemColors
import com.drivecoach.app.ui.theme.SimCollision
import com.drivecoach.app.ui.theme.SimCurbFill
import com.drivecoach.app.ui.theme.SimCurbStroke
import com.drivecoach.app.ui.theme.SimParkingLine
import com.drivecoach.app.ui.theme.SimPathPredicted
import com.drivecoach.app.ui.theme.SimPathReference
import com.drivecoach.app.ui.theme.SimPrimaryAccent
import com.drivecoach.app.ui.theme.SimRoadSurface
import com.drivecoach.app.ui.theme.SimTargetZoneFill
import com.drivecoach.app.ui.theme.SimWarning
import java.util.Locale

// ============================================================================
// SECTION 07 & 08: HOME SCREEN (`DriveCoachHomeScreen`)
// - Replaces the old 2x2 card grid with an asymmetric automotive split layout:
//   Left 35%: "운전 연습" title + 2 primary domain selectors (주차 / 주행)
//   Right 65%: Large-scale hero vehicle & environment visual stage
// ============================================================================

enum class HomePrimaryDomain(
    val titleKorean: String,
    val summaryKorean: String,
    val primaryCategory: PracticeCategory,
    val heroDrawableName: String,
) {
    PARKING(
        titleKorean = "주차",
        summaryKorean = "평행주차 · 후진주차 · 전면주차",
        primaryCategory = PracticeCategory.PARKING,
        heroDrawableName = "parking_hero",
    ),
    DRIVING(
        titleKorean = "주행",
        summaryKorean = "우회전 · 좌회전 · 차폭 감각 · 기초 조작",
        primaryCategory = PracticeCategory.DRIVING,
        heroDrawableName = "driving_hero",
    ),
}

@Composable
fun DriveCoachHomeScreen(
    isParkedAndSafe: Boolean = true,
    isSteeringConnected: Boolean = true,
    initialSelectedDomain: HomePrimaryDomain = HomePrimaryDomain.PARKING,
    onSelectCategory: (PracticeCategory) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSteeringDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedDomain by remember { mutableStateOf(initialSelectedDomain) }

    val statusText = when {
        !isParkedAndSafe -> "주차 상태에서만 연습할 수 있습니다."
        !isSteeringConnected -> "핸들 신호를 확인할 수 없습니다."
        else -> "연습 준비 완료"
    }
    val statusTone = when {
        !isParkedAndSafe -> OemSemanticTone.WARNING
        !isSteeringConnected -> OemSemanticTone.ALERT
        else -> OemSemanticTone.NEUTRAL
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OemColors.BackgroundPrimary)
            .padding(
                horizontal = OemSpacing.screenHorizontal,
                vertical = OemSpacing.screenVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(OemSpacing.lg),
    ) {
        // Minimal top navigation header
        OemTopBar(
            title = "DriveCoach",
            trailingContent = {
                OemStatusChip(
                    text = statusText,
                    tone = statusTone,
                )
                if (!isSteeringConnected) {
                    OemButton(
                        text = "핸들 연결 확인",
                        onClick = onOpenSteeringDiagnostics,
                        variant = OemButtonVariant.SECONDARY,
                        minHeight = 48.dp,
                    )
                }
                OemButton(
                    text = "설정",
                    onClick = onOpenSettings,
                    variant = OemButtonVariant.GHOST,
                    minHeight = 48.dp,
                )
            },
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val isWide = maxWidth >= 760.dp

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(OemSpacing.lg),
                ) {
                    // Left 35%: Title + 2 Major Domain Selectors
                    HomeLeftMenuColumn(
                        selectedDomain = selectedDomain,
                        onSelectDomain = { domain ->
                            if (selectedDomain == domain) {
                                onSelectCategory(domain.primaryCategory)
                            } else {
                                selectedDomain = domain
                            }
                        },
                        onEnterSelectedDomain = {
                            onSelectCategory(selectedDomain.primaryCategory)
                        },
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight(),
                    )

                    // Right 65%: Large Hero Vehicle Preview Stage
                    HomeRightHeroStage(
                        selectedDomain = selectedDomain,
                        onEnterDomain = { onSelectCategory(selectedDomain.primaryCategory) },
                        modifier = Modifier
                            .weight(0.65f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(OemSpacing.md),
                ) {
                    HomeLeftMenuColumn(
                        selectedDomain = selectedDomain,
                        onSelectDomain = { domain ->
                            if (selectedDomain == domain) {
                                onSelectCategory(domain.primaryCategory)
                            } else {
                                selectedDomain = domain
                            }
                        },
                        onEnterSelectedDomain = {
                            onSelectCategory(selectedDomain.primaryCategory)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    HomeRightHeroStage(
                        selectedDomain = selectedDomain,
                        onEnterDomain = { onSelectCategory(selectedDomain.primaryCategory) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeLeftMenuColumn(
    selectedDomain: HomePrimaryDomain,
    onSelectDomain: (HomePrimaryDomain) -> Unit,
    onEnterSelectedDomain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "운전 연습",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = OemColors.TextPrimary,
                )
                Text(
                    text = "주차 상태에서 실제 차량 핸들로 경로를 연습합니다.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                    color = OemColors.TextSecondary,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HomePrimaryDomain.entries.forEach { domain ->
                    val isSelected = domain == selectedDomain
                    HomeDomainSelectorItem(
                        title = domain.titleKorean,
                        subtitle = domain.summaryKorean,
                        isSelected = isSelected,
                        onClick = { onSelectDomain(domain) },
                    )
                }
            }
        }

        OemButton(
            text = "${selectedDomain.titleKorean} 코스 선택",
            onClick = onEnterSelectedDomain,
            variant = OemButtonVariant.PRIMARY,
            minHeight = 64.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HomeDomainSelectorItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .clip(OemRadius.card)
            .clickable(onClick = onClick),
        color = if (isSelected) OemColors.SurfaceElevated else OemColors.SurfacePrimary,
        shape = OemRadius.card,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) OemColors.AccentPrimary else OemColors.BorderSubtle,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isSelected) OemColors.AccentPrimary else OemColors.Divider,
                        ),
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = OemColors.TextPrimary,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        color = if (isSelected) OemColors.TextPrimary.copy(alpha = 0.88f) else OemColors.TextSecondary,
                    )
                }
            }

            Text(
                text = "→",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                color = if (isSelected) OemColors.AccentPrimary else OemColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun HomeRightHeroStage(
    selectedDomain: HomePrimaryDomain,
    onEnterDomain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(OemRadius.largeCanvas)
            .clickable(onClick = onEnterDomain),
        color = OemColors.SurfaceSecondary,
        shape = OemRadius.largeCanvas,
        border = BorderStroke(1.dp, OemColors.Divider),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Optional WebP asset slot (`parking_hero` / `driving_hero`) or sculpted vector hero scene
            OptionalHeroAssetOrFallback(
                drawableName = selectedDomain.heroDrawableName,
                contentDescription = selectedDomain.titleKorean,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (selectedDomain) {
                    HomePrimaryDomain.PARKING -> ParkingHeroStageCanvas(modifier = Modifier.fillMaxSize())
                    HomePrimaryDomain.DRIVING -> DrivingHeroStageCanvas(modifier = Modifier.fillMaxSize())
                }
            }

            // Bottom subtle gradient scrim + concise domain summary bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                OemColors.BackgroundPrimary.copy(alpha = 0.88f),
                            ),
                        ),
                    )
                    .padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = selectedDomain.titleKorean,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = OemColors.TextPrimary,
                        )
                        Text(
                            text = if (selectedDomain == HomePrimaryDomain.PARKING) {
                                "실제 핸들 조작에 따른 주차 진입 궤적을 실시간으로 확인합니다."
                            } else {
                                "교차로 회전 반경과 차폭 여유 공간을 직관적으로 익힙니다."
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                            color = OemColors.TextSecondary,
                        )
                    }

                    Text(
                        text = "코스 보기 →",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = OemColors.AccentPrimary,
                    )
                }
            }
        }
    }
}

// ============================================================================
// SECTION 09: HIGH-DENSITY SCULPTED HERO GRAPHICS (Parking & Driving)
// - Vehicle is the large visual protagonist on a refined charcoal-graphite stage
// ============================================================================

@Composable
private fun ParkingHeroStageCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Subtle atmospheric stage radial vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1A1D24),
                    Color(0xFF101114),
                ),
                center = Offset(w * 0.48f, h * 0.46f),
                radius = w * 0.65f,
            ),
        )

        // Curb & parking bay environment on the right
        val bayLeft = w * 0.56f
        val bayRight = w * 0.84f
        val bayTop = h * 0.24f
        val bayBottom = h * 0.72f

        // Right curb edge
        drawRect(
            color = SimCurbFill,
            topLeft = Offset(bayRight, 0f),
            size = Size(w - bayRight, h),
        )
        drawLine(
            color = SimCurbStroke,
            start = Offset(bayRight, 0f),
            end = Offset(bayRight, h),
            strokeWidth = 2.dp.toPx(),
        )

        // Target parallel parking bay highlight
        drawRoundRect(
            color = SimTargetZoneFill,
            topLeft = Offset(bayLeft, bayTop),
            size = Size(bayRight - bayLeft, bayBottom - bayTop),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )

        // Crisp parking bay lines
        val markStroke = 3.dp.toPx()
        drawLine(SimParkingLine, Offset(bayLeft, bayTop), Offset(bayRight, bayTop), markStroke, StrokeCap.Round)
        drawLine(SimParkingLine, Offset(bayLeft, bayBottom), Offset(bayRight, bayBottom), markStroke, StrokeCap.Round)
        drawLine(
            color = SimPrimaryAccent.copy(alpha = 0.75f),
            start = Offset(bayLeft, bayTop),
            end = Offset(bayLeft, bayBottom),
            strokeWidth = 2.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
        )

        val carLen = h * 0.34f
        val carWid = carLen * 0.44f
        val parkedCenterX = (bayLeft + bayRight) * 0.5f

        // Front & Rear parked vehicles (Sculpted dark graphite cars)
        drawSculptedTopDownVehicle(
            center = Offset(parkedCenterX, h * 0.04f),
            lengthPx = carLen,
            widthPx = carWid,
            headingDegreesScreen = -90f,
            isEgoVehicle = false,
        )
        drawSculptedTopDownVehicle(
            center = Offset(parkedCenterX, h * 0.92f),
            lengthPx = carLen,
            widthPx = carWid,
            headingDegreesScreen = -90f,
            isEgoVehicle = false,
        )

        // Precision entry trajectory curve & wheel sweep envelope
        val entryCurve = Path().apply {
            moveTo(w * 0.34f, h * 0.28f)
            cubicTo(
                w * 0.35f,
                h * 0.52f,
                w * 0.68f,
                h * 0.38f,
                parkedCenterX,
                (bayTop + bayBottom) * 0.54f,
            )
        }
        drawPath(
            path = entryCurve,
            color = SimPrimaryAccent.copy(alpha = 0.24f),
            style = Stroke(width = carWid * 0.85f, cap = StrokeCap.Round),
        )
        drawPath(
            path = entryCurve,
            color = SimPrimaryAccent,
            style = Stroke(
                width = 3.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)),
            ),
        )
        drawCircle(
            color = SimPrimaryAccent,
            radius = 6.dp.toPx(),
            center = Offset(parkedCenterX, (bayTop + bayBottom) * 0.54f),
        )

        // Hero Ego Vehicle (Large, prominent, sculpted metallic silver-white car)
        drawSculptedTopDownVehicle(
            center = Offset(w * 0.41f, h * 0.38f),
            lengthPx = carLen * 1.06f,
            widthPx = carWid * 1.06f,
            headingDegreesScreen = -64f,
            frontWheelSteerDegreesScreen = 26f,
            isEgoVehicle = true,
        )
    }
}

@Composable
private fun DrivingHeroStageCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1A1D24),
                    Color(0xFF101114),
                ),
                center = Offset(w * 0.48f, h * 0.46f),
                radius = w * 0.65f,
            ),
        )

        // Intersection road surface
        val verticalRoadLeft = w * 0.16f
        val verticalRoadRight = w * 0.56f
        val horizontalRoadTop = h * 0.14f
        val horizontalRoadBottom = h * 0.62f

        drawRect(
            color = SimRoadSurface,
            topLeft = Offset(verticalRoadLeft, 0f),
            size = Size(verticalRoadRight - verticalRoadLeft, h),
        )
        drawRect(
            color = SimRoadSurface,
            topLeft = Offset(verticalRoadLeft, horizontalRoadTop),
            size = Size(w - verticalRoadLeft, horizontalRoadBottom - horizontalRoadTop),
        )

        // Inner corner curb block
        drawRoundRect(
            color = SimCurbFill,
            topLeft = Offset(verticalRoadRight, horizontalRoadBottom),
            size = Size(w - verticalRoadRight + 40f, h - horizontalRoadBottom + 40f),
            cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
        )
        drawRoundRect(
            color = SimCurbStroke,
            topLeft = Offset(verticalRoadRight, horizontalRoadBottom),
            size = Size(w - verticalRoadRight + 40f, h - horizontalRoadBottom + 40f),
            cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
            style = Stroke(width = 2.5.dp.toPx()),
        )

        // Road lane markings
        val laneDash = PathEffect.dashPathEffect(floatArrayOf(20f, 16f))
        val midVerticalX = (verticalRoadLeft + verticalRoadRight) * 0.5f
        val midHorizontalY = (horizontalRoadTop + horizontalRoadBottom) * 0.5f
        drawLine(
            color = SimParkingLine.copy(alpha = 0.55f),
            start = Offset(midVerticalX, horizontalRoadBottom + 16.dp.toPx()),
            end = Offset(midVerticalX, h),
            strokeWidth = 2.5.dp.toPx(),
            pathEffect = laneDash,
        )
        drawLine(
            color = SimParkingLine.copy(alpha = 0.55f),
            start = Offset(verticalRoadRight + 16.dp.toPx(), midHorizontalY),
            end = Offset(w, midHorizontalY),
            strokeWidth = 2.5.dp.toPx(),
            pathEffect = laneDash,
        )

        val carLen = h * 0.34f
        val carWid = carLen * 0.44f

        // Smooth turning trajectory corridor & centerline
        val turnCurve = Path().apply {
            moveTo(w * 0.45f, h * 0.76f)
            quadraticTo(
                w * 0.45f,
                h * 0.48f,
                w * 0.84f,
                h * 0.48f,
            )
        }
        drawPath(
            path = turnCurve,
            color = SimPrimaryAccent.copy(alpha = 0.22f),
            style = Stroke(width = carWid * 0.88f, cap = StrokeCap.Round),
        )
        drawPath(
            path = turnCurve,
            color = SimPrimaryAccent,
            style = Stroke(
                width = 3.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)),
            ),
        )
        drawCircle(
            color = SimPrimaryAccent,
            radius = 6.dp.toPx(),
            center = Offset(w * 0.84f, h * 0.48f),
        )

        // Hero Ego Vehicle mid-turn
        drawSculptedTopDownVehicle(
            center = Offset(w * 0.49f, h * 0.58f),
            lengthPx = carLen * 1.06f,
            widthPx = carWid * 1.06f,
            headingDegreesScreen = -48f,
            frontWheelSteerDegreesScreen = 24f,
            isEgoVehicle = true,
        )
    }
}

// ============================================================================
// SECTION 10 & 11: PARKING & DRIVING COURSE SELECTION SCREEN (`CategorySelectionScreen`)
// - Master-Detail Asymmetric Layout:
//   Left 36%: Selectable Course Menu List (Parking: 평행주차 / 후진주차 / 전면주차;
//             Driving: 우회전 / 좌회전 / 차폭 감각 / 기초 조작)
//   Right 64%: Large Live Course Schematic + "연습 시작" Primary CTA
// ============================================================================

private data class DrivingMenuEntry(
    val titleKorean: String,
    val primaryScenario: ScenarioType,
    val relatedScenarios: List<ScenarioType>,
)

@Composable
fun CategorySelectionScreen(
    category: PracticeCategory,
    onSelectScenario: (ScenarioType) -> Unit,
    onSwitchCategory: (PracticeCategory) -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isParkingDomain = category == PracticeCategory.PARKING

    val parkingCourses = remember {
        listOf(
            ScenarioType.PARALLEL_PARKING,
            ScenarioType.REVERSE_PARKING,
            ScenarioType.FORWARD_PARKING,
        )
    }

    val drivingGroups = remember {
        listOf(
            DrivingMenuEntry(
                titleKorean = "우회전",
                primaryScenario = ScenarioType.RIGHT_TURN,
                relatedScenarios = listOf(ScenarioType.RIGHT_TURN, ScenarioType.TURN_RADIUS_SENSE),
            ),
            DrivingMenuEntry(
                titleKorean = "좌회전",
                primaryScenario = ScenarioType.LEFT_TURN,
                relatedScenarios = listOf(ScenarioType.LEFT_TURN),
            ),
            DrivingMenuEntry(
                titleKorean = "차폭 감각",
                primaryScenario = ScenarioType.WIDTH_PERCEPTION,
                relatedScenarios = listOf(
                    ScenarioType.WIDTH_PERCEPTION,
                    ScenarioType.RIGHT_CLEARANCE,
                    ScenarioType.LANE_CENTERING,
                ),
            ),
            DrivingMenuEntry(
                titleKorean = "기초 조작",
                primaryScenario = ScenarioType.STEERING_RETURN,
                relatedScenarios = listOf(
                    ScenarioType.STEERING_RETURN,
                    ScenarioType.STEERING_TIMING,
                    ScenarioType.LOW_SPEED_STEERING,
                ),
            ),
        )
    }

    var selectedScenario by remember(category) {
        mutableStateOf(
            when (category) {
                PracticeCategory.PARKING -> ScenarioType.PARALLEL_PARKING
                PracticeCategory.DRIVING -> ScenarioType.RIGHT_TURN
                PracticeCategory.WIDTH_AND_LANE -> ScenarioType.WIDTH_PERCEPTION
                PracticeCategory.BASIC_CONTROL -> ScenarioType.STEERING_RETURN
            },
        )
    }

    val previewScenarioDef = remember(selectedScenario) {
        SimulatorScenarios.buildScenario(selectedScenario)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OemColors.BackgroundPrimary)
            .padding(
                horizontal = OemSpacing.screenHorizontal,
                vertical = OemSpacing.screenVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(OemSpacing.lg),
    ) {
        OemTopBar(
            title = if (isParkingDomain) "주차" else "주행",
            onBack = onBackToHome,
            backLabel = "← 홈",
            trailingContent = {
                OemButton(
                    text = "주차",
                    onClick = { onSwitchCategory(PracticeCategory.PARKING) },
                    variant = if (isParkingDomain) OemButtonVariant.PRIMARY else OemButtonVariant.SECONDARY,
                    minHeight = 48.dp,
                )
                OemButton(
                    text = "주행",
                    onClick = { onSwitchCategory(PracticeCategory.DRIVING) },
                    variant = if (!isParkingDomain) OemButtonVariant.PRIMARY else OemButtonVariant.SECONDARY,
                    minHeight = 48.dp,
                )
            },
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val isWide = maxWidth >= 760.dp

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(OemSpacing.lg),
                ) {
                    // Left 36%: Course Menu List
                    Column(
                        modifier = Modifier
                            .weight(0.36f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (isParkingDomain) {
                                parkingCourses.forEach { course ->
                                    CourseListSelectorRow(
                                        title = course.koreanTitle,
                                        subtitle = course.shortSubtitle,
                                        isSelected = selectedScenario == course,
                                        onClick = { selectedScenario = course },
                                    )
                                }
                            } else {
                                drivingGroups.forEach { group ->
                                    val isGroupSelected = selectedScenario in group.relatedScenarios
                                    CourseListSelectorRow(
                                        title = group.titleKorean,
                                        subtitle = group.primaryScenario.shortSubtitle,
                                        isSelected = isGroupSelected,
                                        onClick = { selectedScenario = group.primaryScenario },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OemButton(
                            text = "연습 시작",
                            onClick = { onSelectScenario(selectedScenario) },
                            variant = OemButtonVariant.PRIMARY,
                            minHeight = 64.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Right 64%: Large Course Schematic Stage
                    CoursePreviewStagePanel(
                        scenarioDef = previewScenarioDef,
                        selectedScenario = selectedScenario,
                        subCourses = if (!isParkingDomain) {
                            drivingGroups.firstOrNull { selectedScenario in it.relatedScenarios }?.relatedScenarios.orEmpty()
                        } else {
                            emptyList()
                        },
                        onSelectSubCourse = { selectedScenario = it },
                        modifier = Modifier
                            .weight(0.64f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(OemSpacing.md),
                ) {
                    if (isParkingDomain) {
                        parkingCourses.forEach { course ->
                            CourseListSelectorRow(
                                title = course.koreanTitle,
                                subtitle = course.shortSubtitle,
                                isSelected = selectedScenario == course,
                                onClick = { selectedScenario = course },
                            )
                        }
                    } else {
                        drivingGroups.forEach { group ->
                            val isGroupSelected = selectedScenario in group.relatedScenarios
                            CourseListSelectorRow(
                                title = group.titleKorean,
                                subtitle = group.primaryScenario.shortSubtitle,
                                isSelected = isGroupSelected,
                                onClick = { selectedScenario = group.primaryScenario },
                            )
                        }
                    }

                    CoursePreviewStagePanel(
                        scenarioDef = previewScenarioDef,
                        selectedScenario = selectedScenario,
                        subCourses = emptyList(),
                        onSelectSubCourse = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                    )

                    OemButton(
                        text = "연습 시작",
                        onClick = { onSelectScenario(selectedScenario) },
                        variant = OemButtonVariant.PRIMARY,
                        minHeight = 64.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseListSelectorRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(OemRadius.card)
            .clickable(onClick = onClick),
        color = if (isSelected) OemColors.SurfaceElevated else OemColors.SurfacePrimary,
        shape = OemRadius.card,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) OemColors.AccentPrimary else OemColors.BorderSubtle,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isSelected) OemColors.AccentPrimary else OemColors.Divider),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = OemColors.TextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                    color = if (isSelected) OemColors.TextPrimary.copy(alpha = 0.85f) else OemColors.TextSecondary,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun CoursePreviewStagePanel(
    scenarioDef: ScenarioDefinition,
    selectedScenario: ScenarioType,
    subCourses: List<ScenarioType>,
    onSelectSubCourse: (ScenarioType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = OemColors.SurfaceSecondary,
        shape = OemRadius.largeCanvas,
        border = BorderStroke(1.dp, OemColors.Divider),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Real world-coordinate course visualization with sculpted vehicles
            ScenarioEnvironmentPreviewCanvas(
                scenario = scenarioDef,
                modifier = Modifier.fillMaxSize(),
            )

            // Optional sub-course selector bar when a driving group has multiple variations
            if (subCourses.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    subCourses.forEach { sub ->
                        val active = sub == selectedScenario
                        Surface(
                            modifier = Modifier
                                .clip(OemRadius.button)
                                .clickable { onSelectSubCourse(sub) },
                            color = if (active) OemColors.AccentPrimary else OemColors.SurfacePrimary.copy(alpha = 0.92f),
                            shape = OemRadius.button,
                            border = BorderStroke(1.dp, if (active) OemColors.AccentPrimary else OemColors.Divider),
                        ) {
                            Text(
                                text = sub.koreanTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = if (active) OemColors.TextOnAccent else OemColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }

            // Bottom title & description overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                OemColors.BackgroundPrimary.copy(alpha = 0.90f),
                            ),
                        ),
                    )
                    .padding(horizontal = 26.dp, vertical = 20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = selectedScenario.koreanTitle,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = OemColors.TextPrimary,
                    )
                    Text(
                        text = selectedScenario.shortSubtitle,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        color = OemColors.TextSecondary,
                    )
                }
            }
        }
    }
}

// ============================================================================
// SECTION 12: PRACTICE PREPARATION SCREEN (`PracticeIntroScreen`)
// - Replaces the old text-heavy preparation layout with a focused cockpit launch view:
//   Left 35%: Course title + 1-sentence objective + readiness status + large "연습 시작" CTA
//   Right 65%: Large visualization of the actual course and vehicle starting position
// ============================================================================

@Composable
fun PracticeIntroScreen(
    uiState: PracticeSimulatorUiState,
    onStartPractice: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSteeringDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scenarioType = uiState.activeScenarioType
    val scenario = uiState.scenario
    val isParkedSafe = uiState.isVehicleParkedAndSafeForPractice
    val isSteeringReady = uiState.isSteeringReadyForPractice
    var showHelpModal by remember { mutableStateOf(false) }

    if (showHelpModal) {
        AlertDialog(
            onDismissRequest = { showHelpModal = false },
            containerColor = OemColors.SurfacePrimary,
            titleContentColor = OemColors.TextPrimary,
            textContentColor = OemColors.TextSecondary,
            title = {
                Text(
                    text = "${scenarioType.koreanTitle} 안내",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    scenario.helpStepsKorean.forEachIndexed { idx, step ->
                        val cleaned = step.replace(Regex("^\\d+\\.\\s*"), "")
                        Text(
                            text = "${idx + 1}. $cleaned",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OemColors.TextPrimary,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpModal = false }) {
                    Text(
                        text = "확인",
                        style = MaterialTheme.typography.titleMedium,
                        color = OemColors.AccentPrimary,
                    )
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OemColors.BackgroundPrimary)
            .padding(
                horizontal = OemSpacing.screenHorizontal,
                vertical = OemSpacing.screenVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(OemSpacing.lg),
    ) {
        OemTopBar(
            title = scenarioType.koreanTitle,
            onBack = onBack,
            backLabel = "← 목록",
            trailingContent = {
                OemButton(
                    text = "도움말",
                    onClick = { showHelpModal = true },
                    variant = OemButtonVariant.GHOST,
                    minHeight = 48.dp,
                )
                OemButton(
                    text = "홈",
                    onClick = onHome,
                    variant = OemButtonVariant.GHOST,
                    minHeight = 48.dp,
                )
            },
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val isWide = maxWidth >= 760.dp

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(OemSpacing.lg),
                ) {
                    // Left 35%: Title, 1-sentence goal, readiness status, large "연습 시작" button
                    IntroLeftLaunchColumn(
                        title = scenarioType.koreanTitle,
                        objectiveSentence = scenarioType.shortSubtitle,
                        isParkedSafe = isParkedSafe,
                        isSteeringReady = isSteeringReady,
                        onStartPractice = onStartPractice,
                        onOpenSteeringDiagnostics = onOpenSteeringDiagnostics,
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight(),
                    )

                    // Right 65%: Large visualization of actual course & starting vehicle
                    Surface(
                        modifier = Modifier
                            .weight(0.65f)
                            .fillMaxHeight(),
                        color = OemColors.SurfaceSecondary,
                        shape = OemRadius.largeCanvas,
                        border = BorderStroke(1.dp, OemColors.Divider),
                    ) {
                        OptionalHeroAssetOrFallback(
                            drawableName = "parking_environment",
                            contentDescription = scenarioType.koreanTitle,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            ScenarioEnvironmentPreviewCanvas(
                                scenario = scenario,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(OemSpacing.md),
                ) {
                    IntroLeftLaunchColumn(
                        title = scenarioType.koreanTitle,
                        objectiveSentence = scenarioType.shortSubtitle,
                        isParkedSafe = isParkedSafe,
                        isSteeringReady = isSteeringReady,
                        onStartPractice = onStartPractice,
                        onOpenSteeringDiagnostics = onOpenSteeringDiagnostics,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        color = OemColors.SurfaceSecondary,
                        shape = OemRadius.largeCanvas,
                        border = BorderStroke(1.dp, OemColors.Divider),
                    ) {
                        ScenarioEnvironmentPreviewCanvas(
                            scenario = scenario,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroLeftLaunchColumn(
    title: String,
    objectiveSentence: String,
    isParkedSafe: Boolean,
    isSteeringReady: Boolean,
    onStartPractice: () -> Unit,
    onOpenSteeringDiagnostics: () -> Unit,
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
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = OemColors.TextPrimary,
                    )
                    Text(
                        text = objectiveSentence,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 19.sp,
                            lineHeight = 28.sp,
                        ),
                        color = OemColors.TextSecondary,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(OemColors.Divider),
                )

                // Minimal readiness check items
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ReadinessStatusRow(
                        label = "주차 상태 확인",
                        statusText = if (isParkedSafe) "확인됨" else "P단 필요",
                        isReady = isParkedSafe,
                    )
                    ReadinessStatusRow(
                        label = "핸들 신호 연결",
                        statusText = if (isSteeringReady) "핸들 연결됨" else "확인 필요",
                        isReady = isSteeringReady,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isSteeringReady) {
                    OemButton(
                        text = "핸들 연결 확인",
                        onClick = onOpenSteeringDiagnostics,
                        variant = OemButtonVariant.SECONDARY,
                        minHeight = 56.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OemButton(
                    text = "연습 시작",
                    onClick = onStartPractice,
                    variant = OemButtonVariant.PRIMARY,
                    minHeight = 64.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ReadinessStatusRow(
    label: String,
    statusText: String,
    isReady: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isReady) OemColors.AccentPrimary else OemColors.StatusAlert),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                color = OemColors.TextPrimary,
            )
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (isReady) OemColors.TextSecondary else OemColors.StatusAlert,
        )
    }
}

/**
 * Large Course Visualization Canvas (Used in Course Selection & Preparation Screens)
 * - Renders the actual scenario geometry, sculpted parked cars, target zone,
 *   reference trajectory, and starting ego vehicle without any grid clutter.
 */
@Composable
fun ScenarioEnvironmentPreviewCanvas(
    scenario: ScenarioDefinition,
    modifier: Modifier = Modifier,
) {
    val config = remember { VehicleConfig() }
    Canvas(modifier = modifier) {
        val transform = WorldViewportTransform(
            worldBounds = scenario.worldBounds,
            viewportWidthPx = size.width,
            viewportHeightPx = size.height,
            paddingPx = 36.dp.toPx(),
            zoomFactor = 1.04f,
        )

        // 1. Road surface polygons
        scenario.roadPolygons.forEach { poly ->
            if (poly.size >= 3) {
                val path = Path()
                poly.forEachIndexed { idx, pt ->
                    val sp = transform.worldToScreen(pt)
                    if (idx == 0) path.moveTo(sp.x, sp.y) else path.lineTo(sp.x, sp.y)
                }
                path.close()
                drawPath(path = path, color = SimRoadSurface, style = Fill)
                drawPath(
                    path = path,
                    color = OemColors.Divider,
                    style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round),
                )
            }
        }

        // 2. Lane and parking space lines
        scenario.roadLines.forEach { seg ->
            val p1 = transform.worldToScreen(seg.start)
            val p2 = transform.worldToScreen(seg.end)
            val color = when {
                seg.isCenterLine -> SimWarning.copy(alpha = 0.75f)
                seg.isParkingLine -> SimParkingLine
                else -> SimParkingLine.copy(alpha = 0.72f)
            }
            drawLine(
                color = color,
                start = Offset(p1.x, p1.y),
                end = Offset(p2.x, p2.y),
                strokeWidth = if (seg.isParkingLine) 3.dp.toPx() else 2.2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (seg.isDashed) PathEffect.dashPathEffect(floatArrayOf(18f, 14f)) else null,
            )
        }

        // 3. Target zone
        val targetCorners = scenario.targetZone.corners()
        if (targetCorners.size >= 3) {
            val path = Path()
            targetCorners.forEachIndexed { idx, pt ->
                val sp = transform.worldToScreen(pt)
                if (idx == 0) path.moveTo(sp.x, sp.y) else path.lineTo(sp.x, sp.y)
            }
            path.close()
            drawPath(path = path, color = SimTargetZoneFill, style = Fill)
            drawPath(
                path = path,
                color = SimPrimaryAccent.copy(alpha = 0.85f),
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                ),
            )
        }

        // 4. Reference entry path
        val ref = scenario.referencePath
        if (ref is ReferencePathResult.Available && ref.rearAxlePoints.size >= 2) {
            val path = Path()
            ref.rearAxlePoints.forEachIndexed { idx, pt ->
                val sp = transform.worldToScreen(pt)
                if (idx == 0) path.moveTo(sp.x, sp.y) else path.lineTo(sp.x, sp.y)
            }
            drawPath(
                path = path,
                color = SimPathPredicted,
                style = Stroke(
                    width = 3.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
                ),
            )
        }

        // 5. Obstacles (Sculpted parked vehicles & curbs)
        scenario.obstacles.forEach { obs ->
            when (obs.role) {
                ObstacleRole.FRONT_PARKED_CAR,
                ObstacleRole.REAR_PARKED_CAR,
                ObstacleRole.LEFT_PARKED_CAR,
                ObstacleRole.RIGHT_PARKED_CAR -> {
                    val centerSp = transform.worldToScreen(obs.center)
                    drawSculptedTopDownVehicle(
                        center = Offset(centerSp.x, centerSp.y),
                        lengthPx = transform.metersToPixels(obs.halfLength * 2f),
                        widthPx = transform.metersToPixels(obs.halfWidth * 2f),
                        headingDegreesScreen = transform.worldAngleToScreenDegrees(obs.angleRad),
                        isEgoVehicle = false,
                    )
                }
                else -> {
                    val corners = obs.corners()
                    if (corners.size >= 3) {
                        val path = Path()
                        corners.forEachIndexed { idx, pt ->
                            val sp = transform.worldToScreen(pt)
                            if (idx == 0) path.moveTo(sp.x, sp.y) else path.lineTo(sp.x, sp.y)
                        }
                        path.close()
                        drawPath(path = path, color = SimCurbFill, style = Fill)
                        drawPath(path = path, color = SimCurbStroke, style = Stroke(width = 1.8.dp.toPx()))
                    }
                }
            }
        }

        // 6. Starting Ego Vehicle (Sculpted top-down vehicle)
        val initFp = com.drivecoach.app.simulation.geometry.VehicleGeometry.computeFootprint(
            scenario.initialState,
            config,
        )
        val egoCenterSp = transform.worldToScreen(initFp.bodyCenter)
        val totalLen = config.rearAxleToFrontBumperMeters + config.rearAxleToRearBumperMeters
        drawSculptedTopDownVehicle(
            center = Offset(egoCenterSp.x, egoCenterSp.y),
            lengthPx = transform.metersToPixels(totalLen),
            widthPx = transform.metersToPixels(config.sanitizedWidthMeters),
            headingDegreesScreen = transform.worldAngleToScreenDegrees(scenario.initialState.headingRad),
            isEgoVehicle = true,
        )
    }
}

// ============================================================================
// Practice Result Screen (`PracticeResultScreen`)
// ============================================================================

@Composable
fun PracticeResultScreen(
    uiState: PracticeSimulatorUiState,
    onRetry: () -> Unit,
    onNextPractice: () -> Unit,
    onBackToCategory: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSuccess = uiState.evaluation.status == EvaluationStatus.SUCCESS
    val summaryText = "${uiState.evaluation.headlineKorean} — ${uiState.evaluation.detailKorean}"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OemColors.BackgroundPrimary)
            .padding(
                horizontal = OemSpacing.screenHorizontal,
                vertical = OemSpacing.screenVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(OemSpacing.lg),
    ) {
        OemTopBar(
            title = "연습 결과",
            subtitle = uiState.activeScenarioType.koreanTitle,
            onBack = onBackToCategory,
            backLabel = "← 목록",
            trailingContent = {
                OemButton(
                    text = "홈",
                    onClick = onHome,
                    variant = OemButtonVariant.GHOST,
                    minHeight = 48.dp,
                )
            },
        )

        OemResultSummaryCard(
            scenarioTitle = uiState.activeScenarioType.koreanTitle,
            categoryTitle = uiState.activeScenarioType.category.koreanTitle,
            isCompletedSuccess = isSuccess,
            statusSummary = summaryText,
            keyTakeaways = uiState.scenario.keyTakeawaysKorean,
            onRetry = onRetry,
            onNextPractice = onNextPractice,
            onBackToCategory = onBackToCategory,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

// ============================================================================
// Vehicle Specification & Safety Settings Screen (`SimulatorSettingsScreen`)
// ============================================================================

@Composable
fun SimulatorSettingsScreen(
    vehicleConfig: VehicleConfig,
    spaceOptions: ScenarioSpaceOptions,
    diagnosticState: PleosSdkDiagnosticState,
    isDrivingRestrictionSimulated: Boolean,
    showRearAxleDot: Boolean,
    onApplyConfig: (VehicleConfig, ScenarioSpaceOptions) -> Unit,
    onToggleRearAxleDot: () -> Unit,
    onToggleDrivingRestrictionSimulation: () -> Unit,
    onRefreshPleosState: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lengthM by remember(vehicleConfig) { mutableFloatStateOf(vehicleConfig.lengthMeters) }
    var widthM by remember(vehicleConfig) { mutableFloatStateOf(vehicleConfig.widthMeters) }
    var wheelbaseM by remember(vehicleConfig) { mutableFloatStateOf(vehicleConfig.wheelbaseMeters) }
    var maxSteerDeg by remember(vehicleConfig) { mutableFloatStateOf(vehicleConfig.maxSteeringAngleDeg) }
    var parallelBayLenM by remember(spaceOptions) { mutableFloatStateOf(spaceOptions.parallelBayLengthMeters) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OemColors.BackgroundPrimary)
            .padding(
                horizontal = OemSpacing.screenHorizontal,
                vertical = OemSpacing.screenVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(OemSpacing.md),
    ) {
        OemTopBar(
            title = "설정",
            onBack = onBack,
            backLabel = "← 이전",
            trailingContent = {
                OemButton(
                    text = "홈",
                    onClick = onHome,
                    variant = OemButtonVariant.GHOST,
                    minHeight = 48.dp,
                )
                OemButton(
                    text = "기본값 복원",
                    onClick = {
                        val defaultCfg = VehicleConfig()
                        val defaultOpts = ScenarioSpaceOptions()
                        lengthM = defaultCfg.lengthMeters
                        widthM = defaultCfg.widthMeters
                        wheelbaseM = defaultCfg.wheelbaseMeters
                        maxSteerDeg = defaultCfg.maxSteeringAngleDeg
                        parallelBayLenM = defaultOpts.parallelBayLengthMeters
                        onApplyConfig(defaultCfg, defaultOpts)
                    },
                    variant = OemButtonVariant.SECONDARY,
                    minHeight = 48.dp,
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OemSpacing.md),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = OemColors.SurfacePrimary,
                shape = OemRadius.card,
                border = BorderStroke(1.dp, OemColors.Divider),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "차량 제원 및 주차 공간 크기",
                        style = MaterialTheme.typography.titleLarge,
                        color = OemColors.TextPrimary,
                    )

                    ConfigSliderItem(
                        label = "차량 전장",
                        valueText = String.format(Locale.US, "%.2f m", lengthM),
                        value = lengthM,
                        valueRange = 3.8f..5.2f,
                        onValueChange = { lengthM = it },
                    )
                    ConfigSliderItem(
                        label = "차량 전폭",
                        valueText = String.format(Locale.US, "%.2f m", widthM),
                        value = widthM,
                        valueRange = 1.6f..2.1f,
                        onValueChange = { widthM = it },
                    )
                    ConfigSliderItem(
                        label = "축거 (Wheelbase)",
                        valueText = String.format(Locale.US, "%.2f m", wheelbaseM),
                        value = wheelbaseM,
                        valueRange = 2.3f..3.1f,
                        onValueChange = { wheelbaseM = it },
                    )
                    ConfigSliderItem(
                        label = "최대 전륜 조향각",
                        valueText = String.format(Locale.US, "%.0f°", maxSteerDeg),
                        value = maxSteerDeg,
                        valueRange = 24f..38f,
                        onValueChange = { maxSteerDeg = it },
                    )
                    ConfigSliderItem(
                        label = "평행주차 구획 길이",
                        valueText = String.format(Locale.US, "%.2f m", parallelBayLenM),
                        value = parallelBayLenM,
                        valueRange = 5.2f..7.5f,
                        onValueChange = { parallelBayLenM = it },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OemButton(
                            text = if (showRearAxleDot) "후륜 기준점 표시 중" else "후륜 기준점 표시",
                            onClick = onToggleRearAxleDot,
                            variant = OemButtonVariant.SECONDARY,
                        )

                        OemButton(
                            text = "적용",
                            onClick = {
                                val overhang = ((lengthM - wheelbaseM).coerceAtLeast(0.8f)) * 0.5f
                                onApplyConfig(
                                    vehicleConfig.copy(
                                        lengthMeters = lengthM,
                                        widthMeters = widthM,
                                        wheelbaseMeters = wheelbaseM,
                                        frontOverhangMeters = overhang,
                                        rearOverhangMeters = overhang,
                                        maxSteeringAngleDeg = maxSteerDeg,
                                    ),
                                    spaceOptions.copy(parallelBayLengthMeters = parallelBayLenM),
                                )
                            },
                            variant = OemButtonVariant.PRIMARY,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = OemColors.SurfacePrimary,
                shape = OemRadius.card,
                border = BorderStroke(1.dp, OemColors.Divider),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "주차 상태 안전 제한",
                            style = MaterialTheme.typography.titleLarge,
                            color = OemColors.TextPrimary,
                        )
                        OemStatusChip(
                            text = if (isDrivingRestrictionSimulated) "제한 화면 테스트 중" else "주차 상태 확인",
                            tone = if (isDrivingRestrictionSimulated) OemSemanticTone.WARNING else OemSemanticTone.NEUTRAL,
                        )
                    }

                    Text(
                        text = "Pleos Vehicle SDK (${diagnosticState.sdkArtifactVersion}) · 감지 기어: ${diagnosticState.currentGear.koreanName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OemColors.TextSecondary,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OemButton(
                            text = if (isDrivingRestrictionSimulated) "주차 상태로 복귀" else "주행 제한 화면 미리보기",
                            onClick = onToggleDrivingRestrictionSimulation,
                            variant = OemButtonVariant.SECONDARY,
                        )
                        OemButton(
                            text = "SDK 상태 새로고침",
                            onClick = onRefreshPleosState,
                            variant = OemButtonVariant.GHOST,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigSliderItem(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = OemColors.TextPrimary)
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                color = OemColors.AccentPrimary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = OemColors.AccentPrimary,
                activeTrackColor = OemColors.AccentPrimary,
            ),
        )
    }
}

// ============================================================================
// SECTION 20: COMPOSE PREVIEWS FOR VISUAL VERIFICATION (1280x720)
// ============================================================================

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewHomeParkingScreen() {
    DriveCoachTheme {
        DriveCoachHomeScreen(
            isParkedAndSafe = true,
            isSteeringConnected = true,
            initialSelectedDomain = HomePrimaryDomain.PARKING,
            onSelectCategory = {},
            onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewHomeDrivingScreen() {
    DriveCoachTheme {
        DriveCoachHomeScreen(
            isParkedAndSafe = true,
            isSteeringConnected = true,
            initialSelectedDomain = HomePrimaryDomain.DRIVING,
            onSelectCategory = {},
            onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewParkingCategoryScreen() {
    DriveCoachTheme {
        CategorySelectionScreen(
            category = PracticeCategory.PARKING,
            onSelectScenario = {},
            onSwitchCategory = {},
            onBackToHome = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewDrivingCategoryScreen() {
    DriveCoachTheme {
        CategorySelectionScreen(
            category = PracticeCategory.DRIVING,
            onSelectScenario = {},
            onSwitchCategory = {},
            onBackToHome = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewParallelParkingIntroScreen() {
    DriveCoachTheme {
        PracticeIntroScreen(
            uiState = PracticeSimulatorUiState(
                activeScenarioType = ScenarioType.PARALLEL_PARKING,
            ),
            onStartPractice = {},
            onBack = {},
            onHome = {},
            onOpenSteeringDiagnostics = {},
        )
    }
}
