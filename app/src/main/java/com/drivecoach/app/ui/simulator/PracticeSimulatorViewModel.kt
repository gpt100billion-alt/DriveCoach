package com.drivecoach.app.ui.simulator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivecoach.app.data.vehicle.PleosSdkDiagnosticState
import com.drivecoach.app.data.vehicle.PleosTelemetryProvider
import com.drivecoach.app.domain.model.GearPosition
import com.drivecoach.app.simulation.collision.CollisionDetector
import com.drivecoach.app.simulation.collision.CollisionResult
import com.drivecoach.app.simulation.collision.EvaluationStatus
import com.drivecoach.app.simulation.collision.ParkingAndTurnEvaluator
import com.drivecoach.app.simulation.collision.PredictedTrajectory
import com.drivecoach.app.simulation.collision.ScenarioEvaluationResult
import com.drivecoach.app.simulation.collision.TrajectoryPredictor
import com.drivecoach.app.simulation.geometry.Vec2
import com.drivecoach.app.simulation.geometry.VehicleFootprint
import com.drivecoach.app.simulation.geometry.VehicleGeometry
import com.drivecoach.app.simulation.scenarios.LiveSteeringGuidance
import com.drivecoach.app.simulation.scenarios.PracticeCategory
import com.drivecoach.app.simulation.scenarios.ScenarioDefinition
import com.drivecoach.app.simulation.scenarios.ScenarioSpaceOptions
import com.drivecoach.app.simulation.scenarios.ScenarioType
import com.drivecoach.app.simulation.scenarios.SimulatorScenarios
import com.drivecoach.app.simulation.steering.DiagnosticSteeringInputProvider
import com.drivecoach.app.simulation.steering.PleosSteeringInputProvider
import com.drivecoach.app.simulation.steering.SteeringCalibrationConfig
import com.drivecoach.app.simulation.steering.SteeringInputProvider
import com.drivecoach.app.simulation.steering.SteeringInputSourceMode
import com.drivecoach.app.simulation.steering.SteeringTelemetrySnapshot
import com.drivecoach.app.simulation.vehicle.VehicleConfig
import com.drivecoach.app.simulation.vehicle.VehicleState
import com.drivecoach.app.simulation.vehicle.VirtualGearDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class SimulatorScreenRoute {
    HOME,
    CATEGORY_LIST,
    PRACTICE_INTRO,
    SIMULATOR,
    PRACTICE_RESULT,
    STEERING_DIAGNOSTICS,
    SETTINGS,
}

