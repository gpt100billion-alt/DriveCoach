package com.drivecoach.app.ui.history

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivecoach.app.domain.analysis.MultiSessionTrendSummary
import com.drivecoach.app.domain.model.DrivingEventType
import com.drivecoach.app.domain.model.DrivingSession
import com.drivecoach.app.ui.components.DataSourceBadge
import com.drivecoach.app.ui.components.ScoreTrendSparklineChart
import com.drivecoach.app.ui.components.StatusPillBadge
import com.drivecoach.app.ui.components.colorForScore
import com.drivecoach.app.ui.components.formatDurationKorean
import com.drivecoach.app.ui.components.formatTimestampKorean
import com.drivecoach.app.ui.theme.CardBorder
import com.drivecoach.app.ui.theme.ElevatedSurface
import com.drivecoach.app.ui.theme.MainBackground
import com.drivecoach.app.ui.theme.PrimaryAccent
import com.drivecoach.app.ui.theme.SecondaryAccent
import com.drivecoach.app.ui.theme.SecondaryBackground
import com.drivecoach.app.ui.theme.StatusSuccess
import com.drivecoach.app.ui.theme.StatusWarning
import com.drivecoach.app.ui.theme.TextMuted
import com.drivecoach.app.ui.theme.TextPrimary
import com.drivecoach.app.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 주행 기록 및 전후 주행 비교 화면 (섹션 8, 33.5, 36.1, 36.2)
 * - 전체 주행 기록 날짜순 조회, 평균 점수, 최근 3·5·10회 추세, 총 분석 주행 시간
 * - 두 주행 기록(예: 개선 전 vs 개선 후) 선택 비교 기능
 * - 기록이 없을 때 명확한 빈 화면 안내 제공
 */
