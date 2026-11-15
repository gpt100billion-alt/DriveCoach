package com.drivecoach.app.simulation.collision

import com.drivecoach.app.simulation.geometry.ObstacleRole
import com.drivecoach.app.simulation.geometry.OrientedBox2D
import com.drivecoach.app.simulation.geometry.Vec2
import com.drivecoach.app.simulation.geometry.VehicleGeometry
import com.drivecoach.app.simulation.physics.KinematicBicycleModel
import com.drivecoach.app.simulation.vehicle.VehicleConfig
import com.drivecoach.app.simulation.vehicle.VehicleState
import com.drivecoach.app.simulation.vehicle.VirtualGearDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 단일 충돌 검사 결과 (PART 7 섹션 17)
 */
data class CollisionResult(
    val isColliding: Boolean,
    val hitObstacle: OrientedBox2D? = null,
    val contactPointWorld: Vec2? = null,
    val minClearanceMeters: Float = Float.MAX_VALUE,
    val messageKorean: String = "",
)

/**
 * Separating Axis Theorem (SAT) 기반 회전 직사각형(OBB) 충돌 및 여유 거리 계산기
 */
object CollisionDetector {

    /**
     * 두 회전 직사각형(OBB A, OBB B)이 겹치는지 SAT(분리축 정리)로 검사합니다.
     */
    fun intersectsOBB(boxA: OrientedBox2D, boxB: OrientedBox2D): Boolean {
        return separationDistanceMeters(boxA, boxB) <= 0f
    }

    /**
     * 두 OBB 사이의 최소 분리 거리(m)를 계산합니다.
     * - 0 이하이면 두 직사각형이 겹침(충돌)을 의미합니다.
     * - 양수이면 가장 가까운 분리축 기준 여유 거리(m)를 나타냅니다.
     */
    fun separationDistanceMeters(boxA: OrientedBox2D, boxB: OrientedBox2D): Float {
        val cornersA = boxA.corners()
        val cornersB = boxB.corners()
        val axes = listOf(
            boxA.forwardAxis,
            boxA.leftAxis,
            boxB.forwardAxis,
            boxB.leftAxis,
        )

        var maxAxisGap = Float.NEGATIVE_INFINITY
        for (axis in axes) {
            var minA = Float.POSITIVE_INFINITY
            var maxA = Float.NEGATIVE_INFINITY
            for (c in cornersA) {
                val p = c.dot(axis)
                if (p < minA) minA = p
                if (p > maxA) maxA = p
            }

            var minB = Float.POSITIVE_INFINITY
            var maxB = Float.NEGATIVE_INFINITY
            for (c in cornersB) {
                val p = c.dot(axis)
                if (p < minB) minB = p
                if (p > maxB) maxB = p
            }

            val gap = max(minB - maxA, minA - maxB)
            if (gap > maxAxisGap) {
                maxAxisGap = gap
            }
        }
        return maxAxisGap
    }

    /**
     * 내 차량 상태와 장애물 목록 간의 충돌 여부 및 최소 여유 거리를 검사합니다.
     */
    fun checkVehicleCollision(
        state: VehicleState,
        config: VehicleConfig,
        obstacles: List<OrientedBox2D>,
    ): CollisionResult {
        if (obstacles.isEmpty()) {
            return CollisionResult(isColliding = false, minClearanceMeters = 10f)
        }

        val footprint = VehicleGeometry.computeFootprint(state, config)
        val egoBox = footprint.bodyBox
        val egoCorners = egoBox.corners()

        var minClearance = Float.MAX_VALUE
        for (obs in obstacles) {
            val sep = separationDistanceMeters(egoBox, obs)
            if (sep < minClearance) {
                minClearance = sep
            }
            if (sep <= 0f) {
                val insideEgoCorner = egoCorners.firstOrNull { obs.containsPoint(it) }
                val insideObsCorner = obs.corners().firstOrNull { egoBox.containsPoint(it) }
                val contact = insideEgoCorner
                    ?: insideObsCorner
                    ?: egoCorners.minByOrNull { it.distanceTo(obs.center) }
                    ?: egoBox.center

                val msg = when (obs.role) {
                    ObstacleRole.FRONT_PARKED_CAR -> "앞 차량과 접촉했습니다. 조향 시작 시점을 조정하세요."
                    ObstacleRole.REAR_PARKED_CAR -> "뒤 차량과 접촉했습니다. 후진 거리를 확인하세요."
                    ObstacleRole.LEFT_PARKED_CAR -> "좌측 차량과 접촉했습니다. 진입 폭을 확인하세요."
                    ObstacleRole.RIGHT_PARKED_CAR -> "우측 차량과 접촉했습니다. 회전 반경을 확인하세요."
                    ObstacleRole.CURB -> "연석과 접촉했습니다. 반대 조향 시점을 앞당기세요."
                    ObstacleRole.ROAD_BOUNDARY -> "차선 경계를 벗어났습니다. 조향 시점을 확인하세요."
                    else -> "${obs.label}과 접촉했습니다."
                }

                return CollisionResult(
                    isColliding = true,
                    hitObstacle = obs,
                    contactPointWorld = contact,
                    minClearanceMeters = 0f,
                    messageKorean = msg,
                )
            }
        }

        return CollisionResult(
            isColliding = false,
            hitObstacle = null,
            contactPointWorld = null,
            minClearanceMeters = minClearance.coerceAtLeast(0f),
            messageKorean = if (minClearance < 0.25f) {
                "장애물 접근 주의 (여유 ${(minClearance * 100).toInt()}cm)"
            } else {
                ""
            },
        )
    }

