package com.drivecoach.app

import com.drivecoach.app.simulation.collision.CollisionDetector
import com.drivecoach.app.simulation.collision.CollisionResult
import com.drivecoach.app.simulation.collision.EvaluationStatus
import com.drivecoach.app.simulation.collision.ParkingAndTurnEvaluator
import com.drivecoach.app.simulation.collision.TrajectoryPredictor
import com.drivecoach.app.simulation.geometry.ObstacleRole
import com.drivecoach.app.simulation.geometry.OrientedBox2D
import com.drivecoach.app.simulation.geometry.Vec2
import com.drivecoach.app.simulation.geometry.VehicleGeometry
import com.drivecoach.app.simulation.geometry.WorldBounds
import com.drivecoach.app.simulation.geometry.WorldViewportTransform
import com.drivecoach.app.simulation.physics.KinematicBicycleModel
import com.drivecoach.app.simulation.scenarios.ReferencePathResult
import com.drivecoach.app.domain.model.GearPosition
import com.drivecoach.app.simulation.scenarios.ScenarioSpaceOptions
import com.drivecoach.app.simulation.scenarios.ScenarioType
import com.drivecoach.app.simulation.scenarios.SimulatorScenarios
import com.drivecoach.app.simulation.steering.DiagnosticSteeringInputProvider
import com.drivecoach.app.simulation.steering.SdkAngleInterpretationMode
import com.drivecoach.app.simulation.steering.SteeringCalibrationConfig
import com.drivecoach.app.simulation.steering.SteeringConnectionState
import com.drivecoach.app.simulation.steering.SteeringDirectionLabel
import com.drivecoach.app.simulation.steering.SteeringMappingPipeline
import com.drivecoach.app.simulation.vehicle.VehicleConfig
import com.drivecoach.app.simulation.vehicle.VehicleState
import com.drivecoach.app.simulation.vehicle.VirtualGearDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.tan

/**
 * PART 15 (섹션 29) 단위 테스트 검증 스위트
 * 1. Vehicle Kinematics Tests (2D 운동학 자전거 모델)
 * 2. Vehicle Geometry & Transform Tests (차량 형상 및 월드-화면 좌표 변환)
 * 3. Collision Detection Tests (SAT 기반 회전 직사각형 충돌 판정)
 * 4. Parking & Turn Success Evaluation Tests (주차 및 주행 성공 판정)
 * 5. Reference Path Generation Tests (운동학 적분 기반 기준 경로 생성)
 */
class VehicleSimulatorEngineTest {

    private val defaultConfig = VehicleConfig(
        lengthMeters = 4.5f,
        widthMeters = 1.8f,
        wheelbaseMeters = 2.7f,
        frontOverhangMeters = 0.9f,
        rearOverhangMeters = 0.9f,
        maxSteeringAngleDeg = 32f,
    )

    // ========================================================================
    // 1. Vehicle Kinematics Tests (PART 15 섹션 29.1)
    // ========================================================================

    @Test
    fun `kinematics - steering angle 0 deg moves strictly straight`() {
        val initial = VehicleState(
            x = 0f,
            y = 0f,
            headingRad = (PI / 2.0).toFloat(), // 북쪽(+Y)
            velocityMps = 1.2f,
            steeringAngleRad = 0f,
        )
        val next = KinematicBicycleModel.step(initial, dtSec = 1.0f, config = defaultConfig)
        assertEquals(0f, next.x, 1e-4f)
        assertEquals(1.2f, next.y, 1e-3f)
        assertEquals((PI / 2.0).toFloat(), next.headingRad, 1e-4f)
    }

    @Test
    fun `kinematics - positive steering angle turns vehicle counterclockwise when moving forward`() {
        val steerRad = Math.toRadians(20.0).toFloat()
        val initial = VehicleState(
            x = 0f,
            y = 0f,
            headingRad = (PI / 2.0).toFloat(),
            velocityMps = 1.0f,
            steeringAngleRad = steerRad,
        )
        val next = KinematicBicycleModel.step(initial, dtSec = 1.0f, config = defaultConfig)
        assertTrue("전진 좌회전 시 headingRad가 증가해야 합니다", next.headingRad > initial.headingRad)
        assertTrue("북쪽 기준 좌회전 시 x 좌표는 음수로 이동해야 합니다", next.x < -0.02f)
    }

