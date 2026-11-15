package com.drivecoach.app.domain.analysis

import com.drivecoach.app.domain.model.CoachingInsight
import com.drivecoach.app.domain.model.DataQualityReport
import com.drivecoach.app.domain.model.DrivingEvent
import com.drivecoach.app.domain.model.DrivingEventType
import com.drivecoach.app.domain.model.DrivingSession
import com.drivecoach.app.domain.model.MetricEvaluation
import kotlin.math.roundToInt

/**
 * 코칭 생성기 인터페이스 (섹션 3.5 & 10)
 * 현재는 100% 설명 가능한 규칙 기반 코칭 엔진으로 동작하며,
 * 향후 온디바이스 LLM 또는 외부 AI 모델을 안전하게 플러그인할 수 있도록 분리되어 있습니다.
 */
interface CoachingGenerator {
    val engineDescription: String

    fun generateForSession(
        sessionId: String,
        overallScore: Int,
        accelEval: MetricEvaluation,
        brakeEval: MetricEvaluation,
        steerEval: MetricEvaluation,
        consistencyEval: MetricEvaluation,
        events: List<DrivingEvent>,
        qualityReport: DataQualityReport,
    ): List<CoachingInsight>

    fun generateForInsufficientData(
        qualityReport: DataQualityReport,
    ): List<CoachingInsight>

    fun generatePersonalTrendCoaching(
        recentSessions: List<DrivingSession>,
    ): List<CoachingInsight>
}

data class MultiSessionTrendSummary(
    val windowLabel: String,
    val sessionCount: Int,
    val averageOverallScore: Int?,
    val averageAccelScore: Int?,
    val averageBrakeScore: Int?,
    val averageSteerScore: Int?,
    val scoreDeltaFromFirstToLast: Int?,
    val dominantHabitText: String,
)

object RuleBasedCoachingGenerator : CoachingGenerator {

    override val engineDescription: String =
        "규칙 기반 설명형 분석 엔진 v1.0 (외부 생성형 AI 미호출 · 판단 규칙과 센서 근거 100% 공개)"

    override fun generateForInsufficientData(
        qualityReport: DataQualityReport,
    ): List<CoachingInsight> {
        return listOf(
            CoachingInsight(
                id = "insufficient_data_notice",
                title = "충분한 주행 데이터 확보 필요",
                priorityLabel = "데이터 품질 안내",
                observedPattern = "유효 샘플 수(${qualityReport.validSamples}개) 또는 주행 시간/속도 정보가 부족하여 운전 습관을 판단하지 않았습니다.",
                evidenceData = qualityReport.summaryText,
                judgmentRule = "최소 유효 샘플 5개 이상, 주행 시간 5초 이상, 데이터 신뢰도 35% 이상 조건 미충족",
                cautionNote = "DriveCoach는 데이터가 부족할 때 임의의 점수로 운전자를 평가하지 않습니다.",
                improvementDirection = "시뮬레이션 화면에서 데모 시나리오를 끝까지 재생하거나 차량 센서 연결 상태를 확인해 주세요.",
                nextDriveChecklist = listOf(
                    "최소 30초 이상의 연속 주행 데이터 수집하기",
                    "개발자 진단 화면에서 센서 가용성 확인하기",
                ),
                recommendedTopicId = "smooth_braking",
                engineTypeLabel = engineDescription,
            )
        )
    }

