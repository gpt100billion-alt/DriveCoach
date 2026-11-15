# DriveCoach 개발 완료 현황 및 향후 로드맵 (`DEVELOPMENT_STATUS.md`)

## 1. 마스터 요구사항 대비 구현 현황

| 구분 | 핵심 요구사항 | 상태 | 구현 파일 및 검증 내역 |
| :--- | :--- | :---: | :--- |
| **플랫폼 & SDK** | 공식 Pleos Maven 저장소 및 `ai.pleos.playground:Vehicle:2.3.2` 연동 | **완료** | `settings.gradle.kts`, `app/build.gradle.kts`, `PleosTelemetryProvider.kt` |
| **안전 & 권한** | 차량 제어(`CONTROL_CAR_*`) 금지, 읽기 전용 권한만 선언, 주행 중 실시간 조향 지시 및 복잡 UI 차단 | **완료** | `AndroidManifest.xml`, `DriveCoachAppShell` (`DrivingSafetyRestrictionOverlay`) |
| **데이터 투명성** | `LIVE`, `SIMULATED`, `DEMO`, `UNAVAILABLE`, `ERROR` 배지 구분, 핸들 회전각 vs 전륜 조향각 분리 | **완료** | `TelemetryModels.kt`, `DataSourceBadge` |
| **분석 엔진** | $\text{km/h} \to \text{m/s}$ 변환, 3점 이동평균, 불규칙/중복 타임스탬프 처리, $\Delta v/\Delta t$ 가속도 추정 | **완료** | `DrivingAnalysisEngine.kt` |
| **설명형 점수** | 4대 지표 가중치 평가, 결측 센서 가중치 정규화, 데이터 부족 시 가짜 85점 대신 `null`("분석 데이터 부족") 반환 | **완료** | `DrivingScoreCalculator.kt` |
| **맞춤형 코칭** | 규칙 기반 설명형 엔진 v1.0 명시, 7단계 구조화 코칭 카드, 3·5·10회 추세, `CoachingGenerator` 인터페이스 분리 | **완료** | `CoachingEngine.kt`, `CoachingScreen.kt` |
| **시뮬레이션** | 주차 상태 전용 5대 결정론적 데모 시나리오 재생, 배속 조절, 즉시 완료, 저장 기록 리플레이 | **완료** | `DemoScenarioCatalog.kt`, `SimulationScreen.kt` |
| **운전 교육** | 5대 교육 주제 + 슬라이더 연동 2D 도로 궤적 비교 캔버스 (`InteractiveRoadConceptCanvas`) | **완료** | `EducationCatalog.kt`, `EducationScreen.kt` |
| **로컬 저장소** | 앱 재시작 후에도 유지되는 SQLite 세션/샘플 저장소 및 SharedPreferences 설정/목표 관리 | **완료** | `DriveCoachLocalStore.kt` |
| **자동화 테스트** | 데이터 전처리, 이벤트 감지, 결측 센서 정규화, 데이터 부족 처리, 결정론적 점수 검증 단위 테스트 15건 | **완료** | `DriveCoachAnalysisAndScoringTest.kt` (`15 passed, 0 failed`) |

---

## 2. 현재 기술적 제약사항 및 대응 (Known Limitations)

1. **`ai.pleos.playground:Vehicle:2.3.2`의 차속 및 페달 답력 API 미노출**
   - 현행 공개 SDK(`2.3.2`)는 `SteeringWheel`(전륜 조향각), `Brake`(페달 ON/OFF), `Odometer`(누적/트립 거리), `Gear`(기어 단수), `TurnSignal`(방향지시등)은 제공하나, 실시간 차량 속도(`PERF_VEHICLE_SPEED`)와 가속 페달 개도량(%) API는 노출하지 않습니다.
   - **대응 완료**: `PleosTelemetryProvider`와 `SDK 진단 화면`에서 이를 `UNAVAILABLE`로 명확히 표시하고, 5종 결정론적 시뮬레이션(`SIMULATED`) 및 사전 생성 데모(`DEMO`)를 통해 모든 분석·시각화·코칭 파이프라인을 100% 검증할 수 있도록 구현했습니다.

2. **생성형 AI(LLM) 연동 확장성**
   - 현재 버전은 오프라인 완결성과 판단 근거의 100% 재현성을 위해 `RuleBasedCoachingGenerator`를 기본 엔진으로 사용합니다.
   - 추후 Pleos Connect 온디바이스 LLM 또는 클라우드 API 연동 시 `CoachingGenerator` 인터페이스의 구현체만 교체하면 UI 수정 없이 즉시 확장 가능합니다.