    @Test
    fun `kinematics - negative steering angle turns vehicle in opposite direction`() {
        val steerRad = Math.toRadians(20.0).toFloat()
        val leftInit = VehicleState(0f, 0f, (PI / 2.0).toFloat(), 1.0f, +steerRad)
        val rightInit = VehicleState(0f, 0f, (PI / 2.0).toFloat(), 1.0f, -steerRad)

        val leftNext = KinematicBicycleModel.step(leftInit, dtSec = 1.0f, config = defaultConfig)
        val rightNext = KinematicBicycleModel.step(rightInit, dtSec = 1.0f, config = defaultConfig)

        assertEquals(-leftNext.x, rightNext.x, 1e-4f)
        assertEquals(leftNext.y, rightNext.y, 1e-4f)
    }

    @Test
    fun `kinematics - forward and reverse movements advance in opposite directions`() {
        val forwardInit = VehicleState(0f, 0f, 0f, velocityMps = 1.0f, steeringAngleRad = 0f)
        val reverseInit = VehicleState(0f, 0f, 0f, velocityMps = -0.8f, steeringAngleRad = 0f)

        val forwardNext = KinematicBicycleModel.step(forwardInit, dtSec = 0.2f, config = defaultConfig)
        val reverseNext = KinematicBicycleModel.step(reverseInit, dtSec = 0.2f, config = defaultConfig)

        assertTrue("전진 시 +X 이동", forwardNext.x > 0.19f)
        assertTrue("후진 시 -X 이동", reverseNext.x < -0.15f)
    }

    @Test
    fun `kinematics - zero velocity keeps position and heading unchanged`() {
        val stationary = VehicleState(
            x = 2.5f,
            y = -1.3f,
            headingRad = 0.8f,
            velocityMps = 0f,
            steeringAngleRad = 0.4f,
        )
        val next = KinematicBicycleModel.step(stationary, dtSec = 0.2f, config = defaultConfig)
        assertEquals(stationary.x, next.x, 1e-6f)
        assertEquals(stationary.y, next.y, 1e-6f)
        assertEquals(stationary.headingRad, next.headingRad, 1e-6f)
    }

    @Test
    fun `kinematics - steering angle exceeding max limit is clamped`() {
        val excessiveSteerRad = Math.toRadians(75.0).toFloat()
        val init = VehicleState(0f, 0f, 0f, velocityMps = 1.0f, steeringAngleRad = excessiveSteerRad)
        val next = KinematicBicycleModel.step(init, dtSec = 0.2f, config = defaultConfig)

        assertEquals(defaultConfig.maxSteeringAngleRad, next.steeringAngleRad, 1e-5f)
        val expectedHeading = (1.0f / defaultConfig.wheelbaseMeters) * tan(defaultConfig.maxSteeringAngleRad) * 0.2f
        assertEquals(expectedHeading, next.headingRad, 1e-3f)
    }

    @Test
    fun `kinematics - time step subdivision maintains consistent travel distance and heading`() {
        val steerRad = Math.toRadians(18.0).toFloat()
        val initial = VehicleState(0f, 0f, 0.3f, velocityMps = 1.0f, steeringAngleRad = steerRad)

        // 한 번에 0.2초 적분 vs 0.02초씩 10회 적분
        val singleCall = KinematicBicycleModel.step(initial, dtSec = 0.2f, config = defaultConfig)
        var multiCall = initial
        repeat(10) {
            multiCall = KinematicBicycleModel.step(multiCall, dtSec = 0.02f, config = defaultConfig)
        }

        assertEquals(singleCall.x, multiCall.x, 1e-4f)
        assertEquals(singleCall.y, multiCall.y, 1e-4f)
        assertEquals(singleCall.headingRad, multiCall.headingRad, 1e-4f)
    }

