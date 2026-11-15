package com.drivecoach.app.data.simulation

import com.drivecoach.app.domain.analysis.DrivingAnalysisEngine
import com.drivecoach.app.domain.model.AnalysisStatus
import com.drivecoach.app.domain.model.DataQualityFlag
import com.drivecoach.app.domain.model.DataSourceType
import com.drivecoach.app.domain.model.DrivingSession
import com.drivecoach.app.domain.model.GearPosition
import com.drivecoach.app.domain.model.ScoringWeights
import com.drivecoach.app.domain.model.TelemetrySample
import com.drivecoach.app.domain.model.TurnSignalState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class DemoScenarioSpec(
    val id: String,
    val codeLabel: String,
    val title: String,
    val subtitle: String,
    val driverPersona: String,
    val expectedBehaviorSummary: String,
    val durationSec: Int,
)

/**
 * 5대 필수 데모 시나리오 생성기 (섹션 17)
 * - 고정된 결정론적(Deterministic) 물리 규칙으로 자연스러운 시계열 데이터를 생성합니다.
 * - 동일한 시나리오 입력에 대해 항상 동일한 속도와 이벤트 탐지 결과를 보장합니다.
 */
object DemoScenarioCatalog {

    val scenarios: List<DemoScenarioSpec> = listOf(
        DemoScenarioSpec(
            id = "smooth_driver",
            codeLabel = "Scenario A · Smooth Driver",
            title = "안정 주행 (Smooth Driver)",
            subtitle = "완만한 출발과 여유 있는 예측 감속, 매끄러운 회전 궤적",
            driverPersona = "부드럽게 출발하고 정지 예측 구간에서 미리 감속을 시작하는 운전자",
            expectedBehaviorSummary = "가속·제동·조향 전 항목 90점대 양호, 급격한 조작 후보 0건",
            durationSec = 60,
        ),
        DemoScenarioSpec(
            id = "late_braking",
            codeLabel = "Scenario B · Late Braking",
            title = "후반 집중 감속 (Late Braking)",
            subtitle = "정차 직전까지 속도를 유지하다가 후반부에 강하게 제동하는 패턴",
            driverPersona = "교차로 및 정지선 앞에서 감속 시작이 늦어 후반 감속도가 커지는 운전자",
            expectedBehaviorSummary = "후반 집중·급감속 후보 2~3건 탐지, 감속 시작 시점 코칭 및 부드러운 감속 교육 추천",
            durationSec = 60,
        ),
        DemoScenarioSpec(
            id = "abrupt_acceleration",
            codeLabel = "Scenario C · Abrupt Acceleration",
            title = "급격한 가속 반복 (Abrupt Acceleration)",
            subtitle = "출발 및 재가속 구간에서 짧은 시간 동안 가속도가 급상승하는 패턴",
            driverPersona = "출발 직후 가속 페달을 깊게 밟아 초기 가속도가 반복적으로 높아지는 운전자",
            expectedBehaviorSummary = "급가속 후보 2~3건 탐지, 가속 안정성 감점 및 초기 페달 완만화 코칭 제공",
            durationSec = 60,
        ),
        DemoScenarioSpec(
            id = "steering_correction",
            codeLabel = "Scenario D · Steering Correction",
            title = "저속 회전 수정 조향 (Steering Correction)",
            subtitle = "교차로 저속 좌·우회전 중 조향 각도를 여러 번 고쳐 잡는 패턴",
            driverPersona = "회전 진입 속도와 시선 처리가 불안정해 회전 도중 핸들을 반복 수정하는 운전자",
            expectedBehaviorSummary = "저속 회전 수정 조향 후보 2건 탐지, 조향 그래프 표시 및 회전 반경 학습 추천",
            durationSec = 60,
        ),
        DemoScenarioSpec(
            id = "mixed_driving",
            codeLabel = "Scenario E · Mixed Driving",
            title = "복합 도심 주행 (Mixed Driving)",
            subtitle = "급가속, 후반 감속, 저속 수정 조향과 일반 주행이 혼합된 현실적인 주행",
            driverPersona = "도심 출퇴근길에서 급출발·후반 제동·교차로 수정 조향이 복합적으로 나타나는 운전자",
            expectedBehaviorSummary = "복수 이벤트 탐지, 항목별 비교 분석 및 최우선 개선 과제 도출",
            durationSec = 70,
        ),
    )

