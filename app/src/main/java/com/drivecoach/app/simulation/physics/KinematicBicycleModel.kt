package com.drivecoach.app.simulation.physics

import com.drivecoach.app.simulation.vehicle.VehicleConfig
import com.drivecoach.app.simulation.vehicle.VehicleState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 저속 차량운동학 모델 — Kinematic Bicycle Model (PART 5 섹션 8)
 *
 * [상태 정의]
 * - (x, y): 후륜 차축 중앙(Rear Axle Center)의 월드 위치 (m)
 * - θ (headingRad): 차량 진행 방향 각도 (rad)
 * - v (velocityMps): 종방향 속도 (m/s, 전진 > 0, 후진 < 0)
 * - δ (steeringAngleRad): 전륜 조향각 (rad)
 * - L (wheelbaseMeters): 축거 (m)
 *
 * [운동 방정식]
 * - dx/dt = v * cos(θ)
 * - dy/dt = v * sin(θ)
 * - dθ/dt = (v / L) * tan(δ)
 *
 * [물리적 한계 및 주의사항 명시]
 * 본 모델은 저속 주차 및 교차로 진입 궤적 학습을 위한 기구학적(Kinematic) 모델입니다.
 * 따라서 타이어 슬립(Tire Slip), 노면 마찰계수 변화, 고속 하중 이동 및 차체 동역학(Dynamic Bicycle Model),
 * 실제 유압/전동식 조향 액추에이터의 응답 지연 및 실제 운전석 3차원 시야각을 그대로 재현하지는 않습니다.
 */
object KinematicBicycleModel {

    /**
     * 물리 계산의 일관성과 충돌 터널링 방지를 위한 최대 서브스텝 시간 간격 (20ms = 50Hz)
     */
    const val FIXED_SUBSTEP_DT_SEC: Float = 0.02f

    /**
     * 단일 호출에서 허용할 최대 누적 시뮬레이션 시간 (비정상 프레임 드랍 방어 및 단위 테스트 1~2초 적분 지원)
     */
    const val MAX_FRAME_DT_SEC: Float = 2.5f

    /**
     * 각도를 [-π, +π] 범위로 정규화합니다.
     */
    fun normalizeAngleRad(angleRad: Float): Float {
        if (!angleRad.isFinite()) return 0f
        return atan2(sin(angleRad.toDouble()), cos(angleRad.toDouble())).toFloat()
    }

    /**
     * 전륜 조향각(rad)을 차량 제원의 최대 조향각 범위 [-δ_max, +δ_max]로 제한합니다.
     */
    fun clampSteeringAngleRad(steeringAngleRad: Float, config: VehicleConfig): Float {
        if (!steeringAngleRad.isFinite()) return 0f
        val maxRad = config.maxSteeringAngleRad
        return steeringAngleRad.coerceIn(-maxRad, maxRad)
    }

    /**
     * 종방향 속도(m/s)를 안전 범위 내로 제한합니다.
     */
    fun clampVelocityMps(velocityMps: Float, config: VehicleConfig): Float {
        if (!velocityMps.isFinite()) return 0f
        val limit = config.maxSpeedLimitMps.coerceIn(0.5f, 8.0f)
        return velocityMps.coerceIn(-limit, limit)
    }

    /**
     * 주어진 상태(state)에서 시간 간격(dtSec) 동안 Kinematic Bicycle Model을 적분하여 다음 상태를 반환합니다.
     *
     * 큰 dtSec가 입력되더라도 내부적으로 고정 서브스텝(FIXED_SUBSTEP_DT_SEC)으로 분할 적분하여
     * 화면 프레임 속도에 관계없이 동일한 이동거리와 회전 궤적을 보장합니다.
     */
    fun step(
        state: VehicleState,
        dtSec: Float,
        config: VehicleConfig,
    ): VehicleState {
        val safeState = sanitizeState(state, config)
        if (!dtSec.isFinite() || dtSec <= 0f) {
            return safeState
        }

        val clampedDt = dtSec.coerceAtMost(MAX_FRAME_DT_SEC)
        if (abs(safeState.velocityMps) < 1e-5f) {
            return safeState.copy(velocityMps = 0f)
        }

        var current = safeState
        var remaining = clampedDt
        while (remaining > 1e-6f) {
            val stepDt = remaining.coerceAtMost(FIXED_SUBSTEP_DT_SEC)
            current = integrateSingleSubstep(current, stepDt, config)
            remaining -= stepDt
        }
        return current
    }

    /**
     * 단일 미소 시간(dt)에 대한 2차 중점(Midpoint / RK2) 기구학 적분
     */
    fun integrateSingleSubstep(
        state: VehicleState,
        dt: Float,
        config: VehicleConfig,
    ): VehicleState {
        val v = clampVelocityMps(state.velocityMps, config)
        val delta = clampSteeringAngleRad(state.steeringAngleRad, config)
        val wheelbase = config.sanitizedWheelbaseMeters

        if (abs(v) < 1e-5f || dt <= 0f) {
            return state.copy(
                velocityMps = if (abs(v) < 1e-5f) 0f else v,
                steeringAngleRad = delta,
                headingRad = normalizeAngleRad(state.headingRad),
            )
        }

        val yawRate = (v / wheelbase) * tan(delta.toDouble()).toFloat()
        val midTheta = state.headingRad + yawRate * (dt * 0.5f)

        val dx = v * cos(midTheta.toDouble()).toFloat() * dt
        val dy = v * sin(midTheta.toDouble()).toFloat() * dt
        val nextTheta = normalizeAngleRad(state.headingRad + yawRate * dt)

        return VehicleState(
            x = state.x + dx,
            y = state.y + dy,
            headingRad = nextTheta,
            velocityMps = v,
            steeringAngleRad = delta,
        )
    }

    /**
     * NaN, Infinity 등 비정상 입력값을 방어하고 안전한 상태로 정규화합니다.
     */
    fun sanitizeState(state: VehicleState, config: VehicleConfig): VehicleState {
        val safeX = if (state.x.isFinite()) state.x.coerceIn(-500f, 500f) else 0f
        val safeY = if (state.y.isFinite()) state.y.coerceIn(-500f, 500f) else 0f
        val safeTheta = normalizeAngleRad(state.headingRad)
        val safeV = clampVelocityMps(state.velocityMps, config)
        val safeDelta = clampSteeringAngleRad(state.steeringAngleRad, config)
        return VehicleState(
            x = safeX,
            y = safeY,
            headingRad = safeTheta,
            velocityMps = safeV,
            steeringAngleRad = safeDelta,
        )
    }
}