data class PracticeSimulatorUiState(
    val currentRoute: SimulatorScreenRoute = SimulatorScreenRoute.HOME,
    val navigationHistory: List<SimulatorScreenRoute> = emptyList(),
    val selectedCategory: PracticeCategory = PracticeCategory.PARKING,
    val activeScenarioType: ScenarioType = ScenarioType.PARALLEL_PARKING,
    val vehicleConfig: VehicleConfig = VehicleConfig(),
    val spaceOptions: ScenarioSpaceOptions = ScenarioSpaceOptions(),
    val scenario: ScenarioDefinition = SimulatorScenarios.buildScenario(ScenarioType.PARALLEL_PARKING),
    val vehicleState: VehicleState = scenario.initialState,
    val footprint: VehicleFootprint = VehicleGeometry.computeFootprint(scenario.initialState, VehicleConfig()),
    val selectedGear: VirtualGearDirection = scenario.initialGear,
    val uiSteeringRightPositiveDeg: Float = 0f,
    val steeringTelemetry: SteeringTelemetrySnapshot = SteeringTelemetrySnapshot(),
    val isHoldMoving: Boolean = false,
    val isLatchMoving: Boolean = false,
    val drivenRearAxlePath: List<Vec2> = listOf(Vec2(scenario.initialState.x, scenario.initialState.y)),
    val predictedTrajectory: PredictedTrajectory = TrajectoryPredictor.predict(
        state = scenario.initialState,
        gearDirection = scenario.initialGear,
        config = VehicleConfig(),
        obstacles = scenario.obstacles,
    ),
    val collisionResult: CollisionResult = CollisionResult(isColliding = false),
    val evaluation: ScenarioEvaluationResult = ParkingAndTurnEvaluator.evaluateParking(
        state = scenario.initialState,
        config = VehicleConfig(),
        targetZone = scenario.targetZone,
        obstacles = scenario.obstacles,
        collisionResult = CollisionResult(isColliding = false),
    ),
    val liveSteeringGuidance: LiveSteeringGuidance = SimulatorScenarios.computeLiveSteeringGuidance(
        scenario = scenario,
        vehicleState = scenario.initialState,
        currentFrontWheelRightPositiveDeg = 0f,
        evaluationStatus = EvaluationStatus.IN_PROGRESS,
        config = VehicleConfig(),
    ),
    val showReferencePath: Boolean = true,
    val showHelpDialog: Boolean = false,
    val showRearAxleReferenceDot: Boolean = false,
    val zoomFactor: Float = 1.0f,
    val isRealVehicleDrivingTestRestriction: Boolean = false,
) {
    val isSteeringReadyForPractice: Boolean
        get() = steeringTelemetry.connectionState.isReadyForPractice &&
            steeringTelemetry.processedFrame.isValid

    val isVehicleParkedAndSafeForPractice: Boolean
        get() = !isRealVehicleDrivingTestRestriction && steeringTelemetry.isParkedAndSafe

    val canMoveVirtualVehicle: Boolean
        get() = isSteeringReadyForPractice &&
            isVehicleParkedAndSafeForPractice &&
            !collisionResult.isColliding &&
            evaluation.status != EvaluationStatus.SUCCESS

    val isMovingActive: Boolean
        get() = (isHoldMoving || isLatchMoving) && canMoveVirtualVehicle
}

class PracticeSimulatorViewModel(application: Application) : AndroidViewModel(application) {

    private val pleosProvider = PleosTelemetryProvider(application)
    val pleosDiagnosticState: StateFlow<PleosSdkDiagnosticState> = pleosProvider.diagnosticState

    private val pleosSteeringProvider = PleosSteeringInputProvider(application)
    private val diagnosticSteeringProvider = DiagnosticSteeringInputProvider()
    private var activeSteeringProvider: SteeringInputProvider = pleosSteeringProvider

    private val _uiState = MutableStateFlow(PracticeSimulatorUiState())
    val uiState: StateFlow<PracticeSimulatorUiState> = _uiState.asStateFlow()

    private var steeringCollectJob: Job? = null
    private var physicsLoopJob: Job? = null
    private var heartbeatJob: Job? = null

    init {
        pleosProvider.initializeAndProbeCapabilities()
        loadScenarioInternal(
            type = ScenarioType.PARALLEL_PARKING,
            config = VehicleConfig(),
            options = ScenarioSpaceOptions(),
            targetRoute = null,
        )
        bindSteeringProvider(pleosSteeringProvider)
        startPhysicsLoop()
        startSteeringHeartbeatLoop()
    }

    // ========================================================================
    // 조향 입력 공급자(SteeringInputProvider) 바인딩 및 실시간 파이프라인 연결
    // ========================================================================

    private fun bindSteeringProvider(provider: SteeringInputProvider) {
        steeringCollectJob?.cancel()
        activeSteeringProvider = provider
        provider.startListening()

        steeringCollectJob = viewModelScope.launch {
            provider.telemetryState.collectLatest { telemetry ->
                applyIncomingSteeringTelemetry(telemetry)
            }
        }
    }