    @Test
    fun `kinematics - NaN or infinite inputs are safely sanitized without crash`() {
        val corrupted = VehicleState(
            x = Float.NaN,
            y = Float.POSITIVE_INFINITY,
            headingRad = Float.NaN,
            velocityMps = Float.NaN,
            steeringAngleRad = Float.NEGATIVE_INFINITY,
        )
        val next = KinematicBicycleModel.step(corrupted, dtSec = Float.NaN, config = defaultConfig)
        assertTrue(next.x.isFinite())
        assertTrue(next.y.isFinite())
        assertTrue(next.headingRad.isFinite())
        assertTrue(next.velocityMps.isFinite())
        assertTrue(next.steeringAngleRad.isFinite())
    }

    // ========================================================================
    // 2. Vehicle Geometry & Transform Tests (PART 15 섹션 29.2)
    // ========================================================================

    @Test
    fun `geometry - vehicle length width and rear axle offset are accurately applied`() {
        val state = VehicleState(x = 0f, y = 0f, headingRad = 0f) // +X 방향
        val footprint = VehicleGeometry.computeFootprint(state, defaultConfig)

        assertEquals(defaultConfig.lengthMeters, footprint.bodyBox.halfLength * 2f, 1e-4f)
        assertEquals(defaultConfig.widthMeters, footprint.bodyBox.halfWidth * 2f, 1e-4f)
        // 후륜 차축(0,0)에서 전방으로 wheelbase/2 = 1.35m 떨어진 곳이 차체 중심이어야 함
        assertEquals(1.35f, footprint.bodyBox.center.x, 1e-4f)
        assertEquals(0f, footprint.bodyBox.center.y, 1e-4f)
        assertEquals(2.7f, footprint.frontAxleCenter.x, 1e-4f)
    }

    @Test
    fun `geometry - rotated vehicle corners match expected 2D OBB coordinates`() {
        val state = VehicleState(x = 1.0f, y = 2.0f, headingRad = (PI / 2.0).toFloat()) // 북쪽(+Y)
        val footprint = VehicleGeometry.computeFootprint(state, defaultConfig)
        val corners = footprint.bodyBox.corners()
        assertEquals(4, corners.size)

        // 북쪽(+Y)을 향할 때 후방 끝은 y = 2.0 - 0.9 = 1.1m, 전방 끝은 y = 2.0 + 2.7 + 0.9 = 5.6m
        val minY = corners.minOf { it.y }
        val maxY = corners.maxOf { it.y }
        val minX = corners.minOf { it.x }
        val maxX = corners.maxOf { it.x }

        assertEquals(1.1f, minY, 1e-3f)
        assertEquals(5.6f, maxY, 1e-3f)
        assertEquals(1.0f - 0.9f, minX, 1e-3f)
        assertEquals(1.0f + 0.9f, maxX, 1e-3f)
    }

    @Test
    fun `geometry - changing VehicleConfig dynamically updates bounding box dimensions`() {
        val compactConfig = defaultConfig.copy(
            lengthMeters = 3.9f,
            widthMeters = 1.65f,
            wheelbaseMeters = 2.4f,
            frontOverhangMeters = 0.75f,
            rearOverhangMeters = 0.75f,
        )
        val state = VehicleState(0f, 0f, 0f)
        val fp = VehicleGeometry.computeFootprint(state, compactConfig)

        assertEquals(3.9f, fp.bodyBox.halfLength * 2f, 1e-4f)
        assertEquals(1.65f, fp.bodyBox.halfWidth * 2f, 1e-4f)
    }

