package com.drivecoach.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.drivecoach.app.data.simulation.DemoScenarioCatalog
import com.drivecoach.app.data.vehicle.PleosSdkDiagnosticState
import com.drivecoach.app.domain.analysis.EducationCatalog
import com.drivecoach.app.domain.analysis.RuleBasedCoachingGenerator
import com.drivecoach.app.domain.model.DataSourceType
import com.drivecoach.app.domain.model.DrivingSession
import com.drivecoach.app.domain.model.GearPosition
import com.drivecoach.app.ui.DriveCoachAppUiState
import com.drivecoach.app.ui.DriveCoachDestination
import com.drivecoach.app.ui.coaching.CoachingScreen
import com.drivecoach.app.ui.components.CockpitScoreRing
import com.drivecoach.app.ui.components.DataSourceBadge
import com.drivecoach.app.ui.components.MetricEvaluationCardRow
import com.drivecoach.app.ui.components.ScoreTrendSparklineChart
import com.drivecoach.app.ui.components.StatusPillBadge
import com.drivecoach.app.ui.components.colorForScore
import com.drivecoach.app.ui.components.formatDurationKorean
import com.drivecoach.app.ui.components.formatTimestampKorean
import com.drivecoach.app.ui.education.EducationScreen
import com.drivecoach.app.ui.history.HistoryScreen
import com.drivecoach.app.ui.report.ReportDetailScreen
import com.drivecoach.app.ui.settings.DiagnosticsScreen
import com.drivecoach.app.ui.settings.SettingsScreen
import com.drivecoach.app.ui.simulation.SimulationScreen
import com.drivecoach.app.ui.theme.CardBorder
import com.drivecoach.app.ui.theme.CockpitBackground
import com.drivecoach.app.ui.theme.DriveCoachTheme
import com.drivecoach.app.ui.theme.ElevatedSurface
import com.drivecoach.app.ui.theme.MainBackground
import com.drivecoach.app.ui.theme.PleosBlueDeep
import com.drivecoach.app.ui.theme.PrimaryAccent
import com.drivecoach.app.ui.theme.SecondaryAccent
import com.drivecoach.app.ui.theme.SecondaryBackground
import com.drivecoach.app.ui.theme.StatusCritical
import com.drivecoach.app.ui.theme.StatusSuccess
import com.drivecoach.app.ui.theme.StatusWarning
import com.drivecoach.app.ui.theme.TextMuted
import com.drivecoach.app.ui.theme.TextPrimary
import com.drivecoach.app.ui.theme.TextSecondary
import java.util.Locale

/**
 * 전체 8개 화면을 연결하는 차량용 인포테인먼트 메인 쉘 (섹션 16 ~ 18)
 */