    private fun applyIncomingSteeringTelemetry(telemetry: SteeringTelemetrySnapshot) {
        val current = _uiState.value
        val readyAndValid = telemetry.connectionState.isReadyForPractice && telemetry.processedFrame.isValid
        val safeParked = !current.isRealVehicleDrivingTestRestriction && telemetry.isParkedAndSafe
        val forceStop = !readyAndValid || !safeParked

        val nextDeltaRad = if (readyAndValid) {
            telemetry.processedFrame.physicsSteeringAngleRad
        } else {
            current.vehicleState.steeringAngleRad
        }
        val nextUiSteerDeg = if (readyAndValid) {
            telemetry.processedFrame.estimatedFrontWheelAngleRightPositiveDeg
        } else {
            current.uiSteeringRightPositiveDeg
        }

        val updatedVehicle = current.vehicleState.copy(
            steeringAngleRad = nextDeltaRad,
            velocityMps = if (forceStop) 0f else current.vehicleState.velocityMps,
        )
        val fp = VehicleGeometry.computeFootprint(updatedVehicle, current.vehicleConfig)
        val col = CollisionDetector.checkVehicleCollision(updatedVehicle, current.vehicleConfig, current.scenario.obstacles)
        val pred = TrajectoryPredictor.predict(
            state = updatedVehicle,
            gearDirection = current.selectedGear,
            config = current.vehicleConfig,
            obstacles = current.scenario.obstacles,
        )
        val eval = evaluateCurrentScenario(updatedVehicle, current.vehicleConfig, current.scenario, col)
        val liveCue = SimulatorScenarios.computeLiveSteeringGuidance(
            scenario = current.scenario,
            vehicleState = updatedVehicle,
            currentFrontWheelRightPositiveDeg = nextUiSteerDeg,
            evaluationStatus = eval.status,
            config = current.vehicleConfig,
        )

        _uiState.update { state ->
            state.copy(
                steeringTelemetry = telemetry,
                vehicleState = updatedVehicle,
                footprint = fp,
                uiSteeringRightPositiveDeg = nextUiSteerDeg,
                predictedTrajectory = pred,
                collisionResult = col,
                evaluation = eval,
                liveSteeringGuidance = liveCue,
                isHoldMoving = if (forceStop) false else state.isHoldMoving,
                isLatchMoving = if (forceStop) false else state.isLatchMoving,
            )
        }
    }

    fun switchSteeringInputSourceMode(mode: SteeringInputSourceMode) {
        stopMovementImmediate()
        val currentCal = _uiState.value.steeringTelemetry.calibration
        val currentCfg = _uiState.value.vehicleConfig
        when (mode) {
            SteeringInputSourceMode.PLEOS_VEHICLE_SDK -> {
                diagnosticSteeringProvider.stopListening()
                pleosSteeringProvider.updateCalibrationAndVehicleConfig(currentCal, currentCfg)
                bindSteeringProvider(pleosSteeringProvider)
            }
            SteeringInputSourceMode.DIAGNOSTIC_FALLBACK -> {
                pleosSteeringProvider.stopListening()
                diagnosticSteeringProvider.updateCalibrationAndVehicleConfig(currentCal, currentCfg)
                bindSteeringProvider(diagnosticSteeringProvider)
            }
        }
    }

    fun updateSteeringCalibration(calibration: SteeringCalibrationConfig) {
        activeSteeringProvider.updateCalibrationAndVehicleConfig(
            calibration = calibration,
            vehicleConfig = _uiState.value.vehicleConfig,
        )
    }

    fun calibrateSteeringZeroAtCurrentAngle() {
        activeSteeringProvider.calibrateZeroAtCurrentAngle()
    }

    fun resetSteeringCalibration() {
        updateSteeringCalibration(SteeringCalibrationConfig())
    }

    fun injectDiagnosticSteeringAngle(rawAngleDeg: Float?) {
        if (activeSteeringProvider === diagnosticSteeringProvider) {
            diagnosticSteeringProvider.injectDiagnosticRawAngle(rawAngleDeg)
        }
    }

    // ========================================================================
    // OEM 화면 플로우 내비게이션
    // HOME -> CATEGORY_LIST -> PRACTICE_INTRO -> SIMULATOR -> PRACTICE_RESULT
    // ========================================================================

    fun openHome() {
        stopMovementImmediate()
        _uiState.update {
            it.copy(
                currentRoute = SimulatorScreenRoute.HOME,
                navigationHistory = emptyList(),
                showHelpDialog = false,
            )
        }
    }

