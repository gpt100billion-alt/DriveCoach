package com.drivecoach.app.simulation.steering

import com.drivecoach.app.domain.model.GearPosition
import com.drivecoach.app.simulation.vehicle.VehicleConfig
import kotlin.math.PI
import kotlin.math.abs

/**
 * 조향 입력 공급자 유형 (요구사항 5)
 *
 * - PLEOS_VEHICLE_SDK: 기본 입력 장치 (Pleos Vehicle SDK `Vehicle.getSteeringWheel()` 실시간 수신)
 * - DIAGNOSTIC_FALLBACK: 개발자 테스트 전용 진단 입력 (일반 사용자 기본 화면에서는 비활성)
 */
enum class SteeringInputSourceMode(
    val koreanTitle: String,
    val descriptionKorean: String,
) {
    PLEOS_VEHICLE_SDK(
        koreanTitle = "실제 차량 핸들 (Pleos Vehicle SDK)",
        descriptionKorean = "Pleos Vehicle SDK의 SteeringWheel API(PERF_STEERING_ANGLE)로부터 실시간 조향각을 수신합니다.",
    ),
    DIAGNOSTIC_FALLBACK(
        koreanTitle = "개발자 진단용 수동 입력 (테스트 전용)",
        descriptionKorean = "개발자 진단 모드에서만 활성화되는 수동 조향 테스트 입력입니다.",
    ),
}

/**
 * Pleos Vehicle SDK에서 수신하는 조향 신호의 해석 모드 (요구사항 2 & 6)
 *
 * 조사된 사실:
 * 1. Pleos Vehicle SDK 2.3.2 `SteeringWheel.kt` 공식 KDoc:
 *    - `getCurrentSteeringWheelAngle` / `registerSteeringWheelAngle`:
 *      "Gets the current front-wheel steering angle in degrees. Leftward steering angles are negative.
 *       This value does not represent the rotation angle of the steering wheel."
 * 2. 반면 Pleos Connect Virtual Console의 Steering Angle 슬라이더는 `-540° ~ +540°` (예: `0° -> +90° -> 0° -> -90°`) 범위의
 *    스티어링 휠(핸들) 회전각을 `PERF_STEERING_ANGLE` 프로퍼티로 전달합니다.
 *
 * 따라서 DriveCoach는 두 가지 물리적 의미를 혼동하지 않고 명확히 구분하여 처리합니다:
 * - STEERING_WHEEL_ROTATION_WITH_RATIO: 수신값을 스티어링 휠(핸들) 회전각(예: -540°~+540°)으로 해석하고,
 *   시뮬레이션 조향 기어비(기본 15.0:1, 설정 가능)로 나누어 전륜 조향각(δ)을 추정합니다.
 * - DIRECT_ROAD_WHEEL_ANGLE: 수신값을 SDK KDoc 명세 그대로 전륜 타이어 조향각(δ, deg)으로 해석하되,
 *   스티어링 휠 표시 각도는 조향 기어비를 곱해 보조 표시합니다.
 */
enum class SdkAngleInterpretationMode(
    val koreanTitle: String,
    val detailKorean: String,
) {
    STEERING_WHEEL_ROTATION_WITH_RATIO(
        koreanTitle = "핸들 회전각 입력 → 조향 기어비로 전륜각 추정 (Virtual Console -540°~+540° 호환)",
        detailKorean = "수신된 각도를 스티어링 휠 회전각(°)으로 보고 시뮬레이션 조향 기어비(기본 15:1)로 나누어 전륜 조향각을 산출합니다.",
    ),
    DIRECT_ROAD_WHEEL_ANGLE(
        koreanTitle = "전륜 조향각 직접 입력 (SDK KDoc 명세 기준)",
        detailKorean = "수신된 각도를 전륜 타이어 조향각(°)으로 직접 사용하고 차량 최대 전륜 조향각 범위로 클램핑합니다.",
    ),
}

/**
 * 조향 입력 연결 상태 (요구사항 3, 5, 6)
 */
enum class SteeringConnectionState(
    val koreanLabel: String,
    val isReadyForPractice: Boolean,
) {
    INITIALIZING("SDK 초기화 중", false),
    CONNECTED_RECEIVING("차량 핸들 연결됨 (실시간 수신 정상)", true),
    CONNECTED_WAITING_FIRST_SAMPLE("SDK 연결됨 (조향 신호 수신 대기 중)", false),
    PERMISSION_DENIED("권한 거부됨 (CAR_DRIVE 필요)", false),
    UNSUPPORTED_CAPABILITY("조향각 센서 미지원 (Capability 없음)", false),
    SIGNAL_LOST_OR_ERROR("차량 핸들 신호 끊김 / 수신 오류", false),
    DISCONNECTED("차량 핸들 연결 불가", false),
    DIAGNOSTIC_OVERRIDE("개발자 진단 입력 활성 (테스트 전용)", true),
}

/**
 * 조향 방향 표시용 열거형
 */
enum class SteeringDirectionLabel(val koreanText: String) {
    LEFT("좌조향"),
    CENTER("중앙 정렬"),
    RIGHT("우조향"),
}

