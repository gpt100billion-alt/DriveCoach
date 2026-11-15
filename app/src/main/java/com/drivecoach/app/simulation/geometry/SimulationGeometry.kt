package com.drivecoach.app.simulation.geometry

import com.drivecoach.app.simulation.vehicle.VehicleConfig
import com.drivecoach.app.simulation.vehicle.VehicleState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * 2차원 월드 좌표 벡터 (단위: m)
 */
data class Vec2(
    val x: Float,
    val y: Float,
) {
    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float): Vec2 = Vec2(x * scalar, y * scalar)

    fun dot(other: Vec2): Float = x * other.x + y * other.y
    fun length(): Float = hypot(x.toDouble(), y.toDouble()).toFloat()

    fun distanceTo(other: Vec2): Float = (this - other).length()

    fun normalized(): Vec2 {
        val len = length()
        return if (len < 1e-6f) Vec2(1f, 0f) else Vec2(x / len, y / len)
    }

    /**
     * 원점 기준 반시계 방향(CCW)으로 angleRad만큼 회전
     */
    fun rotated(angleRad: Float): Vec2 {
        val c = cos(angleRad.toDouble()).toFloat()
        val s = sin(angleRad.toDouble()).toFloat()
        return Vec2(x * c - y * s, x * s + y * c)
    }
}

enum class ObstacleRole(val koreanLabel: String) {
    EGO_VEHICLE("내 차량"),
    FRONT_PARKED_CAR("앞 차량"),
    REAR_PARKED_CAR("뒤 차량"),
    LEFT_PARKED_CAR("왼쪽 주차 차량"),
    RIGHT_PARKED_CAR("오른쪽 주차 차량"),
    CURB("연석"),
    ROAD_BOUNDARY("도로 경계"),
    CONE_OBSTACLE("장애물"),
}

/**
 * 회전된 직사각형(Oriented Bounding Box, OBB) 기하학 모델 (PART 6 & PART 7 섹션 17)
 */
data class OrientedBox2D(
    val id: String,
    val label: String,
    val role: ObstacleRole,
    val center: Vec2,
    val halfLength: Float,
    val halfWidth: Float,
    val angleRad: Float,
) {
    val forwardAxis: Vec2
        get() = Vec2(cos(angleRad.toDouble()).toFloat(), sin(angleRad.toDouble()).toFloat())

    val leftAxis: Vec2
        get() = Vec2(-sin(angleRad.toDouble()).toFloat(), cos(angleRad.toDouble()).toFloat())

    /**
     * 직사각형의 4개 모서리 꼭짓점을 반시계 순서(전방좌측, 전방우측, 후방우측, 후방좌측)로 반환합니다.
     */
    fun corners(): List<Vec2> {
        val f = forwardAxis * halfLength
        val l = leftAxis * halfWidth
        return listOf(
            center + f + l, // Front-Left
            center + f - l, // Front-Right
            center - f - l, // Rear-Right
            center - f + l, // Rear-Left
        )
    }

    /**
     * 점(point)이 회전된 직사각형 내부에 존재하는지 판정합니다 (marginMeters > 0 이면 내부 여유만큼 축소된 영역 기준).
     */
    fun containsPoint(point: Vec2, insetMarginMeters: Float = 0f): Boolean {
        val rel = point - center
        val projForward = abs(rel.dot(forwardAxis))
        val projLeft = abs(rel.dot(leftAxis))
        val allowedHalfLen = (halfLength - insetMarginMeters).coerceAtLeast(0f)
        val allowedHalfWid = (halfWidth - insetMarginMeters).coerceAtLeast(0f)
        return projForward <= allowedHalfLen + 1e-4f && projLeft <= allowedHalfWid + 1e-4f
    }

    /**
     * 다른 OBB의 4개 모서리가 모두 이 직사각형 영역 안에 완전히 들어와 있는지 판정합니다.
     */
    fun containsBox(other: OrientedBox2D, insetMarginMeters: Float = 0f): Boolean {
        return other.corners().all { corner -> containsPoint(corner, insetMarginMeters) }
    }
}

/**
 * Top-down 렌더링 및 충돌 검사를 위한 차량 기하 구조 (PART 6 섹션 11)
 *
 * 모든 위치는 후륜 차축 중앙(Rear Axle Center) (state.x, state.y)로부터 일관되게 계산됩니다.
 */
