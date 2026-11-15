package com.drivecoach.app.ui.education

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivecoach.app.domain.analysis.EducationCatalog
import com.drivecoach.app.domain.model.EducationTopic
import com.drivecoach.app.ui.components.InteractiveRoadConceptCanvas
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
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EducationScreen(
    selectedTopicId: String,
    completedTopicIds: Set<String>,
    onSelectTopic: (String) -> Unit,
    onToggleComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val topics = EducationCatalog.topics
    val currentTopic = EducationCatalog.findById(selectedTopicId)

    var simSpeedKph by remember(currentTopic.id) {
        mutableFloatStateOf(currentTopic.defaultSpeedParam)
    }
    var simControlStability by remember(currentTopic.id) {
        mutableFloatStateOf(currentTopic.defaultControlParam)
    }

    LaunchedEffect(currentTopic.id) {
        simSpeedKph = currentTopic.defaultSpeedParam
        simControlStability = currentTopic.defaultControlParam
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 840.dp

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EducationTopicListCard(
                        topics = topics,
                        selectedTopicId = currentTopic.id,
                        completedTopicIds = completedTopicIds,
                        onSelectTopic = onSelectTopic,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    EducationDetailContent(
                        topic = currentTopic,
                        isCompleted = currentTopic.id in completedTopicIds,
                        simSpeedKph = simSpeedKph,
                        simControlStability = simControlStability,
                        onSpeedChange = { simSpeedKph = it },
                        onControlChange = { simControlStability = it },
                        onResetOptimal = {
                            simSpeedKph = currentTopic.defaultSpeedParam
                            simControlStability = 0.88f
                        },
                        onApplyMistakePreset = {
                            simSpeedKph = (currentTopic.defaultSpeedParam + 14f).coerceAtMost(50f)
                            simControlStability = 0.22f
                        },
                        onToggleComplete = { onToggleComplete(currentTopic.id) },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                EducationTopicListCard(
                    topics = topics,
                    selectedTopicId = currentTopic.id,
                    completedTopicIds = completedTopicIds,
                    onSelectTopic = onSelectTopic,
                )
                EducationDetailContent(
                    topic = currentTopic,
                    isCompleted = currentTopic.id in completedTopicIds,
                    simSpeedKph = simSpeedKph,
                    simControlStability = simControlStability,
                    onSpeedChange = { simSpeedKph = it },
                    onControlChange = { simControlStability = it },
                    onResetOptimal = {
                        simSpeedKph = currentTopic.defaultSpeedParam
                        simControlStability = 0.88f
                    },
                    onApplyMistakePreset = {
                        simSpeedKph = (currentTopic.defaultSpeedParam + 14f).coerceAtMost(50f)
                        simControlStability = 0.22f
                    },
                    onToggleComplete = { onToggleComplete(currentTopic.id) },
                )
            }
        }
    }
}

