package com.drivecoach.app.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.PleosPermission
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.drivecoach.app.domain.model.DataQualityFlag
import com.drivecoach.app.domain.model.DataSourceType
import com.drivecoach.app.domain.model.GearPosition
import com.drivecoach.app.domain.model.SensorAvailability
import com.drivecoach.app.domain.model.TelemetrySample
import com.drivecoach.app.domain.model.TurnSignalState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class PleosSdkDiagnosticState(
    val isPleosDeviceOrEmulator: Boolean = false,
    val sdkArtifactVersion: String = "ai.pleos.playground:Vehicle:2.3.2",
    val isSdkInitialized: Boolean = false,
    val isListening: Boolean = false,
    val vehicleServiceStatusText: String = "초기화 전",
    val receivedPacketCount: Int = 0,
    val lastPacketTimestampMs: Long? = null,
    val lastErrorMessage: String? = null,
    val currentGear: GearPosition = GearPosition.PARK,
    val currentRoadWheelAngleDeg: Float? = null,
    val currentBrakePressed: Boolean? = null,
    val currentTurnSignal: TurnSignalState = TurnSignalState.OFF,
    val currentOdometerKm: Float? = null,
    val currentTripDistanceMeters: Float? = null,
    val currentSpeedKph: Float? = null,
    val sensorAvailabilities: List<SensorAvailability> = emptyList(),
    val liveSamplesBuffer: List<TelemetrySample> = emptyList(),
)

/**
 * 현대자동차 Pleos Connect 공식 Vehicle SDK (2.3.2) 연동 및 센서 가용성 검증 공급자 (섹션 19 ~ 22, 26)
 *
 * 원칙:
 * 1. 공식 문서 및 SDK에 실제로 존재하는 조회(Read) 전용 API만 호출하며 제어(Set) API는 절대 호출하지 않습니다.
 * 2. SDK가 제공하지 않는 항목(차량 속도 PERF_VEHICLE_SPEED, 가속 페달 개도량, 브레이크 답력 %)은
 *    임의로 가짜 값을 생성하지 않고 UNAVAILABLE 상태와 정확한 사유를 보고합니다.
 * 3. SteeringWheel.getCurrentSteeringWheelAngle()이 핸들 회전각이 아닌 '전륜 조향각(degrees)'을 반환함을 명확히 구분합니다.
 */
