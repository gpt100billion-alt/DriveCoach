package com.drivecoach.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecoach.app.domain.model.DataSourceType
import com.drivecoach.app.domain.model.DrivingEvent
import com.drivecoach.app.domain.model.DrivingEventType
import com.drivecoach.app.domain.model.EducationSimType
import com.drivecoach.app.domain.model.MetricEvaluation
import com.drivecoach.app.domain.model.TelemetrySample
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

fun formatTimestampKorean(timestampMs: Long): String {
    val sdf = SimpleDateFormat("MM월 dd일 · HH:mm", Locale.KOREA)
    return sdf.format(Date(timestampMs))
}

fun formatDurationKorean(durationSec: Int): String {
    val min = durationSec / 60
    val sec = durationSec % 60
    return if (min > 0) "${min}분 ${sec}초" else "${sec}초"
}

fun colorForDataSource(source: DataSourceType): Color = when (source) {
    DataSourceType.LIVE -> StatusSuccess
    DataSourceType.SIMULATED -> PrimaryAccent
    DataSourceType.DEMO -> SecondaryAccent
    DataSourceType.UNAVAILABLE -> StatusWarning
    DataSourceType.ERROR -> StatusCritical
}

fun colorForScore(score: Int?): Color = when {
    score == null -> TextMuted
    score >= 88 -> StatusSuccess
    score >= 78 -> PrimaryAccent
    score >= 68 -> StatusWarning
    else -> StatusCritical
}

/**
 * 데이터 출처 상태 배지 (LIVE / SIMULATED / DEMO / UNAVAILABLE / ERROR)
 */
@Composable
fun DataSourceBadge(
    dataSource: DataSourceType,
    modifier: Modifier = Modifier,
    showKoreanTitle: Boolean = true,
) {
    val accent = colorForDataSource(dataSource)
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                text = if (showKoreanTitle) {
                    "${dataSource.badgeText} · ${dataSource.koreanTitle}"
                } else {
                    dataSource.badgeText
                },
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StatusPillBadge(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = accentColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

/**
 * 디지털 콕핏 스타일의 운전 점수 원호 게이지 (Score Ring)
 * 점수가 null("분석 데이터 부족")인 경우 가짜 점수를 표시하지 않고 명확한 안내 상태를 그립니다.
 */
@Composable
fun CockpitScoreRing(
    score: Int?,
    gradeLabel: String,
    deltaScore: Int?,
    reliabilityPercent: Int,
    gaugeSize: Dp = 180.dp,
    modifier: Modifier = Modifier,
) {
    val sweepRatio = ((score ?: 0).coerceIn(0, 100)) / 100f
    val ringColor = colorForScore(score)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(gaugeSize),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = size.minDimension * 0.095f
                val inset = strokeWidth / 2f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(inset, inset)
                val startAngle = 140f
                val maxSweepAngle = 260f

                drawArc(
                    color = GaugeTrack,
                    startAngle = startAngle,
                    sweepAngle = maxSweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                if (score != null) {
                    drawArc(
                        brush = Brush.horizontalGradient(
                            colors = listOf(PleosBlueDeep, PrimaryAccent, ringColor),
                        ),
                        startAngle = startAngle,
                        sweepAngle = maxSweepAngle * sweepRatio,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )

                    val endAngleRad = Math.toRadians((startAngle + (maxSweepAngle * sweepRatio)).toDouble())
                    val radius = (size.minDimension - strokeWidth) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dotOffset = Offset(
                        x = center.x + (radius * cos(endAngleRad)).toFloat(),
                        y = center.y + (radius * sin(endAngleRad)).toFloat(),
                    )
                    drawCircle(
                        color = TextPrimary,
                        radius = strokeWidth * 0.28f,
                        center = dotOffset,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "HABIT SCORE",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
                if (score != null) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = score.toString(),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = if (gaugeSize < 160.dp) 38.sp else 46.sp,
                            ),
                            color = TextPrimary,
                        )
                        Text(
                            text = "점",
                            style = MaterialTheme.typography.titleMedium,
                            color = ringColor,
                            modifier = Modifier.padding(bottom = 7.dp),
                        )
                    }
                } else {
                    Text(
                        text = "데이터 부족",
                        style = MaterialTheme.typography.titleLarge,
                        color = StatusWarning,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                Text(
                    text = gradeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = ringColor,
                )
            }
        }

        // 전 주행 대비 변화 및 데이터 신뢰도 표시
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (deltaScore != null) {
                val deltaText = when {
                    deltaScore > 0 -> "전 주행 대비 +${deltaScore}점 개선"
                    deltaScore < 0 -> "전 주행 대비 ${deltaScore}점 변화"
                    else -> "전 주행과 동일"
                }
                val deltaColor = when {
                    deltaScore > 0 -> StatusSuccess
                    deltaScore < 0 -> StatusWarning
                    else -> TextSecondary
                }
                StatusPillBadge(text = deltaText, accentColor = deltaColor)
            }
            StatusPillBadge(
                text = "데이터 신뢰도 ${reliabilityPercent}%",
                accentColor = if (reliabilityPercent >= 75) SecondaryAccent else StatusWarning,
            )
        }
    }
}

