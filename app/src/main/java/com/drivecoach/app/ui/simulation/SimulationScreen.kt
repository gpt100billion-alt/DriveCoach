package com.drivecoach.app.ui.simulation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivecoach.app.data.simulation.DemoScenarioCatalog
import com.drivecoach.app.data.simulation.DemoScenarioSpec
import com.drivecoach.app.domain.model.DataSourceType
import com.drivecoach.app.domain.model.SessionRecordState
import com.drivecoach.app.ui.SimulationPlaybackState
import com.drivecoach.app.ui.components.DataSourceBadge
import com.drivecoach.app.ui.components.EventTimelineItemCard
import com.drivecoach.app.ui.components.StatusPillBadge
import com.drivecoach.app.ui.components.TelemetryTimeSeriesChart
import com.drivecoach.app.ui.theme.CardBorder
import com.drivecoach.app.ui.theme.ElevatedSurface
import com.drivecoach.app.ui.theme.GaugeTrack
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
 * 데모 시나리오 및 저장 주행 리플레이 재생 화면 (섹션 17 & 18)
 * - 주차(P) 상태에서만 실행 가능하며, 시뮬레이션 데이터임을 명확히 표시합니다.
 * - 재생 진행에 따라 실시간으로 그래프와 감지 이벤트가 생성되고 완료 시 분석 리포트로 이동합니다.
 */
@Composable
fun SimulationScreen(
    simulationState: SimulationPlaybackState,
    isWideLayout: Boolean,
    onSelectScenario: (String) -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onRestart: () -> Unit,
    onCompleteImmediately: () -> Unit,
    onChangeSpeedMultiplier: (Int) -> Unit,
    onOpenCompletedReport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scenarios = DemoScenarioCatalog.scenarios
    val selectedSpec = DemoScenarioCatalog.findSpec(simulationState.selectedScenarioId)
    val progressRatio = if (simulationState.totalSamplesCount > 1) {
        (simulationState.currentSampleIndex.toFloat() / (simulationState.totalSamplesCount - 1)).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 상단 안내 배너 (시뮬레이션 데이터 명시 및 주차 전용 안내)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SecondaryBackground,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "시뮬레이션 데모 재생기 (주차 상태 전용)",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                        )
                        StatusPillBadge(
                            text = "상태: ${simulationState.recordState.koreanLabel}",
                            accentColor = when (simulationState.recordState) {
                                SessionRecordState.RECORDING -> PrimaryAccent
                                SessionRecordState.COMPLETED -> StatusSuccess
                                SessionRecordState.PAUSED -> StatusWarning
                                else -> SecondaryAccent
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "실제 도로 주행이 아닌 가상 시나리오 시뮬레이션입니다. 시나리오를 바꾸면 대시보드·그래프·이벤트·코칭 리포트가 함께 변화합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                DataSourceBadge(dataSource = DataSourceType.SIMULATED)
            }
        }

        if (isWideLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 좌측: 5개 데모 시나리오 선택 목록
                Column(
                    modifier = Modifier.weight(0.42f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ScenarioSelectorPanel(
                        scenarios = scenarios,
                        selectedScenarioId = simulationState.selectedScenarioId,
                        onSelectScenario = onSelectScenario,
                    )
                }

                // 우측: 재생 컨트롤 + 실시간 텔레메트리 게이지 + 실시간 생성 그래프
                Column(
                    modifier = Modifier.weight(0.58f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PlaybackControlAndLiveMetricsCard(
                        spec = selectedSpec,
                        simulationState = simulationState,
                        progressRatio = progressRatio,
                        onStartOrResume = onStartOrResume,
                        onPause = onPause,
                        onRestart = onRestart,
                        onCompleteImmediately = onCompleteImmediately,
                        onChangeSpeedMultiplier = onChangeSpeedMultiplier,
                        onOpenCompletedReport = onOpenCompletedReport,
                    )

                    ProgressiveSimulationCharts(simulationState = simulationState)
                }
            }
        } else {
            ScenarioSelectorPanel(
                scenarios = scenarios,
                selectedScenarioId = simulationState.selectedScenarioId,
                onSelectScenario = onSelectScenario,
            )

            PlaybackControlAndLiveMetricsCard(
                spec = selectedSpec,
                simulationState = simulationState,
                progressRatio = progressRatio,
                onStartOrResume = onStartOrResume,
                onPause = onPause,
                onRestart = onRestart,
                onCompleteImmediately = onCompleteImmediately,
                onChangeSpeedMultiplier = onChangeSpeedMultiplier,
                onOpenCompletedReport = onOpenCompletedReport,
            )

            ProgressiveSimulationCharts(simulationState = simulationState)
        }
    }
}

