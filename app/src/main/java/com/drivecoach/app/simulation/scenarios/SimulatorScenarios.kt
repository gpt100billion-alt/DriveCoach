package com.drivecoach.app.simulation.scenarios

import com.drivecoach.app.simulation.collision.CollisionDetector
import com.drivecoach.app.simulation.collision.EvaluationStatus
import com.drivecoach.app.simulation.geometry.ObstacleRole
import com.drivecoach.app.simulation.geometry.OrientedBox2D
import com.drivecoach.app.simulation.geometry.Vec2
import com.drivecoach.app.simulation.geometry.VehicleGeometry
import com.drivecoach.app.simulation.geometry.WorldBounds
import com.drivecoach.app.simulation.physics.KinematicBicycleModel
import com.drivecoach.app.simulation.vehicle.VehicleConfig
import com.drivecoach.app.simulation.vehicle.VehicleState
import com.drivecoach.app.simulation.vehicle.VirtualGearDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 상위 연습 카테고리 (4대 핵심 OEM 연습 카테고리)
 */
enum class PracticeCategory(
    val indexCode: String,
    val koreanTitle: String,
    val shortSummary: String,
) {
    PARKING(
        indexCode = "01",
        koreanTitle = "주차",
        shortSummary = "평행주차 · 후진주차 · 전면주차",
    ),
    DRIVING(
        indexCode = "02",
        koreanTitle = "주행",
        shortSummary = "우회전 · 좌회전 · 차폭 감각 · 기초 조작",
    ),
    WIDTH_AND_LANE(
        indexCode = "03",
        koreanTitle = "차폭 감각",
        shortSummary = "차폭 감각 · 우측 여유 · 차선 유지",
    ),
    BASIC_CONTROL(
        indexCode = "04",
        koreanTitle = "기초 조작",
        shortSummary = "조향 복귀 · 조향 시점 · 저속 후진",
    ),
}

/**
 * 세부 시뮬레이터 연습 항목
 */
enum class ScenarioType(
    val category: PracticeCategory,
    val koreanTitle: String,
    val shortSubtitle: String,
    val keyActionTag: String,
    val isAvailable: Boolean = true,
    val defaultGear: VirtualGearDirection = VirtualGearDirection.REVERSE,
) {
    // 1. 주차 (PARKING)
    PARALLEL_PARKING(
        category = PracticeCategory.PARKING,
        koreanTitle = "평행주차",
        shortSubtitle = "핸들을 돌리는 시점과 차량의 진입 경로를 연습합니다.",
        keyActionTag = "후진 진입 · 반대 조향",
        isAvailable = true,
        defaultGear = VirtualGearDirection.REVERSE,
    ),
    REVERSE_PARKING(
        category = PracticeCategory.PARKING,
        koreanTitle = "후진주차",
        shortSubtitle = "두 차량 사이 주차 공간에 후진으로 진입하고 정렬합니다.",
        keyActionTag = "후진 진입 · 차체 정렬",
        isAvailable = true,
        defaultGear = VirtualGearDirection.REVERSE,
    ),
    FORWARD_PARKING(
        category = PracticeCategory.PARKING,
        koreanTitle = "전면주차",
        shortSubtitle = "회전 반경을 확보해 주차 공간으로 전진 진입합니다.",
        keyActionTag = "전진 진입 · 회전 반경",
        isAvailable = true,
        defaultGear = VirtualGearDirection.FORWARD,
    ),
    TIGHT_SPACE_PARKING(
        category = PracticeCategory.PARKING,
        koreanTitle = "좁은 공간 평행주차",
        shortSubtitle = "앞뒤 여유가 좁은 구획에서 정밀한 진입 경로를 연습합니다.",
        keyActionTag = "정밀 후진 진입",
        isAvailable = false,
        defaultGear = VirtualGearDirection.REVERSE,
    ),

    // 2. 주행 (DRIVING)
    RIGHT_TURN(
        category = PracticeCategory.DRIVING,
        koreanTitle = "우회전",
        shortSubtitle = "안쪽 연석과의 간격을 유지하며 우회전 경로를 연습합니다.",
        keyActionTag = "교차로 우회전 경로",
        isAvailable = true,
        defaultGear = VirtualGearDirection.FORWARD,
    ),
    LEFT_TURN(
        category = PracticeCategory.DRIVING,
        koreanTitle = "좌회전",
        shortSubtitle = "교차로 중심을 따라 안정적으로 좌회전하는 경로를 연습합니다.",
        keyActionTag = "교차로 좌회전 경로",
        isAvailable = true,
        defaultGear = VirtualGearDirection.FORWARD,
    ),
    TURN_RADIUS_SENSE(
        category = PracticeCategory.DRIVING,
        koreanTitle = "회전 반경 감각",
        shortSubtitle = "앞바퀴와 뒷바퀴의 회전 궤적 차이를 확인하며 회전합니다.",
        keyActionTag = "내륜차 궤적 확인",
        isAvailable = true,
        defaultGear = VirtualGearDirection.FORWARD,
    ),

    // 3. 차폭 감각 (WIDTH_AND_LANE)
    WIDTH_PERCEPTION(
        category = PracticeCategory.WIDTH_AND_LANE,
        koreanTitle = "차폭 감각",
        shortSubtitle = "차량 양측의 여유 공간을 확인하며 통로 중앙을 유지합니다.",
        keyActionTag = "좌우 여유 공간 유지",
        isAvailable = true,
        defaultGear = VirtualGearDirection.FORWARD,
    ),
    RIGHT_CLEARANCE(
        category = PracticeCategory.WIDTH_AND_LANE,
        koreanTitle = "우측 여유 감각",
        shortSubtitle = "우측 차체와 경계선 사이의 안전 간격을 유지하며 주행합니다.",
        keyActionTag = "우측 경계 간격 유지",
        isAvailable = true,
        defaultGear = VirtualGearDirection.FORWARD,
    ),
    LANE_CENTERING(
        category = PracticeCategory.WIDTH_AND_LANE,
        koreanTitle = "차선 중앙 유지",
        shortSubtitle = "작은 조향으로 차량을 차로 중앙에 정렬합니다.",
        keyActionTag = "차로 중앙 복귀",
        isAvailable = true,
        defaultGear = VirtualGearDirection.FORWARD,
    ),

    // 4. 기초 조작 (BASIC_CONTROL)
    STEERING_RETURN(
        category = PracticeCategory.BASIC_CONTROL,
        koreanTitle = "기초 조작",
        shortSubtitle = "조향각 변화에 따른 차량 예상 이동 경로의 관계를 익힙니다.",
        keyActionTag = "조향각과 이동 경로",
        isAvailable = true,
        defaultGear = VirtualGearDirection.FORWARD,
    ),
    STEERING_TIMING(
        category = PracticeCategory.BASIC_CONTROL,
        koreanTitle = "조향 시작 시점",
        shortSubtitle = "진입 위치에 맞춰 조향을 시작하는 시점을 연습합니다.",
        keyActionTag = "기준 지점 조향",
        isAvailable = true,
        defaultGear = VirtualGearDirection.FORWARD,
    ),
    LOW_SPEED_STEERING(
        category = PracticeCategory.BASIC_CONTROL,
        koreanTitle = "후진 조향 감각",
        shortSubtitle = "후진 시 핸들 방향과 차량 후미 이동 방향의 관계를 익힙니다.",
        keyActionTag = "후진 궤적 이해",
        isAvailable = true,
        defaultGear = VirtualGearDirection.REVERSE,
    ),
}

/**
 * 실시간 단계 및 조향 지시 데이터
 */
data class LiveSteeringGuidance(
    val phaseLabel: String,
    val actionCue: String,
    val targetFrontWheelRightPositiveDeg: Float?,
)

/**
 * 기준 경로 상의 주요 조향 전환 안내 지점
 */
data class ReferencePathWaypoint(
    val position: Vec2,
    val stepIndex: Int,
    val shortLabel: String,
)

/**
 * 운동학 기반 연습용 기준 경로 생성 결과
 */
sealed class ReferencePathResult {
    data class Available(
        val rearAxlePoints: List<Vec2>,
        val bodyCenterPoints: List<Vec2>,
        val waypoints: List<ReferencePathWaypoint>,
        val targetGhostState: VehicleState,
        val description: String = "차량 제원 기반 참고 경로입니다.",
    ) : ReferencePathResult()

