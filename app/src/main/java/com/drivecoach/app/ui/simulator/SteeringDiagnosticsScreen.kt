package com.drivecoach.app.ui.simulator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecoach.app.simulation.steering.SdkAngleInterpretationMode
import com.drivecoach.app.simulation.steering.SteeringCalibrationConfig
import com.drivecoach.app.simulation.steering.SteeringConnectionState
import com.drivecoach.app.simulation.steering.SteeringDirectionLabel
import com.drivecoach.app.simulation.steering.SteeringInputSourceMode
import com.drivecoach.app.simulation.steering.SteeringTelemetrySnapshot
import com.drivecoach.app.ui.theme.SimBorder
import com.drivecoach.app.ui.theme.SimCollision
import com.drivecoach.app.ui.theme.SimCollisionSoft
import com.drivecoach.app.ui.theme.SimMainBackground
import com.drivecoach.app.ui.theme.SimMainSurface
import com.drivecoach.app.ui.theme.SimPrimaryAccent
import com.drivecoach.app.ui.theme.SimPrimaryAccentSoft
import com.drivecoach.app.ui.theme.SimPrimaryText
import com.drivecoach.app.ui.theme.SimSecondarySurface
import com.drivecoach.app.ui.theme.SimSecondaryText
import com.drivecoach.app.ui.theme.SimSuccess
import com.drivecoach.app.ui.theme.SimWarning
import com.drivecoach.app.ui.theme.SimWarningSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 실제 차량 핸들 각도 수신 검증 및 진단 화면 (요구사항 2, 3, 4, 5, 6)
 *
 * 필수 표시 항목:
 * 1. Vehicle SDK 연결 상태
 * 2. Steering Wheel Angle Capability (AccessType 및 Min/Max 범위)
 * 3. 필요한 권한(pleos.car.permission.CAR_DRIVE) 및 권한 승인 상태
 * 4. 현재 수신된 핸들 각도 (원시 SDK 값 / 보정 핸들각 / 조향 방향 / 추정 전륜 조향각)
 * 5. 마지막 수신 시각 및 누적 이벤트 수
 * 6. 정상 수신 여부
 * 7. 오류 발생 시 오류 코드 및 원인
 */
