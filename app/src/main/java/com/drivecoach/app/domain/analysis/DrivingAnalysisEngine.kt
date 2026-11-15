package com.drivecoach.app.domain.analysis

import com.drivecoach.app.domain.model.AnalysisStatus
import com.drivecoach.app.domain.model.DataQualityFlag
import com.drivecoach.app.domain.model.DataQualityReport
import com.drivecoach.app.domain.model.DataSourceType
import com.drivecoach.app.domain.model.DrivingAnalysisResult
import com.drivecoach.app.domain.model.DrivingEvent
import com.drivecoach.app.domain.model.DrivingEventType
import com.drivecoach.app.domain.model.DrivingSession
import com.drivecoach.app.domain.model.ScoringWeights
import com.drivecoach.app.domain.model.SeverityCandidate
import com.drivecoach.app.domain.model.TelemetrySample
import com.drivecoach.app.domain.scoring.DrivingScoreCalculator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 순수 Kotlin 기반 주행 데이터 분석 엔진 (섹션 15)
 * UI나 Android Context에 의존하지 않으며 단위 테스트가 가능하도록 설계되었습니다.
 */
object DrivingAnalysisEngine {

    const val ANALYSIS_VERSION = "rule-engine-1.0.0"

    // 물리적 유효 범위 상수
    private const val MAX_REALISTIC_SPEED_KPH = 260.0f
    private const val MAX_REALISTIC_ACCEL_MPS2 = 12.0f
    private const val MAX_REALISTIC_ROAD_WHEEL_DEG = 45.0f
    private const val MAX_REALISTIC_STEERING_WHEEL_DEG = 720.0f
    private const val IRREGULAR_GAP_THRESHOLD_SEC = 3.5f

    // 이벤트 감지 임계치
    private const val ABRUPT_ACCEL_THRESHOLD_MPS2 = 2.25f
    private const val ABRUPT_ACCEL_MIN_DURATION_SEC = 1.6f
    private const val HARD_BRAKE_THRESHOLD_MPS2 = -2.35f
    private const val HARD_BRAKE_MIN_DURATION_SEC = 1.6f

    /**
     * km/h 단위의 속도를 m/s 단위로 변환합니다. (섹션 15.1)
     */
    fun kmhToMps(speedKph: Float): Float = speedKph / 3.6f

    /**
     * m/s 단위의 속도를 km/h 단위로 변환합니다.
     */
    fun mpsToKmh(speedMps: Float): Float = speedMps * 3.6f