data class VehicleFootprint(
    val rearAxleCenter: Vec2,
    val frontAxleCenter: Vec2,
    val bodyCenter: Vec2,
    val bodyBox: OrientedBox2D,
    val frontLeftWheel: OrientedBox2D,
    val frontRightWheel: OrientedBox2D,
    val rearLeftWheel: OrientedBox2D,
    val rearRightWheel: OrientedBox2D,
    val centerlineRear: Vec2,
    val centerlineFront: Vec2,
    val windshieldCorners: List<Vec2>,
    val rearGlassCorners: List<Vec2>,
    val hoodLineLeft: Vec2,
    val hoodLineRight: Vec2,
)

object VehicleGeometry {

    /**
     * 차량 상태(VehicleState)와 제원(VehicleConfig)으로부터 차체 OBB, 4개 바퀴 OBB, 유리창 및 중심선 좌표를 계산합니다.
     */
    fun computeFootprint(
        state: VehicleState,
        config: VehicleConfig,
    ): VehicleFootprint {
        val rearAxle = Vec2(state.x, state.y)
        val forward = Vec2(
            cos(state.headingRad.toDouble()).toFloat(),
            sin(state.headingRad.toDouble()).toFloat(),
        )
        val left = Vec2(
            -sin(state.headingRad.toDouble()).toFloat(),
            cos(state.headingRad.toDouble()).toFloat(),
        )

        val wheelbase = config.sanitizedWheelbaseMeters
        val totalLen = config.rearAxleToFrontBumperMeters + config.rearAxleToRearBumperMeters
        val width = config.sanitizedWidthMeters
        val bodyCenter = rearAxle + forward * config.rearAxleToBodyCenterOffsetMeters
        val frontAxle = rearAxle + forward * wheelbase

        val bodyBox = OrientedBox2D(
            id = "ego_vehicle",
            label = "내 차량",
            role = ObstacleRole.EGO_VEHICLE,
            center = bodyCenter,
            halfLength = totalLen * 0.5f,
            halfWidth = width * 0.5f,
            angleRad = state.headingRad,
        )

        val trackHalfWidth = (width * 0.5f) - (config.wheelWidthMeters * 0.58f)
        val wheelHalfLen = config.wheelLengthMeters * 0.5f
        val wheelHalfWid = config.wheelWidthMeters * 0.5f
        val frontWheelAngle = state.headingRad + state.steeringAngleRad

        val flWheel = OrientedBox2D(
            id = "wheel_fl",
            label = "전방 좌측 바퀴",
            role = ObstacleRole.EGO_VEHICLE,
            center = frontAxle + left * trackHalfWidth,
            halfLength = wheelHalfLen,
            halfWidth = wheelHalfWid,
            angleRad = frontWheelAngle,
        )
        val frWheel = OrientedBox2D(
            id = "wheel_fr",
            label = "전방 우측 바퀴",
            role = ObstacleRole.EGO_VEHICLE,
            center = frontAxle - left * trackHalfWidth,
            halfLength = wheelHalfLen,
            halfWidth = wheelHalfWid,
            angleRad = frontWheelAngle,
        )
        val rlWheel = OrientedBox2D(
            id = "wheel_rl",
            label = "후방 좌측 바퀴",
            role = ObstacleRole.EGO_VEHICLE,
            center = rearAxle + left * trackHalfWidth,
            halfLength = wheelHalfLen,
            halfWidth = wheelHalfWid,
            angleRad = state.headingRad,
        )
        val rrWheel = OrientedBox2D(
            id = "wheel_rr",
            label = "후방 우측 바퀴",
            role = ObstacleRole.EGO_VEHICLE,
            center = rearAxle - left * trackHalfWidth,
            halfLength = wheelHalfLen,
            halfWidth = wheelHalfWid,
            angleRad = state.headingRad,
        )

        val centerlineRear = rearAxle - forward * config.rearAxleToRearBumperMeters
        val centerlineFront = rearAxle + forward * config.rearAxleToFrontBumperMeters

        // 전면 유리(Windshield) 사다리꼴 (운전석 방향 식별용)
        val wsFrontOffset = wheelbase * 0.86f
        val wsRearOffset = wheelbase * 0.50f
        val wsFrontHalfW = width * 0.39f
        val wsRearHalfW = width * 0.35f
        val windshield = listOf(
            rearAxle + forward * wsFrontOffset + left * wsFrontHalfW,
            rearAxle + forward * wsFrontOffset - left * wsFrontHalfW,
            rearAxle + forward * wsRearOffset - left * wsRearHalfW,
            rearAxle + forward * wsRearOffset + left * wsRearHalfW,
        )

        // 후면 유리(Rear Glass) 사다리꼴
        val rgFrontOffset = -config.rearAxleToRearBumperMeters * 0.05f
        val rgRearOffset = -config.rearAxleToRearBumperMeters * 0.55f
        val rgHalfW = width * 0.34f
        val rearGlass = listOf(
            rearAxle + forward * rgFrontOffset + left * rgHalfW,
            rearAxle + forward * rgFrontOffset - left * rgHalfW,
            rearAxle + forward * rgRearOffset - left * (rgHalfW * 0.92f),
            rearAxle + forward * rgRearOffset + left * (rgHalfW * 0.92f),
        )

        val hoodLeft = rearAxle + forward * (wheelbase + config.frontOverhangMeters * 0.72f) + left * (width * 0.38f)
        val hoodRight = rearAxle + forward * (wheelbase + config.frontOverhangMeters * 0.72f) - left * (width * 0.38f)

        return VehicleFootprint(
            rearAxleCenter = rearAxle,
            frontAxleCenter = frontAxle,
            bodyCenter = bodyCenter,
            bodyBox = bodyBox,
            frontLeftWheel = flWheel,
            frontRightWheel = frWheel,
            rearLeftWheel = rlWheel,
            rearRightWheel = rrWheel,
            centerlineRear = centerlineRear,
            centerlineFront = centerlineFront,
            windshieldCorners = windshield,
            rearGlassCorners = rearGlass,
            hoodLineLeft = hoodLeft,
            hoodLineRight = hoodRight,
        )
    }
}