@Composable
private fun EducationTopicListCard(
    topics: List<EducationTopic>,
    selectedTopicId: String,
    completedTopicIds: Set<String>,
    onSelectTopic: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "운전 교육 커리큘럼 (5개 과정)",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "차량이 정차(P)된 상태에서 안전하게 학습하세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                StatusPillBadge(
                    text = "${completedTopicIds.size}/${topics.size} 완료",
                    accentColor = if (completedTopicIds.size == topics.size) StatusSuccess else PrimaryAccent,
                )
            }

            topics.forEachIndexed { idx, topic ->
                val isSelected = topic.id == selectedTopicId
                val isDone = topic.id in completedTopicIds

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTopic(topic.id) },
                    color = if (isSelected) ElevatedSurface else MainBackground.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) PrimaryAccent else CardBorder,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "과정 0${idx + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PrimaryAccent,
                                )
                                StatusPillBadge(
                                    text = topic.categoryBadge,
                                    accentColor = SecondaryAccent,
                                )
                            }
                            Text(
                                text = topic.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            Text(
                                text = topic.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        StatusPillBadge(
                            text = if (isDone) "완료" else "${topic.estimatedMinutes}분",
                            accentColor = if (isDone) StatusSuccess else TextMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EducationDetailContent(
    topic: EducationTopic,
    isCompleted: Boolean,
    simSpeedKph: Float,
    simControlStability: Float,
    onSpeedChange: (Float) -> Unit,
    onControlChange: (Float) -> Unit,
    onResetOptimal: () -> Unit,
    onApplyMistakePreset: () -> Unit,
    onToggleComplete: () -> Unit,
) {
    // 1. 헤더 + 완료 토글 카드
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
                StatusPillBadge(text = "${topic.categoryBadge} · 약 ${topic.estimatedMinutes}분", accentColor = PrimaryAccent)
                Button(
                    onClick = onToggleComplete,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted) StatusSuccess else PrimaryAccent,
                        contentColor = MainBackground,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = if (isCompleted) "✓ 학습 완료됨 (클릭 시 해제)" else "학습 완료로 표시",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                text = topic.title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = topic.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryAccent,
            )
            Text(
                text = topic.whyItMatters,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }

    // 2. 2D 인터랙티브 도로 궤적 및 조작 시뮬레이터
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "2D 인터랙티브 도로 개념도 및 궤적 비교",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = "슬라이더를 움직여 진입 속도와 조작 타이밍에 따른 차량 회전·감속·가속 궤적 차이를 직접 비교해 보세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }

            InteractiveRoadConceptCanvas(
                simType = topic.simType,
                speedParam = simSpeedKph,
                controlParam = simControlStability,
            )

            // 프리셋 비교 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onResetOptimal),
                    color = StatusSuccess.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StatusSuccess),
                ) {
                    Text(
                        text = "권장 안정 조작 예시 보기",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusSuccess,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                    )
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onApplyMistakePreset),
                    color = StatusWarning.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StatusWarning),
                ) {
                    Text(
                        text = "흔한 불안정 조작 예시 보기",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusWarning,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                    )
                }
            }

            // 슬라이더 조작 영역
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${topic.speedParamLabel} (권장 기준: ${topic.defaultSpeedParam.roundToInt()} km/h)",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "${simSpeedKph.roundToInt()} km/h",
                        style = MaterialTheme.typography.labelLarge,
                        color = PrimaryAccent,
                    )
                }
                Slider(
                    value = simSpeedKph,
                    onValueChange = onSpeedChange,
                    valueRange = 10f..50f,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryAccent,
                        activeTrackColor = PrimaryAccent,
                    ),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = topic.controlParamLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = String.format(Locale.US, "%d%%", (simControlStability * 100).roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = SecondaryAccent,
                    )
                }
                Slider(
                    value = simControlStability,
                    onValueChange = onControlChange,
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = SecondaryAccent,
                        activeTrackColor = SecondaryAccent,
                    ),
                )
            }
        }
    }

    // 3. 핵심 원리 요약 및 단계별 실천 방법
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
            Text(
                text = "핵심 차량동역학 원리 및 주의 포인트",
                style = MaterialTheme.typography.titleMedium,
                color = StatusWarning,
            )
            topic.keyConcepts.forEach { concept ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElevatedSurface)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(StatusWarning),
                    )
                    Text(
                        text = concept,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "단계별 안정 주행 가이드",
                style = MaterialTheme.typography.titleMedium,
                color = SecondaryAccent,
            )

            topic.steps.forEach { step ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MainBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
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
                                text = "STEP ${step.stepNumber}. ${step.title}",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryAccent,
                            )
                            StatusPillBadge(text = "단계 ${step.stepNumber}", accentColor = SecondaryAccent)
                        }
                        Text(
                            text = step.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }

            // 주의사항 배너
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ElevatedSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.5f)),
            ) {
                Text(
                    text = "안전 안내: ${topic.cautionNotice}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusWarning,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}
