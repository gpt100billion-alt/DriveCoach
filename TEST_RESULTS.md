# TEST_RESULTS.md — 빌드 및 단위 테스트 검증 결과

## 1. 빌드 및 테스트 실행 요약

- **실행 명령**: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
- **빌드 상태**: `BUILD SUCCESSFUL` (컴파일 에러 0건)
- **전체 단위 테스트 결과**: **총 41개 테스트 통과 (41 passed, 0 skipped, 0 failed)**
  - 신규 시뮬레이터 엔진 테스트 (`VehicleSimulatorEngineTest`): **20개 통과**
  - 기존 도메인 보존 테스트 (`DriveCoachAnalysisAndScoringTest` 외): **21개 통과**

---

## 2. 시뮬레이터 단위 테스트 항목별 검증 내역 (`VehicleSimulatorEngineTest.kt`)

### 2.1 Vehicle Kinematics Tests (2D 운동학 자전거 모델)
| 테스트 메서드 | 검증 내용 | 결과 |
|---|---|---|
| `kinematics - steering angle 0 deg moves strictly straight` | 조향각 $\delta = 0^\circ$일 때 횡방향 편차 없이 직진 이동하는지 검증 | PASS |
| `kinematics - positive steering angle turns vehicle counterclockwise when moving forward` | 전진 중 좌측 조향($\delta > 0$) 시 진행 각도($\theta$) 증가 및 좌측 곡선 주행 검증 | PASS |
| `kinematics - negative steering angle turns vehicle in opposite direction` | 좌·우 대칭 조향 입력($\pm\delta$)에 대해 정확히 반대 방향 대칭 궤적을 그리는지 검증 | PASS |
| `kinematics - forward and reverse movements advance in opposite directions` | 전진($v > 0$)과 후진($v < 0$) 시 이동 방향 부호가 정확히 반대인지 검증 | PASS |
| `kinematics - zero velocity keeps position and heading unchanged` | 정지 상태($v = 0$)에서 조향각을 변경하더라도 위치와 차체 각도가 변하지 않는지 검증 | PASS |
| `kinematics - steering angle exceeding max limit is clamped` | 최대 조향각($32^\circ$)을 초과하는 입력이 $\delta_{\max}$로 안전하게 제한되는지 검증 | PASS |
| `kinematics - time step subdivision maintains consistent travel distance and heading` | $0.2\text{ s}$ 단일 호출과 $0.02\text{ s} \times 10$회 분할 호출의 적분 결과가 일치하는지 검증 | PASS |
| `kinematics - NaN or infinite inputs are safely sanitized without crash` | `NaN` 및 `Infinity` 비정상 입력 시 예외 없이 유한한 안전 상태로 복구되는지 검증 | PASS |

### 2.2 Vehicle Geometry & Transform Tests (차량 형상 및 좌표 변환)
| 테스트 메서드 | 검증 내용 | 결과 |
|---|---|---|
| `geometry - vehicle length width and rear axle offset are accurately applied` | 전장($4.5\text{ m}$), 전폭($1.8\text{ m}$), 후륜 차축 대비 차체 중심 오프셋($+1.35\text{ m}$) 일치 검증 | PASS |
| `geometry - rotated vehicle corners match expected 2D OBB coordinates` | 차체 회전 시 4개 모서리 월드 좌표가 OBB 수식과 일치하는지 검증 | PASS |
| `geometry - changing VehicleConfig dynamically updates bounding box dimensions` | 차량 제원 변경 시 OBB 크기와 바퀴 위치가 즉시 갱신되는지 검증 | PASS |
| `geometry - world to screen and screen to world transform round-trips accurately` | 월드 좌표($\text{m}$) $\leftrightarrow$ 화면 좌표($\text{px}$) 양방향 변환 정밀도 검증 | PASS |

### 2.3 Collision Detection Tests (SAT 회전 직사각형 충돌 판정)
| 테스트 메서드 | 검증 내용 | 결과 |
|---|---|---|
| `collision - detects front parked vehicle collision` | 평행주차 앞 차량(`FRONT_PARKED_CAR`)과의 접촉 감지 검증 | PASS |
| `collision - detects rear parked vehicle collision` | 평행주차 뒤 차량(`REAR_PARKED_CAR`)과의 접촉 감지 검증 | PASS |
| `collision - detects curb collision` | 도로변 연석(`CURB`)과의 접촉 감지 검증 | PASS |
| `collision - non-overlapping vehicle state reports no collision` | 초기 준비 상태에서 비충돌 판정 및 양수 여유 거리 계산 검증 | PASS |
| `collision - detects rotated corner overlap accurately via SAT` | $35^\circ$ 회전된 차체 모서리 간 미세 접촉을 SAT 알고리즘이 정확히 감지하는지 검증 | PASS |
| `collision - continuous substepping prevents fast movement from tunneling through thin obstacle` | 빠른 이동 중 얇은 장애물을 통과(Tunneling)하지 않고 서브스텝에서 즉시 감지·정지하는지 검증 | PASS |

### 2.4 Parking Success Evaluation Tests (주차 성공 판정)
| 테스트 메서드 | 검증 내용 | 결과 |
|---|---|---|
| `evaluation - valid parallel parking inside target zone with aligned heading succeeds` | 구획 내부 완전 진입 + 각도 정렬 + 무충돌 + 정지 시 `EvaluationStatus.SUCCESS` 판정 검증 | PASS |
| `evaluation - vehicle partially outside parking zone does not succeed` | 차체 일부가 주차 구획선을 벗어난 경우 성공으로 판정하지 않는지 검증 | PASS |
| `evaluation - excessive heading angle error prevents parking success` | 구획 내부에 있더라도 정렬 각도 오차($12^\circ > 7.5^\circ$)가 크면 미완료로 판정하는지 검증 | PASS |
| `evaluation - collision state immediately fails parking evaluation` | 장애물 접촉 발생 시 즉시 `EvaluationStatus.COLLIDED`로 판정하는지 검증 | PASS |

### 2.5 Reference Path & Trajectory Prediction Tests (기준 경로 및 예상 궤적)
| 테스트 메서드 | 검증 내용 | 결과 |
|---|---|---|
| `reference path - parallel parking reference path is kinematically valid and collision-free` | 평행주차 기준 경로가 운동학 모델 적분으로 생성되고 무충돌 및 최종 주차 성공 조건을 만족하는지 검증 | PASS |
| `reference path - all active scenarios generate collision-free reference paths` | 6개 전체 활성 시나리오(평행주차, 후진주차, 전면주차, 우회전, 좌회전, 차폭 감각)의 기준 경로 무충돌 검증 | PASS |
| `reference path - returns Unavailable with reason when parking space is smaller than vehicle` | 차량 전장보다 짧은 불가능한 주차 공간($4.0\text{ m}$) 설정 시 `ReferencePathResult.Unavailable` 반환 검증 | PASS |
| `trajectory predictor - predicts future path and detects upcoming obstacle collision` | 현재 조향각과 기어 방향 기준 미래 예상 궤적 포인트 생성 검증 | PASS |