    @Test
    fun `geometry - world to screen and screen to world transform round-trips accurately`() {
        val bounds = WorldBounds(-5f, 8f, -10f, 10f)
        val transform = WorldViewportTransform(
            worldBounds = bounds,
            viewportWidthPx = 1280f,
            viewportHeightPx = 720f,
            paddingPx = 32f,
        )
        val originalWorld = Vec2(2.35f, -3.4f)
        val screenPt = transform.worldToScreen(originalWorld)
        val roundTripWorld = transform.screenToWorld(screenPt)

        assertEquals(originalWorld.x, roundTripWorld.x, 1e-3f)
        assertEquals(originalWorld.y, roundTripWorld.y, 1e-3f)
    }

    // ========================================================================
    // 3. Collision Detection Tests (PART 15 섹션 29.3)
    // ========================================================================

    @Test
    fun `collision - detects front parked vehicle collision`() {
        val scenario = SimulatorScenarios.buildScenario(ScenarioType.PARALLEL_PARKING, defaultConfig)
        val frontCar = scenario.obstacles.first { it.role == ObstacleRole.FRONT_PARKED_CAR }

        val collidingState = VehicleState(
            x = frontCar.center.x,
            y = frontCar.center.y - 1.0f,
            headingRad = (PI / 2.0).toFloat(),
        )
        val result = CollisionDetector.checkVehicleCollision(collidingState, defaultConfig, scenario.obstacles)
        assertTrue("앞차와의 충돌이 감지되어야 합니다", result.isColliding)
        assertEquals(ObstacleRole.FRONT_PARKED_CAR, result.hitObstacle?.role)
    }

    @Test
    fun `collision - detects rear parked vehicle collision`() {
        val scenario = SimulatorScenarios.buildScenario(ScenarioType.PARALLEL_PARKING, defaultConfig)
        val rearCar = scenario.obstacles.first { it.role == ObstacleRole.REAR_PARKED_CAR }

        val collidingState = VehicleState(
            x = rearCar.center.x,
            y = rearCar.center.y + 0.4f,
            headingRad = (PI / 2.0).toFloat(),
        )
        val result = CollisionDetector.checkVehicleCollision(collidingState, defaultConfig, scenario.obstacles)
        assertTrue("뒤차와의 충돌이 감지되어야 합니다", result.isColliding)
        assertEquals(ObstacleRole.REAR_PARKED_CAR, result.hitObstacle?.role)
    }

    @Test
    fun `collision - detects curb collision`() {
        val scenario = SimulatorScenarios.buildScenario(ScenarioType.PARALLEL_PARKING, defaultConfig)
        val curb = scenario.obstacles.first { it.role == ObstacleRole.CURB }

        val collidingState = VehicleState(
            x = curb.center.x - 0.5f,
            y = 0f,
            headingRad = 0f,
        )
        val result = CollisionDetector.checkVehicleCollision(collidingState, defaultConfig, scenario.obstacles)
        assertTrue("연석과의 충돌이 감지되어야 합니다", result.isColliding)
        assertEquals(ObstacleRole.CURB, result.hitObstacle?.role)
    }

    @Test
    fun `collision - non-overlapping vehicle state reports no collision`() {
        val scenario = SimulatorScenarios.buildScenario(ScenarioType.PARALLEL_PARKING, defaultConfig)
        val result = CollisionDetector.checkVehicleCollision(
            state = scenario.initialState,
            config = defaultConfig,
            obstacles = scenario.obstacles,
        )
        assertFalse("초기 준비 위치에서는 충돌이 없어야 합니다", result.isColliding)
        assertTrue("최소 이격 거리는 양수여야 합니다", result.minClearanceMeters > 0.3f)
    }

    @Test
    fun `collision - detects rotated corner overlap accurately via SAT`() {
        val boxA = OrientedBox2D(
            id = "box_a",
            label = "박스 A",
            role = ObstacleRole.EGO_VEHICLE,
            center = Vec2(0f, 0f),
            halfLength = 2.25f,
            halfWidth = 0.9f,
            angleRad = 0f,
        )
        val boxB = OrientedBox2D(
            id = "box_b",
            label = "박스 B",
            role = ObstacleRole.FRONT_PARKED_CAR,
            center = Vec2(2.8f, 1.1f),
            halfLength = 2.0f,
            halfWidth = 0.9f,
            angleRad = Math.toRadians(35.0).toFloat(),
        )
        assertTrue("회전된 모서리 접촉이 SAT로 감지되어야 합니다", CollisionDetector.intersectsOBB(boxA, boxB))
    }