    override fun generateForSession(
        sessionId: String,
        overallScore: Int,
        accelEval: MetricEvaluation,
        brakeEval: MetricEvaluation,
        steerEval: MetricEvaluation,
        consistencyEval: MetricEvaluation,
        events: List<DrivingEvent>,
        qualityReport: DataQualityReport,
    ): List<CoachingInsight> {
        val insights = mutableListOf<CoachingInsight>()

        val brakeEvents = events.filter { it.eventType == DrivingEventType.LATE_HARD_BRAKING_CANDIDATE }
        val accelEvents = events.filter { it.eventType == DrivingEventType.ABRUPT_ACCELERATION_CANDIDATE }
        val steerEvents = events.filter { it.eventType == DrivingEventType.LOW_SPEED_STEERING_CORRECTION_CANDIDATE }

        // 1. 감속·제동 코칭
        if (brakeEvents.isNotEmpty() || (brakeEval.score != null && brakeEval.score < 85)) {
            val worstDecel = brakeEvents.mapNotNull { it.measuredValues["최대 추정 감속도"] }.firstOrNull() ?: "-2.4 m/s² 이하"
            insights.add(
                CoachingInsight(
                    id = "${sessionId}_coach_brake",
                    title = "감속 시작 시점 여유 확보 (예측 감속)",
                    priorityLabel = if (brakeEvents.size >= 2) "최우선 개선 과제" else "권장 코칭",
                    observedPattern = "최근 분석된 일반 감속 구간 중 ${brakeEvents.size}곳에서 정차 직전 후반부에 감속이 집중되는 패턴이 관찰되었습니다.",
                    evidenceData = "후반 집중·급감속 후보 ${brakeEvents.size}건 감지, 최대 추정 감속도 $worstDecel, 제동 안정성 ${brakeEval.score ?: "-"}점",
                    judgmentRule = "속도 미분 추정 종방향 감속도 ≤ -2.35 m/s² 구간이 1.6초 이상 지속되거나 정차 직전 급감속 발생",
                    cautionNote = "갑작스러운 보행자 출현이나 앞차 급정거 등 긴급제동 상황에서는 강하게 브레이크를 밟는 것이 올바른 안전 조작입니다. 본 코칭은 예측 가능한 일반 정지 구간에만 적용하세요.",
                    improvementDirection = "전방 신호등이나 정체 구간이 보이면 평소보다 2~3초 먼저 가속 페달에서 발을 떼어 초기 감속을 시작한 뒤 브레이크 답력을 완만하게 유지해 보세요.",
                    nextDriveChecklist = listOf(
                        "정지선 50~80m 전방에서 미리 가속 페달 오프(타력 주행) 시작하기",
                        "정차 직전 마지막 2초 동안 감속 충격이 커지지 않는지 체감하기",
                    ),
                    recommendedTopicId = "smooth_braking",
                    engineTypeLabel = engineDescription,
                )
            )
        }

        // 2. 가속 조절 코칭
        if (accelEvents.isNotEmpty() || (accelEval.score != null && accelEval.score < 85)) {
            val peakAccel = accelEvents.mapNotNull { it.measuredValues["최대 추정 가속도"] }.firstOrNull() ?: "2.3 m/s² 이상"
            insights.add(
                CoachingInsight(
                    id = "${sessionId}_coach_accel",
                    title = "출발 및 재가속 초기 페달 완만화",
                    priorityLabel = if (insights.isEmpty()) "최우선 개선 과제" else "보조 코칭",
                    observedPattern = "출발 직후 또는 재가속 구간에서 짧은 시간 동안 속도가 급격히 상승하는 패턴이 ${accelEvents.size}회 관찰되었습니다.",
                    evidenceData = "급가속 후보 ${accelEvents.size}건 감지, 최대 추정 가속도 $peakAccel, 가속 안정성 ${accelEval.score ?: "-"}점",
                    judgmentRule = "종방향 추정 가속도 ≥ 2.25 m/s² 구간이 1.6초 이상 연속 유지",
                    cautionNote = "고속도로 진입로 합류나 위험 회피를 위한 가속은 안전상 필요한 조작일 수 있습니다.",
                    improvementDirection = "정지 상태에서 출발할 때 첫 2초 동안 가속 페달을 서서히 밟아 차량 하중 이동을 부드럽게 만든 후 목표 속도로 올려보세요.",
                    nextDriveChecklist = listOf(
                        "출발 후 첫 2초간 페달을 일정한 속도로 깊어지게 밟기",
                        "목표 속도 도달 전 가속 페달을 살짝 늦추어 관성 연결하기",
                    ),
                    recommendedTopicId = "smooth_acceleration",
                    engineTypeLabel = engineDescription,
                )
            )
        }

        // 3. 저속 회전 조향 코칭
        if (steerEvents.isNotEmpty() || (steerEval.isAvailable && (steerEval.score ?: 100) < 85)) {
            val sampleReversal = steerEvents.firstOrNull()?.measuredValues?.get("조향 방향 반전") ?: "다수 반전"
            val sampleTurn = steerEvents.firstOrNull()?.measuredValues?.get("회전 방향") ?: "저속 회전 구간"
            insights.add(
                CoachingInsight(
                    id = "${sessionId}_coach_steer",
                    title = "저속 교차로 진입 속도 감속 및 회전 궤적 안정화",
                    priorityLabel = if (insights.isEmpty()) "최우선 개선 과제" else "조향 습관 코칭",
                    observedPattern = "$sampleTurn 등에서 한 번의 조향 궤적으로 돌아나가지 못하고 핸들을 여러 번 고쳐 잡는 수정 조향 패턴(${steerEvents.size}건)이 관찰되었습니다.",
                    evidenceData = "저속 수정 조향 후보 ${steerEvents.size}건 ($sampleReversal), 조향 안정성 ${steerEval.score ?: "-"}점",
                    judgmentRule = "저속(6~40km/h) 회전 중 전륜 조향각 ≥ 4° 구간에서 조향 증감 부호 반전 3회 이상",
                    cautionNote = "차로 폭, 보행자, 불법 주정차 장애물에 따라 실제 도로에서는 수정 조향이 정당한 경우가 많으며, 모든 교차로에 고정된 정답 핸들 각도는 존재하지 않습니다.",
                    improvementDirection = "회전 진입 전에 충분히 감속(15~20km/h 내외)하고 시선을 출구 차로 중앙에 미리 두면 회전 도중 불필요한 조향 수정을 줄일 수 있습니다.",
                    nextDriveChecklist = listOf(
                        "교차로 진입 전 직선 구간에서 감속을 먼저 완료하기",
                        "회전 중에는 핸들 각도를 급격히 흔들지 않고 출구 차선 끝을 바라보기",
                    ),
                    recommendedTopicId = "low_speed_left_turn",
                    engineTypeLabel = engineDescription,
                )
            )
        }

        // 4. 전반적으로 우수한 주행인 경우 긍정 강화 코칭 제공
        if (insights.isEmpty()) {
            insights.add(
                CoachingInsight(
                    id = "${sessionId}_coach_smooth",
                    title = "안정적인 가감속·조향 리듬 유지",
                    priorityLabel = "우수 습관 유지",
                    observedPattern = "이번 주행에서는 급가속, 후반 집중 감속, 반복 수정 조향 후보가 관찰되지 않고 안정적인 속도 프로필이 유지되었습니다.",
                    evidenceData = "종합 점수 ${overallScore}점, 가속 ${accelEval.score ?: "-"}점, 제동 ${brakeEval.score ?: "-"}점, 조향 ${steerEval.score ?: "미측정"}점, 일관성 ${consistencyEval.score ?: "-"}점",
                    judgmentRule = "모든 활성 안정성 지표 ≥ 85점 및 고위험 조작 후보 0건",
                    cautionNote = "안정적인 점수가 모든 도로 위험의 부재를 의미하지는 않으므로 교차로 및 비보호 구간에서의 방어운전 원칙을 함께 유지하세요.",
                    improvementDirection = "현재의 여유 있는 차간 거리와 예측 감속 습관을 유지하면서, 비보호 좌회전 및 교차로 시야 확보 학습 콘텐츠를 복습해 보세요.",
                    nextDriveChecklist = listOf(
                        "현재와 같은 예측 감속 시작 타이밍 유지하기",
                        "교차로 진입 전 좌·우·전방 보행자 확인 습관 이어가기",
                    ),
                    recommendedTopicId = "unprotected_left_intersection",
                    engineTypeLabel = engineDescription,
                )
            )
        }

        return insights
    }