    data class Unavailable(
        val reason: String = "현재 주차 공간에서는 기준 경로를 생성하지 못했습니다.",
    ) : ReferencePathResult()
}

/**
 * 도로 차선 및 시각적 가이드 선분
 */
data class RoadLineSegment(
    val start: Vec2,
    val end: Vec2,
    val isDashed: Boolean = false,
    val isCenterLine: Boolean = false,
    val isParkingLine: Boolean = false,
)

/**
 * 시뮬레이터 환경 전체 정의
 */
data class ScenarioDefinition(
    val type: ScenarioType,
    val worldBounds: WorldBounds,
    val initialState: VehicleState,
    val initialGear: VirtualGearDirection,
    val targetZone: OrientedBox2D,
    val obstacles: List<OrientedBox2D>,
    val roadPolygons: List<List<Vec2>>,
    val roadLines: List<RoadLineSegment>,
    val referencePath: ReferencePathResult,
    val helpStepsKorean: List<String>,
    val keyTakeawaysKorean: List<String>,
    val noticeKorean: String,
    val corridorCenterX: Float? = null,
    val corridorHalfWidthMeters: Float? = null,
)

/**
 * 사용자 조절 가능한 주차 공간 옵션
 */
data class ScenarioSpaceOptions(
    val parallelBayLengthMeters: Float = 6.4f,
    val parallelBayWidthMeters: Float = 2.3f,
    val perpendicularBayWidthMeters: Float = 2.65f,
    val perpendicularBayLengthMeters: Float = 5.2f,
    val corridorWidthMeters: Float = 2.55f,
)

object SimulatorScenarios {

    fun buildScenario(
        type: ScenarioType,
        vehicleConfig: VehicleConfig = VehicleConfig(),
        spaceOptions: ScenarioSpaceOptions = ScenarioSpaceOptions(),
    ): ScenarioDefinition {
        val base = when (type) {
            ScenarioType.PARALLEL_PARKING,
            ScenarioType.TIGHT_SPACE_PARKING -> buildParallelParkingScenario(vehicleConfig, spaceOptions)
            ScenarioType.REVERSE_PARKING,
            ScenarioType.LOW_SPEED_STEERING -> buildReverseParkingScenario(vehicleConfig, spaceOptions)
            ScenarioType.FORWARD_PARKING -> buildForwardParkingScenario(vehicleConfig, spaceOptions)
            ScenarioType.RIGHT_TURN,
            ScenarioType.TURN_RADIUS_SENSE,
            ScenarioType.STEERING_TIMING -> buildRightTurnScenario(vehicleConfig)
            ScenarioType.LEFT_TURN,
            ScenarioType.STEERING_RETURN -> buildLeftTurnScenario(vehicleConfig)
            ScenarioType.WIDTH_PERCEPTION,
            ScenarioType.RIGHT_CLEARANCE,
            ScenarioType.LANE_CENTERING -> buildWidthPerceptionScenario(vehicleConfig, spaceOptions)
        }
        return base.copy(
            type = type,
            keyTakeawaysKorean = takeawaysFor(type),
        )
    }

    private fun takeawaysFor(type: ScenarioType): List<String> {
        return when (type) {
            ScenarioType.PARALLEL_PARKING,
            ScenarioType.TIGHT_SPACE_PARKING -> listOf(
                "뒷바퀴가 구획 점선을 통과할 때 반대 방향(왼쪽)으로 핸들을 전환합니다.",
                "차체가 연석과 나란해지면 핸들을 천천히 풀어 앞·뒤 간격을 맞춥니다.",
            )
            ScenarioType.REVERSE_PARKING -> listOf(
                "구획을 약간 지나친 진입 위치에서 우측 조향 후진을 시작합니다.",
                "차체가 주차선과 평행해지기 직전 천천히 핸들을 풀어 중앙에 맞춥니다.",
            )
            ScenarioType.FORWARD_PARKING -> listOf(
                "안쪽 뒷바퀴 내륜차를 고려해 통로 바깥쪽으로 넓게 접근합니다.",
                "구획 진입 후 핸들을 중앙으로 맞추고 연석 앞에서 정지합니다.",
            )
            ScenarioType.RIGHT_TURN,
            ScenarioType.TURN_RADIUS_SENSE -> listOf(
                "차량 앞부분이 교차로에 진입한 뒤 조향을 시작해 연석 접촉을 방지합니다.",
                "회전 끝 구간에서 핸들을 천천히 풀어 목표 차로 중앙에 정렬합니다.",
            )
            ScenarioType.LEFT_TURN -> listOf(
                "교차로 진입 전에 미리 꺾지 않고 중심부까지 전진한 뒤 조향을 시작합니다.",
                "목표 차로 진입과 함께 핸들을 천천히 풀어 직진 정렬합니다.",
            )
            ScenarioType.WIDTH_PERCEPTION,
            ScenarioType.RIGHT_CLEARANCE,
            ScenarioType.LANE_CENTERING -> listOf(
                "운전석 시야 편향을 보정하기 위해 좌·우 여유 거리를 함께 확인합니다.",
                "작은 각도로 보정한 뒤 즉시 핸들을 중앙으로 되돌립니다.",
            )
            ScenarioType.STEERING_RETURN -> listOf(
                "차체가 목표 방향과 나란해지기 직전부터 천천히 풀어주세요.",
                "핸들을 0° 중앙에 맞춘 뒤 짧게 전진해 정렬을 마무리합니다.",
            )
            ScenarioType.STEERING_TIMING -> listOf(
                "진입 위치를 확인하고 예상 경로 점선이 목표 구획에 닿을 때 조향을 시작합니다.",
                "조향 시작 시점이 정확하면 중간 수정 조향이 줄어듭니다.",
            )
            ScenarioType.LOW_SPEED_STEERING -> listOf(
                "후진 시 핸들을 돌린 방향으로 차량 후미가 이동합니다.",
                "예상 경로를 먼저 확인한 뒤 저속으로 이동하세요.",
            )
        }
    }