    @Test
    fun `collision - continuous substepping prevents fast movement from tunneling through thin obstacle`() {
        val thinWall = OrientedBox2D(
            id = "thin_wall",
            label = "얇은 경계벽",
            role = ObstacleRole.ROAD_BOUNDARY,
            center = Vec2(0.0f, 0f),
            halfLength = 0.08f,
            halfWidth = 3.0f,
            angleRad = 0f,
        )
        val start = VehicleState(
            x = -2.4f,
            y = 0f,
            headingRad = 0f,
            velocityMps = defaultConfig.maxSpeedLimitMps,
        )
        val (endState, collision) = CollisionDetector.stepWithContinuousCollisionCheck(
            startState = start,
            dtSec = 0.25f,
            config = defaultConfig,
            obstacles = listOf(thinWall),
        )
        assertTrue("연속 서브스텝 검사로 얇은 장애물 충돌을 놓치지 않아야 합니다", collision.isColliding)
        assertEquals(0f, endState.velocityMps, 1e-5f)
    }

    // ========================================================================
    // 4. Parking Success Evaluation Tests (PART 15 섹션 29.4)
    // ========================================================================

    @Test
    fun `evaluation - valid parallel parking inside target zone with aligned heading succeeds`() {
        val scenario = SimulatorScenarios.buildScenario(ScenarioType.PARALLEL_PARKING, defaultConfig)
        val parkedState = VehicleState(
            x = scenario.targetZone.center.x,
            y = scenario.targetZone.center.y - defaultConfig.rearAxleToBodyCenterOffsetMeters,
            headingRad = scenario.targetZone.angleRad,
            velocityMps = 0f,
        )
        val collision = CollisionDetector.checkVehicleCollision(parkedState, defaultConfig, scenario.obstacles)
        val eval = ParkingAndTurnEvaluator.evaluateParking(
            state = parkedState,
            config = defaultConfig,
            targetZone = scenario.targetZone,
            obstacles = scenario.obstacles,
            collisionResult = collision,
        )
        assertFalse(collision.isColliding)
        assertTrue(eval.isInsideTargetZone)
        assertTrue(eval.isHeadingAligned)
        assertTrue(eval.isStopped)
        assertEquals(EvaluationStatus.SUCCESS, eval.status)
    }

    @Test
    fun `evaluation - vehicle partially outside parking zone does not succeed`() {
        val scenario = SimulatorScenarios.buildScenario(ScenarioType.PARALLEL_PARKING, defaultConfig)
        val protrudingState = VehicleState(
            x = scenario.targetZone.center.x - 0.6f,
            y = scenario.targetZone.center.y - defaultConfig.rearAxleToBodyCenterOffsetMeters,
            headingRad = scenario.targetZone.angleRad,
            velocityMps = 0f,
        )
        val collision = CollisionDetector.checkVehicleCollision(protrudingState, defaultConfig, scenario.obstacles)
        val eval = ParkingAndTurnEvaluator.evaluateParking(
            state = protrudingState,
            config = defaultConfig,
            targetZone = scenario.targetZone,
            obstacles = scenario.obstacles,
            collisionResult = collision,
        )
        assertFalse("차체 일부가 구획 밖이면 isInsideTargetZone은 false여야 합니다", eval.isInsideTargetZone)
        assertTrue(eval.status != EvaluationStatus.SUCCESS)
    }