    fun selectCategoryFromHome(category: PracticeCategory) {
        stopMovementImmediate()
        _uiState.update { state ->
            state.copy(
                selectedCategory = category,
                currentRoute = SimulatorScreenRoute.CATEGORY_LIST,
                navigationHistory = if (state.currentRoute == SimulatorScreenRoute.CATEGORY_LIST) {
                    state.navigationHistory
                } else {
                    (state.navigationHistory + state.currentRoute).takeLast(6)
                },
            )
        }
    }

    /**
     * 세부 연습 항목을 선택하면 먼저 '연습 준비(PRACTICE_INTRO)' 화면으로 진입합니다.
     */
    fun openPracticeIntro(type: ScenarioType) {
        if (!type.isAvailable) return
        stopMovementImmediate()
        loadScenarioInternal(
            type = type,
            config = _uiState.value.vehicleConfig,
            options = _uiState.value.spaceOptions,
            targetRoute = SimulatorScreenRoute.PRACTICE_INTRO,
        )
    }

    /**
     * 준비 화면에서 '연습 시작' 버튼을 누르면 실제 시뮬레이터 화면으로 진입합니다.
     */
    fun startPracticeFromIntro() {
        stopMovementImmediate()
        loadScenarioInternal(
            type = _uiState.value.activeScenarioType,
            config = _uiState.value.vehicleConfig,
            options = _uiState.value.spaceOptions,
            targetRoute = SimulatorScreenRoute.SIMULATOR,
        )
    }

    /**
     * 시뮬레이터로 즉시 진입할 때 사용합니다.
     */
    fun openScenario(type: ScenarioType) {
        openPracticeIntro(type)
    }

    fun openPracticeResult() {
        stopMovementImmediate()
        _uiState.update { state ->
            if (state.currentRoute == SimulatorScreenRoute.PRACTICE_RESULT) return@update state
            state.copy(
                currentRoute = SimulatorScreenRoute.PRACTICE_RESULT,
                navigationHistory = (state.navigationHistory + state.currentRoute).takeLast(6),
            )
        }
    }

    fun retryFromResult() {
        stopMovementImmediate()
        loadScenarioInternal(
            type = _uiState.value.activeScenarioType,
            config = _uiState.value.vehicleConfig,
            options = _uiState.value.spaceOptions,
            targetRoute = SimulatorScreenRoute.SIMULATOR,
        )
    }

    fun startNextScenario() {
        stopMovementImmediate()
        val available = ScenarioType.entries.filter { it.isAvailable }
        val currentIdx = available.indexOf(_uiState.value.activeScenarioType)
        val nextType = if (currentIdx in available.indices) {
            available[(currentIdx + 1) % available.size]
        } else {
            ScenarioType.PARALLEL_PARKING
        }
        loadScenarioInternal(
            type = nextType,
            config = _uiState.value.vehicleConfig,
            options = _uiState.value.spaceOptions,
            targetRoute = SimulatorScreenRoute.PRACTICE_INTRO,
        )
    }

    fun openSteeringDiagnostics() {
        stopMovementImmediate()
        activeSteeringProvider.refreshOnce()
        _uiState.update { state ->
            if (state.currentRoute == SimulatorScreenRoute.STEERING_DIAGNOSTICS) return@update state
            state.copy(
                currentRoute = SimulatorScreenRoute.STEERING_DIAGNOSTICS,
                navigationHistory = (state.navigationHistory + state.currentRoute).takeLast(6),
            )
        }
    }

    fun openSimulatorDirect() {
        stopMovementImmediate()
        _uiState.update { state ->
            if (state.currentRoute == SimulatorScreenRoute.SIMULATOR) return@update state
            state.copy(
                currentRoute = SimulatorScreenRoute.SIMULATOR,
                navigationHistory = (state.navigationHistory + state.currentRoute).takeLast(6),
            )
        }
    }

    fun openSettings() {
        stopMovementImmediate()
        _uiState.update { state ->
            if (state.currentRoute == SimulatorScreenRoute.SETTINGS) return@update state
            state.copy(
                currentRoute = SimulatorScreenRoute.SETTINGS,
                navigationHistory = (state.navigationHistory + state.currentRoute).takeLast(6),
            )
        }
    }