    /**
     * 짧은 OEM 한국어 지시어 기반 실시간 조향 가이드 (Section 9 반영)
     * - 조향 시작 / 오른쪽으로 더 돌리세요 / 왼쪽으로 더 돌리세요 / 유지하세요 / 천천히 풀어주세요 / 진입 위치를 확인하세요
     */
    fun computeLiveSteeringGuidance(
        scenario: ScenarioDefinition,
        vehicleState: VehicleState,
        currentFrontWheelRightPositiveDeg: Float,
        evaluationStatus: EvaluationStatus,
        config: VehicleConfig,
    ): LiveSteeringGuidance {
        if (evaluationStatus == EvaluationStatus.SUCCESS) {
            return LiveSteeringGuidance(
                phaseLabel = "주차 완료",
                actionCue = "주차 완료",
                targetFrontWheelRightPositiveDeg = 0f,
            )
        }
        if (evaluationStatus == EvaluationStatus.COLLIDED) {
            return LiveSteeringGuidance(
                phaseLabel = "접촉 감지",
                actionCue = "다시 시도",
                targetFrontWheelRightPositiveDeg = null,
            )
        }
        if (scenario.referencePath is ReferencePathResult.Unavailable) {
            return LiveSteeringGuidance(
                phaseLabel = "연습 중",
                actionCue = "예상 궤적 확인",
                targetFrontWheelRightPositiveDeg = null,
            )
        }

        val maxDeg = config.sanitizedMaxSteeringDeg
        val headingErrRad = KinematicBicycleModel.normalizeAngleRad(vehicleState.headingRad - scenario.targetZone.angleRad)
        val headingErrDeg = (headingErrRad * 180.0 / PI).toFloat()

        return when (scenario.type) {
            ScenarioType.PARALLEL_PARKING,
            ScenarioType.TIGHT_SPACE_PARKING -> {
                val targetX = scenario.targetZone.center.x
                when {
                    vehicleState.x < targetX - 1.15f -> {
                        val targetDeg = maxDeg * 0.90f
                        val cue = if (currentFrontWheelRightPositiveDeg < targetDeg - 5f) {
                            "오른쪽으로 더 돌리세요"
                        } else {
                            "현재 조향을 유지하세요"
                        }
                        LiveSteeringGuidance("조향 시작", cue, targetDeg)
                    }
                    vehicleState.x < targetX - 0.12f || abs(headingErrDeg) > 9f -> {
                        val targetDeg = -maxDeg * 0.90f
                        val cue = if (currentFrontWheelRightPositiveDeg > targetDeg + 5f) {
                            "왼쪽으로 더 돌리세요"
                        } else {
                            "반대 조향을 유지하세요"
                        }
                        LiveSteeringGuidance("반대 조향", cue, targetDeg)
                    }
                    else -> {
                        val cue = if (abs(currentFrontWheelRightPositiveDeg) > 3.5f) {
                            "천천히 풀어주세요"
                        } else {
                            "구획 중앙에서 정지하세요"
                        }
                        LiveSteeringGuidance("중앙 정렬", cue, 0f)
                    }
                }
            }

            ScenarioType.REVERSE_PARKING,
            ScenarioType.LOW_SPEED_STEERING -> {
                if (abs(headingErrDeg) > 10f) {
                    val targetDeg = maxDeg * 0.88f
                    val cue = if (currentFrontWheelRightPositiveDeg < targetDeg - 5f) {
                        "오른쪽으로 더 돌리세요"
                    } else {
                        "현재 조향을 유지하세요"
                    }
                    LiveSteeringGuidance("조향 시작", cue, targetDeg)
                } else {
                    val cue = if (abs(currentFrontWheelRightPositiveDeg) > 3.5f) {
                        "천천히 풀어주세요"
                    } else {
                        "그대로 후진 후 정지하세요"
                    }
                    LiveSteeringGuidance("조향 복귀", cue, 0f)
                }
            }

            ScenarioType.FORWARD_PARKING -> {
                if (abs(headingErrDeg) > 10f) {
                    val targetDeg = maxDeg * 0.92f
                    val cue = if (currentFrontWheelRightPositiveDeg < targetDeg - 5f) {
                        "오른쪽으로 더 돌리세요"
                    } else {
                        "현재 조향을 유지하세요"
                    }
                    LiveSteeringGuidance("조향 시작", cue, targetDeg)
                } else {
                    val cue = if (abs(currentFrontWheelRightPositiveDeg) > 3.5f) {
                        "천천히 풀어주세요"
                    } else {
                        "전방 간격을 확인하고 정지하세요"
                    }
                    LiveSteeringGuidance("조향 복귀", cue, 0f)
                }
            }

            ScenarioType.RIGHT_TURN,
            ScenarioType.TURN_RADIUS_SENSE,
            ScenarioType.STEERING_TIMING -> {
                when {
                    vehicleState.y < -5.2f -> {
                        val cue = if (abs(currentFrontWheelRightPositiveDeg) > 4f) {
                            "천천히 풀어주세요"
                        } else {
                            "진입 위치를 확인하세요"
                        }
                        LiveSteeringGuidance("진입 확인", cue, 0f)
                    }
                    abs(headingErrDeg) > 12f -> {
                        val targetDeg = maxDeg * 0.90f
                        val cue = if (currentFrontWheelRightPositiveDeg < targetDeg - 5f) {
                            "오른쪽으로 더 돌리세요"
                        } else {
                            "현재 조향을 유지하세요"
                        }
                        LiveSteeringGuidance("조향 시작", cue, targetDeg)
                    }
                    else -> {
                        val cue = if (abs(currentFrontWheelRightPositiveDeg) > 3.5f) {
                            "천천히 풀어주세요"
                        } else {
                            "목표 구간에서 정지하세요"
                        }
                        LiveSteeringGuidance("조향 복귀", cue, 0f)
                    }
                }
            }

            ScenarioType.LEFT_TURN,
            ScenarioType.STEERING_RETURN -> {
                when {
                    vehicleState.y < -4.8f -> {
                        val cue = if (abs(currentFrontWheelRightPositiveDeg) > 4f) {
                            "천천히 풀어주세요"
                        } else {
                            "진입 위치를 확인하세요"
                        }
                        LiveSteeringGuidance("진입 확인", cue, 0f)
                    }
                    abs(headingErrDeg) > 12f -> {
                        val targetDeg = -maxDeg * 0.65f
                        val cue = if (currentFrontWheelRightPositiveDeg > targetDeg + 5f) {
                            "왼쪽으로 더 돌리세요"
                        } else {
                            "현재 조향을 유지하세요"
                        }
                        LiveSteeringGuidance("조향 시작", cue, targetDeg)
                    }
                    else -> {
                        val cue = if (abs(currentFrontWheelRightPositiveDeg) > 3.5f) {
                            "천천히 풀어주세요"
                        } else {
                            "목표 구간에서 정지하세요"
                        }
                        LiveSteeringGuidance("조향 복귀", cue, 0f)
                    }
                }
            }

            ScenarioType.WIDTH_PERCEPTION,
            ScenarioType.RIGHT_CLEARANCE,
            ScenarioType.LANE_CENTERING -> {
                val offset = vehicleState.x - (scenario.corridorCenterX ?: 0f)
                when {
                    offset > 0.10f -> LiveSteeringGuidance(
                        phaseLabel = "위치 보정",
                        actionCue = "왼쪽으로 살짝 돌리세요",
                        targetFrontWheelRightPositiveDeg = -4f,
                    )
                    offset < -0.10f -> LiveSteeringGuidance(
                        phaseLabel = "위치 보정",
                        actionCue = "오른쪽으로 살짝 돌리세요",
                        targetFrontWheelRightPositiveDeg = +4f,
                    )
                    abs(currentFrontWheelRightPositiveDeg) > 3f -> LiveSteeringGuidance(
                        phaseLabel = "중앙 유지",
                        actionCue = "천천히 풀어주세요",
                        targetFrontWheelRightPositiveDeg = 0f,
                    )
                    else -> LiveSteeringGuidance(
                        phaseLabel = "중앙 유지",
                        actionCue = "현재 조향을 유지하세요",
                        targetFrontWheelRightPositiveDeg = 0f,
                    )
                }
            }
        }
    }