@Composable
fun HistoryScreen(
    sessions: List<DrivingSession>,
    trendSummaries: List<MultiSessionTrendSummary>,
    compareBaseSessionId: String?,
    compareTargetSessionId: String?,
    isWideLayout: Boolean,
    onSelectSessionForDetail: (String) -> Unit,
    onSelectComparisonSessions: (String, String) -> Unit,
    onNavigateToSimulation: () -> Unit,
    onRestoreDemoSessions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = SecondaryBackground,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.padding(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "아직 분석된 주행 기록이 없습니다.",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = "아직 분석된 주행 기록이 없습니다. 데모 시나리오를 실행하거나 차량 데이터를 연결하면 분석 결과를 확인할 수 있습니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onNavigateToSimulation,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryAccent,
                                contentColor = MainBackground,
                            ),
                        ) {
                            Text("데모 시나리오 실행하기", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onRestoreDemoSessions,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElevatedSurface,
                                contentColor = SecondaryAccent,
                            ),
                        ) {
                            Text("기본 데모 기록 5건 복원", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        return
    }

    val sortedSessions = sessions.sortedByDescending { it.startTimeMs }
    val validScores = sortedSessions.mapNotNull { it.analysisResult?.overallScore }
    val avgScore = if (validScores.isNotEmpty()) validScores.average().roundToInt() else null
    val totalDurationSec = sortedSessions.sumOf { it.durationSec }
    val totalDistanceKm = sortedSessions.sumOf { it.distanceKm.toDouble() }.toFloat()

    val baseSession = sortedSessions.find { it.id == compareBaseSessionId }
        ?: sortedSessions.getOrNull(1)
        ?: sortedSessions.first()
    val targetSession = sortedSessions.find { it.id == compareTargetSessionId }
        ?: sortedSessions.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 상단 요약 통계 & 추세 스파크라인
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SecondaryBackground,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "주행 기록 및 장기 성장 추세",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                        )
                        Text(
                            text = "총 ${sortedSessions.size}회 기록 · 누적 주행 시간 ${formatDurationKorean(totalDurationSec)} · 누적 거리 ${String.format(Locale.US, "%.1f km", totalDistanceKm)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    StatusPillBadge(
                        text = if (avgScore != null) "전체 평균 ${avgScore}점" else "평균 미산출",
                        accentColor = colorForScore(avgScore),
                    )
                }

                // 최근 3회 / 5회 / 10회 추세 요약 카드
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    trendSummaries.forEach { trend ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(ElevatedSurface)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = trend.windowLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = PrimaryAccent,
                            )
                            Text(
                                text = if (trend.averageOverallScore != null) {
                                    "평균 ${trend.averageOverallScore}점"
                                } else {
                                    "기록 부족"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "가속 ${trend.averageAccelScore ?: "-"} · 제동 ${trend.averageBrakeScore ?: "-"} · 조향 ${trend.averageSteerScore ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            Text(
                                text = trend.dominantHabitText,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryAccent,
                            )
                        }
                    }
                }

                // 점수 변화 스파크라인 차트 (과거 -> 최신 순)
                val chronologicalScores = sortedSessions.reversed().mapNotNull { s ->
                    s.analysisResult?.overallScore?.let { score ->
                        s.title.take(8) to score
                    }
                }
                if (chronologicalScores.isNotEmpty()) {
                    ScoreTrendSparklineChart(scoresChronological = chronologicalScores)
                }
            }
        }

        // 시연 5: 주행 습관 전후 비교 (Before & After Comparison)
        SessionComparisonCard(
            allSessions = sortedSessions,
            baseSession = baseSession,
            targetSession = targetSession,
            onSelectComparison = onSelectComparisonSessions,
        )

        // 전체 주행 기록 리스트
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SecondaryBackground,
            shape = RoundedCornerShape(20.dp),
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
                        text = "전체 주행 기록 (최신순 정렬)",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "항목을 선택하면 상세 그래프 리포트로 이동합니다",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                }

                sortedSessions.forEach { session ->
                    val score = session.analysisResult?.overallScore
                    val candidateCount = session.detectedEvents.count {
                        it.eventType != DrivingEventType.DATA_GAP_WARNING
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSessionForDetail(session.id) },
                        color = ElevatedSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CardBorder),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    DataSourceBadge(
                                        dataSource = session.dataSource,
                                        showKoreanTitle = isWideLayout,
                                    )
                                }
                                Text(
                                    text = "${formatTimestampKorean(session.startTimeMs)} · ${formatDurationKorean(session.durationSec)} · ${String.format(Locale.US, "%.2f km", session.distanceKm)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                                Text(
                                    text = session.analysisResult?.overallSummary ?: session.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryAccent,
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                StatusPillBadge(
                                    text = if (score != null) "${score}점" else "데이터 부족",
                                    accentColor = colorForScore(score),
                                )
                                Text(
                                    text = "개선 후보 ${candidateCount}건",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (candidateCount == 0) StatusSuccess else StatusWarning,
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
private fun SessionComparisonCard(
    allSessions: List<DrivingSession>,
    baseSession: DrivingSession,
    targetSession: DrivingSession,
    onSelectComparison: (String, String) -> Unit,
) {
    val baseRes = baseSession.analysisResult
    val targetRes = targetSession.analysisResult
    val baseScore = baseRes?.overallScore ?: 0
    val targetScore = targetRes?.overallScore ?: 0
    val diff = targetScore - baseScore

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "전후 주행 습관 비교 (시연 5 · 어제의 주행 vs 오늘의 주행)",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "두 주행 기록을 선택해 코칭 전후 점수 및 조작 이벤트 감소 폭을 비교합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                StatusPillBadge(
                    text = if (diff >= 0) "종합 +${diff}점 변화" else "종합 ${diff}점 변화",
                    accentColor = if (diff >= 0) StatusSuccess else StatusWarning,
                )
            }

            // 비교 기준(Before) 선택 칩
            Text(
                text = "비교 기준 주행 (Before) 선택:",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allSessions.take(4).forEach { s ->
                    val selected = s.id == baseSession.id
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectComparison(s.id, targetSession.id) },
                        color = if (selected) ElevatedSurface else MainBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (selected) StatusWarning else CardBorder),
                    ) {
                        Text(
                            text = "${s.title.take(10)} (${s.analysisResult?.overallScore ?: "-"}점)",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) StatusWarning else TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            maxLines = 1,
                        )
                    }
                }
            }

            // 비교 대상(After) 선택 칩
            Text(
                text = "비교 대상 주행 (After) 선택:",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allSessions.take(4).forEach { s ->
                    val selected = s.id == targetSession.id
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectComparison(baseSession.id, s.id) },
                        color = if (selected) ElevatedSurface else MainBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (selected) SecondaryAccent else CardBorder),
                    ) {
                        Text(
                            text = "${s.title.take(10)} (${s.analysisResult?.overallScore ?: "-"}점)",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) SecondaryAccent else TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            maxLines = 1,
                        )
                    }
                }
            }

            // 항목별 비교 수치
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ComparisonMetricTile(
                    title = "가속 안정성",
                    beforeVal = baseRes?.accelerationEval?.score,
                    afterVal = targetRes?.accelerationEval?.score,
                    modifier = Modifier.weight(1f),
                )
                ComparisonMetricTile(
                    title = "감속·제동 안정성",
                    beforeVal = baseRes?.brakingEval?.score,
                    afterVal = targetRes?.brakingEval?.score,
                    modifier = Modifier.weight(1f),
                )
                ComparisonMetricTile(
                    title = "조향 안정성",
                    beforeVal = baseRes?.steeringEval?.score,
                    afterVal = targetRes?.steeringEval?.score,
                    modifier = Modifier.weight(1f),
                )
                ComparisonMetricTile(
                    title = "개선 후보 이벤트",
                    beforeVal = baseSession.detectedEvents.size,
                    afterVal = targetSession.detectedEvents.size,
                    unit = "건",
                    lowerIsBetter = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ComparisonMetricTile(
    title: String,
    beforeVal: Int?,
    afterVal: Int?,
    unit: String = "점",
    lowerIsBetter: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val delta = if (beforeVal != null && afterVal != null) afterVal - beforeVal else null
    val isImproved = if (delta == null) false else if (lowerIsBetter) delta <= 0 else delta >= 0

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ElevatedSurface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Text(
            text = "${beforeVal ?: "-"}$unit → ${afterVal ?: "-"}$unit",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        if (delta != null) {
            Text(
                text = if (delta > 0) "+$delta$unit" else "$delta$unit",
                style = MaterialTheme.typography.labelMedium,
                color = if (isImproved) StatusSuccess else StatusWarning,
            )
        }
    }
}
