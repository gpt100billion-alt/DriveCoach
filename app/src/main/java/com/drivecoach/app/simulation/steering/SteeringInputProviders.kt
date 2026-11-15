package com.drivecoach.app.simulation.steering

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.PleosPermission
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.drivecoach.app.domain.model.GearPosition
import com.drivecoach.app.simulation.vehicle.VehicleConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

/**
 * 조향 입력 공급자 추상화 인터페이스 (요구사항 5)
 *
 * - 기본 구현: [PleosSteeringInputProvider] (실제 차량 핸들 및 Pleos Connect Virtual Console SDK 신호 수신)
 * - 개발자 테스트 구현: [DiagnosticSteeringInputProvider] (진단 화면 및 단위 테스트 전용)
 */
interface SteeringInputProvider {
    val telemetryState: StateFlow<SteeringTelemetrySnapshot>

    fun startListening()
    fun stopListening()
    fun refreshOnce()
    fun updateCalibrationAndVehicleConfig(
        calibration: SteeringCalibrationConfig,
        vehicleConfig: VehicleConfig,
    )
    fun calibrateZeroAtCurrentAngle()
    fun release()
}

/**
 * Pleos Vehicle SDK v2.3.2 기반 실제 차량 핸들(및 Virtual Console) 조향각 수신 공급자 (요구사항 2, 3, 4, 5, 6, 8)
 *
 * 공식 API 사용:
 * - `Vehicle.getSteeringWheel().checkSteeringWheelAngleCapabilities`
 * - `Vehicle.getSteeringWheel().getCurrentSteeringWheelAngle`
 * - `Vehicle.getSteeringWheel().registerSteeringWheelAngle`
 * - `Vehicle.getSteeringWheel().unregisterSteeringWheelAngle`
 * - 안전 확인용 조회 전용 API: `getGear()`, `getBrake()`, `getOdometer()`
 *
 * 엄격한 원칙:
 * - SDK 연결 실패나 권한 거부 시 임의의 가짜 조향각이나 가짜 안전 상태를 절대 생성하지 않습니다.
 * - 차량 제어(Set) API는 일절 호출하지 않습니다.
 */