    fun navigateBack(): Boolean {
        stopMovementImmediate()
        val history = _uiState.value.navigationHistory
        if (history.isEmpty()) {
            if (_uiState.value.currentRoute != SimulatorScreenRoute.HOME) {
                openHome()
                return true
            }
            return false
        }
        val prev = history.last()
        _uiState.update {
            it.copy(
                currentRoute = prev,
                navigationHistory = history.dropLast(1),
                showHelpDialog = false,
            )
        }
        return true
    }

    // ========================================================================
    // 가상 기어 및 이동 제어 (조향은 실제 차량 핸들로만 수신)
    // ========================================================================

    fun selectVirtualGear(gear: VirtualGearDirection) {
        val state = _uiState.value
        val stoppedVehicle = state.vehicleState.copy(velocityMps = 0f)
        _uiState.update {
            it.copy(
                selectedGear = gear,
                isHoldMoving = false,
                isLatchMoving = false,
            )
        }
        recomputeDerivedState(stoppedVehicle, gear, state.uiSteeringRightPositiveDeg)
    }

    fun setHoldMoving(holding: Boolean) {
        val current = _uiState.value
        if (holding && !current.canMoveVirtualVehicle) {
            stopMovementImmediate()
            return
        }
        _uiState.update { state ->
            val active = (holding || state.isLatchMoving) && state.canMoveVirtualVehicle
            val speed = if (active) targetSpeedForGear(state.selectedGear, state.vehicleConfig) else 0f
            val updatedVehicle = state.vehicleState.copy(velocityMps = speed)
            val eval = evaluateCurrentScenario(
                vehicleState = updatedVehicle,
                config = state.vehicleConfig,
                scenario = state.scenario,
                collision = state.collisionResult,
            )
            state.copy(
                isHoldMoving = holding && state.canMoveVirtualVehicle,
                vehicleState = updatedVehicle,
                evaluation = eval,
                liveSteeringGuidance = SimulatorScenarios.computeLiveSteeringGuidance(
                    scenario = state.scenario,
                    vehicleState = updatedVehicle,
                    currentFrontWheelRightPositiveDeg = state.uiSteeringRightPositiveDeg,
                    evaluationStatus = eval.status,
                    config = state.vehicleConfig,
                ),
            )
        }
    }

    fun toggleLatchMoving() {
        val current = _uiState.value
        if (!current.canMoveVirtualVehicle) {
            stopMovementImmediate()
            return
        }
        val nextLatch = !current.isLatchMoving
        _uiState.update { state ->
            val active = (nextLatch || state.isHoldMoving) && state.canMoveVirtualVehicle
            val speed = if (active) targetSpeedForGear(state.selectedGear, state.vehicleConfig) else 0f
            val updatedVehicle = state.vehicleState.copy(velocityMps = speed)
            val eval = evaluateCurrentScenario(
                vehicleState = updatedVehicle,
                config = state.vehicleConfig,
                scenario = state.scenario,
                collision = state.collisionResult,
            )
            state.copy(
                isLatchMoving = nextLatch,
                vehicleState = updatedVehicle,
                evaluation = eval,
                liveSteeringGuidance = SimulatorScenarios.computeLiveSteeringGuidance(
                    scenario = state.scenario,
                    vehicleState = updatedVehicle,
                    currentFrontWheelRightPositiveDeg = state.uiSteeringRightPositiveDeg,
                    evaluationStatus = eval.status,
                    config = state.vehicleConfig,
                ),
            )
        }
    }

    fun stopMovementImmediate() {
        _uiState.update { state ->
            val stoppedVehicle = state.vehicleState.copy(velocityMps = 0f)
            val eval = evaluateCurrentScenario(
                vehicleState = stoppedVehicle,
                config = state.vehicleConfig,
                scenario = state.scenario,
                collision = state.collisionResult,
            )
            state.copy(
                isHoldMoving = false,
                isLatchMoving = false,
                vehicleState = stoppedVehicle,
                evaluation = eval,
                liveSteeringGuidance = SimulatorScenarios.computeLiveSteeringGuidance(
                    scenario = state.scenario,
                    vehicleState = stoppedVehicle,
                    currentFrontWheelRightPositiveDeg = state.uiSteeringRightPositiveDeg,
                    evaluationStatus = eval.status,
                    config = state.vehicleConfig,
                ),
            )
        }
    }

