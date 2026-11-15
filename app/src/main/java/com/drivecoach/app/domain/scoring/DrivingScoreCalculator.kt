package com.drivecoach.app.domain.scoring

import com.drivecoach.app.domain.analysis.CoachingGenerator
import com.drivecoach.app.domain.analysis.DrivingAnalysisEngine
import com.drivecoach.app.domain.analysis.RuleBasedCoachingGenerator
import com.drivecoach.app.domain.model.DataQualityFlag
import com.drivecoach.app.domain.model.DataQualityReport
import com.drivecoach.app.domain.model.DrivingAnalysisResult
import com.drivecoach.app.domain.model.DrivingEvent
import com.drivecoach.app.domain.model.DrivingEventType
import com.drivecoach.app.domain.model.MetricEvaluation
import com.drivecoach.app.domain.model.ScoringWeights
import com.drivecoach.app.domain.model.SeverityCandidate
import com.drivecoach.app.domain.model.TelemetrySample
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 설명 가능한 운전 점수 산정기 (섹션 16)
 *
 * 원칙:
 * 1. 데이터가 부족한 경우 가짜 기본 점수(예: 85점)를 출력하지 않고 overallScore = null ("분석 데이터 부족")을 반환합니다.
 * 2. 이용 가능한 센서 항목만 평가하고 결측 항목(예: 조향 센서 미지원)은 평가 불가(score = null)로 처리합니다.
 * 3. 특정 이벤트 1회 발생에 과도한 감점을 방지하며 주행 시간과 데이터 품질을 반영합니다.
 * 4. "왜 이 점수가 나왔습니까?"에 답할 수 있도록 감점 사유와 판단 규칙을 함께 생성합니다.
 */
object DrivingScoreCalculator {

    const val METRIC_ACCELERATION = "ACCELERATION"
    const val METRIC_BRAKING = "BRAKING"
    const val METRIC_STEERING = "STEERING"
    const val METRIC_CONSISTENCY = "CONSISTENCY"