class PleosSteeringInputProvider(
    context: Context,
    initialVehicleConfig: VehicleConfig = VehicleConfig(),
    initialCalibration: SteeringCalibrationConfig = SteeringCalibrationConfig(),
) : SteeringInputProvider {

    private val appContext = context.applicationContext
    private var vehicle: Vehicle? = null
    private var vehicleConfig: VehicleConfig = initialVehicleConfig
    private var baselineOdometerKm: Float? = null

    private val _telemetryState = MutableStateFlow(
        SteeringTelemetrySnapshot(
            sourceMode = SteeringInputSourceMode.PLEOS_VEHICLE_SDK,
            connectionState = SteeringConnectionState.INITIALIZING,
            permissionName = PleosPermission.CAR_DRIVE,
            isPermissionGranted = checkCarDrivePermission(),
            calibration = initialCalibration,
        )
    )
    override val telemetryState: StateFlow<SteeringTelemetrySnapshot> = _telemetryState.asStateFlow()

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) {
            handleRawSteeringUpdate(angle)
        }

        override fun onFailed(e: Exception) {
            handleSdkError("registerSteeringWheelAngle 콜백 오류", e)
        }
    }

    @Suppress("DEPRECATION")
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            val mapped = mapPleosGear(gear)
            _telemetryState.update { state ->
                state.copy(currentGear = mapped)
            }
        }

        override fun onFailed(e: Exception) {
            handleSdkError("GearSelectionListener 오류", e, fatalForSteering = false)
        }
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            _telemetryState.update { state ->
                state.copy(isBrakePressed = status)
            }
        }

        override fun onFailed(e: Exception) {
            handleSdkError("BrakePedalStatusListener 오류", e, fatalForSteering = false)
        }
    }

    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            if (value == null || !value.isFinite()) return
            val base = baselineOdometerKm
            if (base == null) {
                baselineOdometerKm = value
            }
            val movedKm = if (base != null) abs(value - base) else 0f
            // 주행 거리가 1m(0.001km) 이상 변하면 차량이 실제 이동 중인 것으로 간주하여 정차 조건 해제
            val stationary = movedKm < 0.001f
            _telemetryState.update { state ->
                state.copy(
                    currentOdometerKm = value,
                    isVehicleStationaryVerified = stationary,
                )
            }
        }

        override fun onFailed(e: Exception) {
            handleSdkError("DistanceDrivenListener 오류", e, fatalForSteering = false)
        }
    }

    @Suppress("MissingPermission", "DEPRECATION")
    override fun startListening() {
        val hasPerm = checkCarDrivePermission()
        if (!hasPerm) {
            _telemetryState.update {
                it.copy(
                    isPermissionGranted = false,
                    connectionState = SteeringConnectionState.PERMISSION_DENIED,
                    lastErrorCode = "ERROR_SECURITY_PERMISSION",
                    lastErrorMessage = "pleos.car.permission.CAR_DRIVE 권한이 승인되지 않았습니다.",
                )
            }
            return
        }

        try {
            if (vehicle == null) {
                vehicle = Vehicle(appContext).also { it.initialize() }
            }
            val v = vehicle ?: return

            _telemetryState.update {
                it.copy(
                    isPermissionGranted = true,
                    sdkInitialized = true,
                    connectionState = if (it.rawSdkAngleDeg != null) {
                        SteeringConnectionState.CONNECTED_RECEIVING
                    } else {
                        SteeringConnectionState.CONNECTED_WAITING_FIRST_SAMPLE
                    },
                )
            }

            // 1. SteeringWheel Capability 확인 및 초기값 조회
            v.getSteeringWheel().checkSteeringWheelAngleCapabilities(
                onComplete = { config, bounds ->
                    val readable = config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE
                    _telemetryState.update { state ->
                        state.copy(
                            capabilitySupported = readable,
                            capabilityAccessTypeText = "${config.accessType.name} (${bounds.minValue}° ~ ${bounds.maxValue}°)",
                            capabilityMinDeg = bounds.minValue,
                            capabilityMaxDeg = bounds.maxValue,
                            connectionState = if (!readable) {
                                SteeringConnectionState.UNSUPPORTED_CAPABILITY
                            } else if (state.rawSdkAngleDeg != null) {
                                SteeringConnectionState.CONNECTED_RECEIVING
                            } else {
                                SteeringConnectionState.CONNECTED_WAITING_FIRST_SAMPLE
                            },
                        )
                    }
                    if (readable) {
                        v.getSteeringWheel().getCurrentSteeringWheelAngle(
                            onComplete = { angle -> handleRawSteeringUpdate(angle) },
                            onFailed = { e -> handleSdkError("getCurrentSteeringWheelAngle 실패", e) },
                        )
                    }
                },
                onFailed = { e ->
                    handleSdkError("checkSteeringWheelAngleCapabilities 실패", e)
                },
            )

            // 2. 안전 상태(기어 P단, 브레이크, 주행거리) 초기 조회
            v.getGear().getCurrentGearSelectionState(
                onComplete = { gear ->
                    _telemetryState.update { it.copy(currentGear = mapPleosGear(gear)) }
                },
                onFailed = { e -> handleSdkError("getCurrentGearSelectionState 실패", e, fatalForSteering = false) },
            )
            v.getBrake().getCurrentBrakePedalStatus(
                onComplete = { pressed ->
                    _telemetryState.update { it.copy(isBrakePressed = pressed) }
                },
                onFailed = { /* 브레이크 미지원 환경은 기어/주행거리로 판단 */ },
            )
            v.getOdometer().getCurrentDistanceDriven(
                onComplete = { km ->
                    if (km != null && km.isFinite()) {
                        baselineOdometerKm = km
                        _telemetryState.update {
                            it.copy(
                                currentOdometerKm = km,
                                isVehicleStationaryVerified = true,
                            )
                        }
                    }
                },
                onFailed = { /* 주행거리 미지원 시 무시 */ },
            )

            // 3. 실시간 콜백 리스너 등록
            if (!_telemetryState.value.isListenerRegistered) {
                v.getSteeringWheel().registerSteeringWheelAngle(steeringListener)
                v.getGear().registerGearSelection(gearListener)
                v.getBrake().registerBrakePedalStatus(brakeListener)
                v.getOdometer().registerDistanceDriven(odometerListener)
                _telemetryState.update { it.copy(isListenerRegistered = true) }
            }
        } catch (e: Exception) {
            handleSdkError("Vehicle SDK 초기화 또는 리스너 등록 예외", e)
        }
    }

    @Suppress("MissingPermission", "DEPRECATION")
    override fun stopListening() {
        val v = vehicle ?: return
        if (_telemetryState.value.isListenerRegistered && checkCarDrivePermission()) {
            try {
                v.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener)
                v.getGear().unregisterGearSelection(gearListener)
                v.getBrake().unregisterBrakePedalStatus(brakeListener)
                v.getOdometer().unregisterDistanceDriven(odometerListener)
            } catch (_: Exception) {
            }
        }
        _telemetryState.update {
            it.copy(isListenerRegistered = false)
        }
    }

    @Suppress("MissingPermission", "DEPRECATION")
    override fun refreshOnce() {
        if (!_telemetryState.value.sdkInitialized || vehicle == null) {
            startListening()
            return
        }
        val v = vehicle ?: return
        if (!checkCarDrivePermission()) {
            _telemetryState.update {
                it.copy(
                    isPermissionGranted = false,
                    connectionState = SteeringConnectionState.PERMISSION_DENIED,
                )
            }
            return
        }

        v.getSteeringWheel().checkSteeringWheelAngleCapabilities(
            onComplete = { config, bounds ->
                val readable = config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE
                _telemetryState.update {
                    it.copy(
                        capabilitySupported = readable,
                        capabilityAccessTypeText = "${config.accessType.name} (${bounds.minValue}° ~ ${bounds.maxValue}°)",
                        capabilityMinDeg = bounds.minValue,
                        capabilityMaxDeg = bounds.maxValue,
                    )
                }
            },
            onFailed = { e -> handleSdkError("checkSteeringWheelAngleCapabilities 실패", e) },
        )

        v.getSteeringWheel().getCurrentSteeringWheelAngle(
            onComplete = { angle -> handleRawSteeringUpdate(angle) },
            onFailed = { e -> handleSdkError("getCurrentSteeringWheelAngle 실패", e) },
        )

        v.getGear().getCurrentGearSelectionState(
            onComplete = { gear ->
                _telemetryState.update { it.copy(currentGear = mapPleosGear(gear)) }
            },
            onFailed = { e -> handleSdkError("getCurrentGearSelectionState 실패", e, fatalForSteering = false) },
        )
    }

    override fun updateCalibrationAndVehicleConfig(
        calibration: SteeringCalibrationConfig,
        vehicleConfig: VehicleConfig,
    ) {
        this.vehicleConfig = vehicleConfig
        _telemetryState.update { state ->
            val reprocessed = SteeringMappingPipeline.processRawAngle(
                rawSdkAngleDeg = state.rawSdkAngleDeg,
                calibration = calibration,
                vehicleConfig = vehicleConfig,
            )
            state.copy(
                calibration = calibration,
                processedFrame = reprocessed,
            )
        }
    }

    override fun calibrateZeroAtCurrentAngle() {
        _telemetryState.update { state ->
            val currentRaw = state.rawSdkAngleDeg ?: return@update state
            val updatedCal = state.calibration.copy(zeroOffsetDeg = currentRaw)
            val reprocessed = SteeringMappingPipeline.processRawAngle(
                rawSdkAngleDeg = currentRaw,
                calibration = updatedCal,
                vehicleConfig = vehicleConfig,
            )
            state.copy(
                calibration = updatedCal,
                processedFrame = reprocessed,
            )
        }
    }

    override fun release() {
        stopListening()
        try {
            vehicle?.release()
        } catch (_: Exception) {
        }
        vehicle = null
        _telemetryState.update {
            it.copy(
                sdkInitialized = false,
                isListenerRegistered = false,
                connectionState = SteeringConnectionState.DISCONNECTED,
            )
        }
    }

    private fun handleRawSteeringUpdate(rawAngleDeg: Float?) {
        val now = System.currentTimeMillis()
        _telemetryState.update { state ->
            val processed = SteeringMappingPipeline.processRawAngle(
                rawSdkAngleDeg = rawAngleDeg,
                calibration = state.calibration,
                vehicleConfig = vehicleConfig,
            )
            val nextConnection = if (processed.isValid) {
                SteeringConnectionState.CONNECTED_RECEIVING
            } else {
                SteeringConnectionState.SIGNAL_LOST_OR_ERROR
            }
            state.copy(
                rawSdkAngleDeg = rawAngleDeg,
                lastReceivedTimestampMs = now,
                receivedEventCount = state.receivedEventCount + 1,
                connectionState = nextConnection,
                lastErrorCode = if (processed.isValid) null else "INVALID_STEERING_SAMPLE",
                lastErrorMessage = if (processed.isValid) null else processed.validationNoteKorean,
                processedFrame = processed,
            )
        }
    }

    private fun handleSdkError(
        contextLabel: String,
        e: Exception,
        fatalForSteering: Boolean = true,
    ) {
        val codeText = extractPleosErrorCode(e)
        val msgText = "$contextLabel: ${e.message ?: "상세 메시지 없음"}"
        _telemetryState.update { state ->
            state.copy(
                lastErrorCode = codeText,
                lastErrorMessage = msgText,
                connectionState = if (fatalForSteering) {
                    SteeringConnectionState.SIGNAL_LOST_OR_ERROR
                } else {
                    state.connectionState
                },
            )
        }
    }

    private fun extractPleosErrorCode(e: Exception): String {
        return try {
            val method = e.javaClass.methods.firstOrNull { it.name == "getErrorCode" }
            val errorCodeObj = method?.invoke(e)
            errorCodeObj?.toString() ?: e::class.java.simpleName
        } catch (_: Exception) {
            e::class.java.simpleName
        }
    }

    private fun checkCarDrivePermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(appContext, PleosPermission.CAR_DRIVE) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    private fun mapPleosGear(gear: VehicleGear?): GearPosition = when (gear) {
        VehicleGear.GEAR_PARK -> GearPosition.PARK
        VehicleGear.GEAR_REVERSE -> GearPosition.REVERSE
        VehicleGear.GEAR_NEUTRAL -> GearPosition.NEUTRAL
        VehicleGear.GEAR_DRIVE,
        VehicleGear.GEAR_1,
        VehicleGear.GEAR_2,
        VehicleGear.GEAR_3,
        VehicleGear.GEAR_4,
        VehicleGear.GEAR_5,
        VehicleGear.GEAR_6,
        VehicleGear.GEAR_7,
        VehicleGear.GEAR_8,
        VehicleGear.GEAR_9 -> GearPosition.DRIVE
        else -> GearPosition.UNKNOWN
    }
}