/**
 * 세부 안정성 평가 행 (가속 / 감속·제동 / 조향 / 일관성)
 */
@Composable
fun MetricEvaluationCardRow(
    evaluation: MetricEvaluation,
    modifier: Modifier = Modifier,
) {
    val accent = colorForScore(evaluation.score)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = evaluation.metricName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Text(
                    text = "가중치 ${(evaluation.weight * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPillBadge(
                    text = evaluation.statusLabel,
                    accentColor = accent,
                )
                Text(
                    text = if (evaluation.score != null) "${evaluation.score}점" else "미산출",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (evaluation.score != null) TextPrimary else TextMuted,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // 진행 바
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(GaugeTrack),
        ) {
            if (evaluation.score != null && evaluation.isAvailable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((evaluation.score.coerceIn(0, 100)) / 100f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(PleosBlueDeep, accent),
                            ),
                        ),
                )
            }
        }

        Text(
            text = evaluation.interpretation,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

/**
 * 시계열 라인 차트 (속도 km/h, 종방향 가속도 m/s², 조향각 deg 공용) (섹션 9 & 28)
 * - 이벤트 후보 발생 구간을 배경 하이라이트로 함께 시각화합니다.
 */
@Composable
fun TelemetryTimeSeriesChart(
    title: String,
    unitLabel: String,
    sourceLabel: String,
    samples: List<TelemetrySample>,
    valueSelector: (TelemetrySample) -> Float?,
    minY: Float,
    maxY: Float,
    lineColor: Color,
    zeroBaseline: Float? = null,
    highlightEvents: List<DrivingEvent> = emptyList(),
    selectedEventId: String? = null,
    emptyMessage: String = "표시할 시계열 데이터가 없습니다.",
    chartHeight: Dp = 175.dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = "단위: $unitLabel · $sourceLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }

                val validValues = samples.mapNotNull(valueSelector)
                if (validValues.isNotEmpty()) {
                    val minVal = validValues.minOrNull() ?: 0f
                    val maxVal = validValues.maxOrNull() ?: 0f
                    Text(
                        text = String.format(Locale.US, "범위 %.1f ~ %.1f %s", minVal, maxVal, unitLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = lineColor,
                    )
                }
            }

            val validPairs = samples.mapIndexedNotNull { idx, sample ->
                valueSelector(sample)?.let { idx to it }
            }

            if (samples.size < 2 || validPairs.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MainBackground)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusWarning,
                    )
                }
            } else {
                val baseTs = samples.first().timestampMs
                val totalSpanMs = max(1000L, samples.last().timestampMs - baseTs).toFloat()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MainBackground)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val rangeY = (maxY - minY).coerceAtLeast(1f)

                        fun yFor(value: Float): Float {
                            val clamped = value.coerceIn(minY, maxY)
                            val ratio = (clamped - minY) / rangeY
                            return height - (ratio * height)
                        }

                        // 수평 가이드 그리드
                        val gridSteps = 4
                        for (g in 0..gridSteps) {
                            val gy = height * (g / gridSteps.toFloat())
                            drawLine(
                                color = CardBorder.copy(alpha = 0.45f),
                                start = Offset(0f, gy),
                                end = Offset(width, gy),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }

                        // 0 기준선 (가속도/조향각 등 음수 포함 그래프)
                        if (zeroBaseline != null && zeroBaseline in minY..maxY) {
                            val zeroY = yFor(zeroBaseline)
                            drawLine(
                                color = TextMuted.copy(alpha = 0.65f),
                                start = Offset(0f, zeroY),
                                end = Offset(width, zeroY),
                                strokeWidth = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                            )
                        }

                        // 이벤트 구간 배경 하이라이트
                        for (ev in highlightEvents) {
                            val startX = (((ev.startTimeMs - baseTs) / totalSpanMs) * width).coerceIn(0f, width)
                            val endX = (((ev.endTimeMs - baseTs) / totalSpanMs) * width).coerceIn(startX + 6f, width)
                            val isSelected = ev.id == selectedEventId
                            val bandColor = when (ev.eventType) {
                                DrivingEventType.ABRUPT_ACCELERATION_CANDIDATE -> StatusWarning
                                DrivingEventType.LATE_HARD_BRAKING_CANDIDATE -> StatusCritical
                                DrivingEventType.LOW_SPEED_STEERING_CORRECTION_CANDIDATE -> SecondaryAccent
                                else -> PrimaryAccent
                            }
                            drawRoundRect(
                                color = bandColor.copy(alpha = if (isSelected) 0.32f else 0.16f),
                                topLeft = Offset(startX, 0f),
                                size = Size(max(8f, endX - startX), height),
                                cornerRadius = CornerRadius(6f, 6f),
                            )
                        }

                        // 시계열 라인 Path
                        val linePath = Path()
                        validPairs.forEachIndexed { ptIdx, (sampleIdx, value) ->
                            val sample = samples[sampleIdx]
                            val x = (((sample.timestampMs - baseTs) / totalSpanMs) * width).coerceIn(0f, width)
                            val y = yFor(value)
                            if (ptIdx == 0) {
                                linePath.moveTo(x, y)
                            } else {
                                linePath.lineTo(x, y)
                            }
                        }

                        drawPath(
                            path = linePath,
                            color = lineColor,
                            style = Stroke(
                                width = 2.6.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }

                // X축 시간 라벨
                val totalSec = ((samples.last().timestampMs - baseTs) / 1000L).toInt().coerceAtLeast(1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("0초", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Text("${totalSec / 2}초", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Text("${totalSec}초 (X축: 경과 시간)", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                }
            }
        }
    }
}

/**
 * 최근 주행 점수 추세 미니 차트 (Mini Trend Chart)
 */
@Composable
fun ScoreTrendSparklineChart(
    scoresChronological: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
) {
    if (scoresChronological.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MainBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
        ) {
            val w = size.width
            val h = size.height
            val minScore = 50f
            val maxScore = 100f

            // 가이드 라인
            drawLine(
                color = CardBorder.copy(alpha = 0.5f),
                start = Offset(0f, h * 0.3f),
                end = Offset(w, h * 0.3f),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = CardBorder.copy(alpha = 0.5f),
                start = Offset(0f, h * 0.7f),
                end = Offset(w, h * 0.7f),
                strokeWidth = 1.dp.toPx(),
            )

            val path = Path()
            val count = scoresChronological.size
            scoresChronological.forEachIndexed { idx, (_, score) ->
                val x = if (count == 1) w / 2f else (idx.toFloat() / (count - 1)) * (w - 24f) + 12f
                val ratio = ((score.coerceIn(50, 100) - minScore) / (maxScore - minScore))
                val y = h - (ratio * (h - 20f)) - 10f
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            if (count >= 2) {
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(listOf(PrimaryAccent, SecondaryAccent)),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            scoresChronological.forEachIndexed { idx, (_, score) ->
                val x = if (count == 1) w / 2f else (idx.toFloat() / (count - 1)) * (w - 24f) + 12f
                val ratio = ((score.coerceIn(50, 100) - minScore) / (maxScore - minScore))
                val y = h - (ratio * (h - 20f)) - 10f
                drawCircle(
                    color = SecondaryAccent,
                    radius = 5.dp.toPx(),
                    center = Offset(x, y),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            scoresChronological.forEach { (label, score) ->
                Text(
                    text = "$label (${score}점)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 2D 도로 개념도 및 차량 회전·제동 궤적 인터랙티브 시뮬레이션 캔버스 (섹션 11 교육 UI)
 */
@Composable
fun InteractiveRoadConceptCanvas(
    simType: EducationSimType,
    speedParam: Float,
    controlParam: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MainBackground)
            .padding(12.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            when (simType) {
                EducationSimType.LOW_SPEED_LEFT_TURN,
                EducationSimType.UNPROTECTED_LEFT_INTERSECTION -> {
                    // 교차로 십자 도로 그리기
                    val roadWidth = min(w, h) * 0.42f
                    val cx = w * 0.52f
                    val cy = h * 0.55f

                    drawRect(
                        color = ElevatedSurface,
                        topLeft = Offset(cx - roadWidth / 2f, 0f),
                        size = Size(roadWidth, h),
                    )
                    drawRect(
                        color = ElevatedSurface,
                        topLeft = Offset(0f, cy - roadWidth / 2f),
                        size = Size(w, roadWidth),
                    )
                    // 중앙선
                    drawLine(
                        color = StatusWarning.copy(alpha = 0.6f),
                        start = Offset(cx, cy + roadWidth / 2f),
                        end = Offset(cx, h),
                        strokeWidth = 2.dp.toPx(),
                    )
                    drawLine(
                        color = StatusWarning.copy(alpha = 0.6f),
                        start = Offset(0f, cy),
                        end = Offset(cx - roadWidth / 2f, cy),
                        strokeWidth = 2.dp.toPx(),
                    )

                    // 이상적 궤적 (점선)
                    val idealPath = Path().apply {
                        moveTo(cx + roadWidth * 0.25f, h * 0.92f)
                        quadraticTo(
                            cx + roadWidth * 0.2f,
                            cy - roadWidth * 0.22f,
                            w * 0.12f,
                            cy - roadWidth * 0.25f,
                        )
                    }
                    drawPath(
                        path = idealPath,
                        color = SecondaryAccent.copy(alpha = 0.55f),
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                        ),
                    )

                    // 사용자 파라미터 반영 궤적 (진입 속도가 높거나 안정도가 낮으면 바깥으로 밀리고 흔들림)
                    val overshoot = ((speedParam - 18f) / 25f).coerceIn(0f, 1f) * (1f - controlParam * 0.6f)
                    val userPath = Path().apply {
                        moveTo(cx + roadWidth * 0.25f, h * 0.92f)
                        cubicTo(
                            cx + roadWidth * (0.25f + overshoot * 0.35f),
                            cy + roadWidth * 0.1f,
                            cx - roadWidth * (0.1f - overshoot * 0.45f),
                            cy - roadWidth * (0.25f + overshoot * 0.35f),
                            w * 0.12f,
                            cy - roadWidth * (0.25f + overshoot * 0.15f),
                        )
                    }
                    drawPath(
                        path = userPath,
                        color = if (overshoot > 0.28f) StatusWarning else PrimaryAccent,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                EducationSimType.LOW_SPEED_RIGHT_TURN -> {
                    val roadWidth = min(w, h) * 0.42f
                    val cx = w * 0.45f
                    val cy = h * 0.52f

                    drawRect(
                        color = ElevatedSurface,
                        topLeft = Offset(cx - roadWidth / 2f, 0f),
                        size = Size(roadWidth, h),
                    )
                    drawRect(
                        color = ElevatedSurface,
                        topLeft = Offset(0f, cy - roadWidth / 2f),
                        size = Size(w, roadWidth),
                    )

                    // 앞바퀴 궤적 vs 뒷바퀴 내륜차 궤적
                    val frontWheelPath = Path().apply {
                        moveTo(cx + roadWidth * 0.25f, h * 0.92f)
                        quadraticTo(
                            cx + roadWidth * 0.32f,
                            cy + roadWidth * 0.25f,
                            w * 0.88f,
                            cy + roadWidth * 0.25f,
                        )
                    }
                    val rearWheelInset = (1f - controlParam) * 22f + 12f
                    val rearWheelPath = Path().apply {
                        moveTo(cx + roadWidth * 0.20f, h * 0.92f)
                        quadraticTo(
                            cx + roadWidth * 0.32f + rearWheelInset,
                            cy + roadWidth * 0.25f + rearWheelInset,
                            w * 0.88f,
                            cy + roadWidth * 0.28f,
                        )
                    }
                    drawPath(
                        path = frontWheelPath,
                        color = PrimaryAccent,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawPath(
                        path = rearWheelPath,
                        color = StatusWarning,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                        ),
                    )
                }

                EducationSimType.SMOOTH_BRAKING,
                EducationSimType.SMOOTH_ACCELERATION -> {
                    // 직선 차로 및 속도 프로파일 비교 곡선
                    val baselineY = h * 0.82f
                    val topY = h * 0.18f
                    drawLine(
                        color = CardBorder,
                        start = Offset(20f, baselineY),
                        end = Offset(w - 20f, baselineY),
                        strokeWidth = 2.dp.toPx(),
                    )

                    // 이상적인 완만한 곡선
                    val idealCurve = Path().apply {
                        if (simType == EducationSimType.SMOOTH_BRAKING) {
                            moveTo(24f, topY)
                            cubicTo(w * 0.35f, topY, w * 0.65f, baselineY, w - 24f, baselineY)
                        } else {
                            moveTo(24f, baselineY)
                            cubicTo(w * 0.35f, baselineY, w * 0.65f, topY, w - 24f, topY)
                        }
                    }
                    drawPath(
                        path = idealCurve,
                        color = SecondaryAccent.copy(alpha = 0.6f),
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                        ),
                    )

                    // 사용자 조작 파라미터 반영 곡선
                    val sharpness = (1f - controlParam).coerceIn(0f, 1f)
                    val userCurve = Path().apply {
                        if (simType == EducationSimType.SMOOTH_BRAKING) {
                            moveTo(24f, topY)
                            val brakePointX = w * (0.35f + sharpness * 0.42f)
                            lineTo(brakePointX, topY + 8f)
                            quadraticTo(w * 0.88f, topY + (baselineY - topY) * 0.6f, w - 24f, baselineY)
                        } else {
                            moveTo(24f, baselineY)
                            val peakX = w * (0.75f - sharpness * 0.45f)
                            quadraticTo(w * 0.22f, topY, peakX, topY)
                            lineTo(w - 24f, topY)
                        }
                    }
                    drawPath(
                        path = userCurve,
                        color = if (sharpness > 0.45f) StatusWarning else PrimaryAccent,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPillBadge(text = "점선: 권장 기준 궤적", accentColor = SecondaryAccent)
            StatusPillBadge(text = "실선: 시뮬레이션 궤적", accentColor = PrimaryAccent)
        }
    }
}

/**
 * 이벤트 타임라인 행 컴포넌트
 */
@Composable
fun EventTimelineItemCard(
    event: DrivingEvent,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (event.eventType) {
        DrivingEventType.ABRUPT_ACCELERATION_CANDIDATE -> StatusWarning
        DrivingEventType.LATE_HARD_BRAKING_CANDIDATE -> StatusCritical
        DrivingEventType.LOW_SPEED_STEERING_CORRECTION_CANDIDATE -> SecondaryAccent
        DrivingEventType.SPEED_OSCILLATION_CANDIDATE -> PrimaryAccent
        DrivingEventType.DATA_GAP_WARNING -> TextMuted
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) ElevatedSurface else MainBackground.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accent else CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    StatusPillBadge(text = event.eventType.shortBadge, accentColor = accent)
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.0f초 ~ %.0f초 구간",
                            event.elapsedStartSec,
                            event.elapsedEndSec,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                }

                Text(
                    text = "신뢰도 ${(event.confidence * 100).roundToInt()}% · ${event.severityCandidate.koreanLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
            }

            Text(
                text = event.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )

            if (isSelected) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    event.measuredValues.forEach { (k, v) ->
                        Surface(
                            color = SecondaryBackground,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CardBorder),
                        ) {
                            Text(
                                text = "$k: $v",
                                style = MaterialTheme.typography.labelMedium,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "판단 기준: ${event.detectionRule}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    text = "해석 주의: ${event.cautionNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusWarning,
                )
            }
        }
    }
}