/**
 * 조향 신호 변환 파이프라인 설정 (요구사항 6)
 */
data class SteeringCalibrationConfig(
    val zeroOffsetDeg: Float = 0f,
    val steeringRatio: Float = 15.0f, // 일반 승용차 시뮬레이션 근사 기어비 (핸들 480° : 전륜 32° = 15:1)
    val deadzoneDeg: Float = 0.35f,
    val invertDirection: Boolean = false,
    val interpretationMode: SdkAngleInterpretationMode = SdkAngleInterpretationMode.STEERING_WHEEL_ROTATION_WITH_RATIO,
    val staleTimeoutMs: Long = 15_000L,
) {
    val sanitizedRatio: Float
        get() = steeringRatio.coerceIn(10.0f, 22.0f)
}

/**
 * 핸들 각도 수신 → 검증 → 영점 보정 → 조향 방향 확인 → 기어비 매핑 → 전륜 조향각 추정 결과 (요구사항 6)
 */
data class ProcessedSteeringFrame(
    val isValid: Boolean,
    val rawSdkAngleDeg: Float? = null,
    val zeroCalibratedAngleDeg: Float = 0f,
    val estimatedSteeringWheelAngleDeg: Float = 0f,
    val estimatedFrontWheelAngleRightPositiveDeg: Float = 0f,
    val physicsSteeringAngleRad: Float = 0f,
    val direction: SteeringDirectionLabel = SteeringDirectionLabel.CENTER,
    val isClampedToMaxWheelAngle: Boolean = false,
    val validationNoteKorean: String = "",
)

/**
 * 실시간 조향 수신 진단 및 안전 상태 스냅샷 (요구사항 3 & 8)
 */
data class SteeringTelemetrySnapshot(
    val sourceMode: SteeringInputSourceMode = SteeringInputSourceMode.PLEOS_VEHICLE_SDK,
    val connectionState: SteeringConnectionState = SteeringConnectionState.INITIALIZING,
    val sdkInitialized: Boolean = false,
    val isListenerRegistered: Boolean = false,
    val permissionName: String = "pleos.car.permission.CAR_DRIVE",
    val isPermissionGranted: Boolean = false,
    val capabilitySupported: Boolean? = null,
    val capabilityAccessTypeText: String = "확인 전",
    val capabilityMinDeg: Float? = null,
    val capabilityMaxDeg: Float? = null,
    val rawSdkAngleDeg: Float? = null,
    val lastReceivedTimestampMs: Long? = null,
    val receivedEventCount: Int = 0,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val currentGear: GearPosition = GearPosition.UNKNOWN,
    val isBrakePressed: Boolean? = null,
    val currentOdometerKm: Float? = null,
    val isVehicleStationaryVerified: Boolean = true,
    val calibration: SteeringCalibrationConfig = SteeringCalibrationConfig(),
    val processedFrame: ProcessedSteeringFrame = ProcessedSteeringFrame(isValid = false, validationNoteKorean = "수신 대기 중"),
) {
    /**
     * 주차(P) 및 정차 안전 조건 충족 여부 (요구사항 8)
     */
    val isParkedAndSafe: Boolean
        get() = currentGear == GearPosition.PARK && isVehicleStationaryVerified

    val safetyStatusReasonKorean: String
        get() = when {
            currentGear == GearPosition.DRIVE || currentGear == GearPosition.REVERSE || currentGear == GearPosition.NEUTRAL ->
                "현재 기어가 ${currentGear.koreanName} 상태입니다. 안전을 위해 P단(주차)에서만 연습할 수 있습니다."
            !isVehicleStationaryVerified ->
                "차량 이동(주행 거리 변화)이 감지되어 연습이 즉시 중지되었습니다."
            currentGear == GearPosition.UNKNOWN ->
                "차량 기어 상태를 확인할 수 없습니다. P단(주차) 상태가 확인되어야 연습할 수 있습니다."
            else -> "P단(주차) 및 정차 상태 확인됨"
        }
}

/**
 * 실제 핸들 각도 수신값 검증·영점 보정·전륜 조향각 변환 파이프라인 (요구사항 6)
 *
 * 단계:
 * 1. 원시 핸들 각도(rawSdkAngleDeg) 유한성 및 허용 범위(-1080° ~ +1080°) 검증
 * 2. 영점 보정 (zeroOffsetDeg 차감) 및 불감대(deadzone) 처리
 * 3. 조향 방향 확인 (SDK 규약: 좌측 음수(-), 우측 양수(+); 필요 시 invertDirection 옵션 지원)
 * 4. 해석 모드 및 시뮬레이션 조향 기어비(steeringRatio, 기본 15:1)를 통해
 *    스티어링 휠 회전각(°)과 추정 전륜 조향각(δ, °)을 분리 계산
 * 5. 차량 최대 전륜 조향각(±maxSteeringAngleDeg, 기본 ±32°) 클램핑 및 물리 엔진 입력 라디안(좌회전 +δ, 우회전 -δ) 산출
 */