    @Test
    fun `evaluation - excessive heading angle error prevents parking success`() {
        val scenario = SimulatorScenarios.buildScenario(ScenarioType.PARALLEL_PARKING, defaultConfig)
        val skewedHeading = scenario.targetZone.angleRad + Math.toRadians(12.0).toFloat()
        val skewedState = VehicleState(
            x = scenario.targetZone.center.x,
            y = scenario.targetZone.center.y - defaultConfig.rearAxleToBodyCenterOffsetMeters,
            headingRad = skewedHeading,
            velocityMps = 0f,
        )
        val collision = CollisionDetector.checkVehicleCollision(skewedState, defaultConfig, scenario.obstacles)
        val eval = ParkingAndTurnEvaluator.evaluateParking(
            state = skewedState,
            config = defaultConfig,
            targetZone = scenario.targetZone,
            obstacles = scenario.obstacles,
            collisionResult = collision,
        )
        assertFalse("정렬 오차가 허용치를 넘으면 isHeadingAligned는 false여야 합니다", eval.isHeadingAligned)
        assertTrue(eval.status != EvaluationStatus.SUCCESS)
    }

    @Test
    fun `evaluation - collision state immediately fails parking evaluation`() {
        val scenario = SimulatorScenarios.buildScenario(ScenarioType.PARALLEL_PARKING, defaultConfig)
        val state = scenario.initialState
        val fakeCollision = CollisionResult(
            isColliding = true,
            hitObstacle = scenario.obstacles.first(),
            contactPointWorld = Vec2(2.30f, 2.0f),
        )
        val eval = ParkingAndTurnEvaluator.evaluateParking(
            state = state,
            config = defaultConfig,
            targetZone = scenario.targetZone,
            obstacles = scenario.obstacles,
            collisionResult = fakeCollision,
        )
        assertEquals(EvaluationStatus.COLLIDED, eval.status)
    }

    // ========================================================================
    // 5. Reference Path Generation Tests (PART 15 섹션 29.5)
    // ========================================================================

    @Test
    fun `reference path - parallel parking reference path is kinematically valid and collision-free`() {
        val scenario = SimulatorScenarios.buildScenario(ScenarioType.PARALLEL_PARKING, defaultConfig)
        val ref = scenario.referencePath
        assertTrue("기본 제원에서 평행주차 기준 경로가 생성되어야 합니다", ref is ReferencePathResult.Available)

        val available = ref as ReferencePathResult.Available
        assertTrue("충분한 경로 샘플 포인트가 존재해야 합니다", available.rearAxlePoints.size > 40)
        assertEquals("3단계 조향 전환 지점이 포함되어야 합니다", 3, available.waypoints.size)

        val finalState = available.targetGhostState
        val collision = CollisionDetector.checkVehicleCollision(finalState, defaultConfig, scenario.obstacles)
        val eval = ParkingAndTurnEvaluator.evaluateParking(
            state = finalState,
            config = defaultConfig,
            targetZone = scenario.targetZone,
            obstacles = scenario.obstacles,
            collisionResult = collision,
        )
        assertFalse("기준 경로 최종 상태에서 충돌이 없어야 합니다", collision.isColliding)
        assertEquals("기준 경로 완주 시 주차 성공 판정을 받아야 합니다", EvaluationStatus.SUCCESS, eval.status)
    }

    @Test
    fun `reference path - all active scenarios generate collision-free reference paths`() {
        val activeTypes = listOf(
            ScenarioType.PARALLEL_PARKING,
            ScenarioType.REVERSE_PARKING,
            ScenarioType.FORWARD_PARKING,
            ScenarioType.RIGHT_TURN,
            ScenarioType.LEFT_TURN,
            ScenarioType.WIDTH_PERCEPTION,
        )
        for (type in activeTypes) {
            val scenario = SimulatorScenarios.buildScenario(type, defaultConfig)
            assertTrue(
                "${type.koreanTitle} 시나리오의 기준 경로가 정상 생성되어야 합니다",
                scenario.referencePath is ReferencePathResult.Available,
            )
        }
    }