    /**
     * 시간 간격(dtSec) 동안 이동할 때 중간 서브스텝마다 충돌 검사를 수행하여
     * 빠른 이동 중에도 장애물 충돌이 누락되지 않도록 보장합니다 (Swept Sub-step Collision Check).
     */
    fun stepWithContinuousCollisionCheck(
        startState: VehicleState,
        dtSec: Float,
        config: VehicleConfig,
        obstacles: List<OrientedBox2D>,
    ): Pair<VehicleState, CollisionResult> {
        val safeStart = KinematicBicycleModel.sanitizeState(startState, config)
        val initialCheck = checkVehicleCollision(safeStart, config, obstacles)
        if (initialCheck.isColliding || dtSec <= 0f || abs(safeStart.velocityMps) < 1e-5f) {
            return safeStart.copy(velocityMps = if (initialCheck.isColliding) 0f else safeStart.velocityMps) to initialCheck
        }

        var current = safeStart
        var remaining = dtSec.coerceAtMost(KinematicBicycleModel.MAX_FRAME_DT_SEC)
        var latestResult = initialCheck

        while (remaining > 1e-6f) {
            val stepDt = remaining.coerceAtMost(KinematicBicycleModel.FIXED_SUBSTEP_DT_SEC)
            val candidate = KinematicBicycleModel.integrateSingleSubstep(current, stepDt, config)
            val check = checkVehicleCollision(candidate, config, obstacles)
            if (check.isColliding) {
                val stoppedAtImpact = candidate.copy(velocityMps = 0f)
                return stoppedAtImpact to check
            }
            current = candidate
            latestResult = check
            remaining -= stepDt
        }

        return current to latestResult
    }
}

/**
 * 예상 이동 궤적 데이터 (PART 6 섹션 12)
 */
data class PredictedTrajectory(
    val rearAxlePoints: List<Vec2>,
    val frontLeftCornerPoints: List<Vec2>,
    val frontRightCornerPoints: List<Vec2>,
    val rearLeftCornerPoints: List<Vec2>,
    val rearRightCornerPoints: List<Vec2>,
    val willCollide: Boolean,
    val collisionIndex: Int? = null,
    val collisionPoint: Vec2? = null,
    val collisionObstacleLabel: String? = null,
)

object TrajectoryPredictor {