    // ========================================================================
    // 1. 평행주차 시뮬레이터
    // ========================================================================
    private fun buildParallelParkingScenario(
        config: VehicleConfig,
        options: ScenarioSpaceOptions,
    ): ScenarioDefinition {
        val bayLength = options.parallelBayLengthMeters.coerceIn(3.8f, 8.5f)
        val bayWidth = options.parallelBayWidthMeters.coerceIn(1.7f, 3.0f)
        val bayCenterX = 2.30f
        val bayCenterY = 0.0f
        val halfPI = (PI / 2.0).toFloat()

        val targetZone = OrientedBox2D(
            id = "parallel_target",
            label = "평행주차 목표 구획",
            role = ObstacleRole.EGO_VEHICLE,
            center = Vec2(bayCenterX, bayCenterY),
            halfLength = bayLength * 0.5f,
            halfWidth = bayWidth * 0.5f,
            angleRad = halfPI,
        )

        val parkedCarHalfLen = 2.15f
        val parkedCarHalfWid = 0.88f
        val frontCarCenterY = (bayLength * 0.5f) + parkedCarHalfLen + 0.45f
        val rearCarCenterY = -(bayLength * 0.5f) - parkedCarHalfLen - 0.45f

        val frontParkedCar = OrientedBox2D(
            id = "front_car",
            label = "앞 차량",
            role = ObstacleRole.FRONT_PARKED_CAR,
            center = Vec2(bayCenterX, frontCarCenterY),
            halfLength = parkedCarHalfLen,
            halfWidth = parkedCarHalfWid,
            angleRad = halfPI,
        )

        val rearParkedCar = OrientedBox2D(
            id = "rear_car",
            label = "뒤 차량",
            role = ObstacleRole.REAR_PARKED_CAR,
            center = Vec2(bayCenterX, rearCarCenterY),
            halfLength = parkedCarHalfLen,
            halfWidth = parkedCarHalfWid,
            angleRad = halfPI,
        )

        val curbX = bayCenterX + (bayWidth * 0.5f) + 0.32f
        val curbBox = OrientedBox2D(
            id = "right_curb",
            label = "우측 연석",
            role = ObstacleRole.CURB,
            center = Vec2(curbX, 0f),
            halfLength = 10.5f,
            halfWidth = 0.25f,
            angleRad = halfPI,
        )

        val leftRoadBoundary = OrientedBox2D(
            id = "left_boundary",
            label = "좌측 차로 경계",
            role = ObstacleRole.ROAD_BOUNDARY,
            center = Vec2(-3.2f, 0f),
            halfLength = 10.5f,
            halfWidth = 0.20f,
            angleRad = halfPI,
        )

        val obstacles = listOf(frontParkedCar, rearParkedCar, curbBox, leftRoadBoundary)

        val startLaneX = -0.05f
        val steerUseRad = config.maxSteeringAngleRad * 0.96f
        val turnRadius = config.sanitizedWheelbaseMeters / tan(steerUseRad.toDouble()).toFloat()
        val lateralShift = (bayCenterX - startLaneX).coerceAtLeast(0.5f)
        val cosAlpha = (1f - (lateralShift / (2f * turnRadius))).coerceIn(-0.95f, 0.99f)
        val alphaRad = acos(cosAlpha.toDouble()).toFloat()
        val longitudinalShift = 2f * turnRadius * sin(alphaRad.toDouble()).toFloat()

        val entryBodyCenterY = bayCenterY - 0.62f
        val entryRearAxleY = entryBodyCenterY - config.rearAxleToBodyCenterOffsetMeters
        val startRearAxleY = entryRearAxleY + longitudinalShift
        val initialState = VehicleState(
            x = startLaneX,
            y = startRearAxleY,
            headingRad = halfPI,
            velocityMps = 0f,
            steeringAngleRad = 0f,
        )

        val referencePath = generateParallelReferencePath(
            initialState = initialState,
            steerUseRad = steerUseRad,
            alphaRad = alphaRad,
            pullForwardDistanceMeters = 0.62f,
            config = config,
            obstacles = obstacles,
            targetZone = targetZone,
        )

        val roadPoly = listOf(
            Vec2(-3.0f, -10.0f),
            Vec2(curbX - 0.25f, -10.0f),
            Vec2(curbX - 0.25f, 10.0f),
            Vec2(-3.0f, 10.0f),
        )

        val bayLeftX = bayCenterX - bayWidth * 0.5f
        val bayRightX = bayCenterX + bayWidth * 0.5f
        val bayTopY = bayCenterY + bayLength * 0.5f
        val bayBottomY = bayCenterY - bayLength * 0.5f

        val lines = listOf(
            RoadLineSegment(Vec2(-1.45f, -9.8f), Vec2(-1.45f, 9.8f), isDashed = true, isCenterLine = true),
            RoadLineSegment(Vec2(bayLeftX, -9.8f), Vec2(bayLeftX, 9.8f), isDashed = true),
            RoadLineSegment(Vec2(bayLeftX, bayTopY), Vec2(bayRightX, bayTopY), isParkingLine = true),
            RoadLineSegment(Vec2(bayLeftX, bayBottomY), Vec2(bayRightX, bayBottomY), isParkingLine = true),
            RoadLineSegment(Vec2(bayRightX, bayBottomY), Vec2(bayRightX, bayTopY), isParkingLine = true),
        )

        return ScenarioDefinition(
            type = ScenarioType.PARALLEL_PARKING,
            worldBounds = WorldBounds(minX = -3.5f, maxX = curbX + 0.7f, minY = -8.2f, maxY = 8.2f),
            initialState = initialState,
            initialGear = VirtualGearDirection.REVERSE,
            targetZone = targetZone,
            obstacles = obstacles,
            roadPolygons = listOf(roadPoly),
            roadLines = lines,
            referencePath = referencePath,
            helpStepsKorean = listOf(
                "진입 위치를 확인하고 오른쪽으로 핸들을 돌려 후진합니다.",
                "뒷바퀴가 구획 점선에 닿으면 왼쪽(반대 방향)으로 끝까지 돌립니다.",
                "차체가 나란해지면 핸들을 천천히 풀어 중앙에 맞춘 뒤 정지합니다.",
            ),
            keyTakeawaysKorean = takeawaysFor(ScenarioType.PARALLEL_PARKING),
            noticeKorean = "차량 P단 정차 상태에서 실제 핸들을 조작해 예상 경로를 확인합니다.",
        )
    }

    private fun generateParallelReferencePath(
        initialState: VehicleState,
        steerUseRad: Float,
        alphaRad: Float,
        pullForwardDistanceMeters: Float,
        config: VehicleConfig,
        obstacles: List<OrientedBox2D>,
        targetZone: OrientedBox2D,
    ): ReferencePathResult {
        if (targetZone.halfLength * 2f < config.sanitizedLengthMeters + 0.45f) {
            return ReferencePathResult.Unavailable("현재 주차 공간에서는 기준 경로를 생성하지 못했습니다.")
        }

        val rearPts = ArrayList<Vec2>()
        val bodyPts = ArrayList<Vec2>()
        val waypoints = ArrayList<ReferencePathWaypoint>()

        val reverseSpeed = -0.8f
        val yawRateMag = (abs(reverseSpeed) / config.sanitizedWheelbaseMeters) * tan(steerUseRad.toDouble()).toFloat()
        if (yawRateMag < 1e-4f) {
            return ReferencePathResult.Unavailable()
        }

        val arcDurationSec = alphaRad / yawRateMag
        val dt = 0.02f

        var sim = initialState.copy(velocityMps = reverseSpeed, steeringAngleRad = -steerUseRad)
        waypoints.add(
            ReferencePathWaypoint(
                position = Vec2(sim.x, sim.y),
                stepIndex = 0,
                shortLabel = "01 조향 시작",
            )
        )

        var remainingArc1 = arcDurationSec
        while (remainingArc1 > 1e-5f) {
            val stepDt = remainingArc1.coerceAtMost(dt)
            val fp = VehicleGeometry.computeFootprint(sim, config)
            rearPts.add(fp.rearAxleCenter)
            bodyPts.add(fp.bodyCenter)
            val col = CollisionDetector.checkVehicleCollision(sim, config, obstacles)
            if (col.isColliding) {
                return ReferencePathResult.Unavailable("현재 주차 공간에서는 기준 경로를 생성하지 못했습니다.")
            }
            sim = KinematicBicycleModel.integrateSingleSubstep(sim, stepDt, config)
            remainingArc1 -= stepDt
        }

        waypoints.add(
            ReferencePathWaypoint(
                position = Vec2(sim.x, sim.y),
                stepIndex = rearPts.size,
                shortLabel = "02 반대 조향",
            )
        )

        sim = sim.copy(steeringAngleRad = +steerUseRad, velocityMps = reverseSpeed)
        var remainingArc2 = arcDurationSec
        while (remainingArc2 > 1e-5f) {
            val stepDt = remainingArc2.coerceAtMost(dt)
            val fp = VehicleGeometry.computeFootprint(sim, config)
            rearPts.add(fp.rearAxleCenter)
            bodyPts.add(fp.bodyCenter)
            val col = CollisionDetector.checkVehicleCollision(sim, config, obstacles)
            if (col.isColliding) {
                return ReferencePathResult.Unavailable("현재 주차 공간에서는 기준 경로를 생성하지 못했습니다.")
            }
            sim = KinematicBicycleModel.integrateSingleSubstep(sim, stepDt, config)
            remainingArc2 -= stepDt
        }

        val forwardSpeed = 0.75f
        var remainingPull = (pullForwardDistanceMeters / forwardSpeed).coerceAtLeast(0f)
        sim = sim.copy(steeringAngleRad = 0f, velocityMps = forwardSpeed)
        while (remainingPull > 1e-5f) {
            val stepDt = remainingPull.coerceAtMost(dt)
            val fp = VehicleGeometry.computeFootprint(sim, config)
            rearPts.add(fp.rearAxleCenter)
            bodyPts.add(fp.bodyCenter)
            val col = CollisionDetector.checkVehicleCollision(sim, config, obstacles)
            if (col.isColliding) {
                return ReferencePathResult.Unavailable("현재 주차 공간에서는 기준 경로를 생성하지 못했습니다.")
            }
            sim = KinematicBicycleModel.integrateSingleSubstep(sim, stepDt, config)
            remainingPull -= stepDt
        }

        val finalState = sim.copy(velocityMps = 0f, steeringAngleRad = 0f)
        val finalFp = VehicleGeometry.computeFootprint(finalState, config)
        rearPts.add(finalFp.rearAxleCenter)
        bodyPts.add(finalFp.bodyCenter)

        if (!targetZone.containsBox(finalFp.bodyBox, insetMarginMeters = 0.02f)) {
            return ReferencePathResult.Unavailable("현재 주차 공간에서는 기준 경로를 생성하지 못했습니다.")
        }

        waypoints.add(
            ReferencePathWaypoint(
                position = finalFp.rearAxleCenter,
                stepIndex = rearPts.lastIndex,
                shortLabel = "03 중앙 정렬",
            )
        )

        return ReferencePathResult.Available(
            rearAxlePoints = rearPts,
            bodyCenterPoints = bodyPts,
            waypoints = waypoints,
            targetGhostState = finalState,
        )
    }