    /**
     * 원본 샘플 시퀀스를 보존하면서, 시간 간격(Δt) 검증·노이즈 완화(3점 이동평균) 및
     * 종방향 가속도(Δv/Δt) 추정치를 보강한 샘플 리스트와 품질 리포트를 생성합니다.
     */
    fun preprocessAndEvaluateQuality(
        rawSamples: List<TelemetrySample>,
    ): Pair<List<TelemetrySample>, DataQualityReport> {
        if (rawSamples.isEmpty()) {
            val emptyReport = DataQualityReport(
                totalSamples = 0,
                validSamples = 0,
                missingSampleCount = 0,
                duplicateTimestampCount = 0,
                irregularIntervalCount = 0,
                unrealisticValueCount = 0,
                hasSpeedData = false,
                hasRoadWheelAngleData = false,
                hasSteeringWheelAngleData = false,
                hasBrakeStatusData = false,
                overallReliability = 0f,
                summaryText = "수집된 주행 샘플이 없습니다.",
                isSufficientForScoring = false,
            )
            return emptyList<TelemetrySample>() to emptyReport
        }

        val sorted = rawSamples.sortedBy { it.timestampMs }

        // 1. 이동평균으로 속도 노이즈 완화 (원본 결측이나 비현실 값은 제외)
        val smoothedSpeeds = FloatArray(sorted.size) { idx ->
            val current = sorted[idx].speedKph
            if (current == null || current < 0f || current > MAX_REALISTIC_SPEED_KPH) {
                Float.NaN
            } else {
                var sum = current
                var count = 1
                if (idx > 0) {
                    val prev = sorted[idx - 1].speedKph
                    val dtPrev = (sorted[idx].timestampMs - sorted[idx - 1].timestampMs) / 1000f
                    if (prev != null && prev in 0f..MAX_REALISTIC_SPEED_KPH && dtPrev in 0.1f..2.5f) {
                        sum += prev
                        count++
                    }
                }
                if (idx < sorted.lastIndex) {
                    val next = sorted[idx + 1].speedKph
                    val dtNext = (sorted[idx + 1].timestampMs - sorted[idx].timestampMs) / 1000f
                    if (next != null && next in 0f..MAX_REALISTIC_SPEED_KPH && dtNext in 0.1f..2.5f) {
                        sum += next
                        count++
                    }
                }
                sum / count.toFloat()
            }
        }

        var validCount = 0
        var missingSpeedCount = 0
        var duplicateCount = 0
        var irregularCount = 0
        var unrealisticCount = 0
        var speedPresentCount = 0
        var roadWheelPresentCount = 0
        var steeringWheelPresentCount = 0
        var brakePresentCount = 0

        val enriched = ArrayList<TelemetrySample>(sorted.size)

        for (i in sorted.indices) {
            val sample = sorted[i]
            val flags = mutableSetOf<DataQualityFlag>()
            if (sample.dataSource == DataSourceType.SIMULATED || sample.dataSource == DataSourceType.DEMO) {
                flags.add(DataQualityFlag.SIMULATED_SOURCE)
            }

            val speed = sample.speedKph
            val roadWheel = sample.roadWheelAngleDeg
            val steeringWheel = sample.steeringWheelAngleDeg

            if (speed == null) {
                missingSpeedCount++
                flags.add(DataQualityFlag.MISSING_SPEED)
            } else if (speed < 0f || speed > MAX_REALISTIC_SPEED_KPH || speed.isNaN()) {
                unrealisticCount++
                flags.add(DataQualityFlag.UNREALISTIC_VALUE)
            } else {
                speedPresentCount++
            }

            if (roadWheel != null) {
                if (abs(roadWheel) > MAX_REALISTIC_ROAD_WHEEL_DEG || roadWheel.isNaN()) {
                    unrealisticCount++
                    flags.add(DataQualityFlag.UNREALISTIC_VALUE)
                } else {
                    roadWheelPresentCount++
                }
            } else if (steeringWheel != null) {
                if (abs(steeringWheel) > MAX_REALISTIC_STEERING_WHEEL_DEG || steeringWheel.isNaN()) {
                    unrealisticCount++
                    flags.add(DataQualityFlag.UNREALISTIC_VALUE)
                } else {
                    steeringWheelPresentCount++
                }
            } else {
                flags.add(DataQualityFlag.MISSING_STEERING)
            }

            if (sample.brakePedalPressed != null || sample.brakePedalPercent != null) {
                brakePresentCount++
            }

            var estimatedAccel: Float? = sample.longitudinalAccelerationMps2
            var isEstimated = sample.isAccelerationEstimated

            if (i > 0) {
                val prev = sorted[i - 1]
                val dtSec = (sample.timestampMs - prev.timestampMs) / 1000.0f
                when {
                    dtSec <= 0.001f -> {
                        duplicateCount++
                        flags.add(DataQualityFlag.DUPLICATE_TIMESTAMP)
                        estimatedAccel = null
                    }
                    dtSec > IRREGULAR_GAP_THRESHOLD_SEC -> {
                        irregularCount++
                        flags.add(DataQualityFlag.IRREGULAR_INTERVAL)
                        estimatedAccel = null
                    }
                    else -> {
                        val vNowKph = if (!smoothedSpeeds[i].isNaN()) smoothedSpeeds[i] else sample.speedKph
                        val vPrevKph = if (!smoothedSpeeds[i - 1].isNaN()) smoothedSpeeds[i - 1] else prev.speedKph
                        if (vNowKph != null && vPrevKph != null &&
                            vNowKph in 0f..MAX_REALISTIC_SPEED_KPH &&
                            vPrevKph in 0f..MAX_REALISTIC_SPEED_KPH
                        ) {
                            val dvMps = kmhToMps(vNowKph - vPrevKph)
                            val rawDerivedAccel = dvMps / dtSec
                            if (abs(rawDerivedAccel) > MAX_REALISTIC_ACCEL_MPS2) {
                                unrealisticCount++
                                flags.add(DataQualityFlag.UNREALISTIC_VALUE)
                                estimatedAccel = null
                            } else if (estimatedAccel == null) {
                                estimatedAccel = rawDerivedAccel
                                isEstimated = true
                                flags.add(DataQualityFlag.ESTIMATED_ACCELERATION)
                            } else if (abs(estimatedAccel) > MAX_REALISTIC_ACCEL_MPS2) {
                                unrealisticCount++
                                flags.add(DataQualityFlag.UNREALISTIC_VALUE)
                                estimatedAccel = null
                            }
                        } else if (estimatedAccel != null && abs(estimatedAccel) > MAX_REALISTIC_ACCEL_MPS2) {
                            unrealisticCount++
                            flags.add(DataQualityFlag.UNREALISTIC_VALUE)
                            estimatedAccel = null
                        }
                    }
                }
            } else if (estimatedAccel != null && abs(estimatedAccel) > MAX_REALISTIC_ACCEL_MPS2) {
                unrealisticCount++
                flags.add(DataQualityFlag.UNREALISTIC_VALUE)
                estimatedAccel = null
            }

            val isSampleValid = !flags.contains(DataQualityFlag.DUPLICATE_TIMESTAMP) &&
                !flags.contains(DataQualityFlag.UNREALISTIC_VALUE) &&
                !flags.contains(DataQualityFlag.MISSING_SPEED)

            if (isSampleValid) {
                validCount++
                flags.add(DataQualityFlag.VALID)
            }

            enriched.add(
                sample.copy(
                    longitudinalAccelerationMps2 = estimatedAccel,
                    isAccelerationEstimated = isEstimated,
                    qualityFlags = flags,
                )
            )
        }

        val total = sorted.size
        val durationSec = if (sorted.size >= 2) {
            ((sorted.last().timestampMs - sorted.first().timestampMs) / 1000L).toInt()
        } else {
            0
        }

        val validRatio = validCount.toFloat() / total.toFloat()
        val gapPenalty = (irregularCount * 0.08f + duplicateCount * 0.05f + unrealisticCount * 0.10f)
            .coerceAtMost(0.65f)
        val durationFactor = when {
            durationSec >= 30 -> 1.0f
            durationSec >= 10 -> 0.8f
            durationSec >= 5 -> 0.6f
            else -> 0.25f
        }
        val reliability = ((validRatio - gapPenalty) * durationFactor).coerceIn(0f, 1f)
        val hasSpeed = speedPresentCount >= 5
        val isSufficient = validCount >= 5 && durationSec >= 5 && hasSpeed && reliability >= 0.35f

        val summary = buildString {
            append("전체 ${total}개 샘플 중 유효 ${validCount}개 (신뢰도 ${(reliability * 100).roundToInt()}%). ")
            if (!hasSpeed) append("속도 데이터가 부족합니다. ")
            if (roadWheelPresentCount == 0 && steeringWheelPresentCount == 0) {
                append("조향각 센서 데이터가 포함되지 않았습니다. ")
            }
            if (irregularCount > 0) append("불규칙 시간 간격 ${irregularCount}건 감지. ")
            if (unrealisticCount > 0) append("비현실 수치 ${unrealisticCount}건 제외됨.")
        }.trim()

        val report = DataQualityReport(
            totalSamples = total,
            validSamples = validCount,
            missingSampleCount = missingSpeedCount,
            duplicateTimestampCount = duplicateCount,
            irregularIntervalCount = irregularCount,
            unrealisticValueCount = unrealisticCount,
            hasSpeedData = hasSpeed,
            hasRoadWheelAngleData = roadWheelPresentCount >= 5,
            hasSteeringWheelAngleData = steeringWheelPresentCount >= 5,
            hasBrakeStatusData = brakePresentCount >= 5,
            overallReliability = reliability,
            summaryText = summary,
            isSufficientForScoring = isSufficient,
        )

        return enriched to report
    }

