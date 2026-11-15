package com.drivecoach.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivecoach.app.data.local.DriveCoachLocalStore
import com.drivecoach.app.data.simulation.DemoScenarioCatalog
import com.drivecoach.app.data.telemetry.DemoTelemetryProvider
import com.drivecoach.app.data.telemetry.ReplayTelemetryProvider
import com.drivecoach.app.data.telemetry.TelemetryProvider
import com.drivecoach.app.data.vehicle.PleosSdkDiagnosticState
import com.drivecoach.app.data.vehicle.PleosTelemetryProvider
import com.drivecoach.app.domain.analysis.DrivingAnalysisEngine
import com.drivecoach.app.domain.analysis.EducationCatalog
import com.drivecoach.app.domain.analysis.MultiSessionTrendSummary
import com.drivecoach.app.domain.analysis.RuleBasedCoachingGenerator
import com.drivecoach.app.domain.model.AnalysisStatus
import com.drivecoach.app.domain.model.CoachingInsight
import com.drivecoach.app.domain.model.DataSourceType
import com.drivecoach.app.domain.model.DrivingEvent
import com.drivecoach.app.domain.model.DrivingSession
import com.drivecoach.app.domain.model.GearPosition
import com.drivecoach.app.domain.model.PersonalHabitGoal
import com.drivecoach.app.domain.model.ScoringWeights
import com.drivecoach.app.domain.model.SessionRecordState
import com.drivecoach.app.domain.model.TelemetrySample
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DriveCoachDestination(
    val koreanTitle: String,
    val shortLabel: String,
) {
    DASHBOARD("메인 대시보드", "대시보드"),
    SIMULATION("시뮬레이션 재생", "시뮬레이션"),
    REPORT_DETAIL("주행 상세 분석", "상세 분석"),
    HISTORY("주행 기록 · 비교", "주행 기록"),
    COACHING("맞춤형 코칭 · 목표", "AI 코칭"),
    EDUCATION("운전 교육 센터", "운전 교육"),
    SETTINGS("환경 설정", "설정"),
    DIAGNOSTICS("기술 진단 (SDK)", "SDK 진단"),
}

data class SimulationPlaybackState(
    val selectedScenarioId: String = "smooth_driver",
    val replaySessionId: String? = null,
    val recordState: SessionRecordState = SessionRecordState.READY,
    val playbackSpeedMultiplier: Int = 2,
    val totalSamplesCount: Int = 61,
    val currentSampleIndex: Int = 0,
    val progressiveSamples: List<TelemetrySample> = emptyList(),
    val progressiveEvents: List<DrivingEvent> = emptyList(),
    val currentSample: TelemetrySample? = null,
    val lastCompletedSessionId: String? = null,
    val statusMessage: String = "시나리오를 선택하고 재생을 시작하세요 (주차 상태 전용).",
)

data class DriveCoachAppUiState(
    val currentDestination: DriveCoachDestination = DriveCoachDestination.DASHBOARD,
    val navigationBackStack: List<DriveCoachDestination> = emptyList(),
    val sessions: List<DrivingSession> = emptyList(),
    val selectedSessionId: String? = null,
    val compareBaseSessionId: String? = null,
    val compareTargetSessionId: String? = null,
    val selectedEventId: String? = null,
    val selectedEducationTopicId: String = EducationCatalog.topics.first().id,
    val completedEducationIds: Set<String> = emptySet(),
    val personalGoals: List<PersonalHabitGoal> = emptyList(),
    val scoringWeights: ScoringWeights = ScoringWeights(),
    val preferredDataSource: DataSourceType = DataSourceType.DEMO,
    val trendSummaries: List<MultiSessionTrendSummary> = emptyList(),
    val personalTrendInsights: List<CoachingInsight> = emptyList(),
    val simulationState: SimulationPlaybackState = SimulationPlaybackState(),
    val isSimulatedDrivingRestrictionActive: Boolean = false,
    val lastUpdatedTimeText: String = "방금 업데이트됨",
    val snackbarMessage: String? = null,
) {
    val selectedSession: DrivingSession?
        get() = sessions.find { it.id == selectedSessionId } ?: sessions.firstOrNull()

    val previousSessionForSelected: DrivingSession?
        get() {
            val current = selectedSession ?: return null
            val sorted = sessions.sortedByDescending { it.startTimeMs }
            val idx = sorted.indexOfFirst { it.id == current.id }
            return if (idx >= 0 && idx + 1 < sorted.size) sorted[idx + 1] else null
        }
}

