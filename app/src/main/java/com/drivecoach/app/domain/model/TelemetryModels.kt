package com.drivecoach.app.domain.model

/**
 * 데이터 출처 상태 구분 (섹션 3.1 사실과 데모를 구분)
 * - LIVE: 실제 허용된 차량 API에서 수집한 데이터
 * - SIMULATED: Virtual Console 또는 실시간 데모 시뮬레이션 재생 데이터
 * - DEMO: 사전에 생성한 가상 주행 시나리오 기록
 * - UNAVAILABLE: 센서 접근 불가 (SDK 미제공 또는 시스템 권한 미지원)
 * - ERROR: 연결 또는 수집 오류
 */
enum class DataSourceType(
    val badgeText: String,
    val koreanTitle: String,
    val description: String,
) {
    LIVE(
        badgeText = "LIVE",
        koreanTitle = "실제 차량 센서 데이터",
        description = "Pleos Vehicle SDK 및 허용된 차량 API에서 수집한 실제 데이터입니다.",
    ),
    SIMULATED(
        badgeText = "SIMULATED",
        koreanTitle = "시뮬레이션 재생 데이터",
        description = "데모 시나리오 재생기 또는 Virtual Console에서 생성된 시뮬레이션 데이터입니다.",
    ),
    DEMO(
        badgeText = "DEMO",
        koreanTitle = "사전 생성 데모 데이터",
        description = "앱 기능 검증 및 공모전 시연을 위해 고정 규칙으로 생성된 가상 주행 기록입니다.",
    ),
    UNAVAILABLE(
        badgeText = "UNAVAILABLE",
        koreanTitle = "센서 접근 불가",
        description = "현재 환경 또는 서드파티 앱 권한으로 조회할 수 없는 차량 데이터입니다.",
    ),
    ERROR(
        badgeText = "ERROR",
        koreanTitle = "연결/수집 오류",
        description = "차량 서비스 바인딩 실패 또는 데이터 수신 오류가 발생했습니다.",
    ),
}

enum class DataQualityFlag(val label: String) {
    VALID("정상 샘플"),
    IRREGULAR_INTERVAL("불규칙 샘플링 간격"),
    DUPLICATE_TIMESTAMP("중복 타임스탬프"),
    UNREALISTIC_VALUE("물리적 비현실 수치 감지"),
    MISSING_SPEED("속도 값 결측"),
    MISSING_STEERING("조향각 결측"),
    ESTIMATED_ACCELERATION("속도 미분 추정 가속도"),
    SIMULATED_SOURCE("시뮬레이션/데모 값"),
}

enum class GearPosition(val shortCode: String, val koreanName: String) {
    PARK("P", "주차 (P)"),
    REVERSE("R", "후진 (R)"),
    NEUTRAL("N", "중립 (N)"),
    DRIVE("D", "주행 (D)"),
    LOW("L", "저단 (L)"),
    UNKNOWN("-", "미확인"),
}

enum class TurnSignalState(val koreanName: String) {
    OFF("꺼짐"),
    LEFT("좌측 점멸"),
    RIGHT("우측 점멸"),
    HAZARD("비상등"),
    UNKNOWN("미확인"),
}

/**
 * 단일 시점의 주행 텔레메트리 샘플 (섹션 12)
 *
 * 주의:
 * - [steeringWheelAngleDeg](운전대 핸들 회전각, 보통 -540°~+540°)와
 *   [roadWheelAngleDeg](전륜 타이어 조향각, 보통 -38°~+38°, Pleos SDK PERF_STEERING_ANGLE 제공값)는
 *   서로 다른 물리량이므로 명확히 분리하여 관리합니다.
 * - [longitudinalAccelerationMps2]는 실제 가속도 센서가 없는 경우 속도 변화량(Δv/Δt)으로 추정한 값이며,
 *   [isAccelerationEstimated] 플래그로 센서 직측값과 미분 추정값을 구분합니다.
 */
data class TelemetrySample(
    val timestampMs: Long,
    val speedKph: Float? = null,
    val longitudinalAccelerationMps2: Float? = null,
    val isAccelerationEstimated: Boolean = true,
    val brakePedalPercent: Float? = null,
    val brakePedalPressed: Boolean? = null,
    val acceleratorPedalPercent: Float? = null,
    val steeringWheelAngleDeg: Float? = null,
    val roadWheelAngleDeg: Float? = null,
    val odometerKm: Float? = null,
    val tripDistanceMeters: Float? = null,
    val gearPosition: GearPosition = GearPosition.UNKNOWN,
    val turnSignalState: TurnSignalState = TurnSignalState.UNKNOWN,
    val dataSource: DataSourceType = DataSourceType.DEMO,
    val qualityFlags: Set<DataQualityFlag> = setOf(DataQualityFlag.VALID),
) {
    /**
     * 조향 분석에 사용할 대표 각도 (전륜 조향각 우선, 없을 경우 핸들 조향각/15 기어비 환산 참고값)
     */
    val effectiveRoadWheelAngleDeg: Float?
        get() = roadWheelAngleDeg ?: steeringWheelAngleDeg?.let { it / 15.0f }
}

/**
 * 차량 센서별 접근 가능성 및 진단 정보 (섹션 12, 20, 26)
 */
data class SensorAvailability(
    val sensorKey: String,
    val sensorName: String,
    val sdkDomainAndMethod: String,
    val availabilityStatus: DataSourceType,
    val capabilityAccessType: String,
    val requiredPermission: String,
    val permissionGranted: Boolean,
    val lastUpdatedMs: Long? = null,
    val sampleValueText: String = "수신 대기 중",
    val failureReason: String? = null,
    val usableForAnalysis: Boolean = false,
    val notes: String = "",
)