    /**
     * 전처리된 샘플 시퀀스에서 주행 이벤트 후보들을 탐지합니다. (섹션 15.2 ~ 15.6)
     * 단일 순간 노이즈로 이벤트를 확정하지 않도록 최소 지속시간 조건을 적용합니다.
     */
    fun detectEvents(
        sessionId: String,
        samples: List<TelemetrySample>,
    ): List<DrivingEvent> {
        if (samples.size < 3) return emptyList()
        val baseTimeMs = samples.first().timestampMs
        val events = mutableListOf<DrivingEvent>()

        // 1. 급가속 후보 감지 (sustained longitudinal acceleration >= 2.25 m/s² for >= 1.6s)
        var accelStartIdx = -1
        var maxAccelInWindow = 0f
        for (i in samples.indices) {
            val s = samples[i]
            val a = s.longitudinalAccelerationMps2
            val isValid = DataQualityFlag.VALID in s.qualityFlags
            if (isValid && a != null && a >= ABRUPT_ACCEL_THRESHOLD_MPS2) {
                if (accelStartIdx == -1) {
                    accelStartIdx = i
                    maxAccelInWindow = a
                } else {
                    maxAccelInWindow = max(maxAccelInWindow, a)
                }
            } else if (accelStartIdx != -1) {
                val endIdx = i - 1
                val startSample = samples[accelStartIdx]
                val endSample = samples[endIdx]
                val durationSec = (endSample.timestampMs - startSample.timestampMs) / 1000f
                val sampleSpanCount = endIdx - accelStartIdx + 1
                // 최소 지속시간(1.6초 이상 또는 1Hz 샘플 2구간 이상 연속) 만족 시에만 후보 등록
                if (durationSec >= ABRUPT_ACCEL_MIN_DURATION_SEC || sampleSpanCount >= 3) {
                    val vStart = startSample.speedKph ?: 0f
                    val vEnd = endSample.speedKph ?: 0f
                    val effectiveDuration = max(durationSec, sampleSpanCount * 1.0f)
                    val severity = if (maxAccelInWindow >= 3.2f || effectiveDuration >= 4.0f) {
                        SeverityCandidate.HIGH
                    } else {
                        SeverityCandidate.MODERATE
                    }
                    events.add(
                        DrivingEvent(
                            id = "${sessionId}_accel_${events.size + 1}",
                            eventType = DrivingEventType.ABRUPT_ACCELERATION_CANDIDATE,
                            startTimeMs = startSample.timestampMs,
                            endTimeMs = endSample.timestampMs,
                            elapsedStartSec = (startSample.timestampMs - baseTimeMs) / 1000f,
                            elapsedEndSec = (endSample.timestampMs - baseTimeMs) / 1000f,
                            measuredValues = mapOf(
                                "최대 추정 가속도" to String.format("%.2f m/s²", maxAccelInWindow),
                                "속도 변화" to String.format("%.1f → %.1f km/h", vStart, vEnd),
                                "지속 시간" to String.format("%.1f초", effectiveDuration),
                            ),
                            detectionRule = "종방향 가속도 ≥ ${ABRUPT_ACCEL_THRESHOLD_MPS2} m/s² 구간이 ${ABRUPT_ACCEL_MIN_DURATION_SEC}초 이상 지속",
                            severityCandidate = severity,
                            confidence = 0.86f,
                            explanation = "${String.format("%.1f", vStart)}km/h에서 ${String.format("%.1f", vEnd)}km/h까지 짧은 시간 동안 가속도가 최대 ${String.format("%.2f", maxAccelInWindow)}m/s²까지 상승한 구간이 관찰되었습니다.",
                            cautionNote = "고속도로 합류나 추월 등 교통 흐름상 필요한 가속이었을 수 있으므로 주변 상황과 함께 해석해야 합니다.",
                        )
                    )
                }
                accelStartIdx = -1
                maxAccelInWindow = 0f
            }
        }
        if (accelStartIdx != -1) {
            val startSample = samples[accelStartIdx]
            val endSample = samples.last()
            val durationSec = (endSample.timestampMs - startSample.timestampMs) / 1000f
            val sampleSpanCount = samples.lastIndex - accelStartIdx + 1
            if (durationSec >= ABRUPT_ACCEL_MIN_DURATION_SEC || sampleSpanCount >= 3) {
                val vStart = startSample.speedKph ?: 0f
                val vEnd = endSample.speedKph ?: 0f
                val effectiveDuration = max(durationSec, sampleSpanCount * 1.0f)
                events.add(
                    DrivingEvent(
                        id = "${sessionId}_accel_${events.size + 1}",
                        eventType = DrivingEventType.ABRUPT_ACCELERATION_CANDIDATE,
                        startTimeMs = startSample.timestampMs,
                        endTimeMs = endSample.timestampMs,
                        elapsedStartSec = (startSample.timestampMs - baseTimeMs) / 1000f,
                        elapsedEndSec = (endSample.timestampMs - baseTimeMs) / 1000f,
                        measuredValues = mapOf(
                            "최대 추정 가속도" to String.format("%.2f m/s²", maxAccelInWindow),
                            "속도 변화" to String.format("%.1f → %.1f km/h", vStart, vEnd),
                            "지속 시간" to String.format("%.1f초", effectiveDuration),
                        ),
                        detectionRule = "종방향 가속도 ≥ ${ABRUPT_ACCEL_THRESHOLD_MPS2} m/s² 구간이 ${ABRUPT_ACCEL_MIN_DURATION_SEC}초 이상 지속",
                        severityCandidate = SeverityCandidate.MODERATE,
                        confidence = 0.84f,
                        explanation = "가속 구간에서 최대 ${String.format("%.2f", maxAccelInWindow)}m/s²의 급격한 속도 상승이 관찰되었습니다.",
                        cautionNote = "도로 합류 등 정당한 가속 상황일 가능성을 고려해야 합니다.",
                    )
                )
            }
        }

        // 2. 후반 집중 / 급감속 후보 감지 (sustained deceleration <= -2.35 m/s² for >= 1.6s)
        var brakeStartIdx = -1
        var minDecelInWindow = 0f
        for (i in samples.indices) {
            val s = samples[i]
            val a = s.longitudinalAccelerationMps2
            val isValid = DataQualityFlag.VALID in s.qualityFlags
            if (isValid && a != null && a <= HARD_BRAKE_THRESHOLD_MPS2) {
                if (brakeStartIdx == -1) {
                    brakeStartIdx = i
                    minDecelInWindow = a
                } else {
                    minDecelInWindow = min(minDecelInWindow, a)
                }
            } else if (brakeStartIdx != -1) {
                val endIdx = i - 1
                val startSample = samples[max(0, brakeStartIdx - 1)]
                val endSample = samples[endIdx]
                val durationSec = (endSample.timestampMs - samples[brakeStartIdx].timestampMs) / 1000f
                val sampleSpanCount = endIdx - brakeStartIdx + 1
                if (durationSec >= HARD_BRAKE_MIN_DURATION_SEC || sampleSpanCount >= 2) {
                    val vStart = startSample.speedKph ?: 0f
                    val vEnd = endSample.speedKph ?: 0f
                    val effectiveDuration = max(durationSec, sampleSpanCount * 1.0f)
                    val isLateStop = vEnd <= 12.0f && vStart >= 30.0f
                    val severity = if (minDecelInWindow <= -3.3f || isLateStop) {
                        SeverityCandidate.HIGH
                    } else {
                        SeverityCandidate.MODERATE
                    }
                    events.add(
                        DrivingEvent(
                            id = "${sessionId}_brake_${events.size + 1}",
                            eventType = DrivingEventType.LATE_HARD_BRAKING_CANDIDATE,
                            startTimeMs = samples[brakeStartIdx].timestampMs,
                            endTimeMs = endSample.timestampMs,
                            elapsedStartSec = (samples[brakeStartIdx].timestampMs - baseTimeMs) / 1000f,
                            elapsedEndSec = (endSample.timestampMs - baseTimeMs) / 1000f,
                            measuredValues = mapOf(
                                "최대 추정 감속도" to String.format("%.2f m/s²", minDecelInWindow),
                                "속도 변화" to String.format("%.1f → %.1f km/h", vStart, vEnd),
                                "감속 집중 구간" to if (isLateStop) "정차 직전 후반 집중" else "중속 감속 구간",
                            ),
                            detectionRule = "종방향 감속도 ≤ ${HARD_BRAKE_THRESHOLD_MPS2} m/s² 지속 및 정지 직전 감속 집중도 평가",
                            severityCandidate = severity,
                            confidence = 0.88f,
                            explanation = "${String.format("%.1f", vStart)}km/h에서 ${String.format("%.1f", vEnd)}km/h로 감속하는 동안 최대 ${String.format("%.2f", minDecelInWindow)}m/s²의 강한 감속이 후반부에 집중되었습니다.",
                            cautionNote = "전방 장애물 회피나 신호 급변 등 긴급제동 상황에서는 강한 제동이 올바른 안전 조작이므로 무조건 나쁜 습관으로 단정하지 않습니다.",
                        )
                    )
                }
                brakeStartIdx = -1
                minDecelInWindow = 0f
            }
        }

        // 3. 저속 회전 수정 조향 후보 감지 (속도 7~38 km/h & 전륜 조향각 절대값 >= 4.0° 구간에서 조향 변화율 부호 반전 반복)
        var turnStartIdx = -1
        for (i in samples.indices) {
            val s = samples[i]
            val speed = s.speedKph ?: 0f
            val angle = s.effectiveRoadWheelAngleDeg
            val inLowSpeedTurn = angle != null && speed in 6.0f..40.0f && abs(angle) >= 4.0f
            if (inLowSpeedTurn) {
                if (turnStartIdx == -1) turnStartIdx = i
            } else if (turnStartIdx != -1) {
                val endIdx = i - 1
                if (endIdx - turnStartIdx >= 4) {
                    evaluateTurnWindowForSteeringCorrection(
                        sessionId = sessionId,
                        samples = samples,
                        startIdx = turnStartIdx,
                        endIdx = endIdx,
                        baseTimeMs = baseTimeMs,
                        outEvents = events,
                    )
                }
                turnStartIdx = -1
            }
        }
        if (turnStartIdx != -1 && samples.lastIndex - turnStartIdx >= 4) {
            evaluateTurnWindowForSteeringCorrection(
                sessionId = sessionId,
                samples = samples,
                startIdx = turnStartIdx,
                endIdx = samples.lastIndex,
                baseTimeMs = baseTimeMs,
                outEvents = events,
            )
        }

        // 4. 불안정한 속도 변화(가감속 진동) 후보 감지
        if (samples.size >= 14) {
            var windowStart = 0
            while (windowStart + 12 < samples.size) {
                val window = samples.subList(windowStart, windowStart + 12)
                var signFlips = 0
                var prevSign = 0
                for (w in window) {
                    val a = w.longitudinalAccelerationMps2 ?: 0f
                    val sign = when {
                        a >= 1.1f -> 1
                        a <= -1.1f -> -1
                        else -> 0
                    }
                    if (sign != 0) {
                        if (prevSign != 0 && sign != prevSign) {
                            signFlips++
                        }
                        prevSign = sign
                    }
                }
                if (signFlips >= 4) {
                    val firstS = window.first()
                    val lastS = window.last()
                    events.add(
                        DrivingEvent(
                            id = "${sessionId}_osc_${events.size + 1}",
                            eventType = DrivingEventType.SPEED_OSCILLATION_CANDIDATE,
                            startTimeMs = firstS.timestampMs,
                            endTimeMs = lastS.timestampMs,
                            elapsedStartSec = (firstS.timestampMs - baseTimeMs) / 1000f,
                            elapsedEndSec = (lastS.timestampMs - baseTimeMs) / 1000f,
                            measuredValues = mapOf(
                                "가감속 교차 횟수" to "${signFlips}회",
                                "구간 평균 속도" to String.format("%.1f km/h", window.mapNotNull { it.speedKph }.average()),
                            ),
                            detectionRule = "12초 구간 내 ±1.1 m/s² 이상 가감속 부호 교차 4회 이상",
                            severityCandidate = SeverityCandidate.INFO,
                            confidence = 0.78f,
                            explanation = "짧은 시간 동안 가속과 감속이 반복되어 속도 흐름이 일정하지 않은 구간이 관찰되었습니다.",
                            cautionNote = "정체 구간의 가다 서다 반복 주행일 수 있으므로 도로 혼잡도를 함께 고려해야 합니다.",
                        )
                    )
                    windowStart += 12
                } else {
                    windowStart += 4
                }
            }
        }

        // 5. 데이터 누락/간격 불규칙 구간 기록
        for (i in 1 until samples.size) {
            if (DataQualityFlag.IRREGULAR_INTERVAL in samples[i].qualityFlags) {
                val prev = samples[i - 1]
                val curr = samples[i]
                val gapSec = (curr.timestampMs - prev.timestampMs) / 1000f
                events.add(
                    DrivingEvent(
                        id = "${sessionId}_gap_${events.size + 1}",
                        eventType = DrivingEventType.DATA_GAP_WARNING,
                        startTimeMs = prev.timestampMs,
                        endTimeMs = curr.timestampMs,
                        elapsedStartSec = (prev.timestampMs - baseTimeMs) / 1000f,
                        elapsedEndSec = (curr.timestampMs - baseTimeMs) / 1000f,
                        measuredValues = mapOf(
                            "누락 간격" to String.format("%.1f초", gapSec),
                        ),
                        detectionRule = "샘플 간 타임스탬프 간격 > ${IRREGULAR_GAP_THRESHOLD_SEC}초",
                        severityCandidate = SeverityCandidate.INFO,
                        confidence = 0.99f,
                        explanation = "${String.format("%.1f", gapSec)}초 동안 센서 샘플이 수신되지 않아 해당 구간의 미분 가속도 추정을 제외했습니다.",
                        cautionNote = "데이터 누락 구간은 운전자 감점 요인이 아니며 데이터 신뢰도 산정에만 반영됩니다.",
                    )
                )
            }
        }

        return events.sortedBy { it.startTimeMs }
    }