    fun calculateScores(
        sessionId: String,
        samples: List<TelemetrySample>,
        events: List<DrivingEvent>,
        qualityReport: DataQualityReport,
        durationSec: Int,
        weights: ScoringWeights = ScoringWeights(),
        coachingGenerator: CoachingGenerator = RuleBasedCoachingGenerator,
    ): DrivingAnalysisResult {
        if (!qualityReport.isSufficientForScoring) {
            val unavailableEval = fun(key: String, name: String, w: Float): MetricEvaluation {
                return MetricEvaluation(
                    metricKey = key,
                    metricName = name,
                    score = null,
                    weight = w,
                    isAvailable = false,
                    confidence = qualityReport.overallReliability,
                    statusLabel = "분석 데이터 부족",
                    interpretation = "유효한 주행 샘플 또는 주행 시간이 부족하여 점수를 산출할 수 없습니다.",
                    deductionReasons = listOf(qualityReport.summaryText),
                )
            }
            val accel = unavailableEval(METRIC_ACCELERATION, "가속 안정성", weights.accelerationWeight)
            val brake = unavailableEval(METRIC_BRAKING, "감속·제동 안정성", weights.brakingWeight)
            val steer = unavailableEval(METRIC_STEERING, "조향 안정성", weights.steeringWeight)
            val cons = unavailableEval(METRIC_CONSISTENCY, "주행 패턴 일관성", weights.consistencyWeight)

            return DrivingAnalysisResult(
                sessionId = sessionId,
                overallScore = null,
                scoreGradeLabel = "분석 데이터 부족",
                accelerationEval = accel,
                brakingEval = brake,
                steeringEval = steer,
                consistencyEval = cons,
                availableMetrics = emptySet(),
                dataQuality = qualityReport,
                coachingInsights = coachingGenerator.generateForInsufficientData(qualityReport),
                recommendedEducationId = "smooth_braking",
                overallSummary = "충분한 주행 속도 시계열 데이터가 확보되지 않아 종합 점수를 산출하지 않았습니다. (${qualityReport.summaryText})",
                scoreExplanation = "최소 5초 이상의 유효 주행 샘플과 35% 이상의 데이터 신뢰도가 확보되어야 점수를 계산합니다.",
                appliedWeights = weights,
                analysisVersion = DrivingAnalysisEngine.ANALYSIS_VERSION,
            )
        }

        // 주행 시간 보정 계수 (짧은 시연 세션에서도 이벤트 1회가 점수를 0점으로 만들지 않도록 상한 적용)
        val durationMinutes = max(durationSec / 60.0f, 0.8f)

        // 1. 가속 안정성 계산
        val accelEvents = events.filter { it.eventType == DrivingEventType.ABRUPT_ACCELERATION_CANDIDATE }
        val positiveAccels = samples.mapNotNull { s ->
            s.longitudinalAccelerationMps2?.takeIf { it > 0.15f && DataQualityFlag.VALID in s.qualityFlags }
        }
        val avgPositiveAccel = if (positiveAccels.isNotEmpty()) positiveAccels.average().toFloat() else 0.8f
        val gentleAccelPenalty = ((avgPositiveAccel - 1.25f).coerceAtLeast(0f) * 6.0f).coerceAtMost(10f)
        val accelEventPenalty = accelEvents.sumOf { ev ->
            // 단일 이벤트 최대 감점 9점 제한
            when (ev.severityCandidate) {
                SeverityCandidate.HIGH -> 8.5
                SeverityCandidate.MODERATE -> 6.0
                SeverityCandidate.INFO -> 3.0
            }
        }.toFloat().let { (it / sqrt(durationMinutes)).coerceAtMost(28f) }

        val accelScore = (96f - gentleAccelPenalty - accelEventPenalty).roundToInt().coerceIn(40, 100)
        val accelReasons = buildList {
            if (accelEvents.isEmpty()) {
                add("급가속 후보 이벤트가 감지되지 않았습니다 (감점 0점).")
            } else {
                add("급가속 후보 구간 ${accelEvents.size}건 감지 (이벤트 반영 감점 -${accelEventPenalty.roundToInt()}점, 단일 이벤트 감점 상한 적용).")
            }
            add("평균 양(+)의 종방향 가속도 ${String.format("%.2f", avgPositiveAccel)} m/s² 반영.")
        }
        val accelInterpretation = when {
            accelEvents.isEmpty() && accelScore >= 88 ->
                "최근 분석한 출발 및 가속 구간에서 페달 조작이 완만하고 안정적인 속도 상승 패턴이 나타났습니다."
            accelEvents.size == 1 ->
                "전반적으로 안정적이나 일부 구간에서 빠른 가속 상승 후보가 1회 관찰되었습니다."
            else ->
                "출발 또는 재가속 구간에서 짧은 시간 동안 가속도가 빠르게 커지는 경향이 ${accelEvents.size}회 관찰되었습니다."
        }
        val accelEval = MetricEvaluation(
            metricKey = METRIC_ACCELERATION,
            metricName = "가속 안정성",
            score = accelScore,
            weight = weights.accelerationWeight,
            isAvailable = true,
            confidence = qualityReport.overallReliability,
            statusLabel = statusLabelForScore(accelScore),
            interpretation = accelInterpretation,
            deductionReasons = accelReasons,
        )

        // 2. 감속·제동 안정성 계산
        val brakeEvents = events.filter { it.eventType == DrivingEventType.LATE_HARD_BRAKING_CANDIDATE }
        val negativeDecels = samples.mapNotNull { s ->
            s.longitudinalAccelerationMps2?.takeIf { it < -0.15f && DataQualityFlag.VALID in s.qualityFlags }
        }
        val avgAbsDecel = if (negativeDecels.isNotEmpty()) abs(negativeDecels.average().toFloat()) else 0.8f
        val decelSmoothnessPenalty = ((avgAbsDecel - 1.30f).coerceAtLeast(0f) * 6.5f).coerceAtMost(11f)
        val brakeEventPenalty = brakeEvents.sumOf { ev ->
            when (ev.severityCandidate) {
                SeverityCandidate.HIGH -> 9.0
                SeverityCandidate.MODERATE -> 6.5
                SeverityCandidate.INFO -> 3.5
            }
        }.toFloat().let { (it / sqrt(durationMinutes)).coerceAtMost(30f) }

        val brakeScore = (96f - decelSmoothnessPenalty - brakeEventPenalty).roundToInt().coerceIn(40, 100)
        val brakeReasons = buildList {
            if (brakeEvents.isEmpty()) {
                add("후반 집중·급감속 후보 구간이 감지되지 않았습니다 (감점 0점).")
            } else {
                add("후반 집중·급감속 후보 ${brakeEvents.size}건 감지 (이벤트 반영 감점 -${brakeEventPenalty.roundToInt()}점, 긴급제동 가능성 유의).")
            }
            add("평균 감속도 크기 ${String.format("%.2f", avgAbsDecel)} m/s² 반영.")
        }
        val brakeInterpretation = when {
            brakeEvents.isEmpty() && brakeScore >= 88 ->
                "최근 분석한 일반 감속 구간에서는 미리 감속을 시작하여 부드럽게 정차하는 패턴이 나타났습니다."
            brakeEvents.size == 1 ->
                "대부분 여유 있게 감속했으나 정차 직전 감속이 후반부에 집중된 후보 구간이 1회 관찰되었습니다."
            else ->
                "일반 감속 구간에서 감속이 후반부에 집중되는 패턴이 ${brakeEvents.size}회 관찰되었습니다. (단, 돌발 장애물 회피는 예외로 구분합니다.)"
        }
        val brakeEval = MetricEvaluation(
            metricKey = METRIC_BRAKING,
            metricName = "감속·제동 안정성",
            score = brakeScore,
            weight = weights.brakingWeight,
            isAvailable = true,
            confidence = qualityReport.overallReliability,
            statusLabel = statusLabelForScore(brakeScore),
            interpretation = brakeInterpretation,
            deductionReasons = brakeReasons,
        )

        // 3. 조향 안정성 계산 (조향 데이터가 있는 경우에만 평가, 없으면 평가 불가 처리)
        val hasSteering = qualityReport.hasRoadWheelAngleData || qualityReport.hasSteeringWheelAngleData
        val steerEvents = events.filter { it.eventType == DrivingEventType.LOW_SPEED_STEERING_CORRECTION_CANDIDATE }
        val steerEval = if (!hasSteering) {
            MetricEvaluation(
                metricKey = METRIC_STEERING,
                metricName = "조향 안정성",
                score = null,
                weight = weights.steeringWeight,
                isAvailable = false,
                confidence = 0f,
                statusLabel = "센서 미제공",
                interpretation = "현재 세션에 조향각 데이터가 포함되지 않아 조향 안정성을 점수 계산에서 제외했습니다.",
                deductionReasons = listOf("조향각(전륜/핸들) 데이터 결측으로 가중치 자동 재분배 처리"),
            )
        } else {
            val steerEventPenalty = steerEvents.sumOf { ev ->
                when (ev.severityCandidate) {
                    SeverityCandidate.HIGH -> 9.0
                    SeverityCandidate.MODERATE -> 6.5
                    SeverityCandidate.INFO -> 3.5
                }
            }.toFloat().let { (it / sqrt(durationMinutes)).coerceAtMost(28f) }

            // 조향 변화율 표준편차 기반 부드러움 평가
            val angularRates = mutableListOf<Float>()
            for (i in 1 until samples.size) {
                val a1 = samples[i - 1].effectiveRoadWheelAngleDeg ?: continue
                val a2 = samples[i].effectiveRoadWheelAngleDeg ?: continue
                val dt = ((samples[i].timestampMs - samples[i - 1].timestampMs) / 1000f).coerceAtLeast(0.1f)
                if (dt <= 2.5f) {
                    angularRates.add(abs((a2 - a1) / dt))
                }
            }
            val meanRate = if (angularRates.isNotEmpty()) angularRates.average().toFloat() else 1.0f
            val ratePenalty = ((meanRate - 2.2f).coerceAtLeast(0f) * 2.5f).coerceAtMost(9f)
            val steerScore = (96f - steerEventPenalty - ratePenalty).roundToInt().coerceIn(40, 100)

            val steerReasons = buildList {
                if (steerEvents.isEmpty()) {
                    add("저속 회전 수정 조향 후보가 감지되지 않았습니다 (감점 0점).")
                } else {
                    add("저속 회전 중 반복 수정 조향 후보 ${steerEvents.size}건 감지 (-${steerEventPenalty.roundToInt()}점).")
                }
                add("평균 전륜 조향 변화율 ${String.format("%.2f", meanRate)}°/s 반영.")
            }
            val steerInterpretation = when {
                steerEvents.isEmpty() && steerScore >= 88 ->
                    "차선 유지와 저속 회전 구간에서 불필요한 방향 반전 없이 매끄러운 조향 궤적이 관찰되었습니다."
                else ->
                    "유사한 저속 회전 상황에서 조향 방향이 여러 번 수정되는 패턴이 ${steerEvents.size}회 관찰되었습니다."
            }
            MetricEvaluation(
                metricKey = METRIC_STEERING,
                metricName = "조향 안정성",
                score = steerScore,
                weight = weights.steeringWeight,
                isAvailable = true,
                confidence = qualityReport.overallReliability,
                statusLabel = statusLabelForScore(steerScore),
                interpretation = steerInterpretation,
                deductionReasons = steerReasons,
            )
        }

        // 4. 주행 패턴 일관성 계산
        val oscEvents = events.filter { it.eventType == DrivingEventType.SPEED_OSCILLATION_CANDIDATE }
        val validAccels = samples.mapNotNull { s ->
            s.longitudinalAccelerationMps2?.takeIf { DataQualityFlag.VALID in s.qualityFlags }
        }
        val accelStdDev = if (validAccels.size >= 3) {
            val mean = validAccels.average()
            sqrt(validAccels.sumOf { (it - mean) * (it - mean) } / validAccels.size).toFloat()
        } else {
            0.8f
        }
        val stdPenalty = ((accelStdDev - 0.95f).coerceAtLeast(0f) * 8.0f).coerceAtMost(14f)
        val oscPenalty = (oscEvents.size * 5.0f).coerceAtMost(18f)
        val consistencyScore = (95f - stdPenalty - oscPenalty).roundToInt().coerceIn(45, 100)
        val consistencyEval = MetricEvaluation(
            metricKey = METRIC_CONSISTENCY,
            metricName = "주행 패턴 일관성",
            score = consistencyScore,
            weight = weights.consistencyWeight,
            isAvailable = true,
            confidence = qualityReport.overallReliability,
            statusLabel = statusLabelForScore(consistencyScore),
            interpretation = if (consistencyScore >= 86) {
                "주행 전반에서 속도 유지와 가감속 전환 리듬이 일정하게 유지되었습니다."
            } else {
                "구간별 가감속 편차(표준편차 ${String.format("%.2f", accelStdDev)} m/s²)가 다소 높게 나타났습니다."
            },
            deductionReasons = listOf(
                "종방향 가속도 표준편차 ${String.format("%.2f", accelStdDev)} m/s² 및 속도 변동 후보 ${oscEvents.size}건 반영"
            ),
        )

        // 가용 항목 가중 평균으로 종합 점수 계산
        val evaluations = listOf(accelEval, brakeEval, steerEval, consistencyEval)
        val availableEvals = evaluations.filter { it.isAvailable && it.score != null }
        val activeWeightSum = availableEvals.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(0.01f)

        val weightedScore = availableEvals.sumOf { eval ->
            (eval.score ?: 0) * (eval.weight / activeWeightSum).toDouble()
        }.roundToInt().coerceIn(0, 100)

        val availableMetricKeys = availableEvals.map { it.metricKey }.toSet()

        val coachingInsights = coachingGenerator.generateForSession(
            sessionId = sessionId,
            overallScore = weightedScore,
            accelEval = accelEval,
            brakeEval = brakeEval,
            steerEval = steerEval,
            consistencyEval = consistencyEval,
            events = events,
            qualityReport = qualityReport,
        )

        val recommendedTopicId = coachingInsights.firstOrNull { it.recommendedTopicId != null }?.recommendedTopicId
            ?: recommendDefaultTopic(accelEval, brakeEval, steerEval)

        val overallSummary = buildOverallSummary(
            overallScore = weightedScore,
            accelEval = accelEval,
            brakeEval = brakeEval,
            steerEval = steerEval,
            events = events,
        )

        val scoreExplanation = buildString {
            append("적용 가중치(개발 초기 가설): 가속 ${(weights.accelerationWeight * 100).roundToInt()}%, ")
            append("제동 ${(weights.brakingWeight * 100).roundToInt()}%, ")
            append("조향 ${(weights.steeringWeight * 100).roundToInt()}%, ")
            append("일관성 ${(weights.consistencyWeight * 100).roundToInt()}%. ")
            if (!steerEval.isAvailable) {
                append("조향 센서가 미포함되어 가속·제동·일관성 3개 가용 항목 비율로 정규화하여 산출했습니다. ")
            }
            append("감지된 개선 후보 이벤트 총 ${events.count { it.eventType != DrivingEventType.DATA_GAP_WARNING }}건과 ")
            append("데이터 신뢰도 ${(qualityReport.overallReliability * 100).roundToInt()}%가 반영되었습니다.")
        }

        return DrivingAnalysisResult(
            sessionId = sessionId,
            overallScore = weightedScore,
            scoreGradeLabel = statusLabelForScore(weightedScore),
            accelerationEval = accelEval,
            brakingEval = brakeEval,
            steeringEval = steerEval,
            consistencyEval = consistencyEval,
            availableMetrics = availableMetricKeys,
            dataQuality = qualityReport,
            coachingInsights = coachingInsights,
            recommendedEducationId = recommendedTopicId,
            overallSummary = overallSummary,
            scoreExplanation = scoreExplanation,
            appliedWeights = weights,
            analysisVersion = DrivingAnalysisEngine.ANALYSIS_VERSION,
        )
    }

