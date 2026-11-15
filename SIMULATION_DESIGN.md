# SIMULATION_DESIGN.md — 차량운동학 및 시뮬레이션 엔진 설계 문서

본 문서는 **DriveCoach** 시뮬레이터의 수학적·기하학적 설계 원칙과 구현 구조를 설명합니다. 모든 시뮬레이션 로직은 Jetpack Compose UI와 분리된 순수 Kotlin 패키지(`com.drivecoach.app.simulation.*`)로 구현되어 있습니다.

---

## 1. 월드 좌표계 및 기준점 정의

### 1.1 월드 좌표계 (`WorldBounds` / `Vec2`)
- **단위**: 미터($\text{m}$)
- **축 방향**:
  - $+X$: 화면 우측 방향 ($\theta = 0\text{ rad}$)
  - $+Y$: 화면 상단 방향 ($\theta = +\frac{\pi}{2}\text{ rad}$)
- **화면 좌표 변환 (`WorldViewportTransform`)**:
  - 캔버스 픽셀 해상도(`viewportWidthPx`, `viewportHeightPx`)가 달라지거나 화면이 분할되더라도 가로·세로 동일한 `pixelsPerMeter` 등방성 스케일(1:1 종횡비)을 유지하여 차량과 주차 구획의 기하학적 비율이 왜곡되지 않도록 보장합니다.

### 1.2 차량 기준점 (Reference Point)
- 차량 상태 `VehicleState(x, y, headingRad, velocityMps, steeringAngleRad)`의 위치 $(x, y)$는 **후륜 차축 중앙(Rear Axle Center)** 으로 통일되어 있습니다.
- 후륜 차축 중앙 $(x, y)$로부터 주요 기하 위치는 다음과 같이 일관되게 유도됩니다 (`VehicleGeometry.computeFootprint`):
  - 전방 단위 벡터: $\mathbf{f} = (\cos\theta, \sin\theta)$
  - 좌측 단위 벡터: $\mathbf{l} = (-\sin\theta, \cos\theta)$
  - **전륜 차축 중앙**: $\mathbf{p}_{\text{frontAxle}} = \mathbf{p}_{\text{rearAxle}} + L \cdot \mathbf{f}$
  - **차체 기하 중심 (OBB Center)**:
    $$\mathbf{p}_{\text{bodyCenter}} = \mathbf{p}_{\text{rearAxle}} + \frac{(L + o_{\text{front}}) - o_{\text{rear}}}{2} \cdot \mathbf{f}$$
    기본 제원($L = 2.7\text{ m}, o_{\text{front}} = 0.9\text{ m}, o_{\text{rear}} = 0.9\text{ m}$)에서 차체 중심은 후륜 차축 중앙보다 전방으로 $+1.35\text{ m}$ 위치합니다.

---

## 2. 저속 차량운동학 모델 (Kinematic Bicycle Model)

`KinematicBicycleModel`은 저속 주차 및 교차로 주행 시 타이어 횡슬립이 무시할 수 있을 만큼 작다는 기구학적 가정 아래 다음 상미분방정식을 적분합니다:

$$\frac{dx}{dt} = v \cos(\theta)$$
$$\frac{dy}{dt} = v \sin(\theta)$$
$$\frac{d\theta}{dt} = \frac{v}{L} \tan(\delta)$$

- $v$: 종방향 속도 ($\text{m/s}$, 전진 $v > 0$, 후진 $v < 0$, 정지 $v = 0$)
- $L$: 축거 (`wheelbaseMeters`, 기본 $2.70\text{ m}$)
- $\delta$: 전륜 조향각 (`steeringAngleRad`, 물리 좌표계 기준 좌회전 $+\delta$, 우회전 $-\delta$, 범위 $[-\delta_{\max}, +\delta_{\max}]$)
- **순간 회전 반경 (후륜 차축 중앙 기준)**:
  $$R = \frac{L}{|\tan(\delta)|}$$
  기본 제원($L = 2.70\text{ m}, \delta_{\max} = 32^\circ$)에서 후륜 차축 최소 회전 반경은 $R_{\min} \approx 4.32\text{ m}$이며, 바깥쪽 앞범퍼 모서리의 최대 스윕 반경은 $\sqrt{(R + W/2)^2 + (L + o_{\text{front}})^2}$로 계산되어 **내륜차(Inner Rear Off-tracking)** 와 **외륜차(Outer Front Swing)** 가 자연스럽게 재현됩니다.