    fun findSpec(scenarioId: String): DemoScenarioSpec =
        scenarios.find { it.id == scenarioId } ?: scenarios.first()

    /**
     * 시나리오 ID에 맞는 결정론적 1Hz 텔레메트리 샘플 리스트를 생성합니다.
     */
    fun generateSamples(
        scenarioId: String,
        baseTimestampMs: Long = 1_763_100_000_000L,
        dataSource: DataSourceType = DataSourceType.SIMULATED,
    ): List<TelemetrySample> {
        val spec = findSpec(scenarioId)
        val totalSec = spec.durationSec
        val samples = ArrayList<TelemetrySample>(totalSec + 1)

        var currentSpeedKph = 0f
        var distanceMeters = 0f
        val baseOdometerKm = 14_280.0f

        for (t in 0..totalSec) {
            val targetAccelMps2: Float
            val roadWheelAngleDeg: Float
            val turnSignal: TurnSignalState

            when (scenarioId) {
                "smooth_driver" -> {
                    targetAccelMps2 = when (t) {
                        in 0..2 -> 0f
                        in 3..14 -> 1.25f // 부드러운 출발 (0 -> ~54 km/h)
                        in 15..25 -> 0.05f * sin(t * 0.5f)
                        in 26..33 -> -0.95f // 회전 전 완만한 감속 (~54 -> ~27 km/h)
                        in 34..40 -> 0.0f // 일정한 속도로 저속 좌회전 통과
                        in 41..48 -> 1.05f // 부드러운 재가속 (~27 -> ~57 km/h)
                        in 49..58 -> -1.45f // 여유 있는 예측 감속 정차
                        else -> 0f
                    }
                    // 34~40초 구간에서 한 번의 매끄러운 좌회전 궤적 (수정 조향 없음)
                    roadWheelAngleDeg = when (t) {
                        33 -> -3.5f
                        34 -> -8.0f
                        35 -> -12.5f
                        36 -> -14.0f
                        37 -> -14.0f
                        38 -> -11.5f
                        39 -> -6.5f
                        40 -> -2.0f
                        else -> 0.2f * sin(t * 0.3f)
                    }
                    turnSignal = if (t in 31..40) TurnSignalState.LEFT else TurnSignalState.OFF
                }

                "late_braking" -> {
                    targetAccelMps2 = when (t) {
                        in 0..1 -> 0f
                        in 2..12 -> 1.45f // 0 -> ~57 km/h
                        in 13..20 -> 0.1f // 정지선 직전까지 속도 유지
                        in 21..24 -> -3.55f // 1차 후반 집중 급감속 (58 -> ~7 km/h)
                        in 25..33 -> 1.50f // 재가속 (7 -> ~55 km/h)
                        in 34..40 -> 0.0f // 순항 유지
                        in 41..44 -> -3.65f // 2차 후반 집중 급감속 (55 -> ~2 km/h)
                        in 45..51 -> 1.35f // 재출발 (2 -> ~36 km/h)
                        in 52..55 -> -2.90f // 3차 정차 직전 급감속
                        else -> 0f
                    }
                    roadWheelAngleDeg = when (t) {
                        in 26..30 -> 6.5f * sin((t - 26) * 0.6f)
                        else -> 0.15f * sin(t * 0.4f)
                    }
                    turnSignal = if (t in 25..30) TurnSignalState.RIGHT else TurnSignalState.OFF
                }

                "abrupt_acceleration" -> {
                    targetAccelMps2 = when (t) {
                        in 0..1 -> 0f
                        in 2..6 -> 3.35f // 1차 급출발 (5초간 ~60 km/h 급상승)
                        in 7..15 -> -0.2f
                        in 16..24 -> -1.45f // 완만한 감속 (~51 -> ~9 km/h)
                        in 25..29 -> 3.45f // 2차 급격한 재가속 (5초간 급상승)
                        in 30..38 -> -1.55f // 감속
                        in 39..43 -> 3.15f // 3차 급가속 구간
                        in 44..56 -> -1.40f // 안정적 감속 정차
                        else -> 0f
                    }
                    roadWheelAngleDeg = 0.3f * sin(t * 0.35f)
                    turnSignal = TurnSignalState.OFF
                }

                "steering_correction" -> {
                    targetAccelMps2 = when (t) {
                        in 0..2 -> 0f
                        in 3..10 -> 0.95f // ~27 km/h 저속 주행 진입
                        in 11..21 -> 0.0f // 1차 저속 좌회전 구간 (약 25~27 km/h)
                        in 22..32 -> 0.4f // 직선 이동 (~40 km/h)
                        in 33..36 -> -1.1f // 감속 후 (~24 km/h)
                        in 37..47 -> 0.0f // 2차 저속 우회전 구간 (약 24 km/h)
                        in 48..57 -> -0.7f // 완만한 정차
                        else -> 0f
                    }
                    // 저속 회전 도중 각도가 커졌다 작아졌다 반복(부호 반전 4회 이상)하는 수정 조향 시뮬레이션
                    roadWheelAngleDeg = when (t) {
                        // 1차 좌회전 수정 조향 구간 (11~21초)
                        11 -> -6.0f
                        12 -> -15.5f
                        13 -> -9.0f   // 반전 1 (너무 감아서 풀었다가)
                        14 -> -18.0f  // 반전 2 (다시 급하게 감고)
                        15 -> -11.0f  // 반전 3 (다시 풀고)
                        16 -> -16.5f  // 반전 4 (다시 감음)
                        17 -> -10.5f  // 반전 5
                        18 -> -14.0f
                        19 -> -7.0f
                        20 -> -3.0f
                        // 2차 우회전 수정 조향 구간 (37~46초)
                        37 -> 7.0f
                        38 -> 16.0f
                        39 -> 9.5f    // 반전 1
                        40 -> 18.5f   // 반전 2
                        41 -> 11.5f   // 반전 3
                        42 -> 17.0f   // 반전 4
                        43 -> 10.0f
                        44 -> 5.0f
                        else -> 0.2f * sin(t * 0.3f)
                    }
                    turnSignal = when (t) {
                        in 9..20 -> TurnSignalState.LEFT
                        in 35..45 -> TurnSignalState.RIGHT
                        else -> TurnSignalState.OFF
                    }
                }

                else -> { // "mixed_driving"
                    targetAccelMps2 = when (t) {
                        in 0..1 -> 0f
                        in 2..6 -> 3.25f // 초반 급가속 후보 1건
                        in 7..15 -> -0.8f // 감속하여 저속 구간 진입 (~29 km/h)
                        in 16..25 -> 0.0f // 저속 좌회전 수정 조향 구간
                        in 26..35 -> 1.25f // 일반 재가속 (~29 -> ~68 km/h)
                        in 36..44 -> -0.1f // 순항
                        in 45..48 -> -3.50f // 후반 집중 급감속 후보 1건
                        in 49..56 -> 1.15f // 완만한 주행
                        in 57..67 -> -1.35f // 마지막 정차는 안정적인 감속
                        else -> 0f
                    }
                    roadWheelAngleDeg = when (t) {
                        16 -> -7.0f
                        17 -> -16.5f
                        18 -> -9.5f
                        19 -> -18.0f
                        20 -> -11.0f
                        21 -> -16.0f
                        22 -> -8.0f
                        23 -> -3.0f
                        else -> 0.25f * sin(t * 0.3f)
                    }
                    turnSignal = if (t in 14..23) TurnSignalState.LEFT else TurnSignalState.OFF
                }
            }

            val nextSpeedMps = max(0f, (currentSpeedKph / 3.6f) + targetAccelMps2 * 1.0f)
            val nextSpeedKph = min(130f, nextSpeedMps * 3.6f)
            val actualAccelMps2 = if (t == 0) 0f else (nextSpeedKph - currentSpeedKph) / 3.6f
            distanceMeters += ((currentSpeedKph + nextSpeedKph) * 0.5f / 3.6f) * 1.0f
            currentSpeedKph = nextSpeedKph

            val brakePressed = actualAccelMps2 < -0.45f || currentSpeedKph < 0.5f
            val brakePercent = if (actualAccelMps2 < -0.2f) {
                (abs(actualAccelMps2) / 4.5f * 100f).coerceIn(5f, 100f)
            } else if (currentSpeedKph < 0.5f) {
                25f
            } else {
                0f
            }
            val accelPercent = if (actualAccelMps2 > 0.1f) {
                (actualAccelMps2 / 3.8f * 100f).coerceIn(8f, 100f)
            } else if (currentSpeedKph > 5f && actualAccelMps2 >= -0.15f) {
                18f
            } else {
                0f
            }

            val gear = if (t == 0 || (t >= totalSec - 1 && currentSpeedKph < 1.0f)) {
                GearPosition.PARK
            } else {
                GearPosition.DRIVE
            }

            samples.add(
                TelemetrySample(
                    timestampMs = baseTimestampMs + (t * 1000L),
                    speedKph = currentSpeedKph,
                    longitudinalAccelerationMps2 = actualAccelMps2,
                    isAccelerationEstimated = true,
                    brakePedalPercent = brakePercent,
                    brakePedalPressed = brakePressed,
                    acceleratorPedalPercent = accelPercent,
                    steeringWheelAngleDeg = roadWheelAngleDeg * 14.5f,
                    roadWheelAngleDeg = roadWheelAngleDeg,
                    odometerKm = baseOdometerKm + (distanceMeters / 1000f),
                    tripDistanceMeters = distanceMeters,
                    gearPosition = gear,
                    turnSignalState = turnSignal,
                    dataSource = dataSource,
                    qualityFlags = setOf(
                        DataQualityFlag.VALID,
                        DataQualityFlag.ESTIMATED_ACCELERATION,
                        DataQualityFlag.SIMULATED_SOURCE,
                    ),
                )
            )
        }

        return samples
    }