@Composable
fun DriveCoachAppShell(
    appState: DriveCoachAppUiState,
    diagnosticState: PleosSdkDiagnosticState,
    onNavigate: (DriveCoachDestination) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenSessionReport: (String) -> Unit,
    onOpenEducationTopic: (String) -> Unit,
    onSelectEventInReport: (String?) -> Unit,
    onSelectComparisonSessions: (String, String) -> Unit,
    onToggleDrivingRestriction: () -> Unit,
    onSelectScenario: (String) -> Unit,
    onStartOrResumeSimulation: () -> Unit,
    onPauseSimulation: () -> Unit,
    onRestartSimulation: () -> Unit,
    onCompleteSimulationImmediately: () -> Unit,
    onSetPlaybackSpeed: (Int) -> Unit,
    onReplaySavedSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onDeleteAllSessions: () -> Unit,
    onResetDemoData: () -> Unit,
    onResetSettings: () -> Unit,
    onUpdateWeights: (com.drivecoach.app.domain.model.ScoringWeights) -> Unit,
    onUpdatePreferredSource: (DataSourceType) -> Unit,
    onToggleEducationCompleted: (String) -> Unit,
    onTogglePersonalGoal: (String) -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onToggleLiveListening: () -> Unit,
    onClearSnackbar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(appState.snackbarMessage) {
        val msg = appState.snackbarMessage
        if (msg != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            onClearSnackbar()
        }
    }

    val isLiveDriving = diagnosticState.currentGear == GearPosition.DRIVE ||
        diagnosticState.currentGear == GearPosition.REVERSE
    val isDrivingRestricted = appState.isSimulatedDrivingRestrictionActive || isLiveDriving

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MainBackground),
        containerColor = MainBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = ElevatedSurface,
                    contentColor = TextPrimary,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val isWideCockpit = maxWidth >= 820.dp

            if (isWideCockpit) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CockpitSideNavigationRail(
                        currentDestination = appState.currentDestination,
                        onNavigate = onNavigate,
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CockpitTopStatusBar(
                            appState = appState,
                            diagnosticState = diagnosticState,
                            isDrivingRestricted = isDrivingRestricted,
                            canNavigateBack = appState.navigationBackStack.isNotEmpty() ||
                                appState.currentDestination != DriveCoachDestination.DASHBOARD,
                            onNavigateBack = onNavigateBack,
                            onToggleDrivingRestriction = onToggleDrivingRestriction,
                            onOpenDiagnostics = { onNavigate(DriveCoachDestination.DIAGNOSTICS) },
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            DestinationContentArea(
                                appState = appState,
                                diagnosticState = diagnosticState,
                                isDrivingRestricted = isDrivingRestricted,
                                onNavigate = onNavigate,
                                onOpenSessionReport = onOpenSessionReport,
                                onOpenEducationTopic = onOpenEducationTopic,
                                onSelectEventInReport = onSelectEventInReport,
                                onSelectComparisonSessions = onSelectComparisonSessions,
                                onToggleDrivingRestriction = onToggleDrivingRestriction,
                                onSelectScenario = onSelectScenario,
                                onStartOrResumeSimulation = onStartOrResumeSimulation,
                                onPauseSimulation = onPauseSimulation,
                                onRestartSimulation = onRestartSimulation,
                                onCompleteSimulationImmediately = onCompleteSimulationImmediately,
                                onSetPlaybackSpeed = onSetPlaybackSpeed,
                                onReplaySavedSession = onReplaySavedSession,
                                onDeleteSession = onDeleteSession,
                                onDeleteAllSessions = onDeleteAllSessions,
                                onResetDemoData = onResetDemoData,
                                onResetSettings = onResetSettings,
                                onUpdateWeights = onUpdateWeights,
                                onUpdatePreferredSource = onUpdatePreferredSource,
                                onToggleEducationCompleted = onToggleEducationCompleted,
                                onTogglePersonalGoal = onTogglePersonalGoal,
                                onRefreshDiagnostics = onRefreshDiagnostics,
                                onToggleLiveListening = onToggleLiveListening,
                            )
                        }
                    }
                }
            } else {
                // 세로 화면 및 분할 화면용 레이아웃
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CockpitTopStatusBar(
                        appState = appState,
                        diagnosticState = diagnosticState,
                        isDrivingRestricted = isDrivingRestricted,
                        canNavigateBack = appState.navigationBackStack.isNotEmpty() ||
                            appState.currentDestination != DriveCoachDestination.DASHBOARD,
                        onNavigateBack = onNavigateBack,
                        onToggleDrivingRestriction = onToggleDrivingRestriction,
                        onOpenDiagnostics = { onNavigate(DriveCoachDestination.DIAGNOSTICS) },
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        DestinationContentArea(
                            appState = appState,
                            diagnosticState = diagnosticState,
                            isDrivingRestricted = isDrivingRestricted,
                            onNavigate = onNavigate,
                            onOpenSessionReport = onOpenSessionReport,
                            onOpenEducationTopic = onOpenEducationTopic,
                            onSelectEventInReport = onSelectEventInReport,
                            onSelectComparisonSessions = onSelectComparisonSessions,
                            onToggleDrivingRestriction = onToggleDrivingRestriction,
                            onSelectScenario = onSelectScenario,
                            onStartOrResumeSimulation = onStartOrResumeSimulation,
                            onPauseSimulation = onPauseSimulation,
                            onRestartSimulation = onRestartSimulation,
                            onCompleteSimulationImmediately = onCompleteSimulationImmediately,
                            onSetPlaybackSpeed = onSetPlaybackSpeed,
                            onReplaySavedSession = onReplaySavedSession,
                            onDeleteSession = onDeleteSession,
                            onDeleteAllSessions = onDeleteAllSessions,
                            onResetDemoData = onResetDemoData,
                            onResetSettings = onResetSettings,
                            onUpdateWeights = onUpdateWeights,
                            onUpdatePreferredSource = onUpdatePreferredSource,
                            onToggleEducationCompleted = onToggleEducationCompleted,
                            onTogglePersonalGoal = onTogglePersonalGoal,
                            onRefreshDiagnostics = onRefreshDiagnostics,
                            onToggleLiveListening = onToggleLiveListening,
                        )
                    }

                    CockpitBottomNavBar(
                        currentDestination = appState.currentDestination,
                        onNavigate = onNavigate,
                    )
                }
            }
        }
    }
}