    /**
     * 현재 차량 위치, 조향각(δ), 선택된 전진/후진 방향을 기준으로 짧은 미래 구간(기본 4.8m)의 예상 이동 경로를 즉시 계산합니다.
     */
    fun predict(
        state: VehicleState,
        gearDirection: VirtualGearDirection,
        config: VehicleConfig,
        obstacles: List<OrientedBox2D>,
        lookaheadDistanceMeters: Float = 4.8f,
        stepDistanceMeters: Float = 0.15f,
    ): PredictedTrajectory {
        val rearPts = ArrayList<Vec2>()
        val flPts = ArrayList<Vec2>()
        val frPts = ArrayList<Vec2>()
        val rlPts = ArrayList<Vec2>()
        val rrPts = ArrayList<Vec2>()

        val simSpeed = 1.0f * gearDirection.speedSign
        val stepDt = stepDistanceMeters / abs(simSpeed)
        val stepsCount = (lookaheadDistanceMeters / stepDistanceMeters).toInt().coerceIn(5, 60)

        var simState = KinematicBicycleModel.sanitizeState(state, config).copy(velocityMps = simSpeed)
        var willCollide = false
        var collisionIdx: Int? = null
        var collisionPt: Vec2? = null
        var collisionLabel: String? = null

        for (i in 0..stepsCount) {
            val fp = VehicleGeometry.computeFootprint(simState, config)
            val corners = fp.bodyBox.corners()
            rearPts.add(fp.rearAxleCenter)
            flPts.add(corners[0])
            frPts.add(corners[1])
            rrPts.add(corners[2])
            rlPts.add(corners[3])

            if (!willCollide && i > 0) {
                val col = CollisionDetector.checkVehicleCollision(simState, config, obstacles)
                if (col.isColliding) {
                    willCollide = true
                    collisionIdx = i
                    collisionPt = col.contactPointWorld ?: fp.bodyCenter
                    collisionLabel = col.hitObstacle?.label
                }
            }

            simState = KinematicBicycleModel.integrateSingleSubstep(simState, stepDt, config)
        }

        return PredictedTrajectory(
            rearAxlePoints = rearPts,
            frontLeftCornerPoints = flPts,
            frontRightCornerPoints = frPts,
            rearLeftCornerPoints = rlPts,
            rearRightCornerPoints = rrPts,
            willCollide = willCollide,
            collisionIndex = collisionIdx,
            collisionPoint = collisionPt,
            collisionObstacleLabel = collisionLabel,
        )
    }
}

/**
 * 주차 및 주행 시나리오 성공 판정 결과 (PART 7 섹션 18 & PART 10~11)
 */
enum class EvaluationStatus {
    IN_PROGRESS,
    NEAR_SUCCESS_ALIGNING,
    SUCCESS,
    COLLIDED,
}

data class ScenarioEvaluationResult(
    val status: EvaluationStatus,
    val isInsideTargetZone: Boolean,
    val isHeadingAligned: Boolean,
    val hasSafetyMargin: Boolean,
    val isStopped: Boolean,
    val headingErrorDeg: Float,
    val minClearanceMeters: Float,
    val leftClearanceMeters: Float? = null,
    val rightClearanceMeters: Float? = null,
    val lateralOffsetFromCenterMeters: Float? = null,
    val headlineKorean: String,
    val detailKorean: String,
)

object ParkingAndTurnEvaluator {

