package com.drivecoach.app.simulation.vehicle

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

/**
 * 시뮬레이션용 가상 차량 제원 모델 (PART 5 섹션 9)
 *
 * 주의:
 * - 기본 수치는 일반 승용차를 가정한 시뮬레이션용 예시값(전장 4.5m, 전폭 1.8m, 축거 2.7m, 최대 전륜 조향각 32도)이며,
 *   현대자동차 특정 양산 모델의 공식 제원이 아닙니다.
 * - 본 시뮬레이터에서는 가상 조향 입력을 전륜 조향각(δ)으로 직접 매핑하며, 실제 차량의 스티어링 기어비(핸들 회전수 대비 바퀴 각도) 및
 *   속도 감응형 조향 특성을 그대로 재현하지 않습니다.
 */
data class VehicleConfig(
    val name: String = "일반 승용차 (시뮬레이션 기준)",
    val lengthMeters: Float = 4.5f,
    val widthMeters: Float = 1.8f,
    val wheelbaseMeters: Float = 2.7f,
    val frontOverhangMeters: Float = 0.9f,
    val rearOverhangMeters: Float = 0.9f,
    val maxSteeringAngleDeg: Float = 32.0f,
    val defaultForwardSpeedMps: Float = 1.45f,
    val defaultReverseSpeedMps: Float = 1.15f,
    val maxSpeedLimitMps: Float = 3.0f,
    val safetyMarginMeters: Float = 0.12f,
    val wheelLengthMeters: Float = 0.64f,
    val wheelWidthMeters: Float = 0.22f,
) {
    val sanitizedWheelbaseMeters: Float
        get() = wheelbaseMeters.coerceIn(1.5f, 4.5f)

    val sanitizedLengthMeters: Float
        get() = lengthMeters.coerceIn(3.0f, 6.5f)

    val sanitizedWidthMeters: Float
        get() = widthMeters.coerceIn(1.4f, 2.5f)

    val sanitizedMaxSteeringDeg: Float
        get() = maxSteeringAngleDeg.coerceIn(15.0f, 45.0f)

    val maxSteeringAngleRad: Float
        get() = (sanitizedMaxSteeringDeg * PI / 180.0).toFloat()

    /**
     * 후륜 차축 중앙으로부터 앞 범퍼까지의 거리 (L + 앞 오버행)
     */
    val rearAxleToFrontBumperMeters: Float
        get() = sanitizedWheelbaseMeters + frontOverhangMeters.coerceAtLeast(0.3f)

    /**
     * 후륜 차축 중앙으로부터 뒷 범퍼까지의 거리 (뒤 오버행)
     */
    val rearAxleToRearBumperMeters: Float
        get() = rearOverhangMeters.coerceAtLeast(0.3f)

    /**
     * 후륜 차축 중앙으로부터 차체 기하학적 중심까지의 전방 오프셋
     */
    val rearAxleToBodyCenterOffsetMeters: Float
        get() = (rearAxleToFrontBumperMeters - rearAxleToRearBumperMeters) * 0.5f

    /**
     * 최소 회전 반경 (후륜 차축 중앙 기준, R_min = L / tan(δ_max))
     */
    val minTurningRadiusRearAxleMeters: Float
        get() {
            val tanVal = tan(maxSteeringAngleRad.toDouble()).toFloat()
            return if (abs(tanVal) < 1e-4f) 100f else sanitizedWheelbaseMeters / abs(tanVal)
        }
}

/**
 * 시뮬레이션 내 가상 기어 방향 (실제 차량 기어와 무관한 시뮬레이터 전용 상태)
 */
enum class VirtualGearDirection(val koreanLabel: String, val shortCode: String, val speedSign: Int) {
    FORWARD("전진", "D", +1),
    REVERSE("후진", "R", -1),
}

/**
 * 차량의 2D 운동학 상태 (PART 5 섹션 8)
 *
 * - 기준점 (x, y): 후륜 차축 중앙 (Rear Axle Center, 단위: m)
 * - 진행 방향 (headingRad, θ): 라디안 (0 = +X 우측, +π/2 = +Y 상단)
 * - 종방향 속도 (velocityMps, v): m/s (전진 > 0, 후진 < 0, 정지 = 0)
 * - 전륜 조향각 (steeringAngleRad, δ): 라디안 (물리 좌표계 기준 +값은 차량 기준 좌회전/반시계, -값은 우회전/시계)
 */
data class VehicleState(
    val x: Float = 0f,
    val y: Float = 0f,
    val headingRad: Float = 0f,
    val velocityMps: Float = 0f,
    val steeringAngleRad: Float = 0f,
) {
    val headingDeg: Float
        get() = (headingRad * 180.0 / PI).toFloat()

    val steeringAngleDeg: Float
        get() = (steeringAngleRad * 180.0 / PI).toFloat()

    /**
     * UI 표시용 조향 각도 (음수 = 좌측 조향, 0 = 중앙, 양수 = 우측 조향)
     */
    val uiSteeringRightPositiveDeg: Float
        get() = -steeringAngleDeg

    val isStopped: Boolean
        get() = abs(velocityMps) < 1e-3f
}

/**
 * 사용자 조향 및 이동 입력 모델
 */
data class SteeringAndDriveInput(
    val uiSteeringRightPositiveDeg: Float = 0f,
    val gearDirection: VirtualGearDirection = VirtualGearDirection.REVERSE,
    val isMovingRequested: Boolean = false,
)