class DriveCoachViewModel(application: Application) : AndroidViewModel(application) {

    private val localStore = DriveCoachLocalStore(application)
    private val pleosProvider = PleosTelemetryProvider(application)

    private val _uiState = MutableStateFlow(DriveCoachAppUiState())
    val uiState: StateFlow<DriveCoachAppUiState> = _uiState.asStateFlow()

    val pleosDiagnosticState: StateFlow<PleosSdkDiagnosticState> = pleosProvider.diagnosticState

    private var playbackJob: Job? = null

    init {
        loadInitialData()
        pleosProvider.initializeAndProbeCapabilities()
    }

    private fun loadInitialData() {
        val weights = localStore.loadScoringWeights()
        val sessions = localStore.loadAllSessions(weights)
        val completedEdu = localStore.loadCompletedEducationIds()
        val goals = localStore.loadPersonalGoals()
        val preferredSource = localStore.loadPreferredDataSourceMode()
        val firstSession = sessions.firstOrNull()
        val secondSession = sessions.getOrNull(1)

        val defaultScenarioSamples = DemoScenarioCatalog.generateSamples("smooth_driver")

        _uiState.update {
            it.copy(
                sessions = sessions,
                selectedSessionId = firstSession?.id,
                compareBaseSessionId = secondSession?.id ?: firstSession?.id,
                compareTargetSessionId = firstSession?.id,
                completedEducationIds = completedEdu,
                personalGoals = goals,
                scoringWeights = weights,
                preferredDataSource = preferredSource,
                trendSummaries = RuleBasedCoachingGenerator.computeTrendSummaries(sessions),
                personalTrendInsights = RuleBasedCoachingGenerator.generatePersonalTrendCoaching(sessions),
                simulationState = SimulationPlaybackState(
                    selectedScenarioId = "smooth_driver",
                    recordState = SessionRecordState.READY,
                    totalSamplesCount = defaultScenarioSamples.size,
                    currentSampleIndex = 0,
                    progressiveSamples = defaultScenarioSamples.take(1),
                    currentSample = defaultScenarioSamples.firstOrNull(),
                ),
            )
        }
    }

    fun navigateTo(destination: DriveCoachDestination) {
        _uiState.update { state ->
            if (state.currentDestination == destination) return@update state
            state.copy(
                navigationBackStack = (state.navigationBackStack + state.currentDestination).takeLast(8),
                currentDestination = destination,
            )
        }
    }

    fun navigateBack(): Boolean {
        val stack = _uiState.value.navigationBackStack
        if (stack.isEmpty()) {
            if (_uiState.value.currentDestination != DriveCoachDestination.DASHBOARD) {
                _uiState.update { it.copy(currentDestination = DriveCoachDestination.DASHBOARD) }
                return true
            }
            return false
        }
        val prev = stack.last()
        _uiState.update {
            it.copy(
                currentDestination = prev,
                navigationBackStack = stack.dropLast(1),
            )
        }
        return true
    }

    fun openSessionReport(sessionId: String) {
        _uiState.update { state ->
            state.copy(
                selectedSessionId = sessionId,
                selectedEventId = null,
                navigationBackStack = (state.navigationBackStack + state.currentDestination).takeLast(8),
                currentDestination = DriveCoachDestination.REPORT_DETAIL,
            )
        }
    }