    // ========================================================================
    // 2. 후진주차 시뮬레이터
    // ========================================================================
    private fun buildReverseParkingScenario(
        config: VehicleConfig,
        options: ScenarioSpaceOptions,
    ): ScenarioDefinition {
        val bayWidth = options.perpendicularBayWidthMeters.coerceIn(2.3f, 3.2f)
        val bayLength = options.perpendicularBayLengthMeters.coerceIn(4.8f, 6.0f)
        val halfPI = (PI / 2.0).toFloat()

        val bayCenterY = -3.3f
        val targetZone = OrientedBox2D(
            id = "reverse_target",
            label = "후진주차 목표 구획",
            role = ObstacleRole.EGO_VEHICLE,
            center = Vec2(0f, bayCenterY),
            halfLength = bayLength * 0.5f,
            halfWidth = bayWidth * 0.5f,
            angleRad = halfPI,
        )

        val leftCar = OrientedBox2D(
            id = "left_parked_car",
            label = "좌측 주차 차량",
            role = ObstacleRole.LEFT_PARKED_CAR,
            center = Vec2(-bayWidth, bayCenterY),
            halfLength = 2.2f,
            halfWidth = 0.88f,
            angleRad = halfPI,
        )

        val rightCar = OrientedBox2D(
            id = "right_parked_car",
            label = "우측 주차 차량",
            role = ObstacleRole.RIGHT_PARKED_CAR,
            center = Vec2(bayWidth, bayCenterY),
            halfLength = 2.2f,
            halfWidth = 0.88f,
            angleRad = halfPI,
        )

        val rearCurb = OrientedBox2D(
            id = "rear_curb",
            label = "후방 연석",
            role = ObstacleRole.CURB,
            center = Vec2(0f, bayCenterY - bayLength * 0.5f - 0.30f),
            halfLength = 6.5f,
            halfWidth = 0.25f,
            angleRad = 0f,
        )

        val topBoundary = OrientedBox2D(
            id = "top_wall",
            label = "통로 상단 경계",
            role = ObstacleRole.ROAD_BOUNDARY,
            center = Vec2(0f, 5.4f),
            halfLength = 7.5f,
            halfWidth = 0.25f,
            angleRad = 0f,
        )

        val obstacles = listOf(leftCar, rightCar, rearCurb, topBoundary)

        val steerUseRad = config.maxSteeringAngleRad * 0.90f
        val rTurn = config.sanitizedWheelbaseMeters / tan(steerUseRad.toDouble()).toFloat()
        val arcEntryY = 1.65f
        val startX = rTurn
        val initialState = VehicleState(
            x = startX,
            y = arcEntryY,
            headingRad = 0f,
            velocityMps = 0f,
            steeringAngleRad = 0f,
        )

        val referencePath = generateReverseParkingReferencePath(
            initialState = initialState,
            steerUseRad = steerUseRad,
            targetRearY = bayCenterY - config.rearAxleToBodyCenterOffsetMeters,
            config = config,
            obstacles = obstacles,
        )

        val bayTopY = bayCenterY + bayLength * 0.5f
        val bayBotY = bayCenterY - bayLength * 0.5f
        val halfW = bayWidth * 0.5f
        val lines = listOf(
            RoadLineSegment(Vec2(-halfW, bayBotY), Vec2(-halfW, bayTopY), isParkingLine = true),
            RoadLineSegment(Vec2(halfW, bayBotY), Vec2(halfW, bayTopY), isParkingLine = true),
            RoadLineSegment(Vec2(-halfW, bayBotY), Vec2(halfW, bayBotY), isParkingLine = true),
            RoadLineSegment(Vec2(-halfW - bayWidth, bayBotY), Vec2(-halfW - bayWidth, bayTopY), isParkingLine = true),
            RoadLineSegment(Vec2(halfW + bayWidth, bayBotY), Vec2(halfW + bayWidth, bayTopY), isParkingLine = true),
        )

        val roadPoly = listOf(
            Vec2(-7.0f, bayBotY - 0.1f),
            Vec2(7.0f, bayBotY - 0.1f),
            Vec2(7.0f, 5.1f),
            Vec2(-7.0f, 5.1f),
        )

        return ScenarioDefinition(
            type = ScenarioType.REVERSE_PARKING,
            worldBounds = WorldBounds(minX = -6.2f, maxX = 6.8f, minY = -6.6f, maxY = 5.6f),
            initialState = initialState,
            initialGear = VirtualGearDirection.REVERSE,
            targetZone = targetZone,
            obstacles = obstacles,
            roadPolygons = listOf(roadPoly),
            roadLines = lines,
            referencePath = referencePath,
            helpStepsKorean = listOf(
                "구획을 지나친 진입 위치를 확인하고 오른쪽으로 핸들을 돌립니다.",
                "좌·우 차량 모서리 간격을 확인하며 구획 안으로 후진합니다.",
                "차체가 주차선과 나란해지면 천천히 핸들을 풀어 정지합니다.",
            ),
            keyTakeawaysKorean = takeawaysFor(ScenarioType.REVERSE_PARKING),
            noticeKorean = "후진주차는 뒷바퀴를 회전 중심으로 사용하여 좁은 통로에서 진입이 유리합니다.",
        )
    }

    private fun generateReverseParkingReferencePath(
        initialState: VehicleState,
        steerUseRad: Float,
        targetRearY: Float,
        config: VehicleConfig,
        obstacles: List<OrientedBox2D>,
    ): ReferencePathResult {
        val rearPts = ArrayList<Vec2>()
        val bodyPts = ArrayList<Vec2>()
        val waypoints = ArrayList<ReferencePathWaypoint>()
        val dt = 0.02f
        val revSpeed = -0.8f
        val yawRate = (abs(revSpeed) / config.sanitizedWheelbaseMeters) * tan(steerUseRad.toDouble()).toFloat()
        val steps90 = (((PI / 2.0).toFloat() / yawRate) / dt).toInt().coerceAtLeast(1)

        var sim = initialState.copy(velocityMps = revSpeed, steeringAngleRad = -steerUseRad)
        waypoints.add(ReferencePathWaypoint(Vec2(sim.x, sim.y), 0, "01 조향 시작"))

        for (i in 0 until steps90) {
            val fp = VehicleGeometry.computeFootprint(sim, config)
            rearPts.add(fp.rearAxleCenter)
            bodyPts.add(fp.bodyCenter)
            if (CollisionDetector.checkVehicleCollision(sim, config, obstacles).isColliding) {
                return ReferencePathResult.Unavailable()
            }
            sim = KinematicBicycleModel.integrateSingleSubstep(sim, dt, config)
        }

        waypoints.add(ReferencePathWaypoint(Vec2(sim.x, sim.y), rearPts.size, "02 조향 복귀"))
        sim = sim.copy(steeringAngleRad = 0f, velocityMps = revSpeed)
        var guard = 0
        while (sim.y > targetRearY && guard < 250) {
            val fp = VehicleGeometry.computeFootprint(sim, config)
            rearPts.add(fp.rearAxleCenter)
            bodyPts.add(fp.bodyCenter)
            if (CollisionDetector.checkVehicleCollision(sim, config, obstacles).isColliding) {
                return ReferencePathResult.Unavailable()
            }
            sim = KinematicBicycleModel.integrateSingleSubstep(sim, dt, config)
            guard++
        }

        val finalState = sim.copy(velocityMps = 0f, steeringAngleRad = 0f)
        val finalFp = VehicleGeometry.computeFootprint(finalState, config)
        rearPts.add(finalFp.rearAxleCenter)
        bodyPts.add(finalFp.bodyCenter)
        waypoints.add(ReferencePathWaypoint(finalFp.rearAxleCenter, rearPts.lastIndex, "03 정렬 완료"))

        return ReferencePathResult.Available(
            rearAxlePoints = rearPts,
            bodyCenterPoints = bodyPts,
            waypoints = waypoints,
            targetGhostState = finalState,
        )
    }