object SteeringMappingPipeline {

    private const val MAX_SANE_RAW_ANGLE_ABS_DEG = 1080f

    fun processRawAngle(
        rawSdkAngleDeg: Float?,
        calibration: SteeringCalibrationConfig,
        vehicleConfig: VehicleConfig,
    ): ProcessedSteeringFrame {
        if (rawSdkAngleDeg == null) {
            return ProcessedSteeringFrame(
                isValid = false,
                rawSdkAngleDeg = null,
                validationNoteKorean = "조향각 수신값 없음 (null)",
            )
        }

        if (!rawSdkAngleDeg.isFinite() || abs(rawSdkAngleDeg) > MAX_SANE_RAW_ANGLE_ABS_DEG) {
            return ProcessedSteeringFrame(
                isValid = false,
                rawSdkAngleDeg = rawSdkAngleDeg,
                validationNoteKorean = "비정상 조향각 입력 거부됨 ($rawSdkAngleDeg°)",
            )
        }

        // 1. 영점 보정 및 부호 정규화
        // Pleos Connect Emulator v2.1.0 실측 결과:
        // - Virtual Console에서 우회전(+90°) 입력 시 VHAL PERF_STEERING_ANGLE(rawSdkAngleDeg)은 -90.0°(ISO 8855 반시계 + 규약)를 반환하고,
        // - Virtual Console에서 좌회전(-90°) 입력 시 VHAL PERF_STEERING_ANGLE(rawSdkAngleDeg)은 +90.0°를 반환합니다.
        // 따라서 기본 모드(invertDirection = false)에서는 VHAL 원시값의 부호를 반전(-1)하여
        // 우조향 = 양수(+), 좌조향 = 음수(-) (Virtual Console 표시 및 UI 우조향 양수 규약과 일치)로 정규화합니다.
        val toRightPositiveSign = if (calibration.invertDirection) 1f else -1f
        val calibratedRightPositive = (rawSdkAngleDeg - calibration.zeroOffsetDeg) * toRightPositiveSign
        val deadzonedRightPositive = if (abs(calibratedRightPositive) < calibration.deadzoneDeg) 0f else calibratedRightPositive

        // 2. 핸들 회전각과 전륜 조향각 분리 계산
        val ratio = calibration.sanitizedRatio
        val maxFrontWheelDeg = vehicleConfig.sanitizedMaxSteeringDeg

        val (steeringWheelRightPosDeg, unclampedFrontWheelRightPositiveDeg) = when (calibration.interpretationMode) {
            SdkAngleInterpretationMode.STEERING_WHEEL_ROTATION_WITH_RATIO -> {
                // Virtual Console(-540°~+540°) 및 VHAL(-470°~+500°) 핸들 회전각 기준: 기어비로 나누어 전륜 조향각 추정
                val frontDeg = deadzonedRightPositive / ratio
                deadzonedRightPositive to frontDeg
            }
            SdkAngleInterpretationMode.DIRECT_ROAD_WHEEL_ANGLE -> {
                // SDK KDoc 명세 기준(전륜 조향각 직접 반환): 전륜각 그대로 사용, 핸들 표시각은 기어비 곱으로 추정
                val wheelDeg = deadzonedRightPositive * ratio
                wheelDeg to deadzonedRightPositive
            }
        }

        val isClamped = abs(unclampedFrontWheelRightPositiveDeg) > maxFrontWheelDeg
        val clampedFrontRightPosDeg = unclampedFrontWheelRightPositiveDeg.coerceIn(-maxFrontWheelDeg, maxFrontWheelDeg)

        // 3. 조향 방향 판정 (우조향 > 0, 좌조향 < 0)
        val direction = when {
            clampedFrontRightPosDeg < -0.25f -> SteeringDirectionLabel.LEFT
            clampedFrontRightPosDeg > 0.25f -> SteeringDirectionLabel.RIGHT
            else -> SteeringDirectionLabel.CENTER
        }

        // 4. KinematicBicycleModel 입력용 라디안 변환 (물리 좌표계: 좌회전(CCW) = +δ, 우회전(CW) = -δ)
        val physicsDeltaRad = (-clampedFrontRightPosDeg * PI / 180.0).toFloat()

        return ProcessedSteeringFrame(
            isValid = true,
            rawSdkAngleDeg = rawSdkAngleDeg,
            zeroCalibratedAngleDeg = deadzonedRightPositive,
            estimatedSteeringWheelAngleDeg = steeringWheelRightPosDeg,
            estimatedFrontWheelAngleRightPositiveDeg = clampedFrontRightPosDeg,
            physicsSteeringAngleRad = physicsDeltaRad,
            direction = direction,
            isClampedToMaxWheelAngle = isClamped,
            validationNoteKorean = if (isClamped) {
                "최대 전륜 조향각(±${maxFrontWheelDeg.toInt()}°) 범위로 제한됨 (시뮬레이션 기어비 ${ratio}:1 근사)"
            } else {
                "정상 변환됨 (시뮬레이션 기어비 ${ratio}:1 근사)"
            },
        )
    }
}