class PleosTelemetryProvider(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var vehicle: Vehicle? = null

    private val _diagnosticState = MutableStateFlow(
        PleosSdkDiagnosticState(
            isPleosDeviceOrEmulator = detectAutomotiveOrPleosEnvironment(),
            sensorAvailabilities = buildInitialSensorMatrix(),
        )
    )
    val diagnosticState: StateFlow<PleosSdkDiagnosticState> = _diagnosticState.asStateFlow()

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) {
            val now = System.currentTimeMillis()
            updateSensorEntry(
                key = "road_wheel_angle",
                status = if (angle != null) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                sampleText = if (angle != null) String.format(Locale.US, "%.2f° (전륜 조향각)", angle) else "null 수신",
                updatedMs = now,
                usable = angle != null,
                failureReason = null,
            )
            _diagnosticState.update { state ->
                val updatedState = state.copy(
                    receivedPacketCount = state.receivedPacketCount + 1,
                    lastPacketTimestampMs = now,
                    currentRoadWheelAngleDeg = angle,
                    vehicleServiceStatusText = "Vehicle Service 실시간 수신 중",
                )
                appendLiveSample(updatedState, now)
            }
        }

        override fun onFailed(e: Exception) {
            recordSdkError("road_wheel_angle", "SteeringWheelAngleListener 오류", e)
        }
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            val now = System.currentTimeMillis()
            updateSensorEntry(
                key = "brake_pedal",
                status = if (status != null) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                sampleText = when (status) {
                    true -> "밟음 (ON · Boolean)"
                    false -> "해제 (OFF · Boolean)"
                    null -> "null 수신"
                },
                updatedMs = now,
                usable = status != null,
                failureReason = null,
            )
            _diagnosticState.update { state ->
                val updatedState = state.copy(
                    receivedPacketCount = state.receivedPacketCount + 1,
                    lastPacketTimestampMs = now,
                    currentBrakePressed = status,
                    vehicleServiceStatusText = "Vehicle Service 실시간 수신 중",
                )
                appendLiveSample(updatedState, now)
            }
        }

        override fun onFailed(e: Exception) {
            recordSdkError("brake_pedal", "BrakePedalStatusListener 오류", e)
        }
    }

    @Suppress("DEPRECATION")
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            val now = System.currentTimeMillis()
            val mappedGear = mapPleosGear(gear)
            updateSensorEntry(
                key = "gear_selection",
                status = if (gear != null) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                sampleText = "${mappedGear.koreanName} (${gear?.name ?: "null"})",
                updatedMs = now,
                usable = gear != null,
                failureReason = null,
            )
            _diagnosticState.update { state ->
                val updatedState = state.copy(
                    receivedPacketCount = state.receivedPacketCount + 1,
                    lastPacketTimestampMs = now,
                    currentGear = mappedGear,
                    vehicleServiceStatusText = "Vehicle Service 실시간 수신 중",
                )
                appendLiveSample(updatedState, now)
            }
        }

        override fun onFailed(e: Exception) {
            recordSdkError("gear_selection", "GearSelectionListener 오류", e)
        }
    }

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            val now = System.currentTimeMillis()
            val mapped = mapTurnSignal(state)
            updateSensorEntry(
                key = "turn_signal",
                status = if (state != null) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                sampleText = "${mapped.koreanName} (${state?.name ?: "null"})",
                updatedMs = now,
                usable = state != null,
                failureReason = null,
            )
            _diagnosticState.update { s ->
                val updatedState = s.copy(
                    receivedPacketCount = s.receivedPacketCount + 1,
                    lastPacketTimestampMs = now,
                    currentTurnSignal = mapped,
                )
                appendLiveSample(updatedState, now)
            }
        }

        override fun onFailed(e: Exception) {
            recordSdkError("turn_signal", "TurnSignalStateListener 오류", e)
        }
    }

    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            val now = System.currentTimeMillis()
            updateSensorEntry(
                key = "odometer_distance",
                status = if (value != null) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                sampleText = if (value != null) String.format(Locale.US, "%.2f km", value) else "null 수신",
                updatedMs = now,
                usable = value != null,
                failureReason = null,
            )
            _diagnosticState.update { s ->
                val updatedState = s.copy(
                    receivedPacketCount = s.receivedPacketCount + 1,
                    lastPacketTimestampMs = now,
                    currentOdometerKm = value,
                )
                appendLiveSample(updatedState, now)
            }
        }

        override fun onFailed(e: Exception) {
            recordSdkError("odometer_distance", "DistanceDrivenListener 오류", e)
        }
    }

    /**
     * Pleos Vehicle SDK를 초기화하고 각 도메인 API의 Capability 및 현재 값을 검증합니다.
     */
    @Suppress("MissingPermission", "DEPRECATION")
    fun initializeAndProbeCapabilities() {
        val hasCarDrive = isPermissionGranted(PleosPermission.CAR_DRIVE)
        val hasCarLights = isPermissionGranted(PleosPermission.CAR_LIGHTS)
        val hasCarInfo = isPermissionGranted(PleosPermission.CAR_INFO)

        _diagnosticState.update {
            it.copy(
                isPleosDeviceOrEmulator = detectAutomotiveOrPleosEnvironment(),
                sensorAvailabilities = buildInitialSensorMatrix(),
                vehicleServiceStatusText = "Pleos Vehicle SDK 초기화 및 Capability 조회 중...",
            )
        }

        try {
            if (vehicle == null) {
                vehicle = Vehicle(appContext).also { it.initialize() }
            }
            _diagnosticState.update {
                it.copy(
                    isSdkInitialized = true,
                    vehicleServiceStatusText = "Vehicle.initialize() 호출 완료 (IPC 바인딩 대기/연결)",
                )
            }

            val v = vehicle ?: return

            // 1. 전륜 조향각 (SteeringWheel) Capability & Get 검증
            if (hasCarDrive) {
                v.getSteeringWheel().checkSteeringWheelAngleCapabilities(
                    onComplete = { config, bounds ->
                        val now = System.currentTimeMillis()
                        val readable = config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE
                        updateSensorCapability(
                            key = "road_wheel_angle",
                            accessText = "${config.accessType.name} (범위 ${bounds.minValue}° ~ ${bounds.maxValue}°)",
                        )
                        if (readable) {
                            v.getSteeringWheel().getCurrentSteeringWheelAngle(
                                onComplete = { angle ->
                                    updateSensorEntry(
                                        key = "road_wheel_angle",
                                        status = if (angle != null) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                                        sampleText = if (angle != null) String.format(Locale.US, "%.2f° (전륜)", angle) else "null",
                                        updatedMs = now,
                                        usable = angle != null,
                                        failureReason = null,
                                    )
                                    _diagnosticState.update { s ->
                                        s.copy(
                                            currentRoadWheelAngleDeg = angle,
                                            receivedPacketCount = s.receivedPacketCount + 1,
                                            lastPacketTimestampMs = now,
                                            vehicleServiceStatusText = "Vehicle Service 응답 확인됨",
                                        )
                                    }
                                },
                                onFailed = { e -> recordSdkError("road_wheel_angle", "getCurrentSteeringWheelAngle 실패", e) }
                            )
                        }
                    },
                    onFailed = { e -> recordSdkError("road_wheel_angle", "checkSteeringWheelAngleCapabilities 실패", e) }
                )

                // 2. 브레이크 페달 상태 (Brake) Capability & Get 검증
                v.getBrake().checkBrakePedalStatusCapabilities(
                    onComplete = { config ->
                        val now = System.currentTimeMillis()
                        updateSensorCapability(
                            key = "brake_pedal",
                            accessText = "${config.accessType.name} (Boolean ON/OFF 전용, 답력 % 미지원)",
                        )
                        v.getBrake().getCurrentBrakePedalStatus(
                            onComplete = { pressed ->
                                updateSensorEntry(
                                    key = "brake_pedal",
                                    status = if (pressed != null) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                                    sampleText = when (pressed) {
                                        true -> "밟음 (ON · Boolean)"
                                        false -> "해제 (OFF · Boolean)"
                                        null -> "null"
                                    },
                                    updatedMs = now,
                                    usable = pressed != null,
                                    failureReason = null,
                                )
                                _diagnosticState.update { s ->
                                    s.copy(
                                        currentBrakePressed = pressed,
                                        receivedPacketCount = s.receivedPacketCount + 1,
                                        lastPacketTimestampMs = now,
                                    )
                                }
                            },
                            onFailed = { e -> recordSdkError("brake_pedal", "getCurrentBrakePedalStatus 실패", e) }
                        )
                    },
                    onFailed = { e -> recordSdkError("brake_pedal", "checkBrakePedalStatusCapabilities 실패", e) }
                )

                // 3. 주행 거리 (Odometer) Capability & Get 검증
                v.getOdometer().checkCurrentDistanceDrivenCapabilities(
                    onComplete = { config ->
                        val now = System.currentTimeMillis()
                        updateSensorCapability(
                            key = "odometer_distance",
                            accessText = "${config.accessType.name} (누적 km 및 트립 m)",
                        )
                        v.getOdometer().getCurrentDistanceDriven(
                            onComplete = { km ->
                                updateSensorEntry(
                                    key = "odometer_distance",
                                    status = if (km != null) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                                    sampleText = if (km != null) String.format(Locale.US, "%.2f km", km) else "null",
                                    updatedMs = now,
                                    usable = km != null,
                                    failureReason = null,
                                )
                                _diagnosticState.update { s ->
                                    s.copy(
                                        currentOdometerKm = km,
                                        receivedPacketCount = s.receivedPacketCount + 1,
                                        lastPacketTimestampMs = now,
                                    )
                                }
                            },
                            onFailed = { e -> recordSdkError("odometer_distance", "getCurrentDistanceDriven 실패", e) }
                        )
                    },
                    onFailed = { e -> recordSdkError("odometer_distance", "checkCurrentDistanceDrivenCapabilities 실패", e) }
                )

                // 4. 기어 단수 (Gear) Get 검증
                v.getGear().getCurrentGearSelectionState(
                    onComplete = { gear ->
                        val now = System.currentTimeMillis()
                        val mapped = mapPleosGear(gear)
                        updateSensorCapability("gear_selection", "READ (Pleos Gear.getCurrentGearSelectionState)")
                        updateSensorEntry(
                            key = "gear_selection",
                            status = if (gear != null) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                            sampleText = "${mapped.koreanName} (${gear?.name ?: "null"})",
                            updatedMs = now,
                            usable = gear != null,
                            failureReason = null,
                        )
                        _diagnosticState.update { s ->
                            s.copy(
                                currentGear = mapped,
                                receivedPacketCount = s.receivedPacketCount + 1,
                                lastPacketTimestampMs = now,
                            )
                        }
                    },
                    onFailed = { e -> recordSdkError("gear_selection", "getCurrentGearSelectionState 실패", e) }
                )
            }

            // 5. 방향지시등 (TurnSignal) Capability & Get 검증
            if (hasCarLights) {
                v.getTurnSignal().checkTurnSignalStateCapabilities(
                    onComplete = { config ->
                        val now = System.currentTimeMillis()
                        updateSensorCapability("turn_signal", config.accessType.name)
                        v.getTurnSignal().getCurrentTurnSignalState(
                            onComplete = { sig ->
                                val mapped = mapTurnSignal(sig)
                                updateSensorEntry(
                                    key = "turn_signal",
                                    status = if (sig != null) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                                    sampleText = "${mapped.koreanName} (${sig?.name ?: "null"})",
                                    updatedMs = now,
                                    usable = sig != null,
                                    failureReason = null,
                                )
                                _diagnosticState.update { s ->
                                    s.copy(
                                        currentTurnSignal = mapped,
                                        receivedPacketCount = s.receivedPacketCount + 1,
                                        lastPacketTimestampMs = now,
                                    )
                                }
                            },
                            onFailed = { e -> recordSdkError("turn_signal", "getCurrentTurnSignalState 실패", e) }
                        )
                    },
                    onFailed = { e -> recordSdkError("turn_signal", "checkTurnSignalStateCapabilities 실패", e) }
                )
            }

            // 6. CarInfo VIN Capability 확인 (연결성 보조 검증)
            if (hasCarInfo) {
                v.getCarInfo().checkVINInfoCapabilities(
                    onComplete = { _ ->
                        _diagnosticState.update { s ->
                            s.copy(vehicleServiceStatusText = "Pleos Vehicle Service 연결 정상")
                        }
                    },
                    onFailed = { /* VIN 미지원 환경 무시 */ }
                )
            }

            // 7. Android Automotive 표준 CarPropertyManager 속도 센서(PERF_VEHICLE_SPEED) 접근 가능 여부 리플렉션 점검
            probeStandardCarSpeedProperty()

        } catch (e: Exception) {
            _diagnosticState.update {
                it.copy(
                    lastErrorMessage = "Vehicle SDK 초기화 예외: ${e.message ?: e::class.java.simpleName}",
                    vehicleServiceStatusText = "Vehicle SDK 연결 실패 (에뮬레이터/실차 서비스 확인 필요)",
                )
            }
        }
    }

    /**
     * 실시간 리스너 등록 시작 (개발자 진단 및 LIVE 테스트용)
     */
    @Suppress("MissingPermission", "DEPRECATION")
    fun startLiveListening() {
        if (_diagnosticState.value.isListening) return
        if (!_diagnosticState.value.isSdkInitialized) {
            initializeAndProbeCapabilities()
        }
        val v = vehicle ?: return
        try {
            if (isPermissionGranted(PleosPermission.CAR_DRIVE)) {
                v.getSteeringWheel().registerSteeringWheelAngle(steeringListener)
                v.getBrake().registerBrakePedalStatus(brakeListener)
                v.getGear().registerGearSelection(gearListener)
                v.getOdometer().registerDistanceDriven(odometerListener)
            }
            if (isPermissionGranted(PleosPermission.CAR_LIGHTS)) {
                v.getTurnSignal().registerTurnSignalState(turnSignalListener)
            }
            _diagnosticState.update {
                it.copy(
                    isListening = true,
                    vehicleServiceStatusText = "실시간 콜백 리스너 등록됨 (Virtual Console 조작 대기)",
                )
            }
        } catch (e: Exception) {
            _diagnosticState.update {
                it.copy(
                    isListening = false,
                    lastErrorMessage = "리스너 등록 오류: ${e.message}",
                )
            }
        }
    }

    /**
     * 실시간 리스너 등록 해제
     */
    @Suppress("MissingPermission", "DEPRECATION")
    fun stopLiveListening() {
        val v = vehicle ?: return
        try {
            if (isPermissionGranted(PleosPermission.CAR_DRIVE)) {
                v.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener)
                v.getBrake().unregisterBrakePedalStatus(brakeListener)
                v.getGear().unregisterGearSelection(gearListener)
                v.getOdometer().unregisterDistanceDriven(odometerListener)
            }
            if (isPermissionGranted(PleosPermission.CAR_LIGHTS)) {
                v.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
            }
        } catch (_: Exception) {
        }
        _diagnosticState.update {
            it.copy(
                isListening = false,
                vehicleServiceStatusText = "실시간 콜백 리스너 중지됨",
            )
        }
    }

    fun release() {
        stopLiveListening()
        try {
            vehicle?.release()
        } catch (_: Exception) {
        }
        vehicle = null
        _diagnosticState.update {
            it.copy(
                isSdkInitialized = false,
                isListening = false,
                vehicleServiceStatusText = "Vehicle SDK 리소스 해제됨",
            )
        }
    }

    private fun probeStandardCarSpeedProperty() {
        try {
            val carClass = Class.forName("android.car.Car")
            val hasSpeedPerm = ContextCompat.checkSelfPermission(
                appContext,
                "android.car.permission.CAR_SPEED"
            ) == PackageManager.PERMISSION_GRANTED
            updateSensorEntry(
                key = "vehicle_speed",
                status = DataSourceType.UNAVAILABLE,
                sampleText = "Pleos Vehicle SDK 2.3.2 미제공 (android.car 클래스 감지됨)",
                updatedMs = System.currentTimeMillis(),
                usable = false,
                failureReason = if (hasSpeedPerm) {
                    "Pleos Vehicle SDK 2.3.2 공개 도메인에 실시간 차속(PERF_VEHICLE_SPEED) API가 포함되어 있지 않습니다."
                } else {
                    "Pleos Vehicle SDK 2.3.2에 실시간 차속 API가 없으며, Android Automotive 표준 PERF_VEHICLE_SPEED는 android.car.permission.CAR_SPEED 권한이 필요합니다. (${carClass.simpleName} 감지)"
                },
            )
        } catch (_: Throwable) {
            updateSensorEntry(
                key = "vehicle_speed",
                status = DataSourceType.UNAVAILABLE,
                sampleText = "Pleos Vehicle SDK 2.3.2 미제공",
                updatedMs = System.currentTimeMillis(),
                usable = false,
                failureReason = "Pleos Vehicle SDK 2.3.2 공개 인터페이스(Brake, SteeringWheel, Odometer, DrivingMode 등)에 실시간 차량 속도(km/h) 조회 API가 제공되지 않습니다.",
            )
        }
    }

    private fun appendLiveSample(state: PleosSdkDiagnosticState, timestampMs: Long): PleosSdkDiagnosticState {
        val sample = TelemetrySample(
            timestampMs = timestampMs,
            speedKph = state.currentSpeedKph,
            longitudinalAccelerationMps2 = null,
            isAccelerationEstimated = true,
            brakePedalPercent = null,
            brakePedalPressed = state.currentBrakePressed,
            acceleratorPedalPercent = null,
            steeringWheelAngleDeg = null,
            roadWheelAngleDeg = state.currentRoadWheelAngleDeg,
            odometerKm = state.currentOdometerKm,
            tripDistanceMeters = state.currentTripDistanceMeters,
            gearPosition = state.currentGear,
            turnSignalState = state.currentTurnSignal,
            dataSource = DataSourceType.LIVE,
            qualityFlags = buildSet {
                if (state.currentSpeedKph == null) add(DataQualityFlag.MISSING_SPEED)
                if (state.currentRoadWheelAngleDeg == null) add(DataQualityFlag.MISSING_STEERING)
            },
        )
        val trimmed = (state.liveSamplesBuffer + sample).takeLast(180)
        return state.copy(liveSamplesBuffer = trimmed)
    }

    private fun recordSdkError(sensorKey: String, contextMessage: String, e: Exception) {
        val reason = "$contextMessage (${e::class.java.simpleName}: ${e.message ?: "오류 상세 없음"})"
        updateSensorEntry(
            key = sensorKey,
            status = DataSourceType.ERROR,
            sampleText = "조회 실패",
            updatedMs = System.currentTimeMillis(),
            usable = false,
            failureReason = reason,
        )
        _diagnosticState.update {
            it.copy(lastErrorMessage = reason)
        }
    }

    private fun updateSensorCapability(key: String, accessText: String) {
        _diagnosticState.update { state ->
            state.copy(
                sensorAvailabilities = state.sensorAvailabilities.map { item ->
                    if (item.sensorKey == key) item.copy(capabilityAccessType = accessText) else item
                }
            )
        }
    }

    private fun updateSensorEntry(
        key: String,
        status: DataSourceType,
        sampleText: String,
        updatedMs: Long,
        usable: Boolean,
        failureReason: String?,
    ) {
        _diagnosticState.update { state ->
            state.copy(
                sensorAvailabilities = state.sensorAvailabilities.map { item ->
                    if (item.sensorKey == key) {
                        item.copy(
                            availabilityStatus = status,
                            sampleValueText = sampleText,
                            lastUpdatedMs = updatedMs,
                            usableForAnalysis = usable,
                            failureReason = failureReason ?: item.failureReason,
                        )
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return try {
            ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    private fun detectAutomotiveOrPleosEnvironment(): Boolean {
        val pm = appContext.packageManager
        val isAuto = pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        val brandModel = "${Build.BRAND} ${Build.MANUFACTURER} ${Build.MODEL} ${Build.PRODUCT}".lowercase(Locale.ROOT)
        return isAuto || "pleos" in brandModel || "automotive" in brandModel || "car" in brandModel
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

    private fun mapTurnSignal(value: TurnSignalValue?): TurnSignalState = when (value) {
        TurnSignalValue.OFF -> TurnSignalState.OFF
        TurnSignalValue.LEFT -> TurnSignalState.LEFT
        TurnSignalValue.RIGHT -> TurnSignalState.RIGHT
        null -> TurnSignalState.UNKNOWN
    }

    private fun buildInitialSensorMatrix(): List<SensorAvailability> {
        val hasCarDrive = isPermissionGranted(PleosPermission.CAR_DRIVE)
        val hasCarLights = isPermissionGranted(PleosPermission.CAR_LIGHTS)

        return listOf(
            SensorAvailability(
                sensorKey = "vehicle_speed",
                sensorName = "1. 차량 속도 (Speed km/h)",
                sdkDomainAndMethod = "Pleos Vehicle SDK 2.3.2 미제공 (DrivingMode는 크루즈 목표 속도만 포함)",
                availabilityStatus = DataSourceType.UNAVAILABLE,
                capabilityAccessType = "SDK API 없음",
                requiredPermission = "android.car.permission.CAR_SPEED (AAOS 표준)",
                permissionGranted = isPermissionGranted("android.car.permission.CAR_SPEED"),
                sampleValueText = "SDK 미지원 (시뮬레이션/데모 모드에서 검증)",
                failureReason = "ai.pleos.playground:Vehicle:2.3.2 공개 도메인에 실시간 주행 속도(PERF_VEHICLE_SPEED) API가 제공되지 않습니다.",
                usableForAnalysis = false,
                notes = "속도 시계열이 필요한 가감속 분석은 데모/리플레이 공급자에서 수행하며, 실차 연동 시 AAOS CarPropertyManager 권한 승인이 필요합니다.",
            ),
            SensorAvailability(
                sensorKey = "brake_pedal",
                sensorName = "2. 브레이크 페달 상태 (Brake)",
                sdkDomainAndMethod = "Vehicle.getBrake().getCurrentBrakePedalStatus / registerBrakePedalStatus",
                availabilityStatus = if (hasCarDrive) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                capabilityAccessType = "조회 대기 (Boolean ON/OFF)",
                requiredPermission = PleosPermission.CAR_DRIVE,
                permissionGranted = hasCarDrive,
                sampleValueText = "초기화 대기",
                failureReason = if (!hasCarDrive) "pleos.car.permission.CAR_DRIVE 권한 미부여" else null,
                usableForAnalysis = hasCarDrive,
                notes = "페달 밟음 여부(Boolean)만 제공되며, 0~100% 연속 답력 수치는 SDK에서 제공되지 않습니다.",
            ),
            SensorAvailability(
                sensorKey = "accelerator_pedal",
                sensorName = "3. 가속 페달 개도량 (Accelerator %)",
                sdkDomainAndMethod = "Pleos Vehicle SDK 2.3.2 미제공",
                availabilityStatus = DataSourceType.UNAVAILABLE,
                capabilityAccessType = "SDK API 없음",
                requiredPermission = "미제공 (서드파티 미공개)",
                permissionGranted = false,
                sampleValueText = "SDK 미지원",
                failureReason = "ai.pleos.playground:Vehicle:2.3.2에 가속 페달 위치(ACCELERATOR_PEDAL_POSITION) 조회 API가 포함되어 있지 않습니다.",
                usableForAnalysis = false,
                notes = "DriveCoach 분석 엔진은 가속 페달 원시값 대신 속도 변화율(Δv/Δt) 기반 종방향 가속도 추정치를 활용합니다.",
            ),
            SensorAvailability(
                sensorKey = "road_wheel_angle",
                sensorName = "4. 전륜 조향각 (Front-Wheel Angle °)",
                sdkDomainAndMethod = "Vehicle.getSteeringWheel().getCurrentSteeringWheelAngle / registerSteeringWheelAngle",
                availabilityStatus = if (hasCarDrive) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                capabilityAccessType = "조회 대기 (PERF_STEERING_ANGLE, 단위: deg)",
                requiredPermission = PleosPermission.CAR_DRIVE,
                permissionGranted = hasCarDrive,
                sampleValueText = "초기화 대기",
                failureReason = if (!hasCarDrive) "pleos.car.permission.CAR_DRIVE 권한 미부여" else null,
                usableForAnalysis = hasCarDrive,
                notes = "공식 KDoc 명시사항: 운전대 핸들 회전각이 아닌 '전륜 타이어 조향각(좌측 음수, 도 단위)'을 반환합니다.",
            ),
            SensorAvailability(
                sensorKey = "gear_selection",
                sensorName = "5. 기어 단수 상태 (Gear Selection)",
                sdkDomainAndMethod = "Vehicle.getGear().getCurrentGearSelectionState / registerGearSelection",
                availabilityStatus = if (hasCarDrive) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                capabilityAccessType = "조회 대기 (VehicleGear P/R/N/D)",
                requiredPermission = PleosPermission.CAR_DRIVE,
                permissionGranted = hasCarDrive,
                sampleValueText = "초기화 대기",
                failureReason = if (!hasCarDrive) "pleos.car.permission.CAR_DRIVE 권한 미부여" else null,
                usableForAnalysis = hasCarDrive,
                notes = "주차(P) 상태 판별 및 주행 중 화면 제한 안전 정책 연동에 활용됩니다.",
            ),
            SensorAvailability(
                sensorKey = "turn_signal",
                sensorName = "6. 방향지시등 상태 (Turn Signal)",
                sdkDomainAndMethod = "Vehicle.getTurnSignal().getCurrentTurnSignalState / registerTurnSignalState",
                availabilityStatus = if (hasCarLights) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                capabilityAccessType = "조회 대기 (OFF / LEFT / RIGHT)",
                requiredPermission = PleosPermission.CAR_LIGHTS,
                permissionGranted = hasCarLights,
                sampleValueText = "초기화 대기",
                failureReason = if (!hasCarLights) "pleos.car.permission.CAR_LIGHTS 권한 미부여" else null,
                usableForAnalysis = hasCarLights,
                notes = "교차로 회전 의도 및 차로 변경 컨텍스트 보조 지표로 활용 가능합니다.",
            ),
            SensorAvailability(
                sensorKey = "odometer_distance",
                sensorName = "7. 누적 및 트립 주행 거리 (Odometer)",
                sdkDomainAndMethod = "Vehicle.getOdometer().getCurrentDistanceDriven / getTripCurrentDrivingDistance",
                availabilityStatus = if (hasCarDrive) DataSourceType.LIVE else DataSourceType.UNAVAILABLE,
                capabilityAccessType = "조회 대기 (km / m)",
                requiredPermission = PleosPermission.CAR_DRIVE,
                permissionGranted = hasCarDrive,
                sampleValueText = "초기화 대기",
                failureReason = if (!hasCarDrive) "pleos.car.permission.CAR_DRIVE 권한 미부여" else null,
                usableForAnalysis = hasCarDrive,
                notes = "세션 총 주행 거리 산출 및 누적 주행 통계 검증에 활용됩니다.",
            ),
        )
    }
}