    /**
     * 시나리오로부터 완전히 분석된 DrivingSession 객체를 생성합니다.
     */
    fun buildAnalyzedScenarioSession(
        scenarioId: String,
        sessionId: String = "demo_${scenarioId}",
        startTimeMs: Long = System.currentTimeMillis() - 3_600_000L,
        dataSource: DataSourceType = DataSourceType.DEMO,
        weights: ScoringWeights = ScoringWeights(),
    ): DrivingSession {
        val spec = findSpec(scenarioId)
        val rawSamples = generateSamples(
            scenarioId = scenarioId,
            baseTimestampMs = startTimeMs,
            dataSource = dataSource,
        )
        val rawSession = DrivingSession(
            id = sessionId,
            title = spec.title,
            subtitle = spec.subtitle,
            scenarioId = spec.id,
            startTimeMs = rawSamples.first().timestampMs,
            endTimeMs = rawSamples.last().timestampMs,
            durationSec = spec.durationSec,
            distanceKm = (rawSamples.last().tripDistanceMeters ?: 0f) / 1000f,
            averageSpeedKph = 0f,
            maximumSpeedKph = 0f,
            telemetrySamples = rawSamples,
            detectedEvents = emptyList(),
            analysisStatus = AnalysisStatus.PENDING,
            analysisResult = null,
            dataSource = dataSource,
        )
        return DrivingAnalysisEngine.analyzeSession(
            session = rawSession,
            weights = weights,
        )
    }