    @Test
    fun `reference path - returns Unavailable with reason when parking space is smaller than vehicle`() {
        val impossibleOptions = ScenarioSpaceOptions(parallelBayLengthMeters = 4.0f) // 차량 전장 4.5m보다 짧은 구획
        val scenario = SimulatorScenarios.buildScenario(
            type = ScenarioType.PARALLEL_PARKING,
            vehicleConfig = defaultConfig,
            spaceOptions = impossibleOptions,
        )
        assertTrue(
            "물리적으로 진입 불가능한 좁은 구획에서는 Unavailable을 반환해야 합니다",
            scenario.referencePath is ReferencePathResult.Unavailable,
        )
        val unavailable = scenario.referencePath as ReferencePathResult.Unavailable
        assertTrue(unavailable.reason.contains("기준 경로를 생성하지 못했습니다"))
    }

    @Test
    fun `trajectory predictor - predicts future path and detects upcoming obstacle collision`() {
        val scenario = SimulatorScenarios.buildScenario(ScenarioType.PARALLEL_PARKING, defaultConfig)
        val predSafe = TrajectoryPredictor.predict(
            state = scenario.initialState,
            gearDirection = VirtualGearDirection.REVERSE,
            config = defaultConfig,
            obstacles = scenario.obstacles,
        )
        assertNotNull(predSafe)
        assertTrue(predSafe.rearAxlePoints.size > 10)
    }

    // ========================================================================
    // 6. Physical Steering Wheel Mapping & Safety Pipeline Tests (요구사항 4, 5, 6, 8)
    // ========================================================================

    @Test
    fun `steering pipeline - Virtual Console sequence 0 to +90 to 0 to -90 to 0 maps accurately`() {
        val cal = SteeringCalibrationConfig(
            zeroOffsetDeg = 0f,
            steeringRatio = 15.0f,
            interpretationMode = SdkAngleInterpretationMode.STEERING_WHEEL_ROTATION_WITH_RATIO,
        )

        // 1. 0° -> 중앙 정렬
        val f0 = SteeringMappingPipeline.processRawAngle(0f, cal, defaultConfig)
        assertTrue(f0.isValid)
        assertEquals(SteeringDirectionLabel.CENTER, f0.direction)
        assertEquals(0f, f0.estimatedFrontWheelAngleRightPositiveDeg, 1e-4f)
        assertEquals(0f, f0.physicsSteeringAngleRad, 1e-4f)

        // 2. Virtual Console +90° (우조향) -> Pleos VHAL Raw SDK -90.0f -> 우조향 +90°, 전륜각 +6.0°, 물리엔진 -6.0° rad
        val fVcPlus90 = SteeringMappingPipeline.processRawAngle(-90f, cal, defaultConfig)
        assertTrue(fVcPlus90.isValid)
        assertEquals(SteeringDirectionLabel.RIGHT, fVcPlus90.direction)
        assertEquals(90f, fVcPlus90.estimatedSteeringWheelAngleDeg, 1e-3f)
        assertEquals(6.0f, fVcPlus90.estimatedFrontWheelAngleRightPositiveDeg, 1e-3f)
        assertTrue("우조향 시 물리 좌표계 전륜각(δ)은 음수여야 합니다", fVcPlus90.physicsSteeringAngleRad < 0f)

        // 3. Virtual Console -90° (좌조향) -> Pleos VHAL Raw SDK +90.0f -> 좌조향 -90°, 전륜각 -6.0°, 물리엔진 +6.0° rad
        val fVcMinus90 = SteeringMappingPipeline.processRawAngle(90f, cal, defaultConfig)
        assertTrue(fVcMinus90.isValid)
        assertEquals(SteeringDirectionLabel.LEFT, fVcMinus90.direction)
        assertEquals(-90f, fVcMinus90.estimatedSteeringWheelAngleDeg, 1e-3f)
        assertEquals(-6.0f, fVcMinus90.estimatedFrontWheelAngleRightPositiveDeg, 1e-3f)
        assertTrue("좌조향 시 물리 좌표계 전륜각(δ)은 양수여야 합니다", fVcMinus90.physicsSteeringAngleRad > 0f)
    }