    /**
     * 주차 성공 판정 (평행주차 / 후진주차 / 전면주차 공통) (PART 7 섹션 18)
     */
    fun evaluateParking(
        state: VehicleState,
        config: VehicleConfig,
        targetZone: OrientedBox2D,
        obstacles: List<OrientedBox2D>,
        collisionResult: CollisionResult,
        allowedHeadingErrorDeg: Float = 7.5f,
        allowOppositeHeading: Boolean = false,
    ): ScenarioEvaluationResult {
        if (collisionResult.isColliding) {
            return ScenarioEvaluationResult(
                status = EvaluationStatus.COLLIDED,
                isInsideTargetZone = false,
                isHeadingAligned = false,
                hasSafetyMargin = false,
                isStopped = true,
                headingErrorDeg = 0f,
                minClearanceMeters = 0f,
                headlineKorean = "접촉 발생",
                detailKorean = collisionResult.messageKorean,
            )
        }

        val footprint = VehicleGeometry.computeFootprint(state, config)
        val isInside = targetZone.containsBox(footprint.bodyBox, insetMarginMeters = 0.02f)

        val rawDiffRad = abs(KinematicBicycleModel.normalizeAngleRad(state.headingRad - targetZone.angleRad))
        val oppDiffRad = abs(KinematicBicycleModel.normalizeAngleRad(state.headingRad - (targetZone.angleRad + PI.toFloat())))
        val effectiveDiffRad = if (allowOppositeHeading) min(rawDiffRad, oppDiffRad) else rawDiffRad
        val headingErrorDeg = (effectiveDiffRad * 180.0 / PI).toFloat()
        val isHeadingAligned = headingErrorDeg <= allowedHeadingErrorDeg

        val hasSafetyMargin = collisionResult.minClearanceMeters >= config.safetyMarginMeters
        val isStopped = state.isStopped

        return when {
            isInside && isHeadingAligned && hasSafetyMargin && isStopped -> {
                ScenarioEvaluationResult(
                    status = EvaluationStatus.SUCCESS,
                    isInsideTargetZone = true,
                    isHeadingAligned = true,
                    hasSafetyMargin = true,
                    isStopped = true,
                    headingErrorDeg = headingErrorDeg,
                    minClearanceMeters = collisionResult.minClearanceMeters,
                    headlineKorean = "주차 완료",
                    detailKorean = "주차 구획 안에 바르게 정렬되었습니다.",
                )
            }
            isInside && isHeadingAligned && hasSafetyMargin && !isStopped -> {
                ScenarioEvaluationResult(
                    status = EvaluationStatus.NEAR_SUCCESS_ALIGNING,
                    isInsideTargetZone = true,
                    isHeadingAligned = true,
                    hasSafetyMargin = true,
                    isStopped = false,
                    headingErrorDeg = headingErrorDeg,
                    minClearanceMeters = collisionResult.minClearanceMeters,
                    headlineKorean = "정지 시 완료",
                    detailKorean = "위치와 각도가 맞았습니다. 차량을 정지하세요.",
                )
            }
            isInside && !isHeadingAligned -> {
                ScenarioEvaluationResult(
                    status = EvaluationStatus.NEAR_SUCCESS_ALIGNING,
                    isInsideTargetZone = true,
                    isHeadingAligned = false,
                    hasSafetyMargin = hasSafetyMargin,
                    isStopped = isStopped,
                    headingErrorDeg = headingErrorDeg,
                    minClearanceMeters = collisionResult.minClearanceMeters,
                    headlineKorean = "차체 각도 보정 필요",
                    detailKorean = "구획 기준선 대비 약 ${headingErrorDeg.toInt()}° 기울어졌습니다.",
                )
            }
            isInside && !hasSafetyMargin -> {
                ScenarioEvaluationResult(
                    status = EvaluationStatus.NEAR_SUCCESS_ALIGNING,
                    isInsideTargetZone = true,
                    isHeadingAligned = isHeadingAligned,
                    hasSafetyMargin = false,
                    isStopped = isStopped,
                    headingErrorDeg = headingErrorDeg,
                    minClearanceMeters = collisionResult.minClearanceMeters,
                    headlineKorean = "여유 거리 부족",
                    detailKorean = "주변 차량 또는 연석과 가깝습니다 (여유 ${(collisionResult.minClearanceMeters * 100).toInt()}cm).",
                )
            }
            else -> {
                val centerDist = footprint.bodyCenter.distanceTo(targetZone.center)
                val hint = if (collisionResult.minClearanceMeters < 0.30f) {
                    "주변 장애물 접근 주의 (여유 ${(collisionResult.minClearanceMeters * 100).toInt()}cm)"
                } else if (centerDist < 2.2f) {
                    "주차 구획 진입 중입니다. 차체 각도와 모서리를 확인하세요."
                } else {
                    "예상 경로를 확인하며 핸들을 조작하세요."
                }
                ScenarioEvaluationResult(
                    status = EvaluationStatus.IN_PROGRESS,
                    isInsideTargetZone = false,
                    isHeadingAligned = isHeadingAligned,
                    hasSafetyMargin = hasSafetyMargin,
                    isStopped = isStopped,
                    headingErrorDeg = headingErrorDeg,
                    minClearanceMeters = collisionResult.minClearanceMeters,
                    headlineKorean = "연습 진행 중",
                    detailKorean = hint,
                )
            }
        }
    }