    // ========================================================================
    // 3. 전면주차 시뮬레이터
    // ========================================================================
    private fun buildForwardParkingScenario(
        config: VehicleConfig,
        options: ScenarioSpaceOptions,
    ): ScenarioDefinition {
        val bayWidth = (options.perpendicularBayWidthMeters + 0.30f).coerceIn(2.6f, 3.4f)
        val bayLength = options.perpendicularBayLengthMeters.coerceIn(4.9f, 6.0f)
        val negHalfPI = -(PI / 2.0).toFloat()

        val bayCenterY = -3.55f
        val targetZone = OrientedBox2D(
            id = "forward_target",
            label = "전면주차 목표 구획",
            role = ObstacleRole.EGO_VEHICLE,
            center = Vec2(0f, bayCenterY),
            halfLength = bayLength * 0.5f,
            halfWidth = bayWidth * 0.5f,
            angleRad = negHalfPI,
        )

        val leftCar = OrientedBox2D(
            id = "left_parked_car",
            label = "좌측 주차 차량",
            role = ObstacleRole.LEFT_PARKED_CAR,
            center = Vec2(-bayWidth, bayCenterY),
            halfLength = 2.15f,
            halfWidth = 0.86f,
            angleRad = negHalfPI,
        )

        val rightCar = OrientedBox2D(
            id = "right_parked_car",
            label = "우측 주차 차량",
            role = ObstacleRole.RIGHT_PARKED_CAR,
            center = Vec2(bayWidth, bayCenterY),
            halfLength = 2.15f,
            halfWidth = 0.86f,
            angleRad = negHalfPI,
        )

        val frontCurb = OrientedBox2D(
            id = "front_curb",
            label = "전방 연석",
            role = ObstacleRole.CURB,
            center = Vec2(0f, bayCenterY - bayLength * 0.5f - 0.30f),
            halfLength = 6.5f,
            halfWidth = 0.25f,
            angleRad = 0f,
        )

        val obstacles = listOf(leftCar, rightCar, frontCurb)

        val steerUseRad = config.maxSteeringAngleRad * 0.98f
        val rTurn = config.sanitizedWheelbaseMeters / tan(steerUseRad.toDouble()).toFloat()
        val initialState = VehicleState(
            x = -rTurn,
            y = 4.15f,
            headingRad = 0f,
            velocityMps = 0f,
            steeringAngleRad = 0f,
        )

        val targetRearY = bayCenterY + config.rearAxleToBodyCenterOffsetMeters
        val referencePath = generateForwardParkingReferencePath(
            initialState = initialState,
            steerUseRad = steerUseRad,
            targetRearY = targetRearY,
            config = config,
            obstacles = obstacles,
        )

        val bayTopY = bayCenterY + bayLength * 0.5f
        val bayBotY = bayCenterY - bayLength * 0.5f
        val halfW = bayWidth * 0.5f
        val lines = listOf(
            RoadLineSegment(Vec2(-halfW, bayBotY), Vec2(-halfW, bayTopY), isParkingLine = true),
            RoadLineSegment(Vec2(halfW, bayBotY), Vec2(halfW, bayTopY), isParkingLine = true),
            RoadLineSegment(Vec2(-halfW, bayBotY), Vec2(halfW, bayBotY), isParkingLine = true),
        )

        val roadPoly = listOf(
            Vec2(-7.0f, bayBotY - 0.1f),
            Vec2(7.0f, bayBotY - 0.1f),
            Vec2(7.0f, 5.5f),
            Vec2(-7.0f, 5.5f),
        )

        return ScenarioDefinition(
            type = ScenarioType.FORWARD_PARKING,
            worldBounds = WorldBounds(minX = -6.5f, maxX = 6.5f, minY = -6.6f, maxY = 5.8f),
            initialState = initialState,
            initialGear = VirtualGearDirection.FORWARD,
            targetZone = targetZone,
            obstacles = obstacles,
            roadPolygons = listOf(roadPoly),
            roadLines = lines,
            referencePath = referencePath,
            helpStepsKorean = listOf(
                "통로 바깥쪽 진입 위치를 확인하고 안쪽 뒷바퀴 여유를 확보합니다.",
                "오른쪽으로 핸들을 돌려 구획 안으로 전진 진입합니다.",
                "차체가 정렬되면 핸들을 천천히 풀어 연석 앞에서 정지합니다.",
            ),
            keyTakeawaysKorean = takeawaysFor(ScenarioType.FORWARD_PARKING),
            noticeKorean = "전면주차 시에는 안쪽 뒷바퀴가 그리는 내륜차 궤적을 함께 확인해야 합니다.",
        )
    }

    private fun generateForwardParkingReferencePath(
        initialState: VehicleState,
        steerUseRad: Float,
        targetRearY: Float,
        config: VehicleConfig,
        obstacles: List<OrientedBox2D>,
    ): ReferencePathResult {
        val rearPts = ArrayList<Vec2>()
        val bodyPts = ArrayList<Vec2>()
        val waypoints = ArrayList<ReferencePathWaypoint>()
        val dt = 0.02f
        val fwdSpeed = 0.85f
        val yawRate = (fwdSpeed / config.sanitizedWheelbaseMeters) * tan(steerUseRad.toDouble()).toFloat()
        val arcDurationSec = ((PI / 2.0).toFloat() / yawRate)

        var sim = initialState.copy(velocityMps = fwdSpeed, steeringAngleRad = -steerUseRad)
        waypoints.add(ReferencePathWaypoint(Vec2(sim.x, sim.y), 0, "01 조향 시작"))

        var remainingArc = arcDurationSec
        while (remainingArc > 1e-5f) {
            val stepDt = remainingArc.coerceAtMost(dt)
            val fp = VehicleGeometry.computeFootprint(sim, config)
            rearPts.add(fp.rearAxleCenter)
            bodyPts.add(fp.bodyCenter)
            if (CollisionDetector.checkVehicleCollision(sim, config, obstacles).isColliding) {
                return ReferencePathResult.Unavailable()
            }
            sim = KinematicBicycleModel.integrateSingleSubstep(sim, stepDt, config)
            remainingArc -= stepDt
        }

        waypoints.add(ReferencePathWaypoint(Vec2(sim.x, sim.y), rearPts.size, "02 조향 복귀"))
        sim = sim.copy(steeringAngleRad = 0f, velocityMps = fwdSpeed)
        var guard = 0
        while (sim.y > targetRearY && guard < 250) {
            val fp = VehicleGeometry.computeFootprint(sim, config)
            rearPts.add(fp.rearAxleCenter)
            bodyPts.add(fp.bodyCenter)
            if (CollisionDetector.checkVehicleCollision(sim, config, obstacles).isColliding) {
                return ReferencePathResult.Unavailable()
            }
            sim = KinematicBicycleModel.integrateSingleSubstep(sim, dt, config)
            guard++
        }

        val finalState = sim.copy(velocityMps = 0f, steeringAngleRad = 0f)
        val finalFp = VehicleGeometry.computeFootprint(finalState, config)
        rearPts.add(finalFp.rearAxleCenter)
        bodyPts.add(finalFp.bodyCenter)
        waypoints.add(ReferencePathWaypoint(finalFp.rearAxleCenter, rearPts.lastIndex, "03 정렬 완료"))

        return ReferencePathResult.Available(
            rearAxlePoints = rearPts,
            bodyCenterPoints = bodyPts,
            waypoints = waypoints,
            targetGhostState = finalState,
        )
    }