### 수치 적분 및 안정성 방어
- 프레임 드랍이나 호출 주기 변화에 영향을 받지 않도록 내부적으로 `FIXED_SUBSTEP_DT_SEC = 0.02s` ($50\text{ Hz}$) 단위로 분할하여 2차 중점법(Midpoint / RK2)으로 적분합니다.
- 모든 입력에 대해 `NaN`/`Infinity` 방어(`sanitizeState`), 각도 정규화(`normalizeAngleRad` $\in [-\pi, +\pi]$), 최대 조향각 클램핑(`clampSteeringAngleRad`), 속도 클램핑(`clampVelocityMps`)을 수행합니다.

---

## 3. SAT 기반 회전 직사각형(OBB) 충돌 판정

차량 차체와 주변 주차 차량·연석·도로 경계는 모두 회전 각도 `angleRad`를 갖는 2D 회전 직사각형(`OrientedBox2D`)으로 표현됩니다.

- **분리축 정리 (Separating Axis Theorem, SAT)**:
  - 두 OBB $A, B$에 대해 총 4개의 정규직교 후보 분리축($\mathbf{f}_A, \mathbf{l}_A, \mathbf{f}_B, \mathbf{l}_B$) 위로 각 직사각형의 4개 꼭짓점을 정사영합니다.
  - 모든 축에서 투영 구간이 겹치면(`separationDistanceMeters(A, B) <= 0f`) 충돌로 판정하고, 하나라도 분리된 축이 존재하면 최대 분리 간격을 최소 이격 여유 거리($\text{m}$)로 반환합니다.
- **연속 서브스텝 충돌 검사 (`stepWithContinuousCollisionCheck`)**:
  - 차량 이동 시 $0.02\text{ s}$ 서브스텝마다 OBB 충돌 검사를 수행하여, 이동 속도가 빠르거나 얇은 경계선과 접촉할 때도 충돌이 누락(Tunneling)되지 않고 접촉 즉시 정지하도록 구현했습니다.

---

## 4. 예상 이동 경로 및 운동학 기반 연습용 기준 경로

1. **실시간 예상 이동 경로 (`TrajectoryPredictor`)**:
   - 현재 차량 상태 $(x, y, \theta)$, 현재 조향각 $\delta$, 선택된 가상 기어 방향(전진/후진)을 유지한 채 미래 $4.8\text{ m}$ 구간을 $0.15\text{ m}$ 간격으로 시뮬레이션하여 후륜 중심 궤적과 좌·우 모서리 스윕 가이드를 실시간 점선으로 렌더링합니다.
   - 예상 경로 상에서 장애물 충돌이 감지되면 즉시 붉은색 경고 점선과 예상 접촉 지점을 표시합니다.
2. **연습용 기준 경로 (`ReferencePathResult`)**:
   - 화면에 표시하기 위해 임의로 그린 베지어 곡선이 아니라, 실제로 `KinematicBicycleModel.integrateSingleSubstep`을 실행하고 매 스텝 `CollisionDetector.checkVehicleCollision`을 통과한 **운동학적으로 실현 가능한 경로**만 생성합니다.
   - 만약 사용자가 설정에서 주차 구획 길이를 차량이 물리적으로 진입할 수 없을 만큼 좁게 줄이면, 억지 경로를 그리지 않고 `ReferencePathResult.Unavailable("현재 주차 공간에서는 기준 경로를 생성하지 못했습니다.")`를 반환합니다.

---

## 5. 주차 및 주행 성공 판정 (`ParkingAndTurnEvaluator`)

### 주차 시나리오 (평행주차 / 후진주차 / 전면주차) 성공 조건
1. 차량 차체 OBB의 4개 모서리가 모두 목표 주차 구획(`targetZone`) 내부에 포함됨 (`containsBox`)
2. 주변 차량·연석·경계선과 충돌이 없음 (`!collisionResult.isColliding`)
3. 차량 진행 방향($\theta$)과 주차 구획 기준 방향의 각도 오차가 허용 범위($\pm 7.5^\circ$) 이내임
4. 주변 장애물과의 최소 여유 거리가 안전 여유 기준(`safetyMarginMeters = 0.12m`) 이상임
5. 차량이 완전히 정지 상태임 (`state.isStopped`)

---

## 6. 시뮬레이션 한계 명시

- 본 엔진은 저속 기구학(Kinematic) 모델이므로 고속 주행 시의 타이어 슬립각(Slip Angle), 노면 마찰 한계, 횡방향 하중 이동, 서스펜션 롤/피치 동역학을 포함하지 않습니다.
- 실제 차량의 스티어링 휠 회전수(Lock-to-Lock 기어비) 및 유압/MDPS 응답 지연 대신, 학습자가 전륜 조향각($\delta$)과 회전 중심의 관계를 직관적으로 실험할 수 있도록 전륜 조향각 직접 제어 방식을 채택했습니다.
