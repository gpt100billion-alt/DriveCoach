package com.drivecoach.app.domain.model

/**
 * 주행 기록 및 세션 상태 머신 (섹션 14)
 */
enum class SessionRecordState(val koreanLabel: String, val description: String) {
    IDLE("대기 중", "주행 기록 또는 시뮬레이션이 시작되지 않은 상태입니다."),
    READY("준비 완료", "시나리오 또는 차량 센서 연결이 준비되어 시작할 수 있습니다."),
    RECORDING("기록/재생 중", "주행 텔레메트리 샘플을 시간 순서대로 수집·재생하고 있습니다."),
    PAUSED("일시정지", "시뮬레이션 또는 데이터 수집이 일시정지된 상태입니다."),
    ANALYZING("분석 중", "수집된 시계열 데이터를 바탕으로 이벤트와 안정성 점수를 계산 중입니다."),
    COMPLETED("분석 완료", "주행 분석과 맞춤형 코칭 리포트 생성이 완료되었습니다."),
    ERROR("오류 발생", "센서 연결 해제 또는 데이터 처리 중 오류가 발생했습니다."),
}

enum class AnalysisStatus(val koreanLabel: String) {
    PENDING("분석 대기"),
    ANALYZED("분석 완료"),
    INSUFFICIENT_DATA("분석 데이터 부족"),
    ERROR("분석 오류"),
}

enum class DrivingEventType(
    val koreanTitle: String,
    val shortBadge: String,
    val categoryKey: String,
) {
    ABRUPT_ACCELERATION_CANDIDATE(
        koreanTitle = "급가속 후보 구간",
        shortBadge = "급가속 후보",
        categoryKey = "ACCELERATION",
    ),
    LATE_HARD_BRAKING_CANDIDATE(
        koreanTitle = "후반 집중·급감속 후보 구간",
        shortBadge = "급감속 후보",
        categoryKey = "BRAKING",
    ),
    LOW_SPEED_STEERING_CORRECTION_CANDIDATE(
        koreanTitle = "저속 회전 수정 조향 후보",
        shortBadge = "수정 조향 후보",
        categoryKey = "STEERING",
    ),
    SPEED_OSCILLATION_CANDIDATE(
        koreanTitle = "불안정한 속도 변화 후보",
        shortBadge = "속도 변동 후보",
        categoryKey = "CONSISTENCY",
    ),
    DATA_GAP_WARNING(
        koreanTitle = "데이터 누락·간격 불규칙 구간",
        shortBadge = "데이터 누락",
        categoryKey = "QUALITY",
    ),
}

enum class SeverityCandidate(val koreanLabel: String) {
    INFO("참고"),
    MODERATE("주의 관찰"),
    HIGH("집중 개선 후보"),
}

/**
 * 감지된 주행 이벤트 후보 (섹션 9.E, 12, 15)
 * 단 한 번의 조작만으로 위험 운전을 단정하지 않으며, 근거 데이터와 판단 규칙을 함께 보관합니다.
 */
data class DrivingEvent(
    val id: String,
    val eventType: DrivingEventType,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val elapsedStartSec: Float,
    val elapsedEndSec: Float,
    val measuredValues: Map<String, String>,
    val detectionRule: String,
    val severityCandidate: SeverityCandidate,
    val confidence: Float,
    val explanation: String,
    val cautionNote: String,
)

/**
 * 세부 항목별 평가 결과 (가속 안정성, 제동 안정성, 조향 안정성, 주행 일관성)
 */
data class MetricEvaluation(
    val metricKey: String,
    val metricName: String,
    val score: Int?,
    val weight: Float,
    val isAvailable: Boolean,
    val confidence: Float,
    val statusLabel: String,
    val interpretation: String,
    val deductionReasons: List<String> = emptyList(),
)

/**
 * 데이터 품질 평가 리포트 (섹션 15.6)
 */
data class DataQualityReport(
    val totalSamples: Int,
    val validSamples: Int,
    val missingSampleCount: Int,
    val duplicateTimestampCount: Int,
    val irregularIntervalCount: Int,
    val unrealisticValueCount: Int,
    val hasSpeedData: Boolean,
    val hasRoadWheelAngleData: Boolean,
    val hasSteeringWheelAngleData: Boolean,
    val hasBrakeStatusData: Boolean,
    val overallReliability: Float,
    val summaryText: String,
    val isSufficientForScoring: Boolean,
)

/**
 * 7단계 구조화 개인 맞춤형 코칭 카드 (섹션 10)
 */
data class CoachingInsight(
    val id: String,
    val title: String,
    val priorityLabel: String,
    val observedPattern: String,
    val evidenceData: String,
    val judgmentRule: String,
    val cautionNote: String,
    val improvementDirection: String,
    val nextDriveChecklist: List<String>,
    val recommendedTopicId: String? = null,
    val engineTypeLabel: String = "규칙 기반 설명형 분석 엔진 v1.0 (생성형 AI 미사용)",
)

/**
 * 설정 가능한 운전 점수 가중치 (섹션 16)
 * 개발 초기 가설 가중치이며 공인 안전도 지표가 아님을 명시합니다.
 */
data class ScoringWeights(
    val accelerationWeight: Float = 0.25f,
    val brakingWeight: Float = 0.35f,
    val steeringWeight: Float = 0.30f,
    val consistencyWeight: Float = 0.10f,
) {
    val totalWeight: Float
        get() = accelerationWeight + brakingWeight + steeringWeight + consistencyWeight
}

/**
 * 주행 세션 종합 분석 결과 (섹션 12, 16)
 */
data class DrivingAnalysisResult(
    val sessionId: String,
    val overallScore: Int?,
    val scoreGradeLabel: String,
    val accelerationEval: MetricEvaluation,
    val brakingEval: MetricEvaluation,
    val steeringEval: MetricEvaluation,
    val consistencyEval: MetricEvaluation,
    val availableMetrics: Set<String>,
    val dataQuality: DataQualityReport,
    val coachingInsights: List<CoachingInsight>,
    val recommendedEducationId: String?,
    val overallSummary: String,
    val scoreExplanation: String,
    val appliedWeights: ScoringWeights = ScoringWeights(),
    val analysisVersion: String = "rule-engine-1.0.0",
)

/**
 * 주행 세션 전체 기록 (섹션 12, 23)
 */
data class DrivingSession(
    val id: String,
    val title: String,
    val subtitle: String,
    val scenarioId: String? = null,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationSec: Int,
    val distanceKm: Float,
    val averageSpeedKph: Float,
    val maximumSpeedKph: Float,
    val telemetrySamples: List<TelemetrySample>,
    val detectedEvents: List<DrivingEvent>,
    val analysisStatus: AnalysisStatus,
    val analysisResult: DrivingAnalysisResult?,
    val dataSource: DataSourceType,
)

/**
 * 개인 운전 습관 목표 (섹션 36.3)
 */
data class PersonalHabitGoal(
    val id: String,
    val title: String,
    val description: String,
    val targetMetricKey: String,
    val targetScore: Int,
    val isEnabled: Boolean = true,
)
