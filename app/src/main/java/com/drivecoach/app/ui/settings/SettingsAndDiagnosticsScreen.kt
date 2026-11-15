package com.drivecoach.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivecoach.app.data.vehicle.PleosSdkDiagnosticState
import com.drivecoach.app.domain.model.DataSourceType
import com.drivecoach.app.domain.model.ScoringWeights
import com.drivecoach.app.domain.model.SensorAvailability
import com.drivecoach.app.ui.components.DataSourceBadge
import com.drivecoach.app.ui.components.StatusPillBadge
import com.drivecoach.app.ui.theme.CardBorder
import com.drivecoach.app.ui.theme.ElevatedSurface
import com.drivecoach.app.ui.theme.MainBackground
import com.drivecoach.app.ui.theme.PrimaryAccent
import com.drivecoach.app.ui.theme.SecondaryAccent
import com.drivecoach.app.ui.theme.SecondaryBackground
import com.drivecoach.app.ui.theme.StatusCritical
import com.drivecoach.app.ui.theme.StatusSuccess
import com.drivecoach.app.ui.theme.StatusWarning
import com.drivecoach.app.ui.theme.TextMuted
import com.drivecoach.app.ui.theme.TextPrimary
import com.drivecoach.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    scoringWeights: ScoringWeights,
    preferredDataSource: DataSourceType,
    totalSessionCount: Int,
    onUpdateWeights: (ScoringWeights) -> Unit,
    onUpdatePreferredSource: (DataSourceType) -> Unit,
    onResetDemoData: () -> Unit,
    onDeleteAllSessions: () -> Unit,
    onResetSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var accelWeight by remember(scoringWeights) { mutableFloatStateOf(scoringWeights.accelerationWeight) }
    var brakeWeight by remember(scoringWeights) { mutableFloatStateOf(scoringWeights.brakingWeight) }
    var steerWeight by remember(scoringWeights) { mutableFloatStateOf(scoringWeights.steeringWeight) }
    var consistWeight by remember(scoringWeights) { mutableFloatStateOf(scoringWeights.consistencyWeight) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            containerColor = SecondaryBackground,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("전체 주행 기록 삭제 확인") },
            text = {
                Text("저장된 모든 주행 세션 기록(${totalSessionCount}건)을 삭제하시겠습니까? 삭제 후에도 '기본 데모 복원' 버튼으로 샘플 데이터를 다시 생성할 수 있습니다.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        onDeleteAllSessions()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusCritical,
                        contentColor = MainBackground,
                    ),
                ) {
                    Text("전체 삭제 실행", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("취소", color = TextSecondary)
                }
            },
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 840.dp

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DataSourcePreferenceCard(
                        preferredDataSource = preferredDataSource,
                        onUpdatePreferredSource = onUpdatePreferredSource,
                        onOpenDiagnostics = onOpenDiagnostics,
                    )
                    ScoringWeightsCustomizeCard(
                        accelWeight = accelWeight,
                        brakeWeight = brakeWeight,
                        steerWeight = steerWeight,
                        consistWeight = consistWeight,
                        onAccelChange = { accelWeight = it },
                        onBrakeChange = { brakeWeight = it },
                        onSteerChange = { steerWeight = it },
                        onConsistChange = { consistWeight = it },
                        onApply = {
                            onUpdateWeights(
                                ScoringWeights(
                                    accelerationWeight = accelWeight,
                                    brakingWeight = brakeWeight,
                                    steeringWeight = steerWeight,
                                    consistencyWeight = consistWeight,
                                )
                            )
                        },
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DataManagementAndResetCard(
                        totalSessionCount = totalSessionCount,
                        onResetDemoData = onResetDemoData,
                        onRequestDeleteAll = { showDeleteAllDialog = true },
                        onResetSettings = onResetSettings,
                    )
                    PrivacyAndAppInfoCard()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DataSourcePreferenceCard(
                    preferredDataSource = preferredDataSource,
                    onUpdatePreferredSource = onUpdatePreferredSource,
                    onOpenDiagnostics = onOpenDiagnostics,
                )
                ScoringWeightsCustomizeCard(
                    accelWeight = accelWeight,
                    brakeWeight = brakeWeight,
                    steerWeight = steerWeight,
                    consistWeight = consistWeight,
                    onAccelChange = { accelWeight = it },
                    onBrakeChange = { brakeWeight = it },
                    onSteerChange = { steerWeight = it },
                    onConsistChange = { consistWeight = it },
                    onApply = {
                        onUpdateWeights(
                            ScoringWeights(
                                accelerationWeight = accelWeight,
                                brakingWeight = brakeWeight,
                                steeringWeight = steerWeight,
                                consistencyWeight = consistWeight,
                            )
                        )
                    },
                )
                DataManagementAndResetCard(
                    totalSessionCount = totalSessionCount,
                    onResetDemoData = onResetDemoData,
                    onRequestDeleteAll = { showDeleteAllDialog = true },
                    onResetSettings = onResetSettings,
                )
                PrivacyAndAppInfoCard()
            }
        }
    }
}