    private fun evaluateTurnWindowForSteeringCorrection(
        sessionId: String,
        samples: List<TelemetrySample>,
        startIdx: Int,
        endIdx: Int,
        baseTimeMs: Long,
        outEvents: MutableList<DrivingEvent>,
    ) {
        var directionReversals = 0
        var prevDeltaSign = 0
        var maxAbsRoadAngle = 0f
        var maxAngularRateDegPerSec = 0f

        for (i in (startIdx + 1)..endIdx) {
            val prevAngle = samples[i - 1].effectiveRoadWheelAngleDeg ?: continue
            val currAngle = samples[i].effectiveRoadWheelAngleDeg ?: continue
            val dtSec = ((samples[i].timestampMs - samples[i - 1].timestampMs) / 1000f).coerceAtLeast(0.1f)
            val delta = currAngle - prevAngle
            val rate = abs(delta / dtSec)
            maxAbsRoadAngle = max(maxAbsRoadAngle, abs(currAngle))
            maxAngularRateDegPerSec = max(maxAngularRateDegPerSec, rate)

            // 의미 있는 각도 변화(전륜 기준 1.2° 이상)에서 방향 전환 횟수 집계
            if (abs(delta) >= 1.2f) {
                val sign = if (delta > 0f) 1 else -1
                if (prevDeltaSign != 0 && sign != prevDeltaSign) {
                    directionReversals++
                }
                prevDeltaSign = sign
            }
        }

        // 단순 진입(0 -> 피크) 후 복원(피크 -> 0)은 반전 1회이므로, 3회 이상 반전 시에만 수정 조향 후보로 판단
        if (directionReversals >= 3) {
            val startS = samples[startIdx]
            val endS = samples[endIdx]
            val avgSpeed = samples.subList(startIdx, endIdx + 1)
                .mapNotNull { it.speedKph }
                .average()
                .toFloat()
            val turnDirection = if ((samples[(startIdx + endIdx) / 2].effectiveRoadWheelAngleDeg ?: 0f) < 0f) {
                "좌회전"
            } else {
                "우회전"
            }
            outEvents.add(
                DrivingEvent(
                    id = "${sessionId}_steer_${outEvents.size + 1}",
                    eventType = DrivingEventType.LOW_SPEED_STEERING_CORRECTION_CANDIDATE,
                    startTimeMs = startS.timestampMs,
                    endTimeMs = endS.timestampMs,
                    elapsedStartSec = (startS.timestampMs - baseTimeMs) / 1000f,
                    elapsedEndSec = (endS.timestampMs - baseTimeMs) / 1000f,
                    measuredValues = mapOf(
                        "회전 방향" to "저속 $turnDirection 구간",
                        "조향 방향 반전" to "${directionReversals}회 (수정 조향 ${directionReversals - 1}회)",
                        "최대 전륜 조향각" to String.format("%.1f°", maxAbsRoadAngle),
                        "최대 조향 변화율" to String.format("%.1f°/s", maxAngularRateDegPerSec),
                        "평균 진입 속도" to String.format("%.1f km/h", avgSpeed),
                    ),
                    detectionRule = "저속(6~40km/h) 회전 중 전륜 조향각 ≥ 4° 구간 내 조향 증감 방향 반전 3회 이상",
                    severityCandidate = if (directionReversals >= 4) SeverityCandidate.HIGH else SeverityCandidate.MODERATE,
                    confidence = 0.82f,
                    explanation = "평균 ${String.format("%.1f", avgSpeed)}km/h 저속 $turnDirection 과정에서 조향 각도가 한 번의 궤적으로 유지되지 않고 ${directionReversals - 1}회 수정 조향되는 패턴이 관찰되었습니다.",
                    cautionNote = "보행자 보호, 불법 주정차 차량 회피, 좁은 골목길 회전 등 도로 환경에 따라 수정 조향이 필수적인 경우가 있으므로 최적 핸들 각도를 하나로 단정하지 않습니다.",
                )
            )
        }
    }