@Composable
private fun ScenarioSelectorPanel(
    scenarios: List<DemoScenarioSpec>,
    selectedScenarioId: String,
    onSelectScenario: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "1. 데모 시나리오 선택 (5종)",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Text(
                text = "심사위원 시연 흐름에 맞춰 운전 습관별 시나리오를 선택하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            scenarios.forEach { spec ->
                val isSelected = spec.id == selectedScenarioId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectScenario(spec.id) },
                    color = if (isSelected) ElevatedSurface else MainBackground.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) PrimaryAccent else CardBorder,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = spec.codeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) PrimaryAccent else TextMuted,
                            )
                            Text(
                                text = "${spec.durationSec}초",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                            )
                        }
                        Text(
                            text = spec.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = spec.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "예상 결과: ${spec.expectedBehaviorSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryAccent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackControlAndLiveMetricsCard(
    spec: DemoScenarioSpec,
    simulationState: SimulationPlaybackState,
    progressRatio: Float,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onRestart: () -> Unit,
    onCompleteImmediately: () -> Unit,
    onChangeSpeedMultiplier: (Int) -> Unit,
    onOpenCompletedReport: (String) -> Unit,
) {
    val currentSample = simulationState.currentSample
    val speedKph = currentSample?.speedKph ?: 0f
    val accelMps2 = currentSample?.longitudinalAccelerationMps2 ?: 0f
    val roadWheelDeg = currentSample?.roadWheelAngleDeg ?: 0f
    val eventCount = simulationState.progressiveEvents.size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = simulationState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryAccent,
                    )
                }

                // 배속 선택 버튼 (1x / 2x / 5x)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 5).forEach { mult ->
                        val active = simulationState.playbackSpeedMultiplier == mult
                        Surface(
                            modifier = Modifier.clickable { onChangeSpeedMultiplier(mult) },
                            color = if (active) PrimaryAccent else ElevatedSurface,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = "${mult}x 배속",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) MainBackground else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            // 진행률 바
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "재생 시간: ${simulationState.currentSampleIndex}초 / ${simulationState.totalSamplesCount - 1}초",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Text(
                        text = "진행률 ${(progressRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = SecondaryAccent,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(GaugeTrack),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressRatio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.horizontalGradient(listOf(PleosBlueDeep, PrimaryAccent, SecondaryAccent)),
                            ),
                    )
                }
            }

            // 실시간 수치 4종 (현재 속도, 추정 가속도, 전륜 조향각(데모), 감지 이벤트 수)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LiveMetricTile(
                    label = "현재 차량 속도",
                    valueText = String.format(Locale.US, "%.1f km/h", speedKph),
                    subText = "시뮬레이션 차속",
                    accentColor = PrimaryAccent,
                    modifier = Modifier.weight(1f),
                )
                LiveMetricTile(
                    label = "추정 종방향 가속도",
                    valueText = String.format(Locale.US, "%+.2f m/s²", accelMps2),
                    subText = "속도 미분 (Δv/Δt)",
                    accentColor = when {
                        accelMps2 <= -2.35f -> StatusCritical
                        accelMps2 >= 2.25f -> StatusWarning
                        else -> SecondaryAccent
                    },
                    modifier = Modifier.weight(1f),
                )
                LiveMetricTile(
                    label = "전륜 조향각 (데모)",
                    valueText = String.format(Locale.US, "%+.1f°", roadWheelDeg),
                    subText = "핸들 약 ${String.format(Locale.US, "%+.0f°", roadWheelDeg * 14.5f)}",
                    accentColor = SecondaryAccent,
                    modifier = Modifier.weight(1f),
                )
                LiveMetricTile(
                    label = "감지 이벤트 후보",
                    valueText = "${eventCount}건",
                    subText = "실시간 규칙 탐지",
                    accentColor = if (eventCount > 0) StatusWarning else StatusSuccess,
                    modifier = Modifier.weight(1f),
                )
            }

            // 조작 버튼 영역 (재생 / 일시정지 / 다시 시작 / 즉시 완료 및 분석)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (simulationState.recordState == SessionRecordState.RECORDING) {
                    Button(
                        onClick = onPause,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusWarning,
                            contentColor = MainBackground,
                        ),
                    ) {
                        Text("일시정지", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onStartOrResume,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryAccent,
                            contentColor = MainBackground,
                        ),
                    ) {
                        Text(
                            text = if (simulationState.recordState == SessionRecordState.PAUSED) "이어서 재생" else "시뮬레이션 재생",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text("다시 시작", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                }

                OutlinedButton(
                    onClick = onCompleteImmediately,
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SecondaryAccent),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text("즉시 완료 · 분석 리포트", color = SecondaryAccent, style = MaterialTheme.typography.titleMedium)
                }
            }

            if (simulationState.lastCompletedSessionId != null) {
                Button(
                    onClick = { onOpenCompletedReport(simulationState.lastCompletedSessionId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElevatedSurface,
                        contentColor = SecondaryAccent,
                    ),
                ) {
                    Text("방금 생성된 분석 리포트 다시 열기 →", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun LiveMetricTile(
    label: String,
    valueText: String,
    subText: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleLarge,
            color = accentColor,
            fontWeight = FontWeight.Bold,
        )
        Text(text = subText, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun ProgressiveSimulationCharts(
    simulationState: SimulationPlaybackState,
) {
    val samples = simulationState.progressiveSamples
    val events = simulationState.progressiveEvents

    TelemetryTimeSeriesChart(
        title = "실시간 시뮬레이션 속도 변화 (Progressive Speed Chart)",
        unitLabel = "km/h",
        sourceLabel = "시뮬레이션 데이터 (SIMULATED)",
        samples = samples,
        valueSelector = { it.speedKph },
        minY = 0f,
        maxY = 80f,
        lineColor = PrimaryAccent,
        highlightEvents = events,
        emptyMessage = "'시뮬레이션 재생' 버튼을 누르면 시간에 따른 속도 그래프가 실시간으로 그려집니다.",
        chartHeight = 155.dp,
    )

    TelemetryTimeSeriesChart(
        title = "실시간 추정 종방향 가속·감속도 및 전륜 조향각",
        unitLabel = "m/s²",
        sourceLabel = "속도 미분 추정치 (Δv/Δt)",
        samples = samples,
        valueSelector = { it.longitudinalAccelerationMps2 },
        minY = -4.5f,
        maxY = 4.5f,
        lineColor = SecondaryAccent,
        zeroBaseline = 0f,
        highlightEvents = events,
        emptyMessage = "재생이 시작되면 종방향 가감속 변화가 실시간으로 표시됩니다.",
        chartHeight = 140.dp,
    )

    if (events.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SecondaryBackground,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "실시간 감지된 주행 이벤트 후보 (${events.size}건)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                events.forEach { ev ->
                    EventTimelineItemCard(
                        event = ev,
                        isSelected = false,
                        onClick = {},
                    )
                }
            }
        }
    }
}
