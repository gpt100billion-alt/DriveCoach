package com.drivecoach.app.ui.report

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivecoach.app.domain.model.DrivingEventType
import com.drivecoach.app.domain.model.DrivingSession
import com.drivecoach.app.ui.components.CockpitScoreRing
import com.drivecoach.app.ui.components.DataSourceBadge
import com.drivecoach.app.ui.components.EventTimelineItemCard
import com.drivecoach.app.ui.components.MetricEvaluationCardRow
import com.drivecoach.app.ui.components.StatusPillBadge
import com.drivecoach.app.ui.components.TelemetryTimeSeriesChart
import com.drivecoach.app.ui.components.formatDurationKorean
import com.drivecoach.app.ui.components.formatTimestampKorean
import com.drivecoach.app.ui.theme.CardBorder
import com.drivecoach.app.ui.theme.ElevatedSurface
import com.drivecoach.app.ui.theme.MainBackground
import com.drivecoach.app.ui.theme.PrimaryAccent
import com.drivecoach.app.ui.theme.SecondaryAccent
import com.drivecoach.app.ui.theme.SecondaryBackground
import com.drivecoach.app.ui.theme.StatusCritical
import com.drivecoach.app.ui.theme.StatusWarning
import com.drivecoach.app.ui.theme.TextMuted
import com.drivecoach.app.ui.theme.TextPrimary
import com.drivecoach.app.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 주행 상세 분석 화면 (섹션 9, 16, 28, 36.6)
 * - A. 기본 정보 (시작/종료 시각, 주행 시간, 주행 거리, 평균/최대 속도, 데이터 출처, 분석 신뢰도)
 * - B. 시간에 따른 속도 그래프 (X축 시간, Y축 km/h, 이벤트 구간 하이라이트)
 * - C. 종방향 가속·감속 그래프 (속도 미분 추정치와 실제 가속도 센서 구분 명시)
 * - D. 조향 분석 그래프 (전륜 조향각 시계열 및 조향 변화량, 미지원 시 안내 및 데모 시험 버튼)
 * - E. 인터랙티브 이벤트 타임라인 (선택 시 근거 데이터 및 판단 규칙 표시)
 * - F. 종합 리포트 및 "왜 이 점수가 나왔습니까?" 점수 산출 설명
 */