    fun openEducationTopic(topicId: String) {
        _uiState.update { state ->
            state.copy(
                selectedEducationTopicId = topicId,
                navigationBackStack = (state.navigationBackStack + state.currentDestination).takeLast(8),
                currentDestination = DriveCoachDestination.EDUCATION,
            )
        }
    }

    fun selectEventInReport(eventId: String?) {
        _uiState.update { it.copy(selectedEventId = eventId) }
    }

    fun selectComparisonSessions(baseId: String, targetId: String) {
        _uiState.update {
            it.copy(
                compareBaseSessionId = baseId,
                compareTargetSessionId = targetId,
            )
        }
    }

    fun toggleSimulatedDrivingRestriction() {
        val next = !_uiState.value.isSimulatedDrivingRestrictionActive
        if (next) {
            pauseSimulation()
        }
        _uiState.update {
            it.copy(
                isSimulatedDrivingRestrictionActive = next,
                snackbarMessage = if (next) {
                    "주행 안전 제한 모드가 활성화되었습니다. 상세 리포트와 시뮬레이션이 제한됩니다."
                } else {
                    "주차(P) 상태로 전환되어 모든 분석 리포트와 교육을 이용할 수 있습니다."
                },
            )
        }
    }

    // ======================== 시뮬레이션 및 리플레이 제어 ========================

    fun selectScenario(scenarioId: String) {
        playbackJob?.cancel()
        val samples = DemoScenarioCatalog.generateSamples(
            scenarioId = scenarioId,
            baseTimestampMs = System.currentTimeMillis(),
            dataSource = DataSourceType.SIMULATED,
        )
        val spec = DemoScenarioCatalog.findSpec(scenarioId)
        _uiState.update { state ->
            state.copy(
                simulationState = SimulationPlaybackState(
                    selectedScenarioId = scenarioId,
                    replaySessionId = null,
                    recordState = SessionRecordState.READY,
                    playbackSpeedMultiplier = state.simulationState.playbackSpeedMultiplier,
                    totalSamplesCount = samples.size,
                    currentSampleIndex = 0,
                    progressiveSamples = samples.take(1),
                    progressiveEvents = emptyList(),
                    currentSample = samples.firstOrNull(),
                    statusMessage = "'${spec.title}' 시나리오 준비 완료 (${spec.durationSec}초 시뮬레이션)",
                )
            )
        }
    }

    fun startReplayForSession(sessionId: String) {
        val session = _uiState.value.sessions.find { it.id == sessionId } ?: return
        playbackJob?.cancel()
        val provider = ReplayTelemetryProvider(session)
        val samples = provider.getTimelineSamples()
        if (samples.isEmpty()) return

        _uiState.update { state ->
            state.copy(
                currentDestination = DriveCoachDestination.SIMULATION,
                navigationBackStack = (state.navigationBackStack + state.currentDestination).takeLast(8),
                simulationState = SimulationPlaybackState(
                    selectedScenarioId = session.scenarioId ?: "smooth_driver",
                    replaySessionId = session.id,
                    recordState = SessionRecordState.READY,
                    playbackSpeedMultiplier = state.simulationState.playbackSpeedMultiplier,
                    totalSamplesCount = samples.size,
                    currentSampleIndex = 0,
                    progressiveSamples = samples.take(1),
                    progressiveEvents = emptyList(),
                    currentSample = samples.firstOrNull(),
                    statusMessage = "'${session.title}' 저장 기록 리플레이 준비 완료",
                ),
            )
        }
        startOrResumeSimulation()
    }

    fun setPlaybackSpeedMultiplier(multiplier: Int) {
        _uiState.update { state ->
            state.copy(
                simulationState = state.simulationState.copy(
                    playbackSpeedMultiplier = multiplier.coerceIn(1, 10)
                )
            )
        }
    }