    override fun generatePersonalTrendCoaching(
        recentSessions: List<DrivingSession>,
    ): List<CoachingInsight> {
        val analyzed = recentSessions
            .filter { it.analysisResult?.overallScore != null }
            .sortedByDescending { it.startTimeMs }

        if (analyzed.isEmpty()) return emptyList()

        val latest = analyzed.first()
        val previous = analyzed.getOrNull(1)
        val recent3 = analyzed.take(3)

        val totalBrakeEvents = recent3.sumOf { s ->
            s.detectedEvents.count { it.eventType == DrivingEventType.LATE_HARD_BRAKING_CANDIDATE }
        }
        val totalAccelEvents = recent3.sumOf { s ->
            s.detectedEvents.count { it.eventType == DrivingEventType.ABRUPT_ACCELERATION_CANDIDATE }
        }
        val totalSteerEvents = recent3.sumOf { s ->
            s.detectedEvents.count { it.eventType == DrivingEventType.LOW_SPEED_STEERING_CORRECTION_CANDIDATE }
        }

        val deltaText = if (previous != null) {
            val currScore = latest.analysisResult?.overallScore ?: 0
            val prevScore = previous.analysisResult?.overallScore ?: 0
            val diff = currScore - prevScore
            when {
                diff > 0 -> "직전 주행 대비 종합 점수가 +${diff}점 향상(${prevScore}점 → ${currScore}점)되었습니다."
                diff < 0 -> "직전 주행 대비 종합 점수가 ${diff}점 변화(${prevScore}점 → ${currScore}점)했습니다."
                else -> "직전 주행과 동일한 종합 점수(${currScore}점)를 유지했습니다."
            }
        } else {
            "현재 1건의 분석 주행 기록이 존재합니다."
        }

        return listOf(
            CoachingInsight(
                id = "personal_trend_summary",
                title = "나의 최근 주행 습관 변화 요약 (어제의 나 vs 오늘의 나)",
                priorityLabel = "장기 습관 추적",
                observedPattern = "$deltaText 최근 ${recent3.size}회 주행에서 급감속 후보 ${totalBrakeEvents}건, 급가속 후보 ${totalAccelEvents}건, 저속 수정 조향 후보 ${totalSteerEvents}건이 기록되었습니다.",
                evidenceData = "최근 ${recent3.size}회 평균 점수 ${
                    recent3.mapNotNull { it.analysisResult?.overallScore }.average().roundToInt()
                }점 (타 운전자 랭킹 비교 없이 오직 사용자 본인의 이전 기록과 비교)",
                judgmentRule = "최근 3·5·10회 세션의 항목별 점수 추이 및 반복 발생 이벤트 빈도 비교",
                cautionNote = "주행 경로(도심 정체 vs 고속도로)와 데이터 출처(데모 vs 실측)가 다를 경우 단순 점수 차이만으로 실력을 단정하지 마세요.",
                improvementDirection = when {
                    totalBrakeEvents >= totalAccelEvents && totalBrakeEvents >= totalSteerEvents && totalBrakeEvents > 0 ->
                        "최근 주행에서 후반 감속 패턴이 가장 자주 관찰되었습니다. 다음 주행에서는 정지 예측 구간에서 2초 먼저 가속 페달에서 발을 떼는 연습에 집중해 보세요."
                    totalAccelEvents >= totalSteerEvents && totalAccelEvents > 0 ->
                        "출발 및 합류 구간에서 빠른 가속 패턴이 반복되었습니다. 출발 초기 2초간 페달을 부드럽게 밟는 습관을 의식해 보세요."
                    totalSteerEvents > 0 ->
                        "저속 회전 구간에서 수정 조향이 관찰되었습니다. 회전 진입 전 감속을 먼저 마치는 연습이 효과적입니다."
                    else ->
                        "최근 주행 전반에서 매우 일관되고 부드러운 조작 습관을 유지하고 있습니다."
                },
                nextDriveChecklist = listOf(
                    "주행 전 나의 개인 목표 1가지를 먼저 확인하기",
                    "주행 종료(P단 주차) 후 직전 주행과 세부 안정성 점수 변화 비교하기",
                ),
                recommendedTopicId = latest.analysisResult?.recommendedEducationId ?: "smooth_braking",
                engineTypeLabel = engineDescription,
            )
        )
    }

    fun computeTrendSummaries(sessions: List<DrivingSession>): List<MultiSessionTrendSummary> {
        val analyzed = sessions
            .filter { it.analysisResult?.overallScore != null }
            .sortedByDescending { it.startTimeMs }

        return listOf(3, 5, 10).map { windowSize ->
            val slice = analyzed.take(windowSize)
            if (slice.isEmpty()) {
                MultiSessionTrendSummary(
                    windowLabel = "최근 ${windowSize}회",
                    sessionCount = 0,
                    averageOverallScore = null,
                    averageAccelScore = null,
                    averageBrakeScore = null,
                    averageSteerScore = null,
                    scoreDeltaFromFirstToLast = null,
                    dominantHabitText = "분석된 기록 없음",
                )
            } else {
                val overallAvg = slice.mapNotNull { it.analysisResult?.overallScore }.average().roundToInt()
                val accelAvg = slice.mapNotNull { it.analysisResult?.accelerationEval?.score }
                    .takeIf { it.isNotEmpty() }?.average()?.roundToInt()
                val brakeAvg = slice.mapNotNull { it.analysisResult?.brakingEval?.score }
                    .takeIf { it.isNotEmpty() }?.average()?.roundToInt()
                val steerAvg = slice.mapNotNull { it.analysisResult?.steeringEval?.score }
                    .takeIf { it.isNotEmpty() }?.average()?.roundToInt()

                val newestScore = slice.first().analysisResult?.overallScore ?: overallAvg
                val oldestScore = slice.last().analysisResult?.overallScore ?: overallAvg
                val delta = newestScore - oldestScore

                val dominant = when {
                    (brakeAvg ?: 100) <= (accelAvg ?: 100) && (brakeAvg ?: 100) < 84 -> "후반 감속 완만화 필요"
                    (accelAvg ?: 100) < 84 -> "초기 가속 완만화 필요"
                    (steerAvg ?: 100) < 84 -> "저속 회전 궤적 안정화 필요"
                    else -> "안정적인 주행 리듬 유지 중"
                }

                MultiSessionTrendSummary(
                    windowLabel = "최근 ${windowSize}회 (실제 ${slice.size}건)",
                    sessionCount = slice.size,
                    averageOverallScore = overallAvg,
                    averageAccelScore = accelAvg,
                    averageBrakeScore = brakeAvg,
                    averageSteerScore = steerAvg,
                    scoreDeltaFromFirstToLast = delta,
                    dominantHabitText = dominant,
                )
            }
        }
    }
}