    /**
     * 주행 시나리오(회전 / 차폭·차선 감각 / 기초 조작) 성공 및 주행 상태 판정
     */
    fun evaluateDrivingScenario(
        state: VehicleState,
        config: VehicleConfig,
        targetZone: OrientedBox2D,
        obstacles: List<OrientedBox2D>,
        collisionResult: CollisionResult,
        corridorCenterX: Float? = null,
        corridorHalfWidthMeters: Float? = null,
    ): ScenarioEvaluationResult {
        val footprint = VehicleGeometry.computeFootprint(state, config)

        var leftClearance: Float? = null
        var rightClearance: Float? = null
        var lateralOffset: Float? = null

        if (corridorCenterX != null && corridorHalfWidthMeters != null) {
            val corners = footprint.bodyBox.corners()
            val minCarX = corners.minOf { it.x }
            val maxCarX = corners.maxOf { it.x }
            val leftWallX = corridorCenterX - corridorHalfWidthMeters
            val rightWallX = corridorCenterX + corridorHalfWidthMeters
            leftClearance = (minCarX - leftWallX).coerceAtLeast(0f)
            rightClearance = (rightWallX - maxCarX).coerceAtLeast(0f)
            lateralOffset = footprint.bodyCenter.x - corridorCenterX
        }

        if (collisionResult.isColliding) {
            return ScenarioEvaluationResult(
                status = EvaluationStatus.COLLIDED,
                isInsideTargetZone = false,
                isHeadingAligned = false,
                hasSafetyMargin = false,
                isStopped = true,
                headingErrorDeg = 0f,
                minClearanceMeters = 0f,
                leftClearanceMeters = leftClearance,
                rightClearanceMeters = rightClearance,
                lateralOffsetFromCenterMeters = lateralOffset,
                headlineKorean = "경계 접촉",
                detailKorean = collisionResult.messageKorean,
            )
        }

        val isInsideTarget = targetZone.containsBox(footprint.bodyBox, insetMarginMeters = 0.0f)
        val rawDiffRad = abs(KinematicBicycleModel.normalizeAngleRad(state.headingRad - targetZone.angleRad))
        val headingErrorDeg = (rawDiffRad * 180.0 / PI).toFloat()
        val isHeadingAligned = headingErrorDeg <= 10.0f

        return when {
            isInsideTarget && isHeadingAligned && state.isStopped -> {
                ScenarioEvaluationResult(
                    status = EvaluationStatus.SUCCESS,
                    isInsideTargetZone = true,
                    isHeadingAligned = true,
                    hasSafetyMargin = true,
                    isStopped = true,
                    headingErrorDeg = headingErrorDeg,
                    minClearanceMeters = collisionResult.minClearanceMeters,
                    leftClearanceMeters = leftClearance,
                    rightClearanceMeters = rightClearance,
                    lateralOffsetFromCenterMeters = lateralOffset,
                    headlineKorean = "연습 완료",
                    detailKorean = "목표 구간 안에 안정적으로 정렬되었습니다.",
                )
            }
            isInsideTarget && isHeadingAligned && !state.isStopped -> {
                ScenarioEvaluationResult(
                    status = EvaluationStatus.NEAR_SUCCESS_ALIGNING,
                    isInsideTargetZone = true,
                    isHeadingAligned = true,
                    hasSafetyMargin = true,
                    isStopped = false,
                    headingErrorDeg = headingErrorDeg,
                    minClearanceMeters = collisionResult.minClearanceMeters,
                    leftClearanceMeters = leftClearance,
                    rightClearanceMeters = rightClearance,
                    lateralOffsetFromCenterMeters = lateralOffset,
                    headlineKorean = "목표 구간 진입",
                    detailKorean = "핸들을 중앙으로 맞추고 정지하면 완료됩니다.",
                )
            }
            else -> {
                val detail = if (leftClearance != null && rightClearance != null) {
                    val offsetCm = ((lateralOffset ?: 0f) * 100).toInt()
                    when {
                        offsetCm > 8 -> "오른쪽으로 치우쳤습니다 (좌 ${(leftClearance * 100).toInt()}cm · 우 ${(rightClearance * 100).toInt()}cm)"
                        offsetCm < -8 -> "왼쪽으로 치우쳤습니다 (좌 ${(leftClearance * 100).toInt()}cm · 우 ${(rightClearance * 100).toInt()}cm)"
                        else -> "좌측 여유 ${(leftClearance * 100).toInt()}cm · 우측 여유 ${(rightClearance * 100).toInt()}cm"
                    }
                } else if (collisionResult.minClearanceMeters < 0.35f) {
                    "연석·경계선 접근 주의 (여유 ${(collisionResult.minClearanceMeters * 100).toInt()}cm)"
                } else {
                    "예상 경로를 확인하며 조향 시작과 복귀 시점을 맞추세요."
                }
                ScenarioEvaluationResult(
                    status = EvaluationStatus.IN_PROGRESS,
                    isInsideTargetZone = isInsideTarget,
                    isHeadingAligned = isHeadingAligned,
                    hasSafetyMargin = collisionResult.minClearanceMeters >= config.safetyMarginMeters,
                    isStopped = state.isStopped,
                    headingErrorDeg = headingErrorDeg,
                    minClearanceMeters = collisionResult.minClearanceMeters,
                    leftClearanceMeters = leftClearance,
                    rightClearanceMeters = rightClearance,
                    lateralOffsetFromCenterMeters = lateralOffset,
                    headlineKorean = "연습 진행 중",
                    detailKorean = detail,
                )
            }
        }
    }
}
