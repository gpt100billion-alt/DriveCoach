package com.drivecoach.app.ui.coaching

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivecoach.app.domain.analysis.RuleBasedCoachingGenerator
import com.drivecoach.app.domain.model.CoachingInsight
import com.drivecoach.app.domain.model.DrivingSession
import com.drivecoach.app.domain.model.PersonalHabitGoal
import com.drivecoach.app.ui.components.DataSourceBadge
import com.drivecoach.app.ui.components.StatusPillBadge
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

/**
 * 개인 맞춤형 AI 코칭 및 개인 습관 목표 화면 (섹션 3.5, 10, 36.3)
 * - 7단계 구조화 코칭 카드 (제목 / 관찰 패턴 / 분석 데이터 / 판단 조건 / 해석상 주의사항 / 개선 방향 / 다음 주행 확인 항목)
 * - 규칙 기반 설명형 엔진임을 정직하게 고지하며, 생성형 AI 과장 표현을 사용하지 않습니다.
 */
@Composable
fun CoachingScreen(
    selectedSession: DrivingSession?,
    personalTrendInsights: List<CoachingInsight>,
    personalGoals: List<PersonalHabitGoal>,
    onToggleGoal: (String) -> Unit,
    onOpenEducationTopic: (String) -> Unit,
    onNavigateToSimulation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionInsights = selectedSession?.analysisResult?.coachingInsights.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 정직한 AI 엔진 설계 고지 카드 (섹션 3.5)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SecondaryBackground,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "개인 맞춤형 운전 습관 코칭",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                        )
                        StatusPillBadge(
                            text = "설명 가능한 규칙 기반 엔진 v1.0",
                            accentColor = SecondaryAccent,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = RuleBasedCoachingGenerator.engineDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryAccent,
                    )
                    Text(
                        text = "단 한 번의 브레이크나 조향 조작만으로 위험 운전을 단정하지 않으며, 관찰된 센서 수치와 판단 조건을 투명하게 제시합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                if (selectedSession != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    DataSourceBadge(dataSource = selectedSession.dataSource)
                }
            }
        }

        // 개인 운전 습관 목표 설정 및 달성 여부 (섹션 36.3)
        PersonalGoalsSectionCard(
            goals = personalGoals,
            latestSession = selectedSession,
            onToggleGoal = onToggleGoal,
        )

        // 장기 습관 추세 코칭 ("어제의 나와 오늘의 나를 비교")
        if (personalTrendInsights.isNotEmpty()) {
            Text(
                text = "장기 운전 습관 추세 코칭 (최근 3·5·10회 분석)",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            personalTrendInsights.forEach { insight ->
                StructuredCoachingCard(
                    insight = insight,
                    onOpenEducationTopic = onOpenEducationTopic,
                )
            }
        }

        // 선택된 주행 세션의 7단계 상세 코칭 카드
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectedSession != null) {
                    "선택 주행 분석 코칭: ${selectedSession.title}"
                } else {
                    "주행 세션 코칭"
                },
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            if (selectedSession == null) {
                Button(
                    onClick = onNavigateToSimulation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryAccent,
                        contentColor = MainBackground,
                    ),
                ) {
                    Text("시뮬레이션 실행", fontWeight = FontWeight.Bold)
                }
            }
        }

        sessionInsights.forEach { insight ->
            StructuredCoachingCard(
                insight = insight,
                onOpenEducationTopic = onOpenEducationTopic,
            )
        }
    }
}

@Composable
private fun PersonalGoalsSectionCard(
    goals: List<PersonalHabitGoal>,
    latestSession: DrivingSession?,
    onToggleGoal: (String) -> Unit,
) {
    val latestRes = latestSession?.analysisResult

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
                    text = "나의 운전 습관 집중 목표 (개인 목표 설정)",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = "체크박스를 눌러 다음 주행 집중 목표를 활성화하세요",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
            }

            goals.forEach { goal ->
                val currentMetricScore = when (goal.targetMetricKey) {
                    "BRAKING" -> latestRes?.brakingEval?.score
                    "ACCELERATION" -> latestRes?.accelerationEval?.score
                    "STEERING" -> latestRes?.steeringEval?.score
                    else -> latestRes?.overallScore
                }
                val achieved = currentMetricScore != null && currentMetricScore >= goal.targetScore

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ElevatedSurface)
                        .clickable { onToggleGoal(goal.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = goal.isEnabled,
                            onCheckedChange = { onToggleGoal(goal.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PrimaryAccent,
                                checkmarkColor = MainBackground,
                            ),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = goal.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            Text(
                                text = goal.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    StatusPillBadge(
                        text = if (currentMetricScore == null) {
                            "데이터 대기"
                        } else if (achieved) {
                            "달성 (${currentMetricScore}점 ≥ ${goal.targetScore}점)"
                        } else {
                            "진행 중 (${currentMetricScore}점 / 목표 ${goal.targetScore}점)"
                        },
                        accentColor = if (achieved) StatusSuccess else StatusWarning,
                    )
                }
            }
        }
    }
}

/**
 * 섹션 10의 7개 필수 요소를 모두 갖춘 코칭 카드
 */
@Composable
private fun StructuredCoachingCard(
    insight: CoachingInsight,
    onOpenEducationTopic: (String) -> Unit,
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
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. 코칭 제목 & 우선순위
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "1. 코칭 제목: ${insight.title}",
                    style = MaterialTheme.typography.titleLarge,
                    color = PrimaryAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StatusPillBadge(text = insight.priorityLabel, accentColor = SecondaryAccent)
            }

            // 2. 관찰된 운전 패턴
            CoachingDetailBlock(
                stepLabel = "2. 관찰된 운전 패턴",
                content = insight.observedPattern,
                textColor = TextPrimary,
            )

            // 3. 분석에 사용한 데이터
            CoachingDetailBlock(
                stepLabel = "3. 분석에 사용한 데이터 근거",
                content = insight.evidenceData,
                textColor = SecondaryAccent,
            )

            // 4. 판단 조건
            CoachingDetailBlock(
                stepLabel = "4. 규칙 기반 판단 조건",
                content = insight.judgmentRule,
                textColor = TextSecondary,
            )

            // 5. 해석상 주의사항
            CoachingDetailBlock(
                stepLabel = "5. 해석상 주의사항 (과잉 단정 방지)",
                content = insight.cautionNote,
                textColor = StatusWarning,
            )

            // 6. 개선 방향
            CoachingDetailBlock(
                stepLabel = "6. 구체적 개선 방향",
                content = insight.improvementDirection,
                textColor = TextPrimary,
            )

            // 7. 다음 주행에서 확인할 항목
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElevatedSurface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "7. 다음 주행에서 확인할 체크리스트",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryAccent,
                )
                insight.nextDriveChecklist.forEach { checkItem ->
                    Text(
                        text = "✓ $checkItem",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = insight.engineTypeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
                if (insight.recommendedTopicId != null) {
                    Button(
                        onClick = { onOpenEducationTopic(insight.recommendedTopicId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryAccent,
                            contentColor = MainBackground,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("추천 운전 교육 모듈 학습하기 →", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachingDetailBlock(
    stepLabel: String,
    content: String,
    textColor: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stepLabel,
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
    }
}