    fun resetCurrentScenario() {
        val s = _uiState.value
        loadScenarioInternal(
            type = s.activeScenarioType,
            config = s.vehicleConfig,
            options = s.spaceOptions,
            targetRoute = null,
        )
    }

    fun toggleShowReferencePath() {
        _uiState.update { it.copy(showReferencePath = !it.showReferencePath) }
    }

    fun toggleHelpDialog(show: Boolean) {
        _uiState.update { it.copy(showHelpDialog = show) }
    }

    fun toggleRearAxleReferenceDot() {
        _uiState.update { it.copy(showRearAxleReferenceDot = !it.showRearAxleReferenceDot) }
    }

    fun adjustZoom(deltaZoom: Float) {
        _uiState.update {
            it.copy(zoomFactor = (it.zoomFactor + deltaZoom).coerceIn(0.75f, 1.85f))
        }
    }

    fun updateVehicleAndSpaceConfig(
        newConfig: VehicleConfig,
        newSpaceOptions: ScenarioSpaceOptions,
    ) {
        activeSteeringProvider.updateCalibrationAndVehicleConfig(
            calibration = _uiState.value.steeringTelemetry.calibration,
            vehicleConfig = newConfig,
        )
        loadScenarioInternal(
            type = _uiState.value.activeScenarioType,
            config = newConfig,
            options = newSpaceOptions,
            targetRoute = null,
        )
    }

    fun toggleDrivingSafetyRestrictionSimulation() {
        stopMovementImmediate()
        _uiState.update {
            it.copy(isRealVehicleDrivingTestRestriction = !it.isRealVehicleDrivingTestRestriction)
        }
    }

    fun refreshPleosSdkState() {
        pleosProvider.initializeAndProbeCapabilities()
        activeSteeringProvider.refreshOnce()
    }

    // ========================================================================
    // 고정 시간 간격 물리 루프 (20ms) 및 조향/안전 상태 하트비트 폴링
    // ========================================================================