    // ========================================================================
    // 4. 교차로 우회전 시뮬레이터
    // ========================================================================
    private fun buildRightTurnScenario(config: VehicleConfig): ScenarioDefinition {
        val halfPI = (PI / 2.0).toFloat()
        val exitLaneY = -2.10f

        val initialState = VehicleState(
            x = 1.75f,
            y = -8.2f,
            headingRad = halfPI,
            velocityMps = 0f,
            steeringAngleRad = 0f,
        )

        val targetZone = OrientedBox2D(
            id = "right_turn_target",
            label = "우회전 목표 차로",
            role = ObstacleRole.EGO_VEHICLE,
            center = Vec2(8.2f, exitLaneY),
            halfLength = 3.2f,
            halfWidth = 1.65f,
            angleRad = 0f,
        )

        val innerCornerCurb = OrientedBox2D(
            id = "inner_curb_se",
            label = "우회전 안쪽 연석",
            role = ObstacleRole.CURB,
            center = Vec2(7.4f, -7.6f),
            halfLength = 3.4f,
            halfWidth = 3.4f,
            angleRad = 0f,
        )

        val centerDividerEast = OrientedBox2D(
            id = "center_line_east",
            label = "진출 도로 중앙선",
            role = ObstacleRole.ROAD_BOUNDARY,
            center = Vec2(8.4f, 0.35f),
            halfLength = 4.0f,
            halfWidth = 0.18f,
            angleRad = 0f,
        )

        val entryCenterDivider = OrientedBox2D(
            id = "center_line_south",
            label = "진입 도로 중앙선",
            role = ObstacleRole.ROAD_BOUNDARY,
            center = Vec2(-0.15f, -7.5f),
            halfLength = 3.5f,
            halfWidth = 0.18f,
            angleRad = halfPI,
        )

        val obstacles = listOf(innerCornerCurb, centerDividerEast, entryCenterDivider)

        val vRoadPoly = listOf(
            Vec2(-3.5f, -11.0f),
            Vec2(3.9f, -11.0f),
            Vec2(3.9f, 6.0f),
            Vec2(-3.5f, 6.0f),
        )
        val hRoadPoly = listOf(
            Vec2(-5.0f, -4.1f),
            Vec2(12.0f, -4.1f),
            Vec2(12.0f, 3.5f),
            Vec2(-5.0f, 3.5f),
        )

        val lines = listOf(
            RoadLineSegment(Vec2(0f, -11.0f), Vec2(0f, -4.1f), isCenterLine = true),
            RoadLineSegment(Vec2(3.9f, 0f), Vec2(12.0f, 0f), isCenterLine = true),
            RoadLineSegment(Vec2(3.9f, -11.0f), Vec2(3.9f, -4.1f)),
            RoadLineSegment(Vec2(3.9f, -4.1f), Vec2(12.0f, -4.1f)),
        )

        val rightSteerRad = -config.maxSteeringAngleRad * 0.96f
        val rRight = config.sanitizedWheelbaseMeters / tan(abs(rightSteerRad).toDouble()).toFloat()
        val rightTurnStartY = exitLaneY - rRight
        val rightLeadMeters = (rightTurnStartY - initialState.y).coerceAtLeast(0.4f)

        val referencePath = generateTurnReferencePath(
            initialState = initialState,
            straightLeadMeters = rightLeadMeters,
            steerAngleRad = rightSteerRad,
            turnSweepRad = halfPI,
            straightFollowMeters = 1.2f,
            config = config,
            obstacles = obstacles,
            turnLabel = "조향 시작",
        )

        return ScenarioDefinition(
            type = ScenarioType.RIGHT_TURN,
            worldBounds = WorldBounds(minX = -4.2f, maxX = 12.2f, minY = -11.0f, maxY = 5.0f),
            initialState = initialState,
            initialGear = VirtualGearDirection.FORWARD,
            targetZone = targetZone,
            obstacles = obstacles,
            roadPolygons = listOf(vRoadPoly, hRoadPoly),
            roadLines = lines,
            referencePath = referencePath,
            helpStepsKorean = listOf(
                "교차로 모서리 전방까지 직진해 진입 위치를 확인하세요.",
                "차량 앞부분이 교차로에 진입하면 오른쪽으로 조향을 시작합니다.",
                "목표 차로 진입과 함께 핸들을 천천히 풀어 정렬합니다.",
            ),
            keyTakeawaysKorean = takeawaysFor(ScenarioType.RIGHT_TURN),
            noticeKorean = "우회전 시 뒷바퀴는 앞바퀴보다 안쪽 궤적(내륜차)을 지나므로 연석 간격을 확인하세요.",
        )
    }

    // ========================================================================
    // 5. 교차로 좌회전 시뮬레이터
    // ========================================================================
    private fun buildLeftTurnScenario(config: VehicleConfig): ScenarioDefinition {
        val halfPI = (PI / 2.0).toFloat()

        val initialState = VehicleState(
            x = 1.75f,
            y = -8.2f,
            headingRad = halfPI,
            velocityMps = 0f,
            steeringAngleRad = 0f,
        )

        val targetZone = OrientedBox2D(
            id = "left_turn_target",
            label = "좌회전 목표 차로",
            role = ObstacleRole.EGO_VEHICLE,
            center = Vec2(-8.2f, 1.75f),
            halfLength = 3.2f,
            halfWidth = 1.55f,
            angleRad = PI.toFloat(),
        )

        val innerCenterDividerWest = OrientedBox2D(
            id = "center_divider_west",
            label = "좌측 도로 중앙선",
            role = ObstacleRole.ROAD_BOUNDARY,
            center = Vec2(-7.8f, -0.18f),
            halfLength = 3.8f,
            halfWidth = 0.18f,
            angleRad = 0f,
        )

        val northOuterBoundary = OrientedBox2D(
            id = "north_outer_curb",
            label = "교차로 바깥 연석",
            role = ObstacleRole.CURB,
            center = Vec2(-3.0f, 3.95f),
            halfLength = 8.5f,
            halfWidth = 0.25f,
            angleRad = 0f,
        )

        val obstacles = listOf(innerCenterDividerWest, northOuterBoundary)

        val vRoadPoly = listOf(
            Vec2(-3.5f, -11.0f),
            Vec2(3.5f, -11.0f),
            Vec2(3.5f, 3.7f),
            Vec2(-3.5f, 3.7f),
        )
        val hRoadPoly = listOf(
            Vec2(-12.0f, -3.5f),
            Vec2(5.0f, -3.5f),
            Vec2(5.0f, 3.7f),
            Vec2(-12.0f, 3.7f),
        )

        val lines = listOf(
            RoadLineSegment(Vec2(0f, -11.0f), Vec2(0f, -3.5f), isCenterLine = true),
            RoadLineSegment(Vec2(-12.0f, 0f), Vec2(-3.5f, 0f), isCenterLine = true),
            RoadLineSegment(Vec2(-12.0f, 3.7f), Vec2(3.5f, 3.7f)),
        )

        val leftSteerRad = +config.maxSteeringAngleRad * 0.65f
        val rLeft = config.sanitizedWheelbaseMeters / tan(abs(leftSteerRad).toDouble()).toFloat()
        val leftTurnStartY = 1.75f - rLeft
        val leftLeadMeters = (leftTurnStartY - initialState.y).coerceAtLeast(0.5f)

        val referencePath = generateTurnReferencePath(
            initialState = initialState,
            straightLeadMeters = leftLeadMeters,
            steerAngleRad = leftSteerRad,
            turnSweepRad = halfPI,
            straightFollowMeters = 1.5f,
            config = config,
            obstacles = obstacles,
            turnLabel = "조향 시작",
        )

        return ScenarioDefinition(
            type = ScenarioType.LEFT_TURN,
            worldBounds = WorldBounds(minX = -12.2f, maxX = 5.2f, minY = -11.0f, maxY = 5.2f),
            initialState = initialState,
            initialGear = VirtualGearDirection.FORWARD,
            targetZone = targetZone,
            obstacles = obstacles,
            roadPolygons = listOf(vRoadPoly, hRoadPoly),
            roadLines = lines,
            referencePath = referencePath,
            helpStepsKorean = listOf(
                "교차로 안쪽까지 직진해 진입 위치를 확인하세요.",
                "완만하게 왼쪽으로 조향을 시작해 목표 차로로 진입합니다.",
                "회전이 끝나기 직전 핸들을 천천히 풀어 정렬합니다.",
            ),
            keyTakeawaysKorean = takeawaysFor(ScenarioType.LEFT_TURN),
            noticeKorean = "교차로 진입 전에 미리 핸들을 꺾으면 좌측 중앙선을 침범하기 쉽습니다.",
        )
    }