    fun startOrResumeSimulation() {
        val currentState = _uiState.value
        val isLiveRestricted = pleosDiagnosticState.value.currentGear == GearPosition.DRIVE ||
            pleosDiagnosticState.value.currentGear == GearPosition.REVERSE
        if (currentState.isSimulatedDrivingRestrictionActive || isLiveRestricted) {
            _uiState.update {
                it.copy(snackbarMessage = "안전 정책에 따라 차량 주차(P) 상태에서만 시뮬레이션을 재생할 수 있습니다.")
            }
            return
        }

        playbackJob?.cancel()

        val sim = currentState.simulationState
        val provider: TelemetryProvider = if (sim.replaySessionId != null) {
            val session = currentState.sessions.find { it.id == sim.replaySessionId }
            if (session != null) {
                ReplayTelemetryProvider(session)
            } else {
                DemoTelemetryProvider(sim.selectedScenarioId)
            }
        } else {
            DemoTelemetryProvider(sim.selectedScenarioId)
        }

        val fullTimeline = provider.getTimelineSamples()
        if (fullTimeline.isEmpty()) {
            _uiState.update { state ->
                state.copy(
                    simulationState = state.simulationState.copy(
                        recordState = SessionRecordState.ERROR,
                        statusMessage = "재생할 샘플 데이터가 없습니다.",
                    )
                )
            }
            return
        }

        val startIndex = if (sim.recordState == SessionRecordState.COMPLETED ||
            sim.currentSampleIndex >= fullTimeline.lastIndex
        ) {
            0
        } else {
            sim.currentSampleIndex
        }

        _uiState.update { state ->
            state.copy(
                simulationState = state.simulationState.copy(
                    recordState = SessionRecordState.RECORDING,
                    totalSamplesCount = fullTimeline.size,
                    currentSampleIndex = startIndex,
                    statusMessage = "${provider.providerName} 시뮬레이션 재생 중...",
                )
            )
        }

        playbackJob = viewModelScope.launch {
            var idx = startIndex
            while (idx < fullTimeline.size) {
                val slice = fullTimeline.subList(0, idx + 1)
                val (preprocessed, _) = DrivingAnalysisEngine.preprocessAndEvaluateQuality(slice)
                val partialEvents = DrivingAnalysisEngine.detectEvents("sim_live", preprocessed)
                val latestSample = preprocessed.lastOrNull()

                _uiState.update { state ->
                    state.copy(
                        simulationState = state.simulationState.copy(
                            currentSampleIndex = idx,
                            progressiveSamples = preprocessed,
                            progressiveEvents = partialEvents,
                            currentSample = latestSample,
                        )
                    )
                }

                idx++
                if (idx < fullTimeline.size) {
                    val mult = _uiState.value.simulationState.playbackSpeedMultiplier.coerceAtLeast(1)
                    delay((350L / mult).coerceAtLeast(60L))
                }
            }

            finalizeSimulationAndAnalyze(fullTimeline)
        }
    }

    fun pauseSimulation() {
        playbackJob?.cancel()
        _uiState.update { state ->
            if (state.simulationState.recordState == SessionRecordState.RECORDING) {
                state.copy(
                    simulationState = state.simulationState.copy(
                        recordState = SessionRecordState.PAUSED,
                        statusMessage = "시뮬레이션 일시정지됨",
                    )
                )
            } else {
                state
            }
        }
    }

    fun restartSimulation() {
        playbackJob?.cancel()
        val scenarioId = _uiState.value.simulationState.selectedScenarioId
        selectScenario(scenarioId)
        startOrResumeSimulation()
    }

    fun completeSimulationImmediately() {
        playbackJob?.cancel()
        val sim = _uiState.value.simulationState
        val samples = DemoScenarioCatalog.generateSamples(
            scenarioId = sim.selectedScenarioId,
            baseTimestampMs = System.currentTimeMillis() - 60_000L,
            dataSource = DataSourceType.SIMULATED,
        )
        viewModelScope.launch {
            finalizeSimulationAndAnalyze(samples)
        }
    }