    private fun statusLabelForScore(score: Int): String = when {
        score >= 90 -> "매우 안정"
        score >= 82 -> "안정 주행"
        score >= 74 -> "양호 · 개선 권장"
        else -> "집중 코칭 필요"
    }

    private fun recommendDefaultTopic(
        accelEval: MetricEvaluation,
        brakeEval: MetricEvaluation,
        steerEval: MetricEvaluation,
    ): String {
        val candidates = listOfNotNull(
            accelEval.score?.let { "smooth_acceleration" to it },
            brakeEval.score?.let { "smooth_braking" to it },
            steerEval.score?.let { "low_speed_left_turn" to it },
        )
        return candidates.minByOrNull { it.second }?.first ?: "smooth_braking"
    }

    private fun buildOverallSummary(
        overallScore: Int,
        accelEval: MetricEvaluation,
        brakeEval: MetricEvaluation,
        steerEval: MetricEvaluation,
        events: List<DrivingEvent>,
    ): String {
        val behaviorEvents = events.filter { it.eventType != DrivingEventType.DATA_GAP_WARNING }
        if (behaviorEvents.isEmpty() && overallScore >= 88) {
            return "가속·감속·조향 전 구간에서 급격한 조작 후보 없이 매우 부드럽고 일관된 운전 습관이 확인되었습니다."
        }
        val lowestMetric = listOfNotNull(
            accelEval.takeIf { it.isAvailable && it.score != null },
            brakeEval.takeIf { it.isAvailable && it.score != null },
            steerEval.takeIf { it.isAvailable && it.score != null },
        ).minByOrNull { it.score ?: 100 }

        return if (lowestMetric != null) {
            "이번 주행에서는 총 ${behaviorEvents.size}건의 조작 패턴 후보가 관찰되었으며, '${lowestMetric.metricName}(${lowestMetric.score}점)' 항목을 우선 점검하면 주행 부드러움을 크게 높일 수 있습니다."
        } else {
            "주행 시계열 분석이 완료되었습니다. 세부 이벤트 타임라인에서 구간별 조작 근거를 확인해 보세요."
        }
    }
}
