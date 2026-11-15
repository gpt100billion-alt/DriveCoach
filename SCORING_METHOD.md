# DriveCoach 주행 분석 및 설명형 점수 산출 모델 (`SCORING_METHOD.md`)

> **고지 사항**: 본 문서에 기술된 임계치와 가중치는 운전 습관의 상대적 변화 추적과 교육을 위해 설계된 **개발용 설명형 규칙 모델(v1.0)**이며, 법적·보험적 공인 안전도 판정 기준이 아닙니다.

---

## 1. 물리량 단위 변환 및 전처리 (`DrivingAnalysisEngine`)

### 1.1 속도 단위 변환
차량 속도 $v_{\text{kph}}$ ($\text{km/h}$)를 국제 표준 단위 $v_{\text{mps}}$ ($\text{m/s}$)로 변환한 후 모든 동역학 계산을 수행합니다.

$$v_{\text{mps}} = \frac{v_{\text{kph}}}{3.6}$$

### 1.2 3점 이동평균 노이즈 완화 및 시간 간격 검증
센서 샘플링 간격 $\Delta t = (t_i - t_{i-1}) / 1000$에 대해:
- **중복 타임스탬프 ($\Delta t \le 0.001\text{ s}$)**: 0으로 나누기(Division by zero) 방지를 위해 가속도 추정을 생략하고 `DUPLICATE_TIMESTAMP` 플래그를 기록합니다.
- **불규칙 간격 ($\Delta t > 3.5\text{ s}$)**: 데이터 누락 구간으로 분류하여 `IRREGULAR_INTERVAL` 플래그 및 `DATA_GAP_WARNING` 이벤트를 생성합니다.
- **정상 간격**: 인접 샘플($\Delta t \le 2.5\text{ s}$)과 3점 이동평균을 적용해 단일 샘플 튀는 노이즈를 완화한 뒤 종방향 가속도를 추정합니다.

### 1.3 종방향 가속·감속 추정 ($\Delta v / \Delta t$)
$$a_{\text{est}}(t_i) = \frac{v_{\text{mps}}(t_i) - v_{\text{mps}}(t_{i-1})}{t_i - t_{i-1}} \quad [\text{m/s}^2]$$

- 추정된 가속도는 `isAccelerationEstimated = true` 및 `ESTIMATED_ACCELERATION` 플래그로 표시되어 실제 IMU 센서 직측값과 명확히 구분됩니다.
- 물리적으로 비현실적인 수치($v < 0\text{ km/h}$, $v > 260\text{ km/h}$, $|a| > 12.0\text{ m/s}^2$, $|\delta_{\text{rw}}| > 45^\circ$)는 `UNREALISTIC_VALUE`로 분류하여 분석 왜곡을 차단합니다.

---

## 2. 주행 이벤트 후보 탐지 규칙

단발성 센서 노이즈나 불가피한 도로 상황을 위험 운전으로 단정하지 않도록 **최소 지속 시간 조건**과 **이벤트 후보(Candidate)** 명칭을 적용합니다.

| 이벤트 후보 유형 | 탐지 물리 조건 및 임계치 | 최소 지속 / 반복 조건 | 해석상 주의사항 |
| :--- | :--- | :--- | :--- |
| **급가속 후보 구간**<br>`ABRUPT_ACCELERATION_CANDIDATE` | 종방향 가속도 $a \ge +2.25\text{ m/s}^2$ | 연속 $\ge 1.6\text{초}$ 유지 | 합류 차로 진입이나 오르막 구간에서는 정상적인 가속일 수 있음 |
| **후반 집중·급감속 후보 구간**<br>`LATE_HARD_BRAKING_CANDIDATE` | 종방향 감속도 $a \le -2.35\text{ m/s}^2$ (집중 감속: $\le -3.2\text{ m/s}^2$) | 연속 $\ge 1.6\text{초}$ 유지 | 돌발 장애물 회피나 황색 신호 정지를 위한 정당한 안전 제동일 수 있음 |
| **저속 회전 수정 조향 후보**<br>`LOW_SPEED_STEERING_CORRECTION_CANDIDATE` | 저속 선회 구간 ($8 \le v \le 38\text{ km/h}$, $|\delta_{\text{rw}}| \ge 5.0^\circ$) | 회전 윈도우 내 조향 변화율 부호 반전($|\Delta \delta_{\text{rw}}| \ge 2.2^\circ$) **3회 이상** 반복 | 공사 구간이나 이중 주차 회피 주행일 가능성을 함께 안내 |
| **불안정한 속도 변화 후보**<br>`SPEED_OSCILLATION_CANDIDATE` | 순항 구간 ($v \ge 20\text{ km/h}$) 내 가감속 부호 교차 | $12\text{초}$ 윈도우 내 가감속 교차($|a| \ge 0.85\text{ m/s}^2$) 반복 | 정체 구간 가다 서다(Stop-and-go) 교통 흐름 영향 가능성 고려 |

---

## 3. 설명 가능한 점수 산출 수식 (`DrivingScoreCalculator`)

### 3.1 최소 데이터 충족 조건 (가짜 85점 기본값 금지)
- 유효 샘플 수 $< 10\text{개}$ 또는 유효 주행 시간 $< 8\text{초}$ 또는 속도 데이터 부재 시:
  - **`overallScore = null`**, **`scoreGradeLabel = "분석 데이터 부족"`**, **`AnalysisStatus.INSUFFICIENT_DATA`**를 반환합니다.
  - 데이터가 부족할 때 임의의 85점 등 고정 점수를 반환하지 않습니다.

### 3.2 4대 세부 지표 산출 (0 ~ 100점)
1. **가속 안정성 ($S_{\text{accel}}$)**: 양(+)의 가속 구간 RMS, 최대 가속도 피크, 급가속 후보 이벤트 시간 비중에 따른 감점 산출
2. **감속·제동 안정성 ($S_{\text{brake}}$)**: 음(-)의 감속 구간 RMS, 후반 감속 집중도, 급감속 후보 이벤트 빈도·강도에 따른 감점 산출
3. **조향 안정성 ($S_{\text{steer}}$)**: 전륜 조향각 변화율($|\Delta \delta_{\text{rw}} / \Delta t|$) 안정성 및 저속 수정 조향 후보 횟수에 따른 감점 산출 *(조향 센서 미가용 시 `score = null`, `isAvailable = false` 처리)*
4. **주행 일관성 ($S_{\text{consist}}$)**: 순항 속도 표준편차 및 가감속 전환 리듬 안정성 평가

### 3.3 결측 센서 가중치 자동 정규화
사용자가 설정한 기본 가중치 $(w_{\text{accel}}, w_{\text{brake}}, w_{\text{steer}}, w_{\text{consist}}) = (0.25, 0.35, 0.30, 0.10)$ 중 실제 수집 가능한 지표 집합을 $M_{\text{avail}}$이라 할 때:

$$S_{\text{overall}} = \text{round}\left( \frac{\sum_{m \in M_{\text{avail}}} w_m \cdot S_m}{\sum_{m \in M_{\text{avail}}} w_m} \right)$$

이를 통해 조향 센서나 일부 신호가 제공되지 않는 환경에서도 나머지 가용 지표만으로 공정하게 정규화된 종합 점수를 산출하고, 어떤 지표가 제외되었는지 리포트에 명시합니다.
