# Pleos Connect Vehicle SDK 센서 호환성 및 검증 보고서 (`SENSOR_COMPATIBILITY.md`)

본 문서는 현대자동차 공식 Pleos Connect SDK (`ai.pleos.playground:Vehicle:2.3.2`)의 실제 바이너리 및 소스 JAR(`Vehicle-2.3.2-sources.jar`)를 정밀 분석하여, 일반 서드파티 앱에서 접근 가능한 차량 신호와 접근 불가능한 신호를 투명하게 기록한 기술 검증 문서입니다.

---

## 1. 핵심 검증 결론

Virtual Console에서 차량 속도나 조향각 슬라이더를 조작할 수 있더라도, **서드파티 앱이 공개 SDK를 통해 실제로 읽을 수 있는 신호는 SDK API 노출 여부 및 플랫폼 권한 정책에 의해 결정**됩니다.

DriveCoach는 접근 불가능한 센서를 가짜 데이터로 위장하여 `LIVE`로 표시하지 않으며, **`PleosTelemetryProvider`와 `SDK 진단 화면(DIAGNOSTICS)`에서 각 센서의 실제 가용성과 대체 분석 전략을 정직하게 보고**합니다.

---

## 2. 센서별 SDK API · 권한 · 가용성 매트릭스 (`Vehicle:2.3.2`)

| 센서 / 차량 신호 | Pleos SDK API (`Vehicle:2.3.2`) | 내부 VHAL Property | 필요 권한 | SDK 노출 여부 | 물리 단위 및 기술적 주의사항 |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **전륜 조향각** | `Vehicle.getSteeringWheel()`<br>`getCurrentSteeringWheelAngle()`<br>`registerSteeringWheelAngle()` | `PERF_STEERING_ANGLE` | `pleos.car.permission.CAR_DRIVE` | **지원 (`LIVE`)** | **주의**: 핸들 회전각($-540^\circ \sim +540^\circ$)이 아니라 **전륜 타이어 조향각(degrees, 좌측 음수)**을 반환함 (SDK KDoc 명시). `TelemetrySample.roadWheelAngleDeg`에 분리 저장 |
| **브레이크 페달 작동 여부** | `Vehicle.getBrake()`<br>`getCurrentBrakePedalStatus()`<br>`registerBrakePedalStatus()` | `BRAKE_PEDAL_STATUS` | `pleos.car.permission.CAR_DRIVE` | **지원 (`LIVE`)** | `Boolean?` (작동 ON/OFF). 0~100% 연속 답력 수치는 제공되지 않음 |
| **누적/트립 주행거리** | `Vehicle.getOdometer()`<br>`getCurrentDistanceDriven()`<br>`getTripCurrentDrivingDistance()` | `PERF_ODOMETER`<br>`TRIP_CURRENT_DRIVING_DISTANCE` | `pleos.car.permission.CAR_DRIVE` | **지원 (`LIVE`)** | 누적 거리(`km`, `Float?`), 현재 트립 주행거리(`m`, `Float?`) 제공 |
| **변속 기어 상태** | `Vehicle.getGear()`<br>`getCurrentGearSelectionState()`<br>`registerGearSelection()` | `GEAR_SELECTION` | `pleos.car.permission.CAR_DRIVE` | **지원 (`LIVE`)** | `VehicleGear` (`GEAR_PARK`, `GEAR_DRIVE`, `GEAR_REVERSE`, `GEAR_NEUTRAL`) → 주차(P) 안전 제한 판정에 활용 |
| **방향지시등 상태** | `Vehicle.getTurnSignal()`<br>`getCurrentTurnSignalState()`<br>`registerTurnSignalState()` | `TURN_SIGNAL_STATE` | `pleos.car.permission.CAR_LIGHTS` | **지원 (`LIVE`)** | `TurnSignalValue` (`NONE`, `LEFT`, `RIGHT`) → 교차로 회전 의도 참고 지표로 활용 |
| **차량 주행 속도** | `Vehicle:2.3.2` 미노출<br>*(DrivingMode는 크루즈 목표 속도만 제공)* | `PERF_VEHICLE_SPEED` (AAOS 표준 `CarPropertyManager`) | `android.car.permission.CAR_SPEED` | **미노출 (`UNAVAILABLE`)** | `Vehicle:2.3.2` 공개 클래스에 실시간 차속 API가 없음. 트립 거리 미분($\Delta d / \Delta t$) 참고 또는 `SIMULATED`/`DEMO` 모드로 전환 안내 |
| **가속 페달 개도량 (%)** | `Vehicle:2.3.2` 미노출 | VHAL 비공개 / 시스템 전용 | 시스템 서명 권한 필요 | **미노출 (`UNAVAILABLE`)** | 서드파티 앱에서 조회 불가. 종방향 가속도($\Delta v / \Delta t$) 기반으로 출발·가속 안정성을 분석 |

---

## 3. 핸들 조향각 vs 전륜 조향각 분리 설계

자동차 공학에서 운전자가 돌리는 **스티어링 휠(핸들) 회전각($\delta_{sw}$)**과 실제 도로와 맞닿는 **전륜 타이어 조향각($\delta_{rw}$)** 사이에는 조향 기어비(Steering Ratio, 승용차 기준 약 $14:1 \sim 16:1$)가 존재합니다.

- Pleos SDK `SteeringWheel.getCurrentSteeringWheelAngle()`의 공식 KDoc은 다음과 같이 정의되어 있습니다:
  > *"Current steering wheel angle in degrees. Positive value means steering wheel is turned to the right; negative value means turned to the left. Note: This represents the front wheel angle, not the steering wheel angle."*
- 따라서 DriveCoach는 `TelemetrySample` 데이터 모델에 `steeringWheelAngleDeg`와 `roadWheelAngleDeg`를 별도 필드로 분리하고, 조향 그래프 및 저속 회전 수정 조향 탐지 알고리즘이 **전륜 조향각(`roadWheelAngleDeg`, 범위 약 $-38^\circ \sim +38^\circ$)** 기준으로 정확히 동작하도록 구현했습니다.