/**
 * 개발자 테스트 및 단위 테스트 전용 진단 입력 공급자 (요구사항 5)
 *
 * 주의:
 * - 일반 사용자 기본 화면에서는 활성화되지 않으며, 오직 개발자 진단 화면 또는 단위 테스트에서만 명시적으로 사용합니다.
 */
class DiagnosticSteeringInputProvider(
    initialVehicleConfig: VehicleConfig = VehicleConfig(),
    initialCalibration: SteeringCalibrationConfig = SteeringCalibrationConfig(),
) : SteeringInputProvider {

    private var vehicleConfig: VehicleConfig = initialVehicleConfig

    private val _telemetryState = MutableStateFlow(
        SteeringTelemetrySnapshot(
            sourceMode = SteeringInputSourceMode.DIAGNOSTIC_FALLBACK,
            connectionState = SteeringConnectionState.DIAGNOSTIC_OVERRIDE,
            sdkInitialized = false,
            isListenerRegistered = true,
            isPermissionGranted = true,
            capabilitySupported = true,
            capabilityAccessTypeText = "DIAGNOSTIC_TEST_ONLY (-540.0° ~ +540.0°)",
            capabilityMinDeg = -540f,
            capabilityMaxDeg = 540f,
            rawSdkAngleDeg = 0f,
            lastReceivedTimestampMs = System.currentTimeMillis(),
            receivedEventCount = 1,
            currentGear = GearPosition.PARK,
            isVehicleStationaryVerified = true,
            calibration = initialCalibration,
            processedFrame = SteeringMappingPipeline.processRawAngle(0f, initialCalibration, initialVehicleConfig),
        )
    )
    override val telemetryState: StateFlow<SteeringTelemetrySnapshot> = _telemetryState.asStateFlow()

    override fun startListening() {
        _telemetryState.update {
            it.copy(
                connectionState = SteeringConnectionState.DIAGNOSTIC_OVERRIDE,
                isListenerRegistered = true,
            )
        }
    }

    override fun stopListening() {
        _telemetryState.update { it.copy(isListenerRegistered = false) }
    }

    override fun refreshOnce() {
        injectDiagnosticRawAngle(_telemetryState.value.rawSdkAngleDeg ?: 0f)
    }

    override fun updateCalibrationAndVehicleConfig(
        calibration: SteeringCalibrationConfig,
        vehicleConfig: VehicleConfig,
    ) {
        this.vehicleConfig = vehicleConfig
        _telemetryState.update { state ->
            val processed = SteeringMappingPipeline.processRawAngle(
                rawSdkAngleDeg = state.rawSdkAngleDeg,
                calibration = calibration,
                vehicleConfig = vehicleConfig,
            )
            state.copy(
                calibration = calibration,
                processedFrame = processed,
            )
        }
    }

    override fun calibrateZeroAtCurrentAngle() {
        _telemetryState.update { state ->
            val raw = state.rawSdkAngleDeg ?: return@update state
            val updatedCal = state.calibration.copy(zeroOffsetDeg = raw)
            state.copy(
                calibration = updatedCal,
                processedFrame = SteeringMappingPipeline.processRawAngle(raw, updatedCal, vehicleConfig),
            )
        }
    }

    fun injectDiagnosticRawAngle(rawAngleDeg: Float?) {
        val now = System.currentTimeMillis()
        _telemetryState.update { state ->
            val processed = SteeringMappingPipeline.processRawAngle(
                rawSdkAngleDeg = rawAngleDeg,
                calibration = state.calibration,
                vehicleConfig = vehicleConfig,
            )
            state.copy(
                rawSdkAngleDeg = rawAngleDeg,
                lastReceivedTimestampMs = now,
                receivedEventCount = state.receivedEventCount + 1,
                connectionState = if (processed.isValid) {
                    SteeringConnectionState.DIAGNOSTIC_OVERRIDE
                } else {
                    SteeringConnectionState.SIGNAL_LOST_OR_ERROR
                },
                lastErrorCode = if (processed.isValid) null else "DIAGNOSTIC_INVALID_INPUT",
                lastErrorMessage = if (processed.isValid) null else processed.validationNoteKorean,
                processedFrame = processed,
            )
        }
    }

    fun injectDiagnosticGear(gear: GearPosition) {
        _telemetryState.update { it.copy(currentGear = gear) }
    }

    fun injectDiagnosticStationaryState(isStationary: Boolean) {
        _telemetryState.update { it.copy(isVehicleStationaryVerified = isStationary) }
    }

    override fun release() {
        stopListening()
    }
}