    private fun startSteeringHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(1200L)
                if (activeSteeringProvider === pleosSteeringProvider) {
                    pleosSteeringProvider.refreshOnce()
                }
            }
        }
    }

    private fun startPhysicsLoop() {
        physicsLoopJob?.cancel()
        physicsLoopJob = viewModelScope.launch {
            val fixedTickMs = 20L
            val fixedDtSec = 0.02f

            while (isActive) {
                delay(fixedTickMs)
                val current = _uiState.value
                val liveGear = current.steeringTelemetry.currentGear
                val isRestricted = current.isRealVehicleDrivingTestRestriction ||
                    liveGear == GearPosition.DRIVE ||
                    liveGear == GearPosition.REVERSE ||
                    liveGear == GearPosition.NEUTRAL ||
                    !current.steeringTelemetry.isVehicleStationaryVerified

                if (isRestricted && (current.isHoldMoving || current.isLatchMoving)) {
                    stopMovementImmediate()
                    continue
                }

                if (current.currentRoute == SimulatorScreenRoute.SIMULATOR &&
                    current.isMovingActive &&
                    !isRestricted
                ) {
                    advancePhysicsTick(fixedDtSec)
                }
            }
        }
    }

    private fun advancePhysicsTick(dtSec: Float) {
        val state = _uiState.value
        if (!state.canMoveVirtualVehicle) {
            stopMovementImmediate()
            return
        }

        val speed = targetSpeedForGear(state.selectedGear, state.vehicleConfig)
        val movingStartState = state.vehicleState.copy(velocityMps = speed)

        val (nextVehicleState, collision) = CollisionDetector.stepWithContinuousCollisionCheck(
            startState = movingStartState,
            dtSec = dtSec,
            config = state.vehicleConfig,
            obstacles = state.scenario.obstacles,
        )

        val nextFootprint = VehicleGeometry.computeFootprint(nextVehicleState, state.vehicleConfig)
        val currentRearPt = nextFootprint.rearAxleCenter
        val lastPathPt = state.drivenRearAxlePath.lastOrNull()
        val updatedPath = if (lastPathPt == null || lastPathPt.distanceTo(currentRearPt) >= 0.06f) {
            (state.drivenRearAxlePath + currentRearPt).takeLast(600)
        } else {
            state.drivenRearAxlePath
        }

        val predicted = TrajectoryPredictor.predict(
            state = nextVehicleState,
            gearDirection = state.selectedGear,
            config = state.vehicleConfig,
            obstacles = state.scenario.obstacles,
        )

        val eval = evaluateCurrentScenario(
            vehicleState = nextVehicleState,
            config = state.vehicleConfig,
            scenario = state.scenario,
            collision = collision,
        )

        val shouldStop = collision.isColliding || eval.status == EvaluationStatus.SUCCESS
        val finalVehicle = if (shouldStop) nextVehicleState.copy(velocityMps = 0f) else nextVehicleState
        val finalEval = if (shouldStop && !collision.isColliding) {
            evaluateCurrentScenario(
                vehicleState = finalVehicle,
                config = state.vehicleConfig,
                scenario = state.scenario,
                collision = collision,
            )
        } else {
            eval
        }
        val liveCue = SimulatorScenarios.computeLiveSteeringGuidance(
            scenario = state.scenario,
            vehicleState = finalVehicle,
            currentFrontWheelRightPositiveDeg = state.uiSteeringRightPositiveDeg,
            evaluationStatus = finalEval.status,
            config = state.vehicleConfig,
        )

        _uiState.update { prev ->
            prev.copy(
                vehicleState = finalVehicle,
                footprint = nextFootprint,
                drivenRearAxlePath = updatedPath,
                predictedTrajectory = predicted,
                collisionResult = collision,
                evaluation = finalEval,
                liveSteeringGuidance = liveCue,
                isHoldMoving = if (shouldStop) false else prev.isHoldMoving,
                isLatchMoving = if (shouldStop) false else prev.isLatchMoving,
            )
        }
    }

    private fun loadScenarioInternal(
        type: ScenarioType,
        config: VehicleConfig,
        options: ScenarioSpaceOptions,
        targetRoute: SimulatorScreenRoute?,
    ) {
        val currentTelemetry = _uiState.value.steeringTelemetry
        val liveSteerRad = if (currentTelemetry.connectionState.isReadyForPractice && currentTelemetry.processedFrame.isValid) {
            currentTelemetry.processedFrame.physicsSteeringAngleRad
        } else {
            0f
        }
        val liveUiDeg = if (currentTelemetry.connectionState.isReadyForPractice && currentTelemetry.processedFrame.isValid) {
            currentTelemetry.processedFrame.estimatedFrontWheelAngleRightPositiveDeg
        } else {
            0f
        }

        val scenario = SimulatorScenarios.buildScenario(type, config, options)
        val initialVehicle = scenario.initialState.copy(steeringAngleRad = liveSteerRad)
        val fp = VehicleGeometry.computeFootprint(initialVehicle, config)
        val col = CollisionDetector.checkVehicleCollision(initialVehicle, config, scenario.obstacles)
        val pred = TrajectoryPredictor.predict(
            state = initialVehicle,
            gearDirection = scenario.initialGear,
            config = config,
            obstacles = scenario.obstacles,
        )
        val eval = evaluateCurrentScenario(initialVehicle, config, scenario, col)
        val liveCue = SimulatorScenarios.computeLiveSteeringGuidance(
            scenario = scenario,
            vehicleState = initialVehicle,
            currentFrontWheelRightPositiveDeg = liveUiDeg,
            evaluationStatus = eval.status,
            config = config,
        )

        _uiState.update { prev ->
            val nextRoute = targetRoute ?: prev.currentRoute
            val nextHistory = if (targetRoute != null && prev.currentRoute != targetRoute) {
                (prev.navigationHistory + prev.currentRoute).takeLast(6)
            } else {
                prev.navigationHistory
            }
            prev.copy(
                currentRoute = nextRoute,
                navigationHistory = nextHistory,
                selectedCategory = type.category,
                activeScenarioType = type,
                vehicleConfig = config,
                spaceOptions = options,
                scenario = scenario,
                vehicleState = initialVehicle,
                footprint = fp,
                selectedGear = scenario.initialGear,
                uiSteeringRightPositiveDeg = liveUiDeg,
                isHoldMoving = false,
                isLatchMoving = false,
                drivenRearAxlePath = listOf(fp.rearAxleCenter),
                predictedTrajectory = pred,
                collisionResult = col,
                evaluation = eval,
                liveSteeringGuidance = liveCue,
                zoomFactor = 1.0f,
            )
        }
    }

    private fun recomputeDerivedState(
        vehicleState: VehicleState,
        gear: VirtualGearDirection,
        uiSteeringDeg: Float,
    ) {
        val current = _uiState.value
        val fp = VehicleGeometry.computeFootprint(vehicleState, current.vehicleConfig)
        val col = CollisionDetector.checkVehicleCollision(vehicleState, current.vehicleConfig, current.scenario.obstacles)
        val pred = TrajectoryPredictor.predict(
            state = vehicleState,
            gearDirection = gear,
            config = current.vehicleConfig,
            obstacles = current.scenario.obstacles,
        )
        val eval = evaluateCurrentScenario(vehicleState, current.vehicleConfig, current.scenario, col)
        val liveCue = SimulatorScenarios.computeLiveSteeringGuidance(
            scenario = current.scenario,
            vehicleState = vehicleState,
            currentFrontWheelRightPositiveDeg = uiSteeringDeg,
            evaluationStatus = eval.status,
            config = current.vehicleConfig,
        )

        _uiState.update {
            it.copy(
                vehicleState = vehicleState,
                footprint = fp,
                selectedGear = gear,
                uiSteeringRightPositiveDeg = uiSteeringDeg,
                predictedTrajectory = pred,
                collisionResult = col,
                evaluation = eval,
                liveSteeringGuidance = liveCue,
            )
        }
    }

    private fun evaluateCurrentScenario(
        vehicleState: VehicleState,
        config: VehicleConfig,
        scenario: ScenarioDefinition,
        collision: CollisionResult,
    ): ScenarioEvaluationResult {
        return when (scenario.type) {
            ScenarioType.PARALLEL_PARKING,
            ScenarioType.TIGHT_SPACE_PARKING,
            ScenarioType.REVERSE_PARKING,
            ScenarioType.FORWARD_PARKING,
            ScenarioType.LOW_SPEED_STEERING -> {
                ParkingAndTurnEvaluator.evaluateParking(
                    state = vehicleState,
                    config = config,
                    targetZone = scenario.targetZone,
                    obstacles = scenario.obstacles,
                    collisionResult = collision,
                )
            }
            else -> {
                ParkingAndTurnEvaluator.evaluateDrivingScenario(
                    state = vehicleState,
                    config = config,
                    targetZone = scenario.targetZone,
                    obstacles = scenario.obstacles,
                    collisionResult = collision,
                    corridorCenterX = scenario.corridorCenterX,
                    corridorHalfWidthMeters = scenario.corridorHalfWidthMeters,
                )
            }
        }
    }

    private fun targetSpeedForGear(gear: VirtualGearDirection, config: VehicleConfig): Float {
        return when (gear) {
            VirtualGearDirection.FORWARD -> abs(config.defaultForwardSpeedMps)
            VirtualGearDirection.REVERSE -> -abs(config.defaultReverseSpeedMps)
        }
    }

    override fun onCleared() {
        super.onCleared()
        steeringCollectJob?.cancel()
        physicsLoopJob?.cancel()
        heartbeatJob?.cancel()
        pleosSteeringProvider.release()
        diagnosticSteeringProvider.release()
        pleosProvider.release()
    }
}