@Composable
fun ReportDetailScreen(
    session: DrivingSession?,
    previousSession: DrivingSession?,
    allSessions: List<DrivingSession>,
    selectedEventId: String?,
    isWideLayout: Boolean,
    onSelectSession: (String) -> Unit,
    onSelectEvent: (String?) -> Unit,
    onReplaySession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onNavigateToSimulation: () -> Unit,
    onOpenEducationTopic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showScoreExplanation by remember { mutableStateOf(true) }

    if (session == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = SecondaryBackground,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.padding(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "선택된 주행 분석 리포트가 없습니다.",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "데모 시나리오를 실행하거나 주행 기록 목록에서 세션을 선택해 주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Button(
                        onClick = onNavigateToSimulation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryAccent,
                            contentColor = MainBackground,
                        ),
                    ) {
                        Text("시뮬레이션 화면으로 이동", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    val analysis = session.analysisResult
    val reliabilityPct = ((analysis?.dataQuality?.overallReliability ?: 0f) * 100).roundToInt()
    val prevScore = previousSession?.analysisResult?.overallScore
    val currScore = analysis?.overallScore
    val scoreDelta = if (currScore != null && prevScore != null) currScore - prevScore else null

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = SecondaryBackground,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("주행 기록 삭제 확인") },
            text = { Text("'${session.title}' 주행 기록과 분석 결과를 기기에서 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteSession(session.id)
                    }
                ) {
                    Text("삭제", color = StatusCritical, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소", color = TextSecondary)
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 상단 세션 전환 탭 및 액션 바
        SessionSelectorAndActionsBar(
            currentSession = session,
            allSessions = allSessions,
            onSelectSession = onSelectSession,
            onReplaySession = { onReplaySession(session.id) },
            onRequestDelete = { showDeleteConfirm = true },
        )

        // A. 기본 정보 및 종합 점수 요약 카드
        if (isWideLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SessionOverviewScoreCard(
                    session = session,
                    scoreDelta = scoreDelta,
                    reliabilityPct = reliabilityPct,
                    showScoreExplanation = showScoreExplanation,
                    onToggleScoreExplanation = { showScoreExplanation = !showScoreExplanation },
                    modifier = Modifier.weight(0.42f),
                )
                BasicSessionMetadataAndMetricsCard(
                    session = session,
                    modifier = Modifier.weight(0.58f),
                )
            }
        } else {
            SessionOverviewScoreCard(
                session = session,
                scoreDelta = scoreDelta,
                reliabilityPct = reliabilityPct,
                showScoreExplanation = showScoreExplanation,
                onToggleScoreExplanation = { showScoreExplanation = !showScoreExplanation },
            )
            BasicSessionMetadataAndMetricsCard(session = session)
        }

        // B. 시간에 따른 속도 그래프 (X축: 시간, Y축: 속도 km/h)
        TelemetryTimeSeriesChart(
            title = "B. 시간에 따른 차량 속도 그래프 (Speed Profile)",
            unitLabel = "km/h",
            sourceLabel = "${session.dataSource.badgeText} (${session.dataSource.koreanTitle})",
            samples = session.telemetrySamples,
            valueSelector = { it.speedKph },
            minY = 0f,
            maxY = 85f,
            lineColor = PrimaryAccent,
            highlightEvents = session.detectedEvents,
            selectedEventId = selectedEventId,
            emptyMessage = "속도 데이터가 포함되지 않은 세션입니다.",
        )

        // C. 종방향 가속·감속 그래프 (속도 미분 추정치 명시)
        TelemetryTimeSeriesChart(
            title = "C. 종방향 가속·감속 그래프 (속도 변화량 미분 추정 Δv/Δt)",
            unitLabel = "m/s²",
            sourceLabel = "센서 직측 가속도가 아닌 차속 미분 추정 가속도 (양수: 가속 / 음수: 감속)",
            samples = session.telemetrySamples,
            valueSelector = { it.longitudinalAccelerationMps2 },
            minY = -4.5f,
            maxY = 4.5f,
            lineColor = SecondaryAccent,
            zeroBaseline = 0f,
            highlightEvents = session.detectedEvents.filter {
                it.eventType == DrivingEventType.ABRUPT_ACCELERATION_CANDIDATE ||
                    it.eventType == DrivingEventType.LATE_HARD_BRAKING_CANDIDATE
            },
            selectedEventId = selectedEventId,
            emptyMessage = "속도 변화로부터 가속도를 추정할 수 있는 유효 샘플이 부족합니다.",
        )

        // D. 조향 분석 (조향 데이터 접근 가능 시 그래프 표시, 불가 시 안내 및 데모 모드 시험 버튼 표시)
        val hasSteeringData = analysis?.dataQuality?.hasRoadWheelAngleData == true ||
            analysis?.dataQuality?.hasSteeringWheelAngleData == true

        if (hasSteeringData) {
            TelemetryTimeSeriesChart(
                title = "D. 시간에 따른 전륜 조향각 분석 (좌측 음수 - / 우측 양수 +)",
                unitLabel = "deg (°)",
                sourceLabel = "${session.dataSource.badgeText} · 전륜 조향각 (핸들 회전각과 구분됨)",
                samples = session.telemetrySamples,
                valueSelector = { it.effectiveRoadWheelAngleDeg },
                minY = -22f,
                maxY = 22f,
                lineColor = StatusWarning,
                zeroBaseline = 0f,
                highlightEvents = session.detectedEvents.filter {
                    it.eventType == DrivingEventType.LOW_SPEED_STEERING_CORRECTION_CANDIDATE
                },
                selectedEventId = selectedEventId,
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SecondaryBackground,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "D. 조향 분석",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "현재 개발 환경에서 조향각 데이터에 접근할 수 없습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusWarning,
                        )
                        Text(
                            text = "시뮬레이션 화면에서 'Steering Correction' 데모 시나리오를 선택하면 저속 회전 수정 조향 분석을 시험할 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onNavigateToSimulation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryAccent,
                            contentColor = MainBackground,
                        ),
                    ) {
                        Text("데모 모드에서 시험하기", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // E. 이벤트 타임라인 & F. 종합 코칭 리포트
        if (isWideLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EventTimelineSectionCard(
                    events = session.detectedEvents,
                    selectedEventId = selectedEventId,
                    onSelectEvent = onSelectEvent,
                    modifier = Modifier.weight(0.5f),
                )
                ComprehensiveSummaryReportCard(
                    session = session,
                    onOpenEducationTopic = onOpenEducationTopic,
                    modifier = Modifier.weight(0.5f),
                )
            }
        } else {
            EventTimelineSectionCard(
                events = session.detectedEvents,
                selectedEventId = selectedEventId,
                onSelectEvent = onSelectEvent,
            )
            ComprehensiveSummaryReportCard(
                session = session,
                onOpenEducationTopic = onOpenEducationTopic,
            )
        }
    }
}

@Composable
private fun SessionSelectorAndActionsBar(
    currentSession: DrivingSession,
    allSessions: List<DrivingSession>,
    onSelectSession: (String) -> Unit,
    onReplaySession: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSession.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "${formatTimestampKorean(currentSession.startTimeMs)} · ${currentSession.subtitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DataSourceBadge(dataSource = currentSession.dataSource)
                    OutlinedButton(
                        onClick = onReplaySession,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PrimaryAccent),
                    ) {
                        Text("시뮬레이션 재생", color = PrimaryAccent, style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = onRequestDelete,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder),
                    ) {
                        Text("기록 삭제", color = StatusCritical, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // 다른 세션 빠른 전환 칩
            if (allSessions.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    allSessions.take(5).forEach { item ->
                        val selected = item.id == currentSession.id
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectSession(item.id) },
                            color = if (selected) ElevatedSurface else MainBackground,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (selected) PrimaryAccent else CardBorder),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) PrimaryAccent else TextPrimary,
                                    maxLines = 1,
                                )
                                Text(
                                    text = "${item.analysisResult?.overallScore ?: "-"}점 · 이벤트 ${item.detectedEvents.size}건",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionOverviewScoreCard(
    session: DrivingSession,
    scoreDelta: Int?,
    reliabilityPct: Int,
    showScoreExplanation: Boolean,
    onToggleScoreExplanation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val analysis = session.analysisResult
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "종합 운전 습관 점수",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                StatusPillBadge(
                    text = if (showScoreExplanation) "산출 원리 접기 ▲" else "왜 이 점수가 나왔나요? ▼",
                    accentColor = PrimaryAccent,
                    modifier = Modifier.clickable(onClick = onToggleScoreExplanation),
                )
            }

            CockpitScoreRing(
                score = analysis?.overallScore,
                gradeLabel = analysis?.scoreGradeLabel ?: "분석 대기",
                deltaScore = scoreDelta,
                reliabilityPercent = reliabilityPct,
                gaugeSize = 172.dp,
            )

            Text(
                text = analysis?.overallSummary ?: "분석 요약 정보가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(visible = showScoreExplanation && analysis != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ElevatedSurface)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "점수 산출 근거 (왜 이 점수가 나왔습니까?)",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryAccent,
                    )
                    Text(
                        text = analysis?.scoreExplanation ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                    )
                    analysis?.let { res ->
                        listOf(res.accelerationEval, res.brakingEval, res.steeringEval, res.consistencyEval)
                            .flatMap { eval -> eval.deductionReasons.map { "${eval.metricName}: $it" } }
                            .forEach { reason ->
                                Text(
                                    text = "• $reason",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicSessionMetadataAndMetricsCard(
    session: DrivingSession,
    modifier: Modifier = Modifier,
) {
    val analysis = session.analysisResult
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "A. 주행 기본 정보 및 항목별 평가",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )

            // 기본 메타데이터 그리드
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetadataChip(
                    label = "주행 시간 / 거리",
                    value = "${formatDurationKorean(session.durationSec)} · ${String.format(Locale.US, "%.2f km", session.distanceKm)}",
                    modifier = Modifier.weight(1f),
                )
                MetadataChip(
                    label = "평균 / 최대 속도",
                    value = String.format(Locale.US, "%.1f / %.1f km/h", session.averageSpeedKph, session.maximumSpeedKph),
                    modifier = Modifier.weight(1f),
                )
                MetadataChip(
                    label = "엔진 버전 / 품질",
                    value = "${analysis?.analysisVersion ?: "-"} (${analysis?.dataQuality?.validSamples ?: 0}샘플)",
                    modifier = Modifier.weight(1f),
                )
            }

            if (analysis != null) {
                MetricEvaluationCardRow(evaluation = analysis.accelerationEval)
                MetricEvaluationCardRow(evaluation = analysis.brakingEval)
                MetricEvaluationCardRow(evaluation = analysis.steeringEval)
                MetricEvaluationCardRow(evaluation = analysis.consistencyEval)
            }
        }
    }
}

@Composable
private fun MetadataChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ElevatedSurface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EventTimelineSectionCard(
    events: List< com.drivecoach.app.domain.model.DrivingEvent>,
    selectedEventId: String?,
    onSelectEvent: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "E. 주행 이벤트 타임라인 (${events.size}건)",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = "항목 클릭 시 근거 수치·규칙 표시",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
            }

            if (events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ElevatedSurface)
                        .padding(18.dp),
                ) {
                    Text(
                        text = "감지된 급가속, 급감속, 저속 수정 조향 후보 구간이 없습니다. 전 구간에서 안정적인 조작이 유지되었습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryAccent,
                    )
                }
            } else {
                events.forEach { ev ->
                    val selected = ev.id == selectedEventId
                    EventTimelineItemCard(
                        event = ev,
                        isSelected = selected,
                        onClick = { onSelectEvent(if (selected) null else ev.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ComprehensiveSummaryReportCard(
    session: DrivingSession,
    onOpenEducationTopic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val insights = session.analysisResult?.coachingInsights.orEmpty()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "F. 종합 리포트 및 다음 주행 개선 방향",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )

            insights.forEach { insight ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ElevatedSurface)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = insight.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = PrimaryAccent,
                            fontWeight = FontWeight.Bold,
                        )
                        StatusPillBadge(text = insight.priorityLabel, accentColor = SecondaryAccent)
                    }
                    Text(
                        text = "관찰 패턴: ${insight.observedPattern}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = "분석 근거: ${insight.evidenceData}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Text(
                        text = "개선 제안: ${insight.improvementDirection}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryAccent,
                    )
                    Text(
                        text = "주의사항: ${insight.cautionNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusWarning,
                    )
                    if (insight.recommendedTopicId != null) {
                        Button(
                            onClick = { onOpenEducationTopic(insight.recommendedTopicId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryAccent,
                                contentColor = MainBackground,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text("연계 운전 교육 시뮬레이션 열기 →", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