/**
 * 월드 좌표계(m) ↔ 화면 픽셀 좌표계(px) 변환기 (PART 6 섹션 13)
 *
 * - 월드 좌표계: +X 우측, +Y 상단 (단위: m)
 * - 화면 좌표계: +X 우측, +Y 하단 (단위: px)
 * - 화면 크기가 변경되거나 분할 화면이 되더라도 종횡비(Aspect Ratio)를 1:1 등방성으로 유지하여
 *   차량 크기나 장애물 상대 위치가 절대 왜곡되지 않도록 보장합니다.
 */
data class WorldBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
) {
    val widthMeters: Float
        get() = (maxX - minX).coerceAtLeast(1f)

    val heightMeters: Float
        get() = (maxY - minY).coerceAtLeast(1f)

    val centerX: Float
        get() = (minX + maxX) * 0.5f

    val centerY: Float
        get() = (minY + maxY) * 0.5f
}

class WorldViewportTransform(
    val worldBounds: WorldBounds,
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    val paddingPx: Float = 24f,
    val zoomFactor: Float = 1.0f,
) {
    private val usableWidthPx: Float = (viewportWidthPx - paddingPx * 2f).coerceAtLeast(1f)
    private val usableHeightPx: Float = (viewportHeightPx - paddingPx * 2f).coerceAtLeast(1f)

    val pixelsPerMeter: Float = (
        min(
            usableWidthPx / worldBounds.widthMeters,
            usableHeightPx / worldBounds.heightMeters,
        ) * zoomFactor.coerceIn(0.5f, 3.0f)
        ).coerceAtLeast(1f)

    private val screenCenterX: Float = viewportWidthPx * 0.5f
    private val screenCenterY: Float = viewportHeightPx * 0.5f

    /**
     * 월드 좌표 (m) -> 화면 좌표 (px)
     */
    fun worldToScreen(worldPoint: Vec2): Vec2 {
        val sx = screenCenterX + (worldPoint.x - worldBounds.centerX) * pixelsPerMeter
        val sy = screenCenterY - (worldPoint.y - worldBounds.centerY) * pixelsPerMeter
        return Vec2(sx, sy)
    }

    /**
     * 화면 좌표 (px) -> 월드 좌표 (m)
     */
    fun screenToWorld(screenPoint: Vec2): Vec2 {
        val wx = worldBounds.centerX + (screenPoint.x - screenCenterX) / pixelsPerMeter
        val wy = worldBounds.centerY - (screenPoint.y - screenCenterY) / pixelsPerMeter
        return Vec2(wx, wy)
    }

    /**
     * 미터 길이를 픽셀 길이로 변환
     */
    fun metersToPixels(meters: Float): Float = meters * pixelsPerMeter

    /**
     * 월드 각도(rad, 반시계 +)를 화면 Canvas 회전 각도(deg, 시계 +)로 변환
     */
    fun worldAngleToScreenDegrees(angleRad: Float): Float =
        (-angleRad * 180.0 / PI).toFloat()
}