    @Test
    fun `steering pipeline - rejects null, NaN, and out-of-range raw angles`() {
        val cal = SteeringCalibrationConfig()
        assertFalse(SteeringMappingPipeline.processRawAngle(null, cal, defaultConfig).isValid)
        assertFalse(SteeringMappingPipeline.processRawAngle(Float.NaN, cal, defaultConfig).isValid)
        assertFalse(SteeringMappingPipeline.processRawAngle(Float.POSITIVE_INFINITY, cal, defaultConfig).isValid)
        assertFalse(SteeringMappingPipeline.processRawAngle(2500f, cal, defaultConfig).isValid)
    }

    @Test
    fun `steering pipeline - zero offset calibration and max front wheel clamping`() {
        val cal = SteeringCalibrationConfig(
            zeroOffsetDeg = 12f,
            steeringRatio = 15.0f,
            interpretationMode = SdkAngleInterpretationMode.STEERING_WHEEL_ROTATION_WITH_RATIO,
        )
        // 원시 각도가 +12°일 때 영점 오프셋 +12°가 적용되면 중앙 정렬(0°)이 되어야 함
        val centered = SteeringMappingPipeline.processRawAngle(12f, cal, defaultConfig)
        assertTrue(centered.isValid)
        assertEquals(SteeringDirectionLabel.CENTER, centered.direction)
        assertEquals(0f, centered.estimatedFrontWheelAngleRightPositiveDeg, 1e-3f)

        // Virtual Console +540° (Raw SDK -540°) 회전 시 540 / 15 = 36° -> 차량 최대 전륜 조향각 +32°로 클램핑되어야 함
        val fullLock = SteeringMappingPipeline.processRawAngle(-540f, SteeringCalibrationConfig(), defaultConfig)
        assertTrue(fullLock.isValid)
        assertTrue(fullLock.isClampedToMaxWheelAngle)
        assertEquals(defaultConfig.sanitizedMaxSteeringDeg, fullLock.estimatedFrontWheelAngleRightPositiveDeg, 1e-3f)
    }

    @Test
    fun `diagnostic provider - transitions to error state on null signal and enforces P gear safety`() {
        val provider = DiagnosticSteeringInputProvider(defaultConfig)
        assertEquals(SteeringConnectionState.DIAGNOSTIC_OVERRIDE, provider.telemetryState.value.connectionState)
        assertTrue(provider.telemetryState.value.isParkedAndSafe)

        // 신호 끊김(null) 주입 시 SIGNAL_LOST_OR_ERROR 전환 및 연습 준비 해제
        provider.injectDiagnosticRawAngle(null)
        assertEquals(SteeringConnectionState.SIGNAL_LOST_OR_ERROR, provider.telemetryState.value.connectionState)
        assertFalse(provider.telemetryState.value.connectionState.isReadyForPractice)

        // D단 기어 주입 시 주차 안전 조건 해제
        provider.injectDiagnosticRawAngle(0f)
        provider.injectDiagnosticGear(GearPosition.DRIVE)
        assertFalse(provider.telemetryState.value.isParkedAndSafe)
    }

    @Test
    fun `oem categories and live steering guidance - all 4 categories and active scenarios provide direct cues`() {
        assertEquals(4, com.drivecoach.app.simulation.scenarios.PracticeCategory.entries.size)

        val activeScenarios = ScenarioType.entries.filter { it.isAvailable }
        assertTrue(activeScenarios.size >= 12)

        for (type in activeScenarios) {
            val scenario = SimulatorScenarios.buildScenario(type, defaultConfig)
            assertTrue(scenario.keyTakeawaysKorean.isNotEmpty())
            val guidance = SimulatorScenarios.computeLiveSteeringGuidance(
                scenario = scenario,
                vehicleState = scenario.initialState,
                currentFrontWheelRightPositiveDeg = 0f,
                evaluationStatus = EvaluationStatus.IN_PROGRESS,
                config = defaultConfig,
            )
            assertTrue(guidance.phaseLabel.isNotBlank())
            assertTrue(guidance.actionCue.isNotBlank())
        }
    }
}