    private fun generateTurnReferencePath(
        initialState: VehicleState,
        straightLeadMeters: Float,
        steerAngleRad: Float,
        turnSweepRad: Float,
        straightFollowMeters: Float,
        config: VehicleConfig,
        obstacles: List<OrientedBox2D>,
        turnLabel: String,
    ): ReferencePathResult {
        val rearPts = ArrayList<Vec2>()
        val bodyPts = ArrayList<Vec2>()
        val waypoints = ArrayList<ReferencePathWaypoint>()
        val dt = 0.02f
        val speed = 1.0f

        var sim = initialState.copy(velocityMps = speed, steeringAngleRad = 0f)
        waypoints.add(ReferencePathWaypoint(Vec2(sim.x, sim.y), 0, "01 진입 확인"))

        var remainingLead = straightLeadMeters / speed
        while (remainingLead > 1e-5f) {
            val stepDt = remainingLead.coerceAtMost(dt)
            val fp = VehicleGeometry.computeFootprint(sim, config)
            rearPts.add(fp.rearAxleCenter)
            bodyPts.add(fp.bodyCenter)
            if (CollisionDetector.checkVehicleCollision(sim, config, obstacles).isColliding) {
                return ReferencePathResult.Unavailable()
            }
            sim = KinematicBicycleModel.integrateSingleSubstep(sim, stepDt, config)
            remainingLead -= stepDt
        }

        waypoints.add(ReferencePathWaypoint(Vec2(sim.x, sim.y), rearPts.size, "02 $turnLabel"))
        val yawRate = abs((speed / config.sanitizedWheelbaseMeters) * tan(steerAngleRad.toDouble()).toFloat())
        var remainingTurn = turnSweepRad / yawRate.coerceAtLeast(1e-4f)
        sim = sim.copy(steeringAngleRad = steerAngleRad)
        while (remainingTurn > 1e-5f) {
            val stepDt = remainingTurn.coerceAtMost(dt)
            val fp = VehicleGeometry.computeFootprint(sim, config)
            rearPts.add(fp.rearAxleCenter)
            bodyPts.add(fp.bodyCenter)
            if (CollisionDetector.checkVehicleCollision(sim, config, obstacles).isColliding) {
                return ReferencePathResult.Unavailable()
            }
            sim = KinematicBicycleModel.integrateSingleSubstep(sim, stepDt, config)
            remainingTurn -= stepDt
        }

        waypoints.add(ReferencePathWaypoint(Vec2(sim.x, sim.y), rearPts.size, "03 조향 복귀"))
        sim = sim.copy(steeringAngleRad = 0f)
        var remainingFollow = straightFollowMeters / speed
        while (remainingFollow > 1e-5f) {
            val stepDt = remainingFollow.coerceAtMost(dt)
            val fp = VehicleGeometry.computeFootprint(sim, config)
            rearPts.add(fp.rearAxleCenter)
            bodyPts.add(fp.bodyCenter)
            if (CollisionDetector.checkVehicleCollision(sim, config, obstacles).isColliding) {
                return ReferencePathResult.Unavailable()
            }
            sim = KinematicBicycleModel.integrateSingleSubstep(sim, stepDt, config)
            remainingFollow -= stepDt
        }

        val finalState = sim.copy(velocityMps = 0f, steeringAngleRad = 0f)
        return ReferencePathResult.Available(
            rearAxlePoints = rearPts,
            bodyCenterPoints = bodyPts,
            waypoints = waypoints,
            targetGhostState = finalState,
        )
    }

    // ========================================================================
    // 6. 차폭·차선 감각 시뮬레이터
    // ========================================================================
    private fun buildWidthPerceptionScenario(
        config: VehicleConfig,
        options: ScenarioSpaceOptions,
    ): ScenarioDefinition {
        val halfPI = (PI / 2.0).toFloat()
        val corridorWidth = options.corridorWidthMeters.coerceIn(2.25f, 3.20f)
        val halfCorridor = corridorWidth * 0.5f

        val initialState = VehicleState(
            x = -0.35f,
            y = -7.6f,
            headingRad = halfPI,
            velocityMps = 0f,
            steeringAngleRad = 0f,
        )

        val targetZone = OrientedBox2D(
            id = "width_target",
            label = "통로 통과 목표 구간",
            role = ObstacleRole.EGO_VEHICLE,
            center = Vec2(0f, 5.2f),
            halfLength = 3.0f,
            halfWidth = halfCorridor,
            angleRad = halfPI,
        )

        val leftWall = OrientedBox2D(
            id = "left_corridor_wall",
            label = "좌측 경계 연석",
            role = ObstacleRole.CURB,
            center = Vec2(-halfCorridor - 0.25f, 0.2f),
            halfLength = 6.2f,
            halfWidth = 0.22f,
            angleRad = halfPI,
        )

        val rightWall = OrientedBox2D(
            id = "right_corridor_wall",
            label = "우측 경계 연석",
            role = ObstacleRole.CURB,
            center = Vec2(halfCorridor + 0.25f, 0.2f),
            halfLength = 6.2f,
            halfWidth = 0.22f,
            angleRad = halfPI,
        )

        val obstacles = listOf(leftWall, rightWall)

        val roadPoly = listOf(
            Vec2(-2.6f, -9.5f),
            Vec2(2.6f, -9.5f),
            Vec2(2.6f, 8.8f),
            Vec2(-2.6f, 8.8f),
        )

        val lines = listOf(
            RoadLineSegment(Vec2(0f, -9.0f), Vec2(0f, 8.2f), isDashed = true, isCenterLine = true),
            RoadLineSegment(Vec2(-halfCorridor, -6.0f), Vec2(-halfCorridor, 6.4f), isParkingLine = true),
            RoadLineSegment(Vec2(halfCorridor, -6.0f), Vec2(halfCorridor, 6.4f), isParkingLine = true),
        )

        val rearPts = listOf(
            Vec2(0f, -7.6f),
            Vec2(0f, -3.5f),
            Vec2(0f, 0.5f),
            Vec2(0f, 4.0f),
        )
        val ghostState = VehicleState(
            x = 0f,
            y = 5.2f - config.rearAxleToBodyCenterOffsetMeters,
            headingRad = halfPI,
            velocityMps = 0f,
            steeringAngleRad = 0f,
        )
        val refPath = ReferencePathResult.Available(
            rearAxlePoints = rearPts,
            bodyCenterPoints = rearPts,
            waypoints = listOf(
                ReferencePathWaypoint(Vec2(0f, -5.8f), 0, "01 중앙 보정"),
                ReferencePathWaypoint(Vec2(0f, 0.0f), 2, "02 여유 유지"),
                ReferencePathWaypoint(Vec2(0f, 4.2f), 3, "03 통과 완료"),
            ),
            targetGhostState = ghostState,
        )

        return ScenarioDefinition(
            type = ScenarioType.WIDTH_PERCEPTION,
            worldBounds = WorldBounds(minX = -3.8f, maxX = 3.8f, minY = -9.6f, maxY = 9.0f),
            initialState = initialState,
            initialGear = VirtualGearDirection.FORWARD,
            targetZone = targetZone,
            obstacles = obstacles,
            roadPolygons = listOf(roadPoly),
            roadLines = lines,
            referencePath = refPath,
            helpStepsKorean = listOf(
                "진입 위치를 확인하고 미세 조향으로 통로 중앙에 맞춥니다.",
                "중앙에 들어오면 핸들을 천천히 풀어 0°로 유지합니다.",
                "좌·우 여유 거리를 확인하며 목표 구간에서 정지합니다.",
            ),
            keyTakeawaysKorean = takeawaysFor(ScenarioType.WIDTH_PERCEPTION),
            noticeKorean = "차로 중앙을 맞출 때는 큰 각도 대신 작은 조향 후 즉시 중앙 복귀가 중요합니다.",
            corridorCenterX = 0f,
            corridorHalfWidthMeters = halfCorridor,
        )
    }
}