    /**
     * 전체 주행 세션을 분석하여 이벤트, 세부 점수, 품질 리포트, 맞춤형 코칭이 포함된 완성된 세션을 반환합니다.
     */
    fun analyzeSession(
        session: DrivingSession,
        weights: ScoringWeights = ScoringWeights(),
        coachingGenerator: CoachingGenerator = RuleBasedCoachingGenerator,
    ): DrivingSession {
        val (processedSamples, qualityReport) = preprocessAndEvaluateQuality(session.telemetrySamples)
        val detectedEvents = detectEvents(session.id, processedSamples)

        val validSpeeds = processedSamples.mapNotNull { sample ->
            sample.speedKph?.takeIf { DataQualityFlag.VALID in sample.qualityFlags }
        }
        val avgSpeed = if (validSpeeds.isNotEmpty()) validSpeeds.average().toFloat() else 0f
        val maxSpeed = validSpeeds.maxOrNull() ?: 0f

        val durationSec = if (processedSamples.size >= 2) {
            ((processedSamples.last().timestampMs - processedSamples.first().timestampMs) / 1000L)
                .toInt()
                .coerceAtLeast(0)
        } else {
            0
        }

        // 거리 계산 (적산 거리계가 있으면 우선 활용, 없으면 속도 적분)
        val calculatedDistanceKm = calculateDistanceKm(processedSamples)
        val finalDistanceKm = if (session.distanceKm > 0.01f) session.distanceKm else calculatedDistanceKm

        val analysisResult: DrivingAnalysisResult = DrivingScoreCalculator.calculateScores(
            sessionId = session.id,
            samples = processedSamples,
            events = detectedEvents,
            qualityReport = qualityReport,
            durationSec = durationSec,
            weights = weights,
            coachingGenerator = coachingGenerator,
        )

        val status = if (qualityReport.isSufficientForScoring && analysisResult.overallScore != null) {
            AnalysisStatus.ANALYZED
        } else {
            AnalysisStatus.INSUFFICIENT_DATA
        }

        return session.copy(
            durationSec = durationSec,
            distanceKm = finalDistanceKm,
            averageSpeedKph = avgSpeed,
            maximumSpeedKph = maxSpeed,
            telemetrySamples = processedSamples,
            detectedEvents = detectedEvents,
            analysisStatus = status,
            analysisResult = analysisResult,
        )
    }

    private fun calculateDistanceKm(samples: List<TelemetrySample>): Float {
        if (samples.size < 2) return 0f
        val firstOdo = samples.firstNotNullOfOrNull { it.odometerKm }
        val lastOdo = samples.lastOrNull { it.odometerKm != null }?.odometerKm
        if (firstOdo != null && lastOdo != null && lastOdo > firstOdo) {
            return (lastOdo - firstOdo)
        }
        var meters = 0.0
        for (i in 1 until samples.size) {
            val dtSec = (samples[i].timestampMs - samples[i - 1].timestampMs) / 1000.0
            if (dtSec in 0.05..5.0) {
                val v1 = samples[i - 1].speedKph ?: continue
                val v2 = samples[i].speedKph ?: continue
                val avgMps = ((v1 + v2) * 0.5f) / 3.6f
                meters += avgMps * dtSec
            }
        }
        return (meters / 1000.0).toFloat()
    }
}