    private fun finalizeSimulationAndAnalyze(fullSamples: List<TelemetrySample>) {
        _uiState.update { state ->
            state.copy(
                simulationState = state.simulationState.copy(
                    recordState = SessionRecordState.ANALYZING,
                    statusMessage = "시뮬레이션 주행 데이터 분석 및 점수 산출 중...",
                )
            )
        }

        val sim = _uiState.value.simulationState
        val spec = DemoScenarioCatalog.findSpec(sim.selectedScenarioId)
        val now = System.currentTimeMillis()
        val sessionId = "sim_${sim.selectedScenarioId}_$now"

        val rawSession = DrivingSession(
            id = sessionId,
            title = "${spec.title} (시뮬레이션)",
            subtitle = spec.subtitle,
            scenarioId = spec.id,
            startTimeMs = fullSamples.first().timestampMs,
            endTimeMs = fullSamples.last().timestampMs,
            durationSec = spec.durationSec,
            distanceKm = (fullSamples.last().tripDistanceMeters ?: 0f) / 1000f,
            averageSpeedKph = 0f,
            maximumSpeedKph = 0f,
            telemetrySamples = fullSamples,
            detectedEvents = emptyList(),
            analysisStatus = AnalysisStatus.PENDING,
            analysisResult = null,
            dataSource = DataSourceType.SIMULATED,
        )

        val analyzedSession = DrivingAnalysisEngine.analyzeSession(
            session = rawSession,
            weights = _uiState.value.scoringWeights,
        )

        localStore.saveSession(analyzedSession)
        val updatedSessions = localStore.loadAllSessions(_uiState.value.scoringWeights)

        _uiState.update { state ->
            state.copy(
                sessions = updatedSessions,
                selectedSessionId = analyzedSession.id,
                compareBaseSessionId = state.selectedSessionId ?: analyzedSession.id,
                compareTargetSessionId = analyzedSession.id,
                trendSummaries = RuleBasedCoachingGenerator.computeTrendSummaries(updatedSessions),
                personalTrendInsights = RuleBasedCoachingGenerator.generatePersonalTrendCoaching(updatedSessions),
                simulationState = state.simulationState.copy(
                    recordState = SessionRecordState.COMPLETED,
                    currentSampleIndex = fullSamples.lastIndex,
                    progressiveSamples = analyzedSession.telemetrySamples,
                    progressiveEvents = analyzedSession.detectedEvents,
                    currentSample = analyzedSession.telemetrySamples.lastOrNull(),
                    lastCompletedSessionId = analyzedSession.id,
                    statusMessage = "분석 완료! 종합 점수 ${analyzedSession.analysisResult?.overallScore ?: "-"}점 · 리포트에 저장되었습니다.",
                ),
                currentDestination = DriveCoachDestination.REPORT_DETAIL,
                navigationBackStack = (state.navigationBackStack + DriveCoachDestination.SIMULATION).takeLast(8),
                snackbarMessage = "'${spec.title}' 시뮬레이션 분석이 완료되어 상세 리포트로 이동했습니다.",
            )
        }
    }

    // ======================== 설정, 가중치, 데이터 관리 ========================

    fun updateScoringWeights(weights: ScoringWeights) {
        localStore.saveScoringWeights(weights)
        val reanalyzed = localStore.loadAllSessions(weights)
        _uiState.update { state ->
            state.copy(
                scoringWeights = weights,
                sessions = reanalyzed,
                trendSummaries = RuleBasedCoachingGenerator.computeTrendSummaries(reanalyzed),
                personalTrendInsights = RuleBasedCoachingGenerator.generatePersonalTrendCoaching(reanalyzed),
                snackbarMessage = "점수 가중치가 업데이트되어 전체 주행 기록에 재적용되었습니다.",
            )
        }
    }

    fun updatePreferredDataSource(source: DataSourceType) {
        localStore.savePreferredDataSourceMode(source)
        if (source == DataSourceType.LIVE) {
            pleosProvider.initializeAndProbeCapabilities()
            pleosProvider.startLiveListening()
        }
        _uiState.update {
            it.copy(
                preferredDataSource = source,
                snackbarMessage = "기본 데이터 출처 모드가 '${source.koreanTitle}'(으)로 설정되었습니다.",
            )
        }
    }

