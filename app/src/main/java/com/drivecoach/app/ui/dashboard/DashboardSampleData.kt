package com.drivecoach.app.ui.dashboard

/**
 * 주의: 이 파일의 모든 데이터는 1단계 대시보드 UI 구성을 위한 샘플 데이터입니다.
 * 실제 차량 센서, 실시간 운전 제어, 외부 서버와는 아직 연결되어 있지 않습니다.
 */

enum class StabilityMetricType {
    STEERING,
    ACCELERATION,
    BRAKING
}

data class StabilityMetric(
    val type: StabilityMetricType,
    val title: String,
    val score: Int,
    val statusLabel: String,
    val description: String
)

data class DrivingReport(
    val id: String,
    val dateTimeLabel: String,
    val routeTitle: String,
    val distanceLabel: String,
    val durationLabel: String,
    val score: Int,
    val summaryNote: String
)

data class DashboardSampleUiState(
    val isSampleMode: Boolean = true,
    val overallScore: Int = 85,
    val scoreGrade: String = "안정 주행",
    val aiSummary: String = "조향과 제동이 전반적으로 부드럽고 안정적입니다. 완만한 가속을 유지하면 더 높은 점수를 받을 수 있습니다.",
    val stabilityMetrics: List<StabilityMetric> = emptyList(),
    val recentReports: List<DrivingReport> = emptyList()
)

object DashboardSampleData {
    val defaultState = DashboardSampleUiState(
        isSampleMode = true,
        overallScore = 85,
        scoreGrade = "안정 주행",
        aiSummary = "조향과 제동 리듬이 전반적으로 안정적입니다. 급가속 구간을 줄이면 90점 이상을 달성할 수 있습니다.",
        stabilityMetrics = listOf(
            StabilityMetric(
                type = StabilityMetricType.STEERING,
                title = "조향 안정성",
                score = 88,
                statusLabel = "매우 안정",
                description = "차선 유지와 코너링 시 핸들 조작이 일정합니다."
            ),
            StabilityMetric(
                type = StabilityMetricType.ACCELERATION,
                title = "가속 안정성",
                score = 82,
                statusLabel = "양호",
                description = "대체로 부드럽게 출발하나 합류 구간 가속이 다소 높습니다."
            ),
            StabilityMetric(
                type = StabilityMetricType.BRAKING,
                title = "제동 안정성",
                score = 85,
                statusLabel = "안정",
                description = "충분한 차간 거리를 확보하며 여유 있게 감속했습니다."
            )
        ),
        recentReports = listOf(
            DrivingReport(
                id = "sample-trip-1",
                dateTimeLabel = "오늘 · 08:40",
                routeTitle = "도심 출근길 주행",
                distanceLabel = "18.4 km",
                durationLabel = "34분",
                score = 85,
                summaryNote = "정체 구간에서도 급제동 없이 일정한 감속 패턴을 유지했습니다."
            ),
            DrivingReport(
                id = "sample-trip-2",
                dateTimeLabel = "어제 · 19:15",
                routeTitle = "야간 고속도로 주행",
                distanceLabel = "42.1 km",
                durationLabel = "48분",
                score = 89,
                summaryNote = "정속 주행 비율이 높고 차선 유지 안정성이 매우 우수했습니다."
            ),
            DrivingReport(
                id = "sample-trip-3",
                dateTimeLabel = "11월 12일 · 14:20",
                routeTitle = "주말 교외 국도 주행",
                distanceLabel = "27.6 km",
                durationLabel = "41분",
                score = 81,
                summaryNote = "연속 커브 구간에서 조향은 안정적이었으나 재가속이 다소 빨랐습니다."
            )
        )
    )
}
