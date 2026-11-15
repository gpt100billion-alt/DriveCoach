package com.drivecoach.app.ui.simulator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecoach.app.data.vehicle.PleosSdkDiagnosticState
import com.drivecoach.app.domain.model.GearPosition
import com.drivecoach.app.simulation.collision.EvaluationStatus
import com.drivecoach.app.simulation.geometry.ObstacleRole
import com.drivecoach.app.simulation.geometry.OrientedBox2D
import com.drivecoach.app.simulation.geometry.Vec2
import com.drivecoach.app.simulation.geometry.VehicleFootprint
import com.drivecoach.app.simulation.geometry.VehicleGeometry
import com.drivecoach.app.simulation.geometry.WorldViewportTransform
import com.drivecoach.app.simulation.scenarios.PracticeCategory
import com.drivecoach.app.simulation.scenarios.ReferencePathResult
import com.drivecoach.app.simulation.scenarios.ScenarioSpaceOptions
import com.drivecoach.app.simulation.scenarios.ScenarioType
import com.drivecoach.app.simulation.scenarios.SimulatorScenarios
import com.drivecoach.app.simulation.steering.SteeringCalibrationConfig
import com.drivecoach.app.simulation.steering.SteeringConnectionState
import com.drivecoach.app.simulation.steering.SteeringDirectionLabel
import com.drivecoach.app.simulation.steering.SteeringInputSourceMode
import com.drivecoach.app.simulation.steering.SteeringTelemetrySnapshot
import com.drivecoach.app.simulation.vehicle.VehicleConfig
import com.drivecoach.app.simulation.vehicle.VirtualGearDirection
import com.drivecoach.app.ui.designsystem.OemButton
import com.drivecoach.app.ui.designsystem.OemButtonVariant
import com.drivecoach.app.ui.designsystem.OemPracticeGuidanceBanner
import com.drivecoach.app.ui.designsystem.OemRadius
import com.drivecoach.app.ui.designsystem.OemSemanticTone
import com.drivecoach.app.ui.designsystem.OemStatusChip
import com.drivecoach.app.ui.designsystem.OemSteeringCuePanel
import com.drivecoach.app.ui.designsystem.OemTopBar
import com.drivecoach.app.ui.designsystem.drawSculptedTopDownVehicle
import com.drivecoach.app.ui.theme.DriveCoachTheme
import com.drivecoach.app.ui.theme.OemColors
import com.drivecoach.app.ui.theme.SimCollision
import com.drivecoach.app.ui.theme.SimCurbFill
import com.drivecoach.app.ui.theme.SimCurbStroke
import com.drivecoach.app.ui.theme.SimMainBackground
import com.drivecoach.app.ui.theme.SimParkingLine
import com.drivecoach.app.ui.theme.SimPathHistory
import com.drivecoach.app.ui.theme.SimPathPredicted
import com.drivecoach.app.ui.theme.SimPathReference
import com.drivecoach.app.ui.theme.SimPrimaryAccent
import com.drivecoach.app.ui.theme.SimRoadSurface
import com.drivecoach.app.ui.theme.SimTargetZoneActive
import com.drivecoach.app.ui.theme.SimTargetZoneFill
import com.drivecoach.app.ui.theme.SimWarning
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * DriveCoach 3.0 Root Navigation Host
 * - Preserves all Pleos SDK telemetry, kinematic bicycle physics, OBB collision, and P-gear safety lock.
 */