    /**
     * 앱 최초 실행 또는 데모 데이터 초기화 시 제공할 5개 기본 데모 세션 목록을 생성합니다.
     * 최근 시간순으로 정렬되어 전후 비교 및 추세 그래프를 즉시 시연할 수 있습니다.
     */
    fun buildInitialDemoSessions(
        nowMs: Long = System.currentTimeMillis(),
        weights: ScoringWeights = ScoringWeights(),
    ): List<DrivingSession> {
        // 시간 순서: 과거(Late Braking -> Abrupt Accel -> Steering Correction -> Mixed Driving) -> 최신(Smooth Driver 전에 Mixed Driving과 비교 가능하도록 구성)
        val orderedSpecs = listOf(
            "smooth_driver" to (nowMs - 25 * 60 * 1000L),          // 오늘 · 25분 전 (최신 개선 주행)
            "mixed_driving" to (nowMs - 4 * 3600 * 1000L),         // 오늘 · 4시간 전 (복합 도심 주행)
            "late_braking" to (nowMs - 26 * 3600 * 1000L),         // 어제 · 후반 집중 감속
            "steering_correction" to (nowMs - 50 * 3600 * 1000L),  // 2일 전 · 저속 회전 수정 조향
            "abrupt_acceleration" to (nowMs - 74 * 3600 * 1000L),  // 3일 전 · 급격한 가속 반복
        )

        return orderedSpecs.mapIndexed { idx, (scenarioId, timestamp) ->
            buildAnalyzedScenarioSession(
                scenarioId = scenarioId,
                sessionId = "seed_demo_${idx + 1}_$scenarioId",
                startTimeMs = timestamp,
                dataSource = DataSourceType.DEMO,
                weights = weights,
            )
        }
    }
}
