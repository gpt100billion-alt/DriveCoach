package com.drivecoach.app.data.telemetry

import com.drivecoach.app.data.simulation.DemoScenarioCatalog
import com.drivecoach.app.domain.model.DataSourceType
import com.drivecoach.app.domain.model.DrivingSession
import com.drivecoach.app.domain.model.TelemetrySample

/**
 * 데이터 공급자 추상화 인터페이스 (섹션 13)
 * UI와 분석 로직이 데이터 공급자 종류(데모 시나리오, 저장 세션 리플레이, 실차 Pleos SDK)에
 * 종속되지 않도록 분리합니다.
 */
interface TelemetryProvider {
    val providerId: String
    val providerName: String
    val dataSourceType: DataSourceType

    /**
     * 지정된 전체 시퀀스 샘플을 가져옵니다 (시뮬레이션/리플레이용).
     */
    fun getTimelineSamples(): List<TelemetrySample>
}

/**
 * 실제 차량 데이터 없이도 5개 표준 시나리오를 시연할 수 있는 데모 공급자
 */
class DemoTelemetryProvider(
    private val scenarioId: String,
    private val startTimestampMs: Long = System.currentTimeMillis(),
) : TelemetryProvider {

    override val providerId: String = "demo_$scenarioId"
    override val providerName: String = DemoScenarioCatalog.findSpec(scenarioId).title
    override val dataSourceType: DataSourceType = DataSourceType.SIMULATED

    private val cachedSamples: List<TelemetrySample> by lazy {
        DemoScenarioCatalog.generateSamples(
            scenarioId = scenarioId,
            baseTimestampMs = startTimestampMs,
            dataSource = DataSourceType.SIMULATED,
        )
    }

    override fun getTimelineSamples(): List<TelemetrySample> = cachedSamples
}

/**
 * 저장된 과거 주행 세션 데이터를 시간 순서대로 다시 재생하는 리플레이 공급자
 */
class ReplayTelemetryProvider(
    private val session: DrivingSession,
) : TelemetryProvider {

    override val providerId: String = "replay_${session.id}"
    override val providerName: String = "${session.title} (저장 기록 재생)"
    override val dataSourceType: DataSourceType = DataSourceType.SIMULATED

    override fun getTimelineSamples(): List<TelemetrySample> =
        session.telemetrySamples.sortedBy { it.timestampMs }
}