@Composable
fun SteeringDiagnosticsScreen(
    telemetry: SteeringTelemetrySnapshot,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenSimulator: () -> Unit,
    onRefreshSdk: () -> Unit,
    onCalibrateZeroNow: () -> Unit,
    onResetCalibration: () -> Unit,
    onUpdateCalibration: (SteeringCalibrationConfig) -> Unit,
    onSwitchInputSourceMode: (SteeringInputSourceMode) -> Unit,
    onInjectDiagnosticAngle: (Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowMs = System.currentTimeMillis()
    val isHealthy = telemetry.connectionState == SteeringConnectionState.CONNECTED_RECEIVING &&
        telemetry.processedFrame.isValid

    val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.KOREA)
    val lastReceivedText = telemetry.lastReceivedTimestampMs?.let { ts ->
        val elapsedMs = (nowMs - ts).coerceAtLeast(0L)
        "${timeFormatter.format(Date(ts))} (${elapsedMs}ms 전 · 총 ${telemetry.receivedEventCount}회 수신)"
    } ?: "수신 기록 없음"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SimMainBackground)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 상단 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.clickable(onClick = onBack),
                    color = SimMainSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SimBorder),
                ) {
                    Text(
                        text = "← 뒤로",
                        style = MaterialTheme.typography.titleMedium,
                        color = SimPrimaryText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }

                Surface(
                    modifier = Modifier.clickable(onClick = onHome),
                    color = SimMainSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SimBorder),
                ) {
                    Text(
                        text = "홈",
                        style = MaterialTheme.typography.titleMedium,
                        color = SimSecondaryText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }

                Text(
                    text = "실제 차량 핸들(SteeringWheel) 수신 진단",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = SimPrimaryText,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRefreshSdk,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SimSecondarySurface,
                        contentColor = SimPrimaryAccent,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("SDK 즉시 재조회", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenSimulator,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SimPrimaryAccent,
                        contentColor = SimMainSurface,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("연습 화면으로 이동", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. 실시간 수신 수치 대형 요약 카드
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SimMainSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = when {
                        isHealthy -> SimSuccess
                        telemetry.connectionState == SteeringConnectionState.DIAGNOSTIC_OVERRIDE -> SimWarning
                        else -> SimCollision
                    },
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isHealthy -> SimSuccess
                                            telemetry.connectionState == SteeringConnectionState.DIAGNOSTIC_OVERRIDE -> SimWarning
                                            else -> SimCollision
                                        }
                                    ),
                            )
                            Text(
                                text = telemetry.connectionState.koreanLabel,
                                style = MaterialTheme.typography.titleLarge,
                                color = SimPrimaryText,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Surface(
                            color = when (telemetry.processedFrame.direction) {
                                SteeringDirectionLabel.LEFT -> SimPrimaryAccentSoft
                                SteeringDirectionLabel.RIGHT -> SimPrimaryAccentSoft
                                SteeringDirectionLabel.CENTER -> SimSecondarySurface
                            },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = "조향 방향: ${telemetry.processedFrame.direction.koreanText}",
                                style = MaterialTheme.typography.titleMedium,
                                color = SimPrimaryAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }

                    // 4개 핵심 수치 박스 (원시 SDK 값 / 보정 핸들각 / 추정 전륜 조향각 / 차량 기어·안전 상태)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DiagnosticMetricBox(
                            title = "현재 수신된 핸들 각도 (Raw SDK)",
                            valueText = telemetry.rawSdkAngleDeg?.let {
                                String.format(Locale.US, "%+.1f°", it)
                            } ?: "미수신 (--)",
                            subText = "getCurrent / registerSteeringWheelAngle",
                            modifier = Modifier.weight(1f),
                        )

                        DiagnosticMetricBox(
                            title = "영점 보정 후 핸들 각도",
                            valueText = if (telemetry.processedFrame.isValid) {
                                String.format(Locale.US, "%+.1f°", telemetry.processedFrame.estimatedSteeringWheelAngleDeg)
                            } else {
                                "--"
                            },
                            subText = String.format(Locale.US, "영점 오프셋 %+.1f°", telemetry.calibration.zeroOffsetDeg),
                            modifier = Modifier.weight(1f),
                        )

                        DiagnosticMetricBox(
                            title = "추정 전륜 조향각 (δ · 근사)",
                            valueText = if (telemetry.processedFrame.isValid) {
                                String.format(Locale.US, "%+.2f°", telemetry.processedFrame.estimatedFrontWheelAngleRightPositiveDeg)
                            } else {
                                "--"
                            },
                            subText = if (telemetry.calibration.interpretationMode == SdkAngleInterpretationMode.STEERING_WHEEL_ROTATION_WITH_RATIO) {
                                String.format(Locale.US, "기어비 %.1f:1 적용 (시뮬레이션 근사)", telemetry.calibration.sanitizedRatio)
                            } else {
                                "SDK 값 직접 전륜각 적용"
                            },
                            modifier = Modifier.weight(1f),
                        )

                        DiagnosticMetricBox(
                            title = "차량 주차(P) 및 정차 안전",
                            valueText = "${telemetry.currentGear.koreanName} · ${if (telemetry.isParkedAndSafe) "안전 확인" else "연습 제한"}",
                            subText = "브레이크: ${telemetry.isBrakePressed?.let { if (it) "밟음" else "해제" } ?: "미수신"}",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // 2. 요구사항 3 필수 진단 항목 7가지 상세 표
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SimMainSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SimBorder),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Pleos Vehicle SDK (v2.3.2) 핸들 각도 수신 필수 검증 항목",
                        style = MaterialTheme.typography.titleLarge,
                        color = SimPrimaryText,
                        fontWeight = FontWeight.Bold,
                    )

                    DiagnosticRowItem(
                        label = "1. Vehicle SDK 연결 상태",
                        value = "${telemetry.connectionState.koreanLabel} (SDK 초기화: ${if (telemetry.sdkInitialized) "성공" else "미완료"}, 리스너 등록: ${if (telemetry.isListenerRegistered) "등록됨" else "해제됨"})",
                        isOk = telemetry.sdkInitialized && telemetry.isListenerRegistered,
                    )
                    DiagnosticRowItem(
                        label = "2. Steering Wheel Angle Capability",
                        value = "${if (telemetry.capabilitySupported == true) "지원됨" else "미지원/확인 전"} · ${telemetry.capabilityAccessTypeText}",
                        isOk = telemetry.capabilitySupported == true,
                    )
                    DiagnosticRowItem(
                        label = "3. 필수 권한 및 승인 상태",
                        value = "${telemetry.permissionName} → ${if (telemetry.isPermissionGranted) "승인됨 (GRANTED)" else "거부됨 (DENIED)"}",
                        isOk = telemetry.isPermissionGranted,
                    )
                    DiagnosticRowItem(
                        label = "4. 현재 수신된 핸들 각도",
                        value = telemetry.rawSdkAngleDeg?.let {
                            String.format(
                                Locale.US,
                                "원시 %+.2f° → 보정 핸들 %+.2f° (%s) → 추정 전륜각 δ = %+.2f°",
                                it,
                                telemetry.processedFrame.estimatedSteeringWheelAngleDeg,
                                telemetry.processedFrame.direction.koreanText,
                                telemetry.processedFrame.estimatedFrontWheelAngleRightPositiveDeg,
                            )
                        } ?: "수신된 각도 없음 (가짜 데이터 생성 안 함)",
                        isOk = telemetry.rawSdkAngleDeg != null && telemetry.processedFrame.isValid,
                    )
                    DiagnosticRowItem(
                        label = "5. 마지막 수신 시각",
                        value = lastReceivedText,
                        isOk = telemetry.lastReceivedTimestampMs != null,
                    )
                    DiagnosticRowItem(
                        label = "6. 정상 수신 여부",
                        value = when {
                            isHealthy -> "정상 수신 중 (${telemetry.processedFrame.validationNoteKorean})"
                            telemetry.connectionState == SteeringConnectionState.DIAGNOSTIC_OVERRIDE ->
                                "개발자 진단 수동 입력 활성 (${telemetry.processedFrame.validationNoteKorean})"
                            else -> "수신 불가 (${telemetry.connectionState.koreanLabel} · ${telemetry.processedFrame.validationNoteKorean})"
                        },
                        isOk = isHealthy,
                    )
                    DiagnosticRowItem(
                        label = "7. 오류 코드 및 원인",
                        value = if (telemetry.lastErrorCode != null || telemetry.lastErrorMessage != null) {
                            "[${telemetry.lastErrorCode ?: "ERROR"}] ${telemetry.lastErrorMessage ?: ""}"
                        } else {
                            "오류 없음 (NONE)"
                        },
                        isOk = telemetry.lastErrorCode == null,
                    )
                }
            }

            // 3. 핸들 각도 ↔ 전륜 조향각 변환 파이프라인 및 영점 보정 설정 (요구사항 6)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SimMainSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SimBorder),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "핸들 각도 ↔ 전륜 조향각(δ) 변환 및 영점 보정 (요구사항 6)",
                        style = MaterialTheme.typography.titleLarge,
                        color = SimPrimaryText,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "• Pleos SDK KDoc에는 전륜 조향각으로 서술되어 있으나, 내부 VHAL 프로퍼티(PERF_STEERING_ANGLE, 291505929) 및 Virtual Console은 실제로 스티어링 휠 회전각(-540° ~ +540°)을 전달합니다.\n" +
                            "• 따라서 DriveCoach는 핸들 각도와 전륜 조향각(δ)을 분리하여 기어비(기본 15.0:1)로 추정하며, 이는 시뮬레이션용 근사값임을 명확히 고지합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SimSecondaryText,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SdkAngleInterpretationMode.entries.forEach { mode ->
                            val selected = telemetry.calibration.interpretationMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onUpdateCalibration(telemetry.calibration.copy(interpretationMode = mode))
                                    },
                                color = if (selected) SimPrimaryAccentSoft else SimSecondarySurface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (selected) SimPrimaryAccent else SimBorder),
                            ) {
                                Text(
                                    text = mode.koreanTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) SimPrimaryAccent else SimPrimaryText,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }

                    // 기어비 슬라이더
                    if (telemetry.calibration.interpretationMode == SdkAngleInterpretationMode.STEERING_WHEEL_ROTATION_WITH_RATIO) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "조향 기어비 (핸들 회전각 ÷ 기어비 = 추정 전륜각 δ)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = SimPrimaryText,
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%.1f : 1 (예: 핸들 +90° → 전륜 +%.1f°)",
                                        telemetry.calibration.sanitizedRatio,
                                        90f / telemetry.calibration.sanitizedRatio,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SimPrimaryAccent,
                                )
                            }
                            Slider(
                                value = telemetry.calibration.sanitizedRatio,
                                onValueChange = { ratio ->
                                    onUpdateCalibration(telemetry.calibration.copy(steeringRatio = ratio))
                                },
                                valueRange = 12.0f..18.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = SimPrimaryAccent,
                                    activeTrackColor = SimPrimaryAccent,
                                ),
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onCalibrateZeroNow,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SimPrimaryAccent,
                                contentColor = SimMainSurface,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("현재 수신 각도를 영점(0°)으로 보정", style = MaterialTheme.typography.labelLarge)
                        }

                        Button(
                            onClick = onResetCalibration,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SimSecondarySurface,
                                contentColor = SimPrimaryText,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("영점 및 기어비 초기화", style = MaterialTheme.typography.labelLarge)
                        }

                        Surface(
                            modifier = Modifier.clickable {
                                onUpdateCalibration(
                                    telemetry.calibration.copy(invertDirection = !telemetry.calibration.invertDirection)
                                )
                            },
                            color = if (telemetry.calibration.invertDirection) SimWarningSoft else SimSecondarySurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SimBorder),
                        ) {
                            Text(
                                text = if (telemetry.calibration.invertDirection) "좌/우 부호 반전: 켜짐" else "좌/우 부호 반전: 꺼짐 (기본)",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (telemetry.calibration.invertDirection) SimWarning else SimPrimaryText,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }

            // 4. Virtual Console 테스트 안내 및 개발자 진단 입력 공급자 전환 (요구사항 4, 5)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SimMainSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SimBorder),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Virtual Console 테스트 안내 및 입력 공급자(Provider) 설정",
                        style = MaterialTheme.typography.titleLarge,
                        color = SimPrimaryText,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "• 에뮬레이터 포트 포워딩: adb forward tcp:8088 tcp:8088 → 브라우저에서 http://127.0.0.1:8088/ 접속\n" +
                            "• 기어 P 설정 후 Steering Angle을 0° → +90° → 0° → -90° → 0° 순서로 변경하면 위 수신 수치와 연습 화면의 예상 궤적이 즉시 갱신됩니다.\n" +
                            "• 기본 입력 공급자는 항상 PleosSteeringInputProvider(실제 차량 및 Virtual Console SDK)입니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SimSecondaryText,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SteeringInputSourceMode.entries.forEach { mode ->
                            val isSelected = telemetry.sourceMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSwitchInputSourceMode(mode) },
                                color = if (isSelected) SimPrimaryAccentSoft else SimSecondarySurface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSelected) SimPrimaryAccent else SimBorder),
                            ) {
                                Text(
                                    text = mode.koreanTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) SimPrimaryAccent else SimPrimaryText,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }

                    if (telemetry.sourceMode == SteeringInputSourceMode.DIAGNOSTIC_FALLBACK) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "개발자 진단 전용 테스트 입력 (일반 연습 기본 화면에서는 비활성):",
                            style = MaterialTheme.typography.labelLarge,
                            color = SimWarning,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(-90f to "-90° (좌)", 0f to "0° (중앙)", 90f to "+90° (우)", -360f to "-360°", 360f to "+360°").forEach { (deg, label) ->
                                Button(
                                    onClick = { onInjectDiagnosticAngle(deg) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SimSecondarySurface,
                                        contentColor = SimPrimaryText,
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Button(
                                onClick = { onInjectDiagnosticAngle(null) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SimCollisionSoft,
                                    contentColor = SimCollision,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("신호 끊김(null) 테스트", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticMetricBox(
    title: String,
    valueText: String,
    subText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = SimSecondarySurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SimBorder),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = SimSecondaryText,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = SimPrimaryText,
            )
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = SimSecondaryText,
            )
        }
    }
}

@Composable
private fun DiagnosticRowItem(
    label: String,
    value: String,
    isOk: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SimSecondarySurface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = SimPrimaryText,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isOk) SimPrimaryAccent else SimCollision,
            fontWeight = FontWeight.Medium,
        )
    }
}