@Composable
private fun DataSourcePreferenceCard(
    preferredDataSource: DataSourceType,
    onUpdatePreferredSource: (DataSourceType) -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "데이터 수집 출처 및 SDK 진단",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "실제 차량 센서(LIVE), Virtual Console/시뮬레이션(SIMULATED), 사전 데모(DEMO)를 명확히 분리합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                DataSourceBadge(dataSource = preferredDataSource)
            }

            val selectableModes = listOf(
                DataSourceType.DEMO,
                DataSourceType.SIMULATED,
                DataSourceType.LIVE,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                selectableModes.forEach { mode ->
                    val isSelected = mode == preferredDataSource
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onUpdatePreferredSource(mode) },
                        color = if (isSelected) PrimaryAccent.copy(alpha = 0.16f) else MainBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) PrimaryAccent else CardBorder,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = mode.badgeText,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) PrimaryAccent else TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = mode.koreanTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onOpenDiagnostics,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElevatedSurface,
                    contentColor = SecondaryAccent,
                ),
            ) {
                Text(
                    text = "Pleos Vehicle SDK (v2.3.2) 센서 호환성 및 기술 진단 화면 열기 →",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ScoringWeightsCustomizeCard(
    accelWeight: Float,
    brakeWeight: Float,
    steerWeight: Float,
    consistWeight: Float,
    onAccelChange: (Float) -> Unit,
    onBrakeChange: (Float) -> Unit,
    onSteerChange: (Float) -> Unit,
    onConsistChange: (Float) -> Unit,
    onApply: () -> Unit,
) {
    val sum = (accelWeight + brakeWeight + steerWeight + consistWeight).coerceAtLeast(0.01f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "설명 가능한 운전 점수 가중치 설정",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Text(
                text = "운전자의 학습 목표에 맞춰 4개 세부 지표의 반영 비중을 조정할 수 있습니다. (저장 시 자동 정규화 및 재산출)",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            WeightSliderRow(
                label = "가속 안정성 (기본 25%)",
                normalizedPercent = ((accelWeight / sum) * 100).roundToInt(),
                rawValue = accelWeight,
                onValueChange = onAccelChange,
            )
            WeightSliderRow(
                label = "감속·제동 안정성 (기본 30%)",
                normalizedPercent = ((brakeWeight / sum) * 100).roundToInt(),
                rawValue = brakeWeight,
                onValueChange = onBrakeChange,
            )
            WeightSliderRow(
                label = "조향 안정성 (기본 25%)",
                normalizedPercent = ((steerWeight / sum) * 100).roundToInt(),
                rawValue = steerWeight,
                onValueChange = onSteerChange,
            )
            WeightSliderRow(
                label = "주행 일관성 (기본 20%)",
                normalizedPercent = ((consistWeight / sum) * 100).roundToInt(),
                rawValue = consistWeight,
                onValueChange = onConsistChange,
            )

            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryAccent,
                    contentColor = MainBackground,
                ),
            ) {
                Text(
                    text = "가중치 적용 및 전체 기록 점수 재산출",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun WeightSliderRow(
    label: String,
    normalizedPercent: Int,
    rawValue: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                text = "정규화 비중 ${normalizedPercent}%",
                style = MaterialTheme.typography.labelLarge,
                color = SecondaryAccent,
            )
        }
        Slider(
            value = rawValue,
            onValueChange = onValueChange,
            valueRange = 0.05f..0.60f,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryAccent,
                activeTrackColor = PrimaryAccent,
            ),
        )
    }
}

@Composable
private fun DataManagementAndResetCard(
    totalSessionCount: Int,
    onResetDemoData: () -> Unit,
    onRequestDeleteAll: () -> Unit,
    onResetSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "로컬 데이터 관리 및 초기화",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                StatusPillBadge(text = "저장 세션 ${totalSessionCount}건", accentColor = PrimaryAccent)
            }

            Text(
                text = "모든 주행 기록과 설정은 기기 내부 SQLite DB 및 SharedPreferences에 로컬 저장되어 앱 재시작 후에도 유지됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            Button(
                onClick = onResetDemoData,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SecondaryAccent,
                    contentColor = MainBackground,
                ),
            ) {
                Text(
                    text = "5개 기본 데모 주행 시나리오 복원",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onResetSettings,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElevatedSurface,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Text(
                        text = "설정·가중치 초기화",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Button(
                    onClick = onRequestDeleteAll,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusCritical.copy(alpha = 0.2f),
                        contentColor = StatusCritical,
                    ),
                ) {
                    Text(
                        text = "전체 주행 기록 삭제",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyAndAppInfoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SecondaryBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "개인정보 보호 · 안전 고지 · 버전 정보",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Text(
                text = "• DriveCoach v1.0 (Hyundai Pleos Connect / AAOS Prototype)\n" +
                    "• Vehicle SDK: ai.pleos.playground:Vehicle:2.3.2 (Read-Only)\n" +
                    "• 차량 제어 API(CONTROL_CAR_*)는 일절 사용하지 않으며 읽기 전용 센서만 조회합니다.\n" +
                    "• 수집·생성된 주행 기록은 외부 서버로 무단 전송되지 않으며, 기기 내 로컬 저장소에만 보관됩니다.\n" +
                    "• 코칭 엔진: 설명 가능한 규칙 기반(Rule-based) 코칭 엔진 v1.0 (Pluggable CoachingGenerator 아키텍처)",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

// ============================================================================
// 개발자 및 심사위원용 기술 진단 화면 (섹션 15 & 16.8)
// ============================================================================

@Composable
fun DiagnosticsScreen(
    diagnosticState: PleosSdkDiagnosticState,
    onRefreshDiagnostics: () -> Unit,
    onToggleLiveListening: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeSource = if (diagnosticState.isSdkInitialized) DataSourceType.LIVE else DataSourceType.UNAVAILABLE

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. 런타임 및 SDK 연결 상태 요약
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SecondaryBackground,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pleos Connect Vehicle SDK 기술 진단",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                        )
                        Text(
                            text = if (diagnosticState.isPleosDeviceOrEmulator) {
                                "Pleos Connect / AAOS 환경 감지됨 · ${diagnosticState.sdkArtifactVersion}"
                            } else {
                                "일반 Android 런타임 · ${diagnosticState.sdkArtifactVersion}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryAccent,
                        )
                    }
                    DataSourceBadge(dataSource = activeSource)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DiagSummaryChip(
                        label = "SDK 아티팩트",
                        value = "Vehicle:2.3.2",
                        accent = PrimaryAccent,
                        modifier = Modifier.weight(1f),
                    )
                    DiagSummaryChip(
                        label = "SDK 초기화",
                        value = if (diagnosticState.isSdkInitialized) "정상 (OK)" else "미연결/대기",
                        accent = if (diagnosticState.isSdkInitialized) StatusSuccess else StatusWarning,
                        modifier = Modifier.weight(1f),
                    )
                    DiagSummaryChip(
                        label = "현재 기어 상태",
                        value = diagnosticState.currentGear.koreanName,
                        accent = SecondaryAccent,
                        modifier = Modifier.weight(1f),
                    )
                    DiagSummaryChip(
                        label = "수신 콜백 수",
                        value = "${diagnosticState.receivedPacketCount}회",
                        accent = PrimaryAccent,
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = "서비스 상태: ${diagnosticState.vehicleServiceStatusText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )

                if (diagnosticState.lastErrorMessage != null) {
                    Surface(
                        color = StatusWarning.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = "진단 참고 메시지: ${diagnosticState.lastErrorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusWarning,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onRefreshDiagnostics,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryAccent,
                            contentColor = MainBackground,
                        ),
                    ) {
                        Text(
                            text = "센서 Capability 및 권한 재검증",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Button(
                        onClick = onToggleLiveListening,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (diagnosticState.isListening) StatusWarning else SecondaryAccent,
                            contentColor = MainBackground,
                        ),
                    ) {
                        Text(
                            text = if (diagnosticState.isListening) "실시간 리스너 중지" else "실시간 센서 리스너 시작",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // 2. 센서별 가용성 및 권한 매트릭스
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SecondaryBackground,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "센서별 SDK API · 권한 · 하드웨어 지원 매트릭스",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = "Virtual Console에서 조작 가능한 신호라도 공개 SDK API 및 런타임 권한 허용 여부에 따라 접근성이 달라집니다. DriveCoach는 접근 불가 신호를 가짜 값으로 숨기지 않고 정직하게 보고합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )

                diagnosticState.sensorAvailabilities.forEach { sensor ->
                    SensorAvailabilityRowCard(sensor = sensor)
                }
            }
        }
    }
}

@Composable
private fun DiagSummaryChip(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ElevatedSurface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SensorAvailabilityRowCard(sensor: SensorAvailability) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MainBackground,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sensor.sensorName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = "API: ${sensor.sdkDomainAndMethod}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryAccent,
                    )
                }
                DataSourceBadge(dataSource = sensor.availabilityStatus, showKoreanTitle = false)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPillBadge(
                    text = "권한: ${sensor.requiredPermission} (${if (sensor.permissionGranted) "허용" else "미허용"})",
                    accentColor = if (sensor.permissionGranted) StatusSuccess else StatusWarning,
                )
                StatusPillBadge(
                    text = "AccessType: ${sensor.capabilityAccessType}",
                    accentColor = if (sensor.usableForAnalysis) SecondaryAccent else TextMuted,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "최신 조회값: ${sensor.sampleValueText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryAccent,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (sensor.failureReason != null) {
                Text(
                    text = "제한 사유: ${sensor.failureReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusWarning,
                )
            }

            if (sensor.notes.isNotBlank()) {
                Text(
                    text = "기술 노트: ${sensor.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}