    fun toggleEducationCompleted(topicId: String) {
        val updated = localStore.toggleEducationCompleted(topicId)
        _uiState.update {
            it.copy(
                completedEducationIds = updated,
                snackbarMessage = if (topicId in updated) "학습 완료로 표시되었습니다." else "학습 완료 표시가 해제되었습니다.",
            )
        }
    }

    fun togglePersonalGoal(goalId: String) {
        val updated = localStore.togglePersonalGoal(goalId)
        _uiState.update { it.copy(personalGoals = updated) }
    }

    fun deleteSession(sessionId: String) {
        localStore.deleteSession(sessionId)
        val updated = localStore.loadAllSessions(_uiState.value.scoringWeights)
        _uiState.update { state ->
            val nextSelected = if (state.selectedSessionId == sessionId) {
                updated.firstOrNull()?.id
            } else {
                state.selectedSessionId
            }
            state.copy(
                sessions = updated,
                selectedSessionId = nextSelected,
                trendSummaries = RuleBasedCoachingGenerator.computeTrendSummaries(updated),
                personalTrendInsights = RuleBasedCoachingGenerator.generatePersonalTrendCoaching(updated),
                snackbarMessage = "선택한 주행 기록이 삭제되었습니다.",
            )
        }
    }

    fun deleteAllSessions() {
        localStore.deleteAllSessions()
        _uiState.update { state ->
            state.copy(
                sessions = emptyList(),
                selectedSessionId = null,
                compareBaseSessionId = null,
                compareTargetSessionId = null,
                trendSummaries = RuleBasedCoachingGenerator.computeTrendSummaries(emptyList()),
                personalTrendInsights = emptyList(),
                snackbarMessage = "모든 주행 기록이 기기에서 삭제되었습니다.",
            )
        }
    }

    fun resetDemoData() {
        val restored = localStore.resetDemoSessions(_uiState.value.scoringWeights)
        val first = restored.firstOrNull()
        val second = restored.getOrNull(1)
        _uiState.update { state ->
            state.copy(
                sessions = restored,
                selectedSessionId = first?.id,
                compareBaseSessionId = second?.id ?: first?.id,
                compareTargetSessionId = first?.id,
                trendSummaries = RuleBasedCoachingGenerator.computeTrendSummaries(restored),
                personalTrendInsights = RuleBasedCoachingGenerator.generatePersonalTrendCoaching(restored),
                snackbarMessage = "5개 기본 데모 시나리오 주행 기록이 복원되었습니다.",
            )
        }
    }

    fun resetSettings() {
        localStore.resetSettingsToDefault()
        val defaultWeights = localStore.loadScoringWeights()
        val reanalyzed = localStore.loadAllSessions(defaultWeights)
        _uiState.update { state ->
            state.copy(
                scoringWeights = defaultWeights,
                preferredDataSource = localStore.loadPreferredDataSourceMode(),
                personalGoals = localStore.loadPersonalGoals(),
                sessions = reanalyzed,
                trendSummaries = RuleBasedCoachingGenerator.computeTrendSummaries(reanalyzed),
                personalTrendInsights = RuleBasedCoachingGenerator.generatePersonalTrendCoaching(reanalyzed),
                snackbarMessage = "사용자 설정과 가중치가 기본값으로 초기화되었습니다.",
            )
        }
    }

    // ======================== Pleos SDK 진단 화면 액션 ========================

    fun refreshPleosDiagnostics() {
        pleosProvider.initializeAndProbeCapabilities()
        _uiState.update {
            it.copy(snackbarMessage = "Pleos Vehicle SDK (2.3.2) 센서 가용성 및 권한 재검증을 요청했습니다.")
        }
    }

    fun togglePleosLiveListening() {
        if (pleosDiagnosticState.value.isListening) {
            pleosProvider.stopLiveListening()
        } else {
            pleosProvider.startLiveListening()
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        pleosProvider.release()
    }
}