@Composable
fun DriveCoachSimulatorAppRoot(
    uiState: PracticeSimulatorUiState,
    diagnosticState: PleosSdkDiagnosticState,
    onSelectCategory: (PracticeCategory) -> Unit,
    onSelectScenario: (ScenarioType) -> Unit,
    onStartPracticeFromIntro: () -> Unit = {},
    onOpenPracticeResult: () -> Unit = {},
    onRetryFromResult: () -> Unit = {},
    onStartNextScenario: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSteeringDiagnostics: () -> Unit,
    onOpenSimulatorDirect: () -> Unit,
    onSelectGear: (VirtualGearDirection) -> Unit,
    onSetHoldMoving: (Boolean) -> Unit,
    onToggleLatchMoving: () -> Unit,
    onStopMoving: () -> Unit,
    onResetScenario: () -> Unit,
    onToggleReferencePath: () -> Unit,
    onToggleHelpDialog: (Boolean) -> Unit,
    onToggleRearAxleDot: () -> Unit,
    onAdjustZoom: (Float) -> Unit,
    onUpdateConfig: (VehicleConfig, ScenarioSpaceOptions) -> Unit,
    onToggleDrivingRestrictionSimulation: () -> Unit,
    onRefreshPleosState: () -> Unit,
    onCalibrateSteeringZero: () -> Unit,
    onResetSteeringCalibration: () -> Unit,
    onUpdateSteeringCalibration: (SteeringCalibrationConfig) -> Unit,
    onSwitchSteeringSourceMode: (SteeringInputSourceMode) -> Unit,
    onInjectDiagnosticSteeringAngle: (Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val liveGear = uiState.steeringTelemetry.currentGear
    val isLiveVehicleDriving = liveGear == GearPosition.DRIVE ||
        liveGear == GearPosition.REVERSE ||
        liveGear == GearPosition.NEUTRAL ||
        !uiState.steeringTelemetry.isVehicleStationaryVerified
    val isDrivingRestricted = uiState.isRealVehicleDrivingTestRestriction || isLiveVehicleDriving

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(SimMainBackground),
        containerColor = SimMainBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isDrivingRestricted &&
                uiState.currentRoute != SimulatorScreenRoute.SETTINGS &&
                uiState.currentRoute != SimulatorScreenRoute.STEERING_DIAGNOSTICS
            ) {
                ParkedOnlySafetyNoticeScreen(
                    reasonText = uiState.steeringTelemetry.safetyStatusReasonKorean,
                    isSimulationOverride = uiState.isRealVehicleDrivingTestRestriction,
                    onSwitchToParkedTestMode = onToggleDrivingRestrictionSimulation,
                    onOpenSteeringDiagnostics = onOpenSteeringDiagnostics,
                    onOpenSettings = onOpenSettings,
                )
            } else {
                when (uiState.currentRoute) {
                    SimulatorScreenRoute.HOME -> {
                        DriveCoachHomeScreen(
                            isParkedAndSafe = uiState.isVehicleParkedAndSafeForPractice,
                            isSteeringConnected = uiState.isSteeringReadyForPractice,
                            onSelectCategory = onSelectCategory,
                            onOpenSettings = onOpenSettings,
                            onOpenSteeringDiagnostics = onOpenSteeringDiagnostics,
                        )
                    }

                    SimulatorScreenRoute.CATEGORY_LIST -> {
                        CategorySelectionScreen(
                            category = uiState.selectedCategory,
                            onSelectScenario = onSelectScenario,
                            onSwitchCategory = onSelectCategory,
                            onBackToHome = onOpenHome,
                        )
                    }

                    SimulatorScreenRoute.PRACTICE_INTRO -> {
                        PracticeIntroScreen(
                            uiState = uiState,
                            onStartPractice = onStartPracticeFromIntro,
                            onBack = onNavigateBack,
                            onHome = onOpenHome,
                            onOpenSteeringDiagnostics = onOpenSteeringDiagnostics,
                        )
                    }

                    SimulatorScreenRoute.SIMULATOR -> {
                        SimulatorPracticeScreen(
                            uiState = uiState,
                            onBack = onNavigateBack,
                            onHome = onOpenHome,
                            onOpenSettings = onOpenSettings,
                            onOpenSteeringDiagnostics = onOpenSteeringDiagnostics,
                            onOpenPracticeResult = onOpenPracticeResult,
                            onCalibrateSteeringZero = onCalibrateSteeringZero,
                            onSelectGear = onSelectGear,
                            onSetHoldMoving = onSetHoldMoving,
                            onToggleLatchMoving = onToggleLatchMoving,
                            onStopMoving = onStopMoving,
                            onResetScenario = onResetScenario,
                            onToggleReferencePath = onToggleReferencePath,
                            onToggleHelpDialog = onToggleHelpDialog,
                            onAdjustZoom = onAdjustZoom,
                        )
                    }

                    SimulatorScreenRoute.PRACTICE_RESULT -> {
                        PracticeResultScreen(
                            uiState = uiState,
                            onRetry = onRetryFromResult,
                            onNextPractice = onStartNextScenario,
                            onBackToCategory = {
                                onSelectCategory(uiState.activeScenarioType.category)
                            },
                            onHome = onOpenHome,
                        )
                    }

                    SimulatorScreenRoute.STEERING_DIAGNOSTICS -> {
                        SteeringDiagnosticsScreen(
                            telemetry = uiState.steeringTelemetry,
                            onBack = onNavigateBack,
                            onHome = onOpenHome,
                            onOpenSimulator = onOpenSimulatorDirect,
                            onRefreshSdk = onRefreshPleosState,
                            onCalibrateZeroNow = onCalibrateSteeringZero,
                            onResetCalibration = onResetSteeringCalibration,
                            onUpdateCalibration = onUpdateSteeringCalibration,
                            onSwitchInputSourceMode = onSwitchSteeringSourceMode,
                            onInjectDiagnosticAngle = onInjectDiagnosticSteeringAngle,
                        )
                    }

                    SimulatorScreenRoute.SETTINGS -> {
                        SimulatorSettingsScreen(
                            vehicleConfig = uiState.vehicleConfig,
                            spaceOptions = uiState.spaceOptions,
                            diagnosticState = diagnosticState,
                            isDrivingRestrictionSimulated = uiState.isRealVehicleDrivingTestRestriction,
                            showRearAxleDot = uiState.showRearAxleReferenceDot,
                            onApplyConfig = onUpdateConfig,
                            onToggleRearAxleDot = onToggleRearAxleDot,
                            onToggleDrivingRestrictionSimulation = onToggleDrivingRestrictionSimulation,
                            onRefreshPleosState = onRefreshPleosState,
                            onBack = onNavigateBack,
                            onHome = onOpenHome,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Parked-Only Safety Restriction Screen (`ParkedOnlySafetyNoticeScreen`)
 */
@Composable
private fun ParkedOnlySafetyNoticeScreen(
    reasonText: String,
    isSimulationOverride: Boolean,
    onSwitchToParkedTestMode: () -> Unit,
    onOpenSteeringDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OemColors.SurfaceDarkRestricted)
            .padding(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = OemColors.SurfaceDarkCard,
            shape = OemRadius.card,
            border = BorderStroke(1.dp, OemColors.Divider),
        ) {
            Column(
                modifier = Modifier
                    .width(640.dp)
                    .padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                OemStatusChip(
                    text = "주차 상태 필요",
                    tone = OemSemanticTone.WARNING,
                )

                Text(
                    text = "주차 상태에서만 연습할 수 있습니다.",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = OemColors.TextOnDark,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = reasonText.ifBlank { "차량을 정차하고 기어를 P단에 둔 상태에서 이용해 주세요." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = OemColors.TextOnDarkSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (isSimulationOverride) {
                        OemButton(
                            text = "주차 상태로 복귀",
                            onClick = onSwitchToParkedTestMode,
                            variant = OemButtonVariant.PRIMARY,
                        )
                    }
                    OemButton(
                        text = "핸들 진단",
                        onClick = onOpenSteeringDiagnostics,
                        variant = OemButtonVariant.SECONDARY,
                    )
                    OemButton(
                        text = "설정",
                        onClick = onOpenSettings,
                        variant = OemButtonVariant.GHOST,
                    )
                }
            }
        }
    }
}

// ============================================================================
// SECTION 13, 14, 15, 16: ACTIVE PRACTICE SIMULATOR SCREEN (`SimulatorPracticeScreen`)
// - Simulation Viewport: ~77% of available width, clean dark surface (NO grid clutter)
// - Control Panel: ~23% of available width, focused on:
//   1) Current practice name
//   2) Steering connection & current practice state ("핸들 연결됨" / "차량 이동 중" / "정지" / "주차 완료")
//   3) Graphical steering direction indicator ("좌회전" / "중앙" / "우회전")
//   4) Virtual Forward / Reverse ("전진" / "후진")
//   5) Move ("이동"), Stop ("정지"), Reset ("다시 시작")
// ============================================================================

@Composable
fun SimulatorPracticeScreen(
    uiState: PracticeSimulatorUiState,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSteeringDiagnostics: () -> Unit,
    onOpenPracticeResult: () -> Unit = {},
    onCalibrateSteeringZero: () -> Unit,
    onSelectGear: (VirtualGearDirection) -> Unit,
    onSetHoldMoving: (Boolean) -> Unit,
    onToggleLatchMoving: () -> Unit,
    onStopMoving: () -> Unit,
    onResetScenario: () -> Unit,
    onToggleReferencePath: () -> Unit,
    onToggleHelpDialog: (Boolean) -> Unit,
    onAdjustZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.showHelpDialog) {
        AlertDialog(
            onDismissRequest = { onToggleHelpDialog(false) },
            containerColor = OemColors.SurfacePrimary,
            titleContentColor = OemColors.TextPrimary,
            textContentColor = OemColors.TextSecondary,
            title = {
                Text(
                    text = "${uiState.activeScenarioType.koreanTitle} 안내",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.scenario.helpStepsKorean.forEachIndexed { idx, step ->
                        val cleaned = step.replace(Regex("^\\d+\\.\\s*"), "")
                        Text(
                            text = "${idx + 1}. $cleaned",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OemColors.TextPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "핸들 영점 보정이나 신호 진단은 우측 상단 '핸들 진단'에서 설정할 수 있습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OemColors.TextSecondary,
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = {
                        onToggleHelpDialog(false)
                        onCalibrateSteeringZero()
                    }) {
                        Text("현재 핸들 영점 맞추기", color = OemColors.TextSecondary)
                    }
                    TextButton(onClick = { onToggleHelpDialog(false) }) {
                        Text("확인", color = OemColors.AccentPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OemColors.BackgroundPrimary)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Minimal Header Bar
        OemTopBar(
            title = uiState.activeScenarioType.koreanTitle,
            onBack = onBack,
            backLabel = "← 이전",
            trailingContent = {
                OemButton(
                    text = if (uiState.showReferencePath) "경로 숨기기" else "경로 보기",
                    onClick = onToggleReferencePath,
                    variant = if (uiState.showReferencePath) OemButtonVariant.SECONDARY else OemButtonVariant.GHOST,
                    minHeight = 46.dp,
                )
                OemButton(
                    text = "도움말",
                    onClick = { onToggleHelpDialog(true) },
                    variant = OemButtonVariant.GHOST,
                    minHeight = 46.dp,
                )
                OemButton(
                    text = "핸들 진단",
                    onClick = onOpenSteeringDiagnostics,
                    variant = if (!uiState.isSteeringReadyForPractice) OemButtonVariant.ALERT else OemButtonVariant.GHOST,
                    minHeight = 46.dp,
                )
                OemButton(
                    text = "설정",
                    onClick = onOpenSettings,
                    variant = OemButtonVariant.GHOST,
                    minHeight = 46.dp,
                )
                OemButton(
                    text = "홈",
                    onClick = onHome,
                    variant = OemButtonVariant.GHOST,
                    minHeight = 46.dp,
                )
            },
        )

        // Main Split Layout: 77% Simulation Viewport + 23% Control Dock
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val isWide = maxWidth >= 820.dp

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SimulationCanvasContainer(
                        uiState = uiState,
                        onAdjustZoom = onAdjustZoom,
                        onResetScenario = onResetScenario,
                        onOpenPracticeResult = onOpenPracticeResult,
                        onOpenSteeringDiagnostics = onOpenSteeringDiagnostics,
                        modifier = Modifier
                            .weight(0.77f)
                            .fillMaxHeight(),
                    )

                    SimulatorControlPanel(
                        uiState = uiState,
                        onOpenPracticeResult = onOpenPracticeResult,
                        onSelectGear = onSelectGear,
                        onSetHoldMoving = onSetHoldMoving,
                        onToggleLatchMoving = onToggleLatchMoving,
                        onStopMoving = onStopMoving,
                        onResetScenario = onResetScenario,
                        modifier = Modifier
                            .weight(0.23f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SimulationCanvasContainer(
                        uiState = uiState,
                        onAdjustZoom = onAdjustZoom,
                        onResetScenario = onResetScenario,
                        onOpenPracticeResult = onOpenPracticeResult,
                        onOpenSteeringDiagnostics = onOpenSteeringDiagnostics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.64f),
                    )

                    SimulatorControlPanel(
                        uiState = uiState,
                        onOpenPracticeResult = onOpenPracticeResult,
                        onSelectGear = onSelectGear,
                        onSetHoldMoving = onSetHoldMoving,
                        onToggleLatchMoving = onToggleLatchMoving,
                        onStopMoving = onStopMoving,
                        onResetScenario = onResetScenario,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.36f),
                    )
                }
            }
        }
    }
}

/**
 * Simulation Viewport (`SimulationCanvasContainer`) — Section 13 & 14
 * - Clean #101114 surface without decorative technical grid lines
 * - Renders only: Ego vehicle, road & parking space, surrounding vehicles, target zone,
 *   predicted trajectory, and driven trajectory.
 */
@Composable
private fun SimulationCanvasContainer(
    uiState: PracticeSimulatorUiState,
    onAdjustZoom: (Float) -> Unit,
    onResetScenario: () -> Unit,
    onOpenPracticeResult: () -> Unit,
    onOpenSteeringDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = OemColors.SurfaceViewport,
        shape = OemRadius.largeCanvas,
        border = BorderStroke(1.dp, OemColors.Divider),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val transform = WorldViewportTransform(
                    worldBounds = uiState.scenario.worldBounds,
                    viewportWidthPx = size.width,
                    viewportHeightPx = size.height,
                    paddingPx = 32.dp.toPx(),
                    zoomFactor = uiState.zoomFactor,
                )

                // 1. Road surface polygons
                uiState.scenario.roadPolygons.forEach { poly ->
                    drawWorldPolygon(
                        pointsWorld = poly,
                        transform = transform,
                        fillColor = SimRoadSurface,
                        strokeColor = OemColors.Divider,
                        strokeWidthPx = 1.6.dp.toPx(),
                    )
                }

                // 2. Lane lines, centerlines, and parking space lines
                uiState.scenario.roadLines.forEach { seg ->
                    val p1 = transform.worldToScreen(seg.start)
                    val p2 = transform.worldToScreen(seg.end)
                    val color = when {
                        seg.isCenterLine -> SimWarning.copy(alpha = 0.72f)
                        seg.isParkingLine -> SimParkingLine
                        else -> SimParkingLine.copy(alpha = 0.76f)
                    }
                    val strokeW = if (seg.isParkingLine) 3.dp.toPx() else 2.2.dp.toPx()
                    val effect = if (seg.isDashed) {
                        PathEffect.dashPathEffect(floatArrayOf(18f, 14f))
                    } else {
                        null
                    }
                    drawLine(
                        color = color,
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round,
                        pathEffect = effect,
                    )
                }

                // 3. Target parking / goal zone
                val targetFill = if (uiState.evaluation.status == EvaluationStatus.SUCCESS) {
                    SimPrimaryAccent.copy(alpha = 0.24f)
                } else if (uiState.evaluation.isInsideTargetZone) {
                    SimTargetZoneActive
                } else {
                    SimTargetZoneFill
                }
                val targetStroke = if (uiState.evaluation.status == EvaluationStatus.SUCCESS) {
                    SimPrimaryAccent
                } else {
                    SimPrimaryAccent.copy(alpha = 0.75f)
                }
                drawWorldPolygon(
                    pointsWorld = uiState.scenario.targetZone.corners(),
                    transform = transform,
                    fillColor = targetFill,
                    strokeColor = targetStroke,
                    strokeWidthPx = 2.4.dp.toPx(),
                    dashed = true,
                )

                // 4. Optional Reference Path (Dotted translucent #5D8EFF)
                if (uiState.showReferencePath) {
                    val ref = uiState.scenario.referencePath
                    if (ref is ReferencePathResult.Available) {
                        drawWorldPolyline(
                            pointsWorld = ref.rearAxlePoints,
                            transform = transform,
                            color = SimPathReference,
                            strokeWidthPx = 2.4.dp.toPx(),
                            dashPattern = floatArrayOf(6f, 10f),
                        )
                    }
                }

                // 5. Surrounding obstacles (Sculpted parked vehicles & curbs)
                uiState.scenario.obstacles.forEach { obs ->
                    val isHit = uiState.collisionResult.hitObstacle?.id == obs.id
                    when (obs.role) {
                        ObstacleRole.FRONT_PARKED_CAR,
                        ObstacleRole.REAR_PARKED_CAR,
                        ObstacleRole.LEFT_PARKED_CAR,
                        ObstacleRole.RIGHT_PARKED_CAR -> {
                            drawParkedObstacleVehicle(
                                box = obs,
                                transform = transform,
                                isHit = isHit,
                            )
                        }
                        ObstacleRole.CURB -> {
                            drawWorldPolygon(
                                pointsWorld = obs.corners(),
                                transform = transform,
                                fillColor = if (isHit) SimCollision.copy(alpha = 0.45f) else SimCurbFill,
                                strokeColor = if (isHit) SimCollision else SimCurbStroke,
                                strokeWidthPx = 2.dp.toPx(),
                            )
                        }
                        else -> {
                            drawWorldPolygon(
                                pointsWorld = obs.corners(),
                                transform = transform,
                                fillColor = if (isHit) SimCollision.copy(alpha = 0.45f) else SimWarning.copy(alpha = 0.25f),
                                strokeColor = if (isHit) SimCollision else SimWarning,
                                strokeWidthPx = 2.dp.toPx(),
                            )
                        }
                    }
                }

                // 6. Driven Trajectory (Solid neutral silver-gray trail)
                if (uiState.drivenRearAxlePath.size >= 2) {
                    drawWorldPolyline(
                        pointsWorld = uiState.drivenRearAxlePath,
                        transform = transform,
                        color = SimPathHistory,
                        strokeWidthPx = 2.8.dp.toPx(),
                        dashPattern = null,
                    )
                }

                // 7. Predicted Trajectory (Primary accent #5D8EFF long-dash + wheel sweep envelope)
                val pred = uiState.predictedTrajectory
                val predColor = if (pred.willCollide) SimCollision else SimPathPredicted
                if (pred.rearAxlePoints.size >= 2) {
                    drawWorldPolyline(
                        pointsWorld = pred.rearAxlePoints,
                        transform = transform,
                        color = predColor,
                        strokeWidthPx = 3.4.dp.toPx(),
                        dashPattern = floatArrayOf(18f, 10f),
                    )

                    val leftSweep = if (uiState.selectedGear == VirtualGearDirection.FORWARD) {
                        pred.frontLeftCornerPoints
                    } else {
                        pred.rearLeftCornerPoints
                    }
                    val rightSweep = if (uiState.selectedGear == VirtualGearDirection.FORWARD) {
                        pred.frontRightCornerPoints
                    } else {
                        pred.rearRightCornerPoints
                    }
                    drawWorldPolyline(
                        pointsWorld = leftSweep,
                        transform = transform,
                        color = predColor.copy(alpha = 0.34f),
                        strokeWidthPx = 1.6.dp.toPx(),
                        dashPattern = floatArrayOf(12f, 10f),
                    )
                    drawWorldPolyline(
                        pointsWorld = rightSweep,
                        transform = transform,
                        color = predColor.copy(alpha = 0.34f),
                        strokeWidthPx = 1.6.dp.toPx(),
                        dashPattern = floatArrayOf(12f, 10f),
                    )

                    if (pred.willCollide && pred.collisionPoint != null) {
                        val cp = transform.worldToScreen(pred.collisionPoint)
                        drawCircle(
                            color = SimCollision.copy(alpha = 0.28f),
                            radius = 14.dp.toPx(),
                            center = Offset(cp.x, cp.y),
                        )
                        drawCircle(
                            color = SimCollision,
                            radius = 5.dp.toPx(),
                            center = Offset(cp.x, cp.y),
                        )
                    }
                }

                // 8. Ego Vehicle (Sculpted Automotive Precision top-down car)
                drawTopDownEgoVehicle(
                    footprint = uiState.footprint,
                    headingRad = uiState.vehicleState.headingRad,
                    steeringAngleRad = uiState.vehicleState.steeringAngleRad,
                    transform = transform,
                    isColliding = uiState.collisionResult.isColliding,
                    showRearAxleDot = uiState.showRearAxleReferenceDot,
                )

                // 9. Collision point highlight
                val hitPt = uiState.collisionResult.contactPointWorld
                if (uiState.collisionResult.isColliding && hitPt != null) {
                    val sp = transform.worldToScreen(hitPt)
                    drawCircle(
                        color = SimCollision.copy(alpha = 0.38f),
                        radius = 22.dp.toPx(),
                        center = Offset(sp.x, sp.y),
                    )
                    drawCircle(
                        color = SimCollision,
                        radius = 7.dp.toPx(),
                        center = Offset(sp.x, sp.y),
                    )
                }
            }

            // Width Perception category: concise left/right clearance readout
            if (uiState.activeScenarioType.category == PracticeCategory.WIDTH_AND_LANE) {
                val leftCm = ((uiState.evaluation.leftClearanceMeters ?: 0f) * 100).roundToInt()
                val rightCm = ((uiState.evaluation.rightClearanceMeters ?: 0f) * 100).roundToInt()
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    color = OemColors.SurfacePrimary.copy(alpha = 0.94f),
                    shape = OemRadius.button,
                    border = BorderStroke(1.dp, OemColors.Divider),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "좌측 여유 ${leftCm}cm",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OemColors.TextPrimary,
                        )
                        Text(
                            text = "우측 여유 ${rightCm}cm",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OemColors.TextPrimary,
                        )
                    }
                }
            }

            // Subtle Zoom Controls at Top-Right
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .clip(OemRadius.button)
                        .clickable { onAdjustZoom(-0.15f) },
                    color = OemColors.SurfacePrimary.copy(alpha = 0.90f),
                    shape = OemRadius.button,
                    border = BorderStroke(1.dp, OemColors.Divider),
                ) {
                    Text(
                        text = "축소",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OemColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Surface(
                    modifier = Modifier
                        .clip(OemRadius.button)
                        .clickable { onAdjustZoom(+0.15f) },
                    color = OemColors.SurfacePrimary.copy(alpha = 0.90f),
                    shape = OemRadius.button,
                    border = BorderStroke(1.dp, OemColors.Divider),
                ) {
                    Text(
                        text = "확대",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OemColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            // Error banner ONLY when steering signal is disconnected
            if (!uiState.isSteeringReadyForPractice) {
                OemPracticeGuidanceBanner(
                    headline = "핸들 신호를 확인할 수 없습니다.",
                    detail = "차량 핸들 연결 상태를 확인해 주세요.",
                    tone = OemSemanticTone.ALERT,
                    actionLabel = "핸들 진단",
                    onActionClick = onOpenSteeringDiagnostics,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                )
            }

            // Completion or Collision overlay banner
            if (uiState.collisionResult.isColliding || uiState.evaluation.status == EvaluationStatus.SUCCESS) {
                val isSuccess = uiState.evaluation.status == EvaluationStatus.SUCCESS
                OemPracticeGuidanceBanner(
                    headline = if (isSuccess) "주차 완료" else uiState.evaluation.headlineKorean,
                    detail = uiState.evaluation.detailKorean,
                    tone = if (isSuccess) OemSemanticTone.NEUTRAL else OemSemanticTone.ALERT,
                    actionLabel = if (isSuccess) "결과 확인" else "다시 시작",
                    onActionClick = if (isSuccess) onOpenPracticeResult else onResetScenario,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(20.dp),
                )
            }
        }
    }
}

// ============================================================================
// Canvas World Geometry & Sculpted Vehicle Rendering Helpers
// ============================================================================

private fun DrawScope.drawWorldPolygon(
    pointsWorld: List<Vec2>,
    transform: WorldViewportTransform,
    fillColor: Color,
    strokeColor: Color? = null,
    strokeWidthPx: Float = 2f,
    dashed: Boolean = false,
) {
    if (pointsWorld.size < 3) return
    val path = Path()
    pointsWorld.forEachIndexed { idx, pt ->
        val sp = transform.worldToScreen(pt)
        if (idx == 0) path.moveTo(sp.x, sp.y) else path.lineTo(sp.x, sp.y)
    }
    path.close()

    drawPath(path = path, color = fillColor, style = Fill)
    if (strokeColor != null) {
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(
                width = strokeWidthPx,
                join = StrokeJoin.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null,
            ),
        )
    }
}

private fun DrawScope.drawWorldPolyline(
    pointsWorld: List<Vec2>,
    transform: WorldViewportTransform,
    color: Color,
    strokeWidthPx: Float,
    dashPattern: FloatArray? = null,
) {
    if (pointsWorld.size < 2) return
    val path = Path()
    pointsWorld.forEachIndexed { idx, pt ->
        val sp = transform.worldToScreen(pt)
        if (idx == 0) path.moveTo(sp.x, sp.y) else path.lineTo(sp.x, sp.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = if (dashPattern != null) PathEffect.dashPathEffect(dashPattern) else null,
        ),
    )
}

private fun DrawScope.drawParkedObstacleVehicle(
    box: OrientedBox2D,
    transform: WorldViewportTransform,
    isHit: Boolean,
) {
    val centerSp = transform.worldToScreen(box.center)
    val lenPx = transform.metersToPixels(box.halfLength * 2f)
    val widPx = transform.metersToPixels(box.halfWidth * 2f)
    val headingDeg = transform.worldAngleToScreenDegrees(box.angleRad)

    drawSculptedTopDownVehicle(
        center = Offset(centerSp.x, centerSp.y),
        lengthPx = lenPx,
        widthPx = widPx,
        headingDegreesScreen = headingDeg,
        frontWheelSteerDegreesScreen = 0f,
        isEgoVehicle = false,
        isColliding = isHit,
    )
}

private fun DrawScope.drawTopDownEgoVehicle(
    footprint: VehicleFootprint,
    headingRad: Float,
    steeringAngleRad: Float,
    transform: WorldViewportTransform,
    isColliding: Boolean,
    showRearAxleDot: Boolean,
) {
    val centerSp = transform.worldToScreen(footprint.bodyCenter)
    val lenPx = transform.metersToPixels(footprint.bodyBox.halfLength * 2f)
    val widPx = transform.metersToPixels(footprint.bodyBox.halfWidth * 2f)
    val headingDegScreen = transform.worldAngleToScreenDegrees(headingRad)
    val frontSteerDegScreen = (-steeringAngleRad * 180.0 / PI).toFloat()

    drawSculptedTopDownVehicle(
        center = Offset(centerSp.x, centerSp.y),
        lengthPx = lenPx,
        widthPx = widPx,
        headingDegreesScreen = headingDegScreen,
        frontWheelSteerDegreesScreen = frontSteerDegScreen,
        isEgoVehicle = true,
        isColliding = isColliding,
    )

    if (showRearAxleDot) {
        val rearAxleScreen = transform.worldToScreen(footprint.rearAxleCenter)
        drawCircle(
            color = SimPrimaryAccent,
            radius = 5.dp.toPx(),
            center = Offset(rearAxleScreen.x, rearAxleScreen.y),
        )
    }
}

// ============================================================================
// SECTION 13 & 15: RIGHT CONTROL DOCK (`SimulatorControlPanel`)
// - Displays ONLY:
//   1. Current practice name
//   2. Vehicle steering connection & movement state
//   3. Current steering direction (graphical + concise label)
//   4. Virtual Forward / Reverse ("전진" / "후진")
//   5. Move ("이동"), Stop ("정지"), Reset ("다시 시작")
// ============================================================================

@Composable
private fun SimulatorControlPanel(
    uiState: PracticeSimulatorUiState,
    onOpenPracticeResult: () -> Unit,
    onSelectGear: (VirtualGearDirection) -> Unit,
    onSetHoldMoving: (Boolean) -> Unit,
    onToggleLatchMoving: () -> Unit,
    onStopMoving: () -> Unit,
    onResetScenario: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSteeringConnected = uiState.isSteeringReadyForPractice
    val liveGuidance = uiState.liveSteeringGuidance

    // Concise steering direction without engineering clutter
    val directionLabel = if (isSteeringConnected) {
        when (uiState.steeringTelemetry.processedFrame.direction) {
            SteeringDirectionLabel.LEFT -> "좌회전"
            SteeringDirectionLabel.RIGHT -> "우회전"
            SteeringDirectionLabel.CENTER -> "중앙"
        }
    } else {
        "연결 필요"
    }

    // Factual state label (Section 13 & 17: "핸들 연결됨", "차량 이동 중", "정지", "주차 완료")
    val practiceStatusText = when {
        uiState.evaluation.status == EvaluationStatus.SUCCESS -> "주차 완료"
        uiState.collisionResult.isColliding -> "다시 시도"
        !isSteeringConnected -> "핸들 신호 없음"
        uiState.isMovingActive -> "차량 이동 중"
        else -> "핸들 연결됨"
    }

    val statusDotColor = when {
        uiState.collisionResult.isColliding || !isSteeringConnected -> OemColors.StatusAlert
        uiState.evaluation.status == EvaluationStatus.SUCCESS || uiState.isMovingActive -> OemColors.AccentPrimary
        else -> OemColors.TextSecondary
    }

    Surface(
        modifier = modifier,
        color = OemColors.SurfacePrimary,
        shape = OemRadius.card,
        border = BorderStroke(1.dp, OemColors.Divider),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top section: Current Practice Title + Connection/Practice State + Steering Graphic
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = uiState.activeScenarioType.koreanTitle,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = OemColors.TextPrimary,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusDotColor),
                        )
                        Text(
                            text = practiceStatusText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = if (uiState.collisionResult.isColliding || !isSteeringConnected) {
                                OemColors.StatusAlert
                            } else {
                                OemColors.TextSecondary
                            },
                        )
                    }
                }

                // Graphical Steering Indicator
                OemSteeringCuePanel(
                    phaseLabel = liveGuidance.phaseLabel,
                    steeringActionCue = liveGuidance.actionCue,
                    currentDirectionLabel = directionLabel,
                    currentFrontWheelDeg = uiState.uiSteeringRightPositiveDeg,
                    targetFrontWheelDeg = if (uiState.showReferencePath) {
                        liveGuidance.targetFrontWheelRightPositiveDeg
                    } else {
                        null
                    },
                    maxFrontWheelDeg = uiState.vehicleConfig.sanitizedMaxSteeringDeg,
                    isSteeringConnected = isSteeringConnected,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom section: Gear (전진 / 후진) + Move (이동) + Stop (정지) / Reset (다시 시작)
            val currentOnHold by rememberUpdatedState(onSetHoldMoving)
            val canMove = uiState.canMoveVirtualVehicle
            val gearName = if (uiState.selectedGear == VirtualGearDirection.FORWARD) "전진" else "후진"

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 1. Virtual Forward / Reverse Selector (60dp height)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GearSelectorChip(
                        label = "전진",
                        isSelected = uiState.selectedGear == VirtualGearDirection.FORWARD,
                        onClick = { onSelectGear(VirtualGearDirection.FORWARD) },
                        modifier = Modifier.weight(1f),
                    )
                    GearSelectorChip(
                        label = "후진",
                        isSelected = uiState.selectedGear == VirtualGearDirection.REVERSE,
                        onClick = { onSelectGear(VirtualGearDirection.REVERSE) },
                        modifier = Modifier.weight(1f),
                    )
                }

                // 2. Primary Action: Hold-to-Move Button (68dp height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .clip(OemRadius.button)
                        .background(
                            when {
                                !canMove -> OemColors.SurfaceElevated
                                uiState.isHoldMoving -> OemColors.TextPrimary
                                else -> OemColors.AccentPrimary
                            },
                        )
                        .pointerInput(uiState.selectedGear, canMove) {
                            if (!canMove) return@pointerInput
                            detectTapGestures(
                                onPress = {
                                    currentOnHold(true)
                                    try {
                                        awaitRelease()
                                    } finally {
                                        currentOnHold(false)
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            !isSteeringConnected -> "핸들 연결 필요"
                            uiState.isHoldMoving -> "$gearName 이동 중"
                            else -> "$gearName 이동 (누르기)"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = if (canMove) OemColors.TextOnAccent else OemColors.TextTertiary,
                    )
                }

                // 3. Continuous Latch Move Toggle (56dp height)
                OemButton(
                    text = if (uiState.isLatchMoving) "연속 이동 중" else "연속 $gearName",
                    onClick = onToggleLatchMoving,
                    enabled = canMove,
                    variant = if (uiState.isLatchMoving) OemButtonVariant.PRIMARY else OemButtonVariant.SECONDARY,
                    minHeight = 56.dp,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 4. Stop (정지) & Reset (다시 시작) Secondary Actions (60dp height)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OemButton(
                        text = "정지",
                        onClick = onStopMoving,
                        variant = OemButtonVariant.SECONDARY,
                        minHeight = 60.dp,
                        modifier = Modifier.weight(1f),
                    )
                    OemButton(
                        text = "다시 시작",
                        onClick = onResetScenario,
                        variant = OemButtonVariant.SECONDARY,
                        minHeight = 60.dp,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (uiState.evaluation.status == EvaluationStatus.SUCCESS) {
                    OemButton(
                        text = "연습 결과",
                        onClick = onOpenPracticeResult,
                        variant = OemButtonVariant.PRIMARY,
                        minHeight = 60.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GearSelectorChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 60.dp)
            .clip(OemRadius.button)
            .clickable(onClick = onClick),
        color = if (isSelected) OemColors.AccentPrimarySoft else OemColors.SurfaceElevated,
        shape = OemRadius.button,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) OemColors.AccentPrimary else OemColors.Divider,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isSelected) OemColors.TextPrimary else OemColors.TextSecondary,
            )
        }
    }
}