@Composable
private fun CockpitSideNavigationRail(
    currentDestination: DriveCoachDestination,
    onNavigate: (DriveCoachDestination) -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(126.dp)
            .fillMaxHeight(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(PrimaryAccent, PleosBlueDeep))),
                    contentAlignment = Alignment.Center,
                ) {
                    SteeringWheelIcon(color = MainBackground, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "DriveCoach",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }

            DriveCoachDestination.entries.forEach { dest ->
                val isSelected = dest == currentDestination
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { onNavigate(dest) },
                    color = if (isSelected) PrimaryAccent.copy(alpha = 0.18f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    border = if (isSelected) BorderStroke(1.dp, PrimaryAccent) else null,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = dest.shortLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) PrimaryAccent else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitBottomNavBar(
    currentDestination: DriveCoachDestination,
    onNavigate: (DriveCoachDestination) -> Unit,
) {
    val compactDestinations = listOf(
        DriveCoachDestination.DASHBOARD,
        DriveCoachDestination.SIMULATION,
        DriveCoachDestination.REPORT_DETAIL,
        DriveCoachDestination.HISTORY,
        DriveCoachDestination.COACHING,
        DriveCoachDestination.EDUCATION,
        DriveCoachDestination.SETTINGS,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            compactDestinations.forEach { dest ->
                val isSelected = dest == currentDestination
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(dest) },
                    color = if (isSelected) PrimaryAccent.copy(alpha = 0.18f) else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = dest.shortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) PrimaryAccent else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitTopStatusBar(
    appState: DriveCoachAppUiState,
    diagnosticState: PleosSdkDiagnosticState,
    isDrivingRestricted: Boolean,
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
    onToggleDrivingRestriction: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val effectiveSource = appState.selectedSession?.dataSource ?: appState.preferredDataSource

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (canNavigateBack) {
                    Surface(
                        modifier = Modifier.clickable(onClick = onNavigateBack),
                        color = ElevatedSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardBorder),
                    ) {
                        Text(
                            text = "← 뒤로",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }

                Column {
                    Text(
                        text = appState.currentDestination.koreanTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "현대자동차 Pleos Connect AI 운전 습관 코치 · ${appState.lastUpdatedTimeText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 주행 중 UX 안전 제한 시뮬레이션 토글
                Surface(
                    modifier = Modifier.clickable(onClick = onToggleDrivingRestriction),
                    color = if (isDrivingRestricted) StatusWarning.copy(alpha = 0.16f) else StatusSuccess.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, if (isDrivingRestricted) StatusWarning else StatusSuccess),
                ) {
                    Text(
                        text = if (isDrivingRestricted) "주행 상태 (D) · 안전 제한 중" else "주차 상태 (P) · 전체 열람 가능",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isDrivingRestricted) StatusWarning else StatusSuccess,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }

                Box(modifier = Modifier.clickable(onClick = onOpenDiagnostics)) {
                    DataSourceBadge(
                        dataSource = effectiveSource,
                        showKoreanTitle = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationContentArea(
    appState: DriveCoachAppUiState,
    diagnosticState: PleosSdkDiagnosticState,
    isDrivingRestricted: Boolean,
    onNavigate: (DriveCoachDestination) -> Unit,
    onOpenSessionReport: (String) -> Unit,
    onOpenEducationTopic: (String) -> Unit,
    onSelectEventInReport: (String?) -> Unit,
    onSelectComparisonSessions: (String, String) -> Unit,
    onToggleDrivingRestriction: () -> Unit,
    onSelectScenario: (String) -> Unit,
    onStartOrResumeSimulation: () -> Unit,
    onPauseSimulation: () -> Unit,
    onRestartSimulation: () -> Unit,
    onCompleteSimulationImmediately: () -> Unit,
    onSetPlaybackSpeed: (Int) -> Unit,
    onReplaySavedSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onDeleteAllSessions: () -> Unit,
    onResetDemoData: () -> Unit,
    onResetSettings: () -> Unit,
    onUpdateWeights: (com.drivecoach.app.domain.model.ScoringWeights) -> Unit,
    onUpdatePreferredSource: (DataSourceType) -> Unit,
    onToggleEducationCompleted: (String) -> Unit,
    onTogglePersonalGoal: (String) -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onToggleLiveListening: () -> Unit,
) {
    // 주행 중 안전 제한 정책 (섹션 18): 주행 중에는 상세 리포트, 시뮬레이션, 교육 등 복잡한 화면 차단
    if (isDrivingRestricted && appState.currentDestination != DriveCoachDestination.DIAGNOSTICS) {
        DrivingSafetyRestrictionOverlay(
            selectedSession = appState.selectedSession,
            onSwitchToParkedMode = onToggleDrivingRestriction,
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 800.dp

        when (appState.currentDestination) {
            DriveCoachDestination.DASHBOARD -> {
                DriveCoachDashboardView(
                    appState = appState,
                    onStartSimulationClick = { onNavigate(DriveCoachDestination.SIMULATION) },
                    onOpenSessionReport = onOpenSessionReport,
                    onOpenComparison = { onNavigate(DriveCoachDestination.HISTORY) },
                    onOpenCoaching = { onNavigate(DriveCoachDestination.COACHING) },
                    onOpenEducationTopic = onOpenEducationTopic,
                )
            }

            DriveCoachDestination.SIMULATION -> {
                SimulationScreen(
                    simulationState = appState.simulationState,
                    isWideLayout = isWide,
                    onSelectScenario = onSelectScenario,
                    onStartOrResume = onStartOrResumeSimulation,
                    onPause = onPauseSimulation,
                    onRestart = onRestartSimulation,
                    onCompleteImmediately = onCompleteSimulationImmediately,
                    onChangeSpeedMultiplier = onSetPlaybackSpeed,
                    onOpenCompletedReport = onOpenSessionReport,
                )
            }

            DriveCoachDestination.REPORT_DETAIL -> {
                ReportDetailScreen(
                    session = appState.selectedSession,
                    previousSession = appState.previousSessionForSelected,
                    allSessions = appState.sessions,
                    selectedEventId = appState.selectedEventId,
                    isWideLayout = isWide,
                    onSelectSession = onOpenSessionReport,
                    onSelectEvent = onSelectEventInReport,
                    onReplaySession = onReplaySavedSession,
                    onDeleteSession = onDeleteSession,
                    onNavigateToSimulation = { onNavigate(DriveCoachDestination.SIMULATION) },
                    onOpenEducationTopic = onOpenEducationTopic,
                )
            }

            DriveCoachDestination.HISTORY -> {
                HistoryScreen(
                    sessions = appState.sessions,
                    trendSummaries = appState.trendSummaries,
                    compareBaseSessionId = appState.compareBaseSessionId,
                    compareTargetSessionId = appState.compareTargetSessionId,
                    isWideLayout = isWide,
                    onSelectSessionForDetail = onOpenSessionReport,
                    onSelectComparisonSessions = onSelectComparisonSessions,
                    onNavigateToSimulation = { onNavigate(DriveCoachDestination.SIMULATION) },
                    onRestoreDemoSessions = onResetDemoData,
                )
            }

            DriveCoachDestination.COACHING -> {
                CoachingScreen(
                    selectedSession = appState.selectedSession,
                    personalTrendInsights = appState.personalTrendInsights,
                    personalGoals = appState.personalGoals,
                    onToggleGoal = onTogglePersonalGoal,
                    onOpenEducationTopic = onOpenEducationTopic,
                    onNavigateToSimulation = { onNavigate(DriveCoachDestination.SIMULATION) },
                )
            }

        DriveCoachDestination.EDUCATION -> {
            EducationScreen(
                selectedTopicId = appState.selectedEducationTopicId,
                completedTopicIds = appState.completedEducationIds,
                onSelectTopic = onOpenEducationTopic,
                onToggleComplete = onToggleEducationCompleted,
            )
        }

        DriveCoachDestination.SETTINGS -> {
            SettingsScreen(
                scoringWeights = appState.scoringWeights,
                preferredDataSource = appState.preferredDataSource,
                totalSessionCount = appState.sessions.size,
                onUpdateWeights = onUpdateWeights,
                onUpdatePreferredSource = onUpdatePreferredSource,
                onResetDemoData = onResetDemoData,
                onDeleteAllSessions = onDeleteAllSessions,
                onResetSettings = onResetSettings,
                onOpenDiagnostics = { onNavigate(DriveCoachDestination.DIAGNOSTICS) },
            )
        }

        DriveCoachDestination.DIAGNOSTICS -> {
            DiagnosticsScreen(
                diagnosticState = diagnosticState,
                onRefreshDiagnostics = onRefreshDiagnostics,
                onToggleLiveListening = onToggleLiveListening,
            )
        }
        }
    }
}

/**
 * 주행 중 UX 안전 제한 화면 (섹션 18 주행 중 안전 정책)
 */
@Composable
private fun DrivingSafetyRestrictionOverlay(
    selectedSession: DrivingSession?,
    onSwitchToParkedMode: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, StatusWarning),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusPillBadge(
                text = "DRIVING SAFETY UX RESTRICTION · 주행 중 화면 제한",
                accentColor = StatusWarning,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "안전 운전을 위해 주행 중에는 상세 분석 리포트와 시뮬레이션이 제한됩니다.",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "DriveCoach는 주행 중 운전자의 시선을 빼앗는 복잡한 차트, 긴 텍스트, 실시간 조향 각도 지시를 표시하지 않습니다.\n" +
                    "목적지에 도착하여 기어를 주차(P) 상태로 전환하면 전체 주행 리포트와 맞춤형 코칭을 확인할 수 있습니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(20.dp))
            if (selectedSession != null) {
                Surface(
                    color = ElevatedSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CardBorder),
                ) {
                    Text(
                        text = "최근 완료 세션: ${selectedSession.title} · 종합 점수 ${selectedSession.analysisResult?.overallScore ?: "-"}점",
                        style = MaterialTheme.typography.titleMedium,
                        color = SecondaryAccent,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
            Button(
                onClick = onSwitchToParkedMode,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryAccent,
                    contentColor = MainBackground,
                ),
            ) {
                Text(
                    text = "주차(P) 상태로 전환하여 리포트 확인 (시연용 토글)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * 메인 대시보드 콘텐츠 뷰 (섹션 16.1 & 17.5 가로 2~3열 반응형 구성)
 */
@Composable
fun DriveCoachDashboardView(
    appState: DriveCoachAppUiState,
    onStartSimulationClick: () -> Unit,
    onOpenSessionReport: (String) -> Unit,
    onOpenComparison: () -> Unit,
    onOpenCoaching: () -> Unit,
    onOpenEducationTopic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedSession = appState.selectedSession
    val prevSession = appState.previousSessionForSelected
    val analysis = selectedSession?.analysisResult
    val prevScore = prevSession?.analysisResult?.overallScore
    val currentScore = analysis?.overallScore
    val deltaScore = if (currentScore != null && prevScore != null) currentScore - prevScore else null

    val recommendedEduId = analysis?.recommendedEducationId
        ?: analysis?.coachingInsights?.firstOrNull()?.recommendedTopicId
        ?: EducationCatalog.topics.first().id
    val recommendedEduTopic = EducationCatalog.findById(recommendedEduId)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isThreeColumn = maxWidth >= 1100.dp
        val isTwoColumn = maxWidth >= 680.dp
        val isCompactHeight = maxHeight < 520.dp

        when {
            isThreeColumn -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 좌측 열: 운전 점수 링 + 빠른 실행
                    Column(
                        modifier = Modifier
                            .weight(0.32f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        DashboardHeroScoreCard(
                            session = selectedSession,
                            deltaScore = deltaScore,
                            gaugeSize = if (isCompactHeight) 148.dp else 178.dp,
                            onStartSimulationClick = onStartSimulationClick,
                            onOpenReportClick = { selectedSession?.let { onOpenSessionReport(it.id) } },
                            onOpenComparisonClick = onOpenComparison,
                        )
                    }

                    // 중앙 열: 4대 세부 안정성 + 점수 추세 미니 차트
                    Column(
                        modifier = Modifier
                            .weight(0.36f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        DashboardStabilityCard(
                            session = selectedSession,
                            sessions = appState.sessions,
                            onOpenCoaching = onOpenCoaching,
                        )
                    }

                    // 우측 열: 최근 주행 3~5건 목록 + 오늘의 추천 교육
                    Column(
                        modifier = Modifier
                            .weight(0.32f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        DashboardRecentSessionsCard(
                            sessions = appState.sessions.take(5),
                            selectedSessionId = selectedSession?.id,
                            onSelectSession = onOpenSessionReport,
                        )
                        DashboardRecommendedLearningCard(
                            topic = recommendedEduTopic,
                            topCoachingTitle = analysis?.coachingInsights?.firstOrNull()?.title,
                            onOpenTopic = { onOpenEducationTopic(recommendedEduTopic.id) },
                        )
                    }
                }
            }

            isTwoColumn -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.43f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        DashboardHeroScoreCard(
                            session = selectedSession,
                            deltaScore = deltaScore,
                            gaugeSize = if (isCompactHeight) 144.dp else 174.dp,
                            onStartSimulationClick = onStartSimulationClick,
                            onOpenReportClick = { selectedSession?.let { onOpenSessionReport(it.id) } },
                            onOpenComparisonClick = onOpenComparison,
                        )
                        DashboardRecommendedLearningCard(
                            topic = recommendedEduTopic,
                            topCoachingTitle = analysis?.coachingInsights?.firstOrNull()?.title,
                            onOpenTopic = { onOpenEducationTopic(recommendedEduTopic.id) },
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.57f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        DashboardStabilityCard(
                            session = selectedSession,
                            sessions = appState.sessions,
                            onOpenCoaching = onOpenCoaching,
                        )
                        DashboardRecentSessionsCard(
                            sessions = appState.sessions.take(5),
                            selectedSessionId = selectedSession?.id,
                            onSelectSession = onOpenSessionReport,
                        )
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DashboardHeroScoreCard(
                        session = selectedSession,
                        deltaScore = deltaScore,
                        gaugeSize = 172.dp,
                        onStartSimulationClick = onStartSimulationClick,
                        onOpenReportClick = { selectedSession?.let { onOpenSessionReport(it.id) } },
                        onOpenComparisonClick = onOpenComparison,
                    )
                    DashboardStabilityCard(
                        session = selectedSession,
                        sessions = appState.sessions,
                        onOpenCoaching = onOpenCoaching,
                    )
                    DashboardRecentSessionsCard(
                        sessions = appState.sessions.take(5),
                        selectedSessionId = selectedSession?.id,
                        onSelectSession = onOpenSessionReport,
                    )
                    DashboardRecommendedLearningCard(
                        topic = recommendedEduTopic,
                        topCoachingTitle = analysis?.coachingInsights?.firstOrNull()?.title,
                        onOpenTopic = { onOpenEducationTopic(recommendedEduTopic.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHeroScoreCard(
    session: DrivingSession?,
    deltaScore: Int?,
    gaugeSize: androidx.compose.ui.unit.Dp,
    onStartSimulationClick: () -> Unit,
    onOpenReportClick: () -> Unit,
    onOpenComparisonClick: () -> Unit,
) {
    val analysis = session?.analysisResult
    val score = analysis?.overallScore
    val grade = analysis?.scoreGradeLabel ?: "분석 데이터 부족"
    val reliability = ((analysis?.dataQuality?.overallReliability ?: 0f) * 100f).toInt()
    val summary = analysis?.overallSummary
        ?: "저장된 주행 기록이 없습니다. '시뮬레이션 재생 시작' 버튼을 눌러 첫 주행 분석을 실행해 보세요."

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "종합 운전 습관 점수",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    if (session != null) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (session != null) {
                    DataSourceBadge(dataSource = session.dataSource, showKoreanTitle = false)
                }
            }

            CockpitScoreRing(
                score = score,
                gradeLabel = grade,
                deltaScore = deltaScore,
                reliabilityPercent = reliability,
                gaugeSize = gaugeSize,
            )

            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth(),
            )

            // 주행 분석/시뮬레이션 시작 메인 CTA 버튼
            Button(
                onClick = onStartSimulationClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryAccent,
                    contentColor = MainBackground,
                ),
            ) {
                PlayTriangleIcon(color = MainBackground, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "시뮬레이션 주행 분석 시작",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpenReportClick,
                    enabled = session != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElevatedSurface,
                        contentColor = TextPrimary,
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp),
                ) {
                    Text("상세 분석 리포트", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = onOpenComparisonClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElevatedSurface,
                        contentColor = SecondaryAccent,
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp),
                ) {
                    Text("전/후 주행 비교", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DashboardStabilityCard(
    session: DrivingSession?,
    sessions: List<DrivingSession>,
    onOpenCoaching: () -> Unit,
) {
    val analysis = session?.analysisResult
    val metrics = if (analysis != null) {
        listOf(
            analysis.steeringEval,
            analysis.accelerationEval,
            analysis.brakingEval,
            analysis.consistencyEval,
        )
    } else {
        emptyList()
    }

    val trendPoints = sessions
        .filter { it.analysisResult?.overallScore != null }
        .take(5)
        .reversed()
        .mapIndexed { idx, s ->
            "주행${idx + 1}" to (s.analysisResult?.overallScore ?: 0)
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "세부 주행 안정성 및 추세",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Surface(
                    modifier = Modifier.clickable(onClick = onOpenCoaching),
                    color = PrimaryAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = "AI 코칭 상세 →",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (metrics.isEmpty()) {
                Text(
                    text = "분석된 주행 기록이 없습니다. 시뮬레이션을 실행하거나 설정에서 기본 데모를 복원하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                metrics.forEach { metric ->
                    MetricEvaluationCardRow(evaluation = metric)
                }
            }

            if (trendPoints.size >= 2) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "최근 ${trendPoints.size}회 주행 점수 추이",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
                ScoreTrendSparklineChart(scoresChronological = trendPoints)
            }
        }
    }
}

@Composable
private fun DashboardRecentSessionsCard(
    sessions: List<DrivingSession>,
    selectedSessionId: String?,
    onSelectSession: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "최근 주행 리포트 (클릭 시 상세 분석)",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = "총 ${sessions.size}건 표시",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
            }

            if (sessions.isEmpty()) {
                Text(
                    text = "저장된 주행 기록이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                sessions.forEach { item ->
                    val isSelected = item.id == selectedSessionId
                    val score = item.analysisResult?.overallScore
                    val scoreAccent = colorForScore(score)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) ElevatedSurface else MainBackground.copy(alpha = 0.65f))
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) PrimaryAccent else CardBorder,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .clickable { onSelectSession(item.id) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatTimestampKorean(item.startTimeMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextMuted,
                                )
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                DataSourceBadge(dataSource = item.dataSource, showKoreanTitle = false)
                                StatusPillBadge(
                                    text = if (score != null) "${score}점" else "데이터 부족",
                                    accentColor = scoreAccent,
                                )
                            }
                        }

                        Text(
                            text = String.format(
                                Locale.US,
                                "%.2f km · %s · 이벤트 %d건",
                                item.distanceKm,
                                formatDurationKorean(item.durationSec),
                                item.detectedEvents.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardRecommendedLearningCard(
    topic: com.drivecoach.app.domain.model.EducationTopic,
    topCoachingTitle: String?,
    onOpenTopic: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenTopic),
        color = SecondaryBackground,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, SecondaryAccent.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPillBadge(text = "오늘의 맞춤 학습 추천", accentColor = SecondaryAccent)
                Text(
                    text = "교육 센터 열기 →",
                    style = MaterialTheme.typography.labelMedium,
                    color = SecondaryAccent,
                )
            }

            Text(
                text = topic.title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            if (topCoachingTitle != null) {
                Text(
                    text = "연계 코칭: $topCoachingTitle",
                    style = MaterialTheme.typography.labelMedium,
                    color = PrimaryAccent,
                )
            }
            Text(
                text = topic.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

/**
 * 단독 프리뷰 및 하위 호환용 진입점
 */
@Composable
fun DriveCoachDashboardScreen(
    modifier: Modifier = Modifier,
    uiState: DashboardSampleUiState = DashboardSampleData.defaultState,
) {
    val previewSessions = remember { DemoScenarioCatalog.buildInitialDemoSessions() }
    var previewAppState by remember {
        mutableStateOf(
            DriveCoachAppUiState(
                currentDestination = DriveCoachDestination.DASHBOARD,
                sessions = previewSessions,
                selectedSessionId = previewSessions.firstOrNull()?.id,
                compareBaseSessionId = previewSessions.getOrNull(1)?.id,
                compareTargetSessionId = previewSessions.firstOrNull()?.id,
                trendSummaries = RuleBasedCoachingGenerator.computeTrendSummaries(previewSessions),
                personalTrendInsights = RuleBasedCoachingGenerator.generatePersonalTrendCoaching(previewSessions),
            )
        )
    }

    DriveCoachAppShell(
        modifier = modifier,
        appState = previewAppState,
        diagnosticState = PleosSdkDiagnosticState(),
        onNavigate = { dest -> previewAppState = previewAppState.copy(currentDestination = dest) },
        onNavigateBack = { previewAppState = previewAppState.copy(currentDestination = DriveCoachDestination.DASHBOARD) },
        onOpenSessionReport = { id ->
            previewAppState = previewAppState.copy(
                selectedSessionId = id,
                currentDestination = DriveCoachDestination.REPORT_DETAIL,
            )
        },
        onOpenEducationTopic = { topicId ->
            previewAppState = previewAppState.copy(
                selectedEducationTopicId = topicId,
                currentDestination = DriveCoachDestination.EDUCATION,
            )
        },
        onSelectEventInReport = { evId -> previewAppState = previewAppState.copy(selectedEventId = evId) },
        onSelectComparisonSessions = { b, t ->
            previewAppState = previewAppState.copy(compareBaseSessionId = b, compareTargetSessionId = t)
        },
        onToggleDrivingRestriction = {
            previewAppState = previewAppState.copy(
                isSimulatedDrivingRestrictionActive = !previewAppState.isSimulatedDrivingRestrictionActive
            )
        },
        onSelectScenario = {},
        onStartOrResumeSimulation = {},
        onPauseSimulation = {},
        onRestartSimulation = {},
        onCompleteSimulationImmediately = {},
        onSetPlaybackSpeed = {},
        onReplaySavedSession = {},
        onDeleteSession = {},
        onDeleteAllSessions = {},
        onResetDemoData = {},
        onResetSettings = {},
        onUpdateWeights = {},
        onUpdatePreferredSource = {},
        onToggleEducationCompleted = {},
        onTogglePersonalGoal = {},
        onRefreshDiagnostics = {},
        onToggleLiveListening = {},
        onClearSnackbar = { previewAppState = previewAppState.copy(snackbarMessage = null) },
    )
}

@Composable
private fun SteeringWheelIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.13f
        val radius = (size.minDimension - stroke) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = stroke),
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.12f,
            center = center,
        )
        drawLine(
            color = color,
            start = Offset(center.x - radius * 0.85f, center.y),
            end = Offset(center.x + radius * 0.85f, center.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x, center.y + radius * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun PlayTriangleIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.1f)
            lineTo(size.width * 0.9f, size.height * 0.5f)
            lineTo(size.width * 0.2f, size.height * 0.9f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Preview(
    name = "Pleos Connect Landscape",
    showBackground = true,
    widthDp = 1280,
    heightDp = 720,
)
@Composable
private fun DriveCoachDashboardLandscapePreview() {
    DriveCoachTheme {
        DriveCoachDashboardScreen()
    }
}

@Preview(
    name = "Mobile Portrait",
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
)
@Composable
private fun DriveCoachDashboardPortraitPreview() {
    DriveCoachTheme {
        DriveCoachDashboardScreen()
    }
}
