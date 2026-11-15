# DriveCoach — 실전형 차량 조작·주차 연습 시뮬레이터

> **현대자동차 Pleos Connect / Android Automotive OS 전용 초보 운전자 차량 조작·주차 연습 시뮬레이터**

---

## 1. 프로젝트 개요

**DriveCoach**는 실제 차량이 **주차(P)** 되어 있는 동안 차량 내 인포테인먼트 디스플레이(Pleos Connect)에서 **평행주차, 후진주차, 전면주차, 교차로 우회전·좌회전, 차폭 감각**을 직접 조작하며 연습할 수 있는 인터랙티브 차량 운동학 시뮬레이터입니다.

운전자가 화면의 가상 핸들(조향각 $\delta$)을 돌리고 가상 기어(**전진 D / 후진 R**)를 선택해 이동하면, 화면 속 차량이 **저속 차량운동학 모델(2D Kinematic Bicycle Model)** 에 따라 실제 기구학적 궤적을 그리며 움직입니다.

- **핵심 가치**:
  - 어느 위치에서 조향을 시작해야 하는지, 핸들 각도에 따라 차량 회전 반경과 앞·뒤 모서리 스윕 궤적이 어떻게 달라지는지를 눈으로 확인하고 반복 실험할 수 있습니다.
  - 정돈된 라이트 톤 자동차 HMI 디자인(`#F6F7F8` 배경, `#FFFFFF` 서피스, `#285F95` 포인트 컬러)을 적용하여 차량 디스플레이에서 한눈에 직관적으로 조작할 수 있습니다.

---

## 2. 정보 구조 (Information Architecture)

앱을 실행하면 복잡한 점수판이나 분석 차트 없이 **주차**와 **주행** 두 가지 대형 카드만 표시됩니다.

1. **홈 화면 (`DriveCoachHomeScreen`)**
   - **주차** 카드 (`평행주차 · 후진주차 · 전면주차`)
   - **주행** 카드 (`우회전 · 좌회전 · 차폭 감각`)
   - 우측 상단 **설정** 버튼
2. **주차 연습 목록 (`PracticeCategory.PARKING`)**
   - **평행주차** *(핵심 시나리오)*: 앞차와 뒤차 사이 도로변 주차 구획에 후진 S-커브로 진입 및 정렬
   - **후진주차**: 좌·우 주차 차량 사이 직각 주차 구획에 후진으로 진입
   - **전면주차**: 안쪽 뒷바퀴 내륜차와 바깥쪽 앞범퍼 외륜차를 고려해 전진으로 직각 구획 진입
   - **좁은 공간 주차**: `후속 확장 예정` (구조 확장용 비활성 항목)
3. **주행 연습 목록 (`PracticeCategory.DRIVING`)**
   - **우회전**: 교차로 안쪽 연석(내륜차)과 진출 차로 중앙선을 침범하지 않고 우회전
   - **좌회전**: 교차로 중앙선을 조기 침범하지 않고 목표 차로 중앙으로 좌회전
   - **차폭 감각**: 좌·우 장애물 통로를 통과하며 실시간 좌·우 여유 거리(cm)와 중심 편향 확인
   - **차로 중앙 유지**: `후속 확장 예정` (구조 확장용 비활성 항목)

---

## 3. 실행 및 빌드 방법

### 요구 환경
- **Android Studio**: Ladybug 이상 권장 (AGP 9.0.0, Kotlin 2.2.10, JDK 11)
- **Target / Min SDK**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 28`
- **Vehicle SDK**: `ai.pleos.playground:Vehicle:2.3.2` (Pleos Connect 공식 Maven 저장소 연동)

### 빌드 및 단위 테스트 명령
```bash
# 디버그 APK 빌드
./gradlew :app:assembleDebug

# 차량운동학·충돌판정·성공판정·기준경로 단위 테스트 실행
./gradlew :app:testDebugUnitTest
```

---

## 4. 패키지 및 모듈 구조

```text
com.drivecoach.app
├── MainActivity.kt                         # 앱 진입점 및 시스템 라이트 바 설정
├── simulation/                             # Compose UI와 분리된 순수 Kotlin 시뮬레이션 엔진
│   ├── vehicle/
│   │   └── VehicleModels.kt                # VehicleConfig, VehicleState, VirtualGearDirection
│   ├── geometry/
│   │   └── SimulationGeometry.kt           # Vec2, OrientedBox2D(OBB), VehicleGeometry, WorldViewportTransform
│   ├── physics/
│   │   └── KinematicBicycleModel.kt        # 2D Kinematic Bicycle Model (고정 서브스텝 0.02s RK2 적분)
│   ├── collision/
│   │   └── CollisionAndEvaluation.kt       # SAT OBB 충돌 감지, TrajectoryPredictor, ParkingAndTurnEvaluator
│   └── scenarios/
│       └── SimulatorScenarios.kt           # 6대 활성 시나리오 정의 및 운동학 적분 기반 연습용 기준 경로 생성기
├── ui/
│   ├── simulator/
│   │   ├── PracticeSimulatorViewModel.kt   # 시뮬레이터 상태 관리, 50Hz 고정 시간 간격 물리 루프, 주차 안전 게이트
│   │   ├── HomeAndCategoryScreens.kt       # 단순화된 홈 화면(주차/주행 2카드), 카테고리 선택 화면, 제원 설정 화면
│   │   └── SimulatorCanvasAndControls.kt   # Top-down 월드 캔버스, 가상 스티어링 휠, 가상 기어 및 이동 컨트롤 패널
│   └── theme/
│       ├── Color.kt                        # 자동차 HMI 라이트 팔레트 정의
│       ├── Theme.kt                        # Material3 LightColorScheme 구성
│       └── Type.kt                         # 차량 디스플레이용 고가독성 타이포그래피
└── data/vehicle/
    └── PleosTelemetryProvider.kt           # 현대자동차 Pleos Connect Vehicle SDK 기어/주차 상태 조회
```

---

## 5. 물리적 한계 및 안전 고지

1. **주차 상태 전용 실행 정책**:
   - 본 앱은 차량이 **주차(P)** 상태일 때만 연습할 수 있도록 설계되었습니다. 실제 차량 주행 신호(`DRIVE` / `REVERSE`)가 감지되거나 설정에서 주행 제한 테스트를 활성화하면 조작 화면이 즉시 차단되고 안전 안내 화면(`ParkedOnlySafetyNoticeScreen`)이 표시됩니다.
2. **실제 차량 비제어**:
   - 앱 내의 핸들, 전진(D), 후진(R) 버튼은 화면 속 가상 자동차만 움직이며 실제 차량의 조향·변속·제동 계통을 일절 제어하지 않습니다.
3. **시뮬레이션 모델의 한계**:
   - 본 시뮬레이터는 저속 주차 및 회전 궤적 이해를 위한 **2D 운동학 자전거 모델(Kinematic Bicycle Model)** 을 사용합니다.
   - 타이어 슬립, 노면 마찰, 서스펜션 하중 이동, 경사도, 실제 차량의 가변 기어비 및 운전석 3차원 시야각은 그대로 재현하지 않으며, 기본 차량 제원(`전장 4.50m, 전폭 1.80m, 축거 2.70m, 최대 전륜 조향각 32°`)은 시뮬레이션용 예시값입니다.
