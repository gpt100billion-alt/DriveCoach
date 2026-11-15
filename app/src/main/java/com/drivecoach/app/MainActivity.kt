package com.drivecoach.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.drivecoach.app.ui.simulator.DriveCoachSimulatorAppRoot
import com.drivecoach.app.ui.simulator.PracticeSimulatorViewModel
import com.drivecoach.app.ui.simulator.SimulatorScreenRoute
import com.drivecoach.app.ui.theme.DriveCoachTheme

class MainActivity : ComponentActivity() {

    private val simulatorViewModel: PracticeSimulatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            DriveCoachTheme {
                val uiState by simulatorViewModel.uiState.collectAsState()
                val diagnosticState by simulatorViewModel.pleosDiagnosticState.collectAsState()

                BackHandler(
                    enabled = uiState.navigationHistory.isNotEmpty() ||
                        uiState.currentRoute != SimulatorScreenRoute.HOME
                ) {
                    simulatorViewModel.navigateBack()
                }

                DriveCoachSimulatorAppRoot(
                    uiState = uiState,
                    diagnosticState = diagnosticState,
                    onSelectCategory = simulatorViewModel::selectCategoryFromHome,
                    onSelectScenario = simulatorViewModel::openPracticeIntro,
                    onStartPracticeFromIntro = simulatorViewModel::startPracticeFromIntro,
                    onOpenPracticeResult = simulatorViewModel::openPracticeResult,
                    onRetryFromResult = simulatorViewModel::retryFromResult,
                    onStartNextScenario = simulatorViewModel::startNextScenario,
                    onNavigateBack = { simulatorViewModel.navigateBack() },
                    onOpenHome = simulatorViewModel::openHome,
                    onOpenSettings = simulatorViewModel::openSettings,
                    onOpenSteeringDiagnostics = simulatorViewModel::openSteeringDiagnostics,
                    onOpenSimulatorDirect = simulatorViewModel::openSimulatorDirect,
                    onSelectGear = simulatorViewModel::selectVirtualGear,
                    onSetHoldMoving = simulatorViewModel::setHoldMoving,
                    onToggleLatchMoving = simulatorViewModel::toggleLatchMoving,
                    onStopMoving = simulatorViewModel::stopMovementImmediate,
                    onResetScenario = simulatorViewModel::resetCurrentScenario,
                    onToggleReferencePath = simulatorViewModel::toggleShowReferencePath,
                    onToggleHelpDialog = simulatorViewModel::toggleHelpDialog,
                    onToggleRearAxleDot = simulatorViewModel::toggleRearAxleReferenceDot,
                    onAdjustZoom = simulatorViewModel::adjustZoom,
                    onUpdateConfig = simulatorViewModel::updateVehicleAndSpaceConfig,
                    onToggleDrivingRestrictionSimulation = simulatorViewModel::toggleDrivingSafetyRestrictionSimulation,
                    onRefreshPleosState = simulatorViewModel::refreshPleosSdkState,
                    onCalibrateSteeringZero = simulatorViewModel::calibrateSteeringZeroAtCurrentAngle,
                    onResetSteeringCalibration = simulatorViewModel::resetSteeringCalibration,
                    onUpdateSteeringCalibration = simulatorViewModel::updateSteeringCalibration,
                    onSwitchSteeringSourceMode = simulatorViewModel::switchSteeringInputSourceMode,
                    onInjectDiagnosticSteeringAngle = simulatorViewModel::injectDiagnosticSteeringAngle,
                )
            }
        }
    }
}