// ============================================================================
// SECTION 20: COMPOSE PREVIEWS FOR ACTIVE SIMULATOR VERIFICATION (1280x720)
// ============================================================================

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewParallelParkingSimulatorScreen() {
    DriveCoachTheme {
        val previewTelemetry = SteeringTelemetrySnapshot(
            sourceMode = SteeringInputSourceMode.PLEOS_VEHICLE_SDK,
            connectionState = SteeringConnectionState.CONNECTED_RECEIVING,
            rawSdkAngleDeg = -180f,
            processedFrame = com.drivecoach.app.simulation.steering.ProcessedSteeringFrame(
                isValid = true,
                rawSdkAngleDeg = -180f,
                zeroCalibratedAngleDeg = -180f,
                estimatedSteeringWheelAngleDeg = 180f,
                estimatedFrontWheelAngleRightPositiveDeg = 12.0f,
                physicsSteeringAngleRad = -Math.toRadians(12.0).toFloat(),
                direction = SteeringDirectionLabel.RIGHT,
                isClampedToMaxWheelAngle = false,
            ),
            currentGear = GearPosition.PARK,
            isVehicleStationaryVerified = true,
        )
        SimulatorPracticeScreen(
            uiState = PracticeSimulatorUiState(
                activeScenarioType = ScenarioType.PARALLEL_PARKING,
                scenario = SimulatorScenarios.buildScenario(ScenarioType.PARALLEL_PARKING),
                uiSteeringRightPositiveDeg = 12.0f,
                steeringTelemetry = previewTelemetry,
            ),
            onBack = {},
            onHome = {},
            onOpenSettings = {},
            onOpenSteeringDiagnostics = {},
            onOpenPracticeResult = {},
            onCalibrateSteeringZero = {},
            onSelectGear = {},
            onSetHoldMoving = {},
            onToggleLatchMoving = {},
            onStopMoving = {},
            onResetScenario = {},
            onToggleReferencePath = {},
            onToggleHelpDialog = {},
            onAdjustZoom = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun PreviewRightTurnSimulatorScreen() {
    DriveCoachTheme {
        val rightTurnScenario = SimulatorScenarios.buildScenario(ScenarioType.RIGHT_TURN)
        val previewTelemetry = SteeringTelemetrySnapshot(
            sourceMode = SteeringInputSourceMode.PLEOS_VEHICLE_SDK,
            connectionState = SteeringConnectionState.CONNECTED_RECEIVING,
            rawSdkAngleDeg = -210f,
            processedFrame = com.drivecoach.app.simulation.steering.ProcessedSteeringFrame(
                isValid = true,
                rawSdkAngleDeg = -210f,
                zeroCalibratedAngleDeg = -210f,
                estimatedSteeringWheelAngleDeg = 210f,
                estimatedFrontWheelAngleRightPositiveDeg = 14.0f,
                physicsSteeringAngleRad = -Math.toRadians(14.0).toFloat(),
                direction = SteeringDirectionLabel.RIGHT,
                isClampedToMaxWheelAngle = false,
            ),
            currentGear = GearPosition.PARK,
            isVehicleStationaryVerified = true,
        )
        SimulatorPracticeScreen(
            uiState = PracticeSimulatorUiState(
                activeScenarioType = ScenarioType.RIGHT_TURN,
                selectedCategory = PracticeCategory.DRIVING,
                scenario = rightTurnScenario,
                vehicleState = rightTurnScenario.initialState,
                footprint = VehicleGeometry.computeFootprint(rightTurnScenario.initialState, VehicleConfig()),
                selectedGear = VirtualGearDirection.FORWARD,
                uiSteeringRightPositiveDeg = 14.0f,
                steeringTelemetry = previewTelemetry,
            ),
            onBack = {},
            onHome = {},
            onOpenSettings = {},
            onOpenSteeringDiagnostics = {},
            onOpenPracticeResult = {},
            onCalibrateSteeringZero = {},
            onSelectGear = {},
            onSetHoldMoving = {},
            onToggleLatchMoving = {},
            onStopMoving = {},
            onResetScenario = {},
            onToggleReferencePath = {},
            onToggleHelpDialog = {},
            onAdjustZoom = {},
        )
    }
}
