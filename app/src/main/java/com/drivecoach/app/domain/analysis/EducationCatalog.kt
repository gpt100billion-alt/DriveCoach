package com.drivecoach.app.domain.analysis

import com.drivecoach.app.domain.model.EducationSimType
import com.drivecoach.app.domain.model.EducationStep
import com.drivecoach.app.domain.model.EducationTopic

/**
 * 주차 상태에서 학습할 수 있는 5대 운전 교육 콘텐츠 카탈로그 (섹션 11)
 * - 확정적 법률 자문이 아닌 안전 운전 원리 및 차량 동역학 이해를 돕는 교육용 자료임을 명시합니다.
 */
object EducationCatalog {

    val topics: List<EducationTopic> = listOf(
        EducationTopic(
            id = "low_speed_left_turn",
            title = "1. 저속 좌회전 이해와 회전 궤적",
            subtitle = "회전 반경, 진입 속도, 조향각 변화가 좌회전 궤적에 미치는 영향",
            categoryBadge = "조향 · 회전 역학",
            estimatedMinutes = 3,
            simType = EducationSimType.LOW_SPEED_LEFT_TURN,
            whyItMatters = "교차로 좌회전 시 진입 속도가 너무 빠르거나 조향을 너무 일찍/늦게 시작하면 차로 바깥으로 밀리거나 회전 도중 핸들을 여러 번 고쳐 잡는 수정 조향이 발생하기 쉽습니다.",
            keyConcepts = listOf(
                "차량 회전 반경(Turning Radius): 휠베이스와 전륜 조향각에 따라 차량이 그리는 원의 크기가 결정됩니다.",
                "진입 속도와 횡가속도: 같은 회전 반경이라도 속도가 빠를수록 쏠림이 커져 조향 수정 빈도가 늘어납니다.",
                "고정된 정답 각도의 부재: 교차로 크기, 차로 수, 장애물에 따라 필요한 조향각은 매번 달라집니다.",
            ),
            steps = listOf(
                EducationStep(1, "진입 전 직선 구간에서 감속 완료", "교차로에 들어가기 전에 15~25km/h 수준으로 미리 속도를 줄여 차량 하중을 안정시킵니다."),
                EducationStep(2, "출구 차로 중앙으로 시선 이동", "차량 바로 앞 바닥이 아니라 빠져나갈 차로의 먼 중앙 지점을 바라보며 조향을 시작합니다."),
                EducationStep(3, "일정한 조향각 유지 후 자연스러운 복원", "회전 정점(Apex)을 지날 때까지 조향각을 안정적으로 유지한 뒤 출구 방향에 맞춰 부드럽게 풀어줍니다."),
            ),
            cautionNotice = "실제 도로에서는 보행자, 자전거, 반대편 좌회전 차량, 도로 폭에 따라 회전 중 수정 조향이 필요한 정당한 상황이 존재합니다.",
            defaultSpeedParam = 20f,
            defaultControlParam = 0.72f,
            speedParamLabel = "좌회전 진입 속도 (km/h)",
            controlParamLabel = "조향 타이밍 및 궤적 안정도",
        ),
        EducationTopic(
            id = "low_speed_right_turn",
            title = "2. 저속 우회전 이해와 내륜차·보행자 안전",
            subtitle = "차로 폭과 내륜차(앞·뒷바퀴 궤적 차이), 횡단보도 전후 확인 습관",
            categoryBadge = "조향 · 보행자 안전",
            estimatedMinutes = 3,
            simType = EducationSimType.LOW_SPEED_RIGHT_TURN,
            whyItMatters = "우회전은 좌회전보다 회전 반경이 작고 연석·횡단보도와 가까워, 뒷바퀴가 안쪽으로 감기는 '내륜차'와 사각지대 보행자에 대한 각별한 주의가 필요합니다.",
            keyConcepts = listOf(
                "내륜차(Inner Wheel Difference): 회전 시 뒷바퀴는 앞바퀴보다 더 안쪽 경로를 통과합니다.",
                "우회전 전·후 횡단보도 확인: 진입 전 횡단보도와 회전 직후 만나는 횡단보도의 보행자를 모두 확인해야 합니다.",
                "속도와 시야 확보: 충분히 낮은 속도로 진입해야 A필러와 우측 사이드미러 사각지대를 침착하게 살필 수 있습니다.",
            ),
            steps = listOf(
                EducationStep(1, "우회전 진입 전 일시정지 및 완전 감속 준비", "우측 횡단보도와 보행자 유무를 먼저 확인하며 언제든 멈출 수 있는 속도로 줄입니다."),
                EducationStep(2, "연석과의 간격을 고려한 진입 궤적", "내륜차를 고려해 연석에 너무 바짝 붙지 않도록 여유 공간을 두고 조향을 시작합니다."),
                EducationStep(3, "회전 완료 전 재가속 자제", "차체가 완전히 정면을 향하고 전방 보행자가 없는 것을 확인한 뒤 부드럽게 가속합니다."),
            ),
            cautionNotice = "교차로 형태와 신호체계는 위치마다 다르므로 현장 신호기 및 도로 표지판을 최우선으로 준수해야 합니다.",
            defaultSpeedParam = 15f,
            defaultControlParam = 0.75f,
            speedParamLabel = "우회전 진입 속도 (km/h)",
            controlParamLabel = "내륜차 여유 및 궤적 완만함",
        ),
        EducationTopic(
            id = "smooth_braking",
            title = "3. 부드러운 감속과 긴급제동의 구분",
            subtitle = "예측 가능한 정차 구간에서의 점진적 감속 vs 돌발 상황에서의 제동 원칙",
            categoryBadge = "제동 안정성",
            estimatedMinutes = 4,
            simType = EducationSimType.SMOOTH_BRAKING,
            whyItMatters = "정지선이 보이는데도 늦게까지 속도를 유지하다가 마지막에 강하게 브레이크를 밟으면 동승자 멀미와 추돌 위험이 커집니다. 반면 돌발 상황에서는 주저 없이 강하게 제동해야 합니다.",
            keyConcepts = listOf(
                "예측 감속(Anticipatory Braking): 전방 교통 흐름을 미리 읽고 가속 페달 오프(타력 주행)로 초기 감속을 시작합니다.",
                "정차 직전 답력 조절: 일반 정차 시 차량이 완전히 멈추기 직전 브레이크 압력을 살짝 완화하면 정차 충격을 줄일 수 있습니다.",
                "일반 감속 vs 긴급제동(ABS 작동): 장애물 출현 등 비상 상황에서는 브레이크를 나눠 밟지 말고 ABS가 작동하도록 끝까지 강하게 밟아야 합니다.",
            ),
            steps = listOf(
                EducationStep(1, "초기 감속: 가속 페달에서 미리 발 떼기", "전방 적색 신호나 정체 꼬리를 발견하면 일찍 가속 페달에서 발을 떼어 자연 감속을 유도합니다."),
                EducationStep(2, "중기 감속: 일정한 브레이크 답력 유지", "필요한 감속량을 한 번에 부드럽게 만들어 후반부에 브레이크를 더 깊게 밟지 않도록 합니다."),
                EducationStep(3, "긴급 상황 예외 인지", "위험 회피가 필요한 순간에는 점수나 부드러움을 의식하지 말고 즉시 단호하게 제동합니다."),
            ),
            cautionNotice = "DriveCoach는 긴급제동 시 브레이크를 여러 번 나눠 밟으라고 안내하지 않습니다. ABS 장착 차량의 긴급 상황에서는 강한 지속 제동이 안전 원칙입니다.",
            defaultSpeedParam = 50f,
            defaultControlParam = 0.78f,
            speedParamLabel = "초기 주행 속도 (km/h)",
            controlParamLabel = "감속 시작 시점 여유도",
        ),
        EducationTopic(
            id = "smooth_acceleration",
            title = "4. 부드러운 가속 조절과 승차감",
            subtitle = "초기 페달 조작 완만화와 도로 상황에 맞는 합류 가속의 균형",
            categoryBadge = "가속 안정성",
            estimatedMinutes = 3,
            simType = EducationSimType.SMOOTH_ACCELERATION,
            whyItMatters = "정지 상태에서 가속 페달을 급격히 깊게 밟으면 차량 구동계에 급격한 토크가 전달되어 차체가 뒤로 젖혀지는 피칭(Pitching) 현상과 에너지 손실이 발생합니다.",
            keyConcepts = listOf(
                "초기 2초 소프트 스타트: 정지 출발 시 처음 1~2초 동안 페달을 점진적으로 밟으면 타이어 접지와 승차감이 안정됩니다.",
                "목표 속도 수렴: 제한 속도에 가까워질수록 가속 페달 개도량을 서서히 줄여 속도 오버슈트를 방지합니다.",
                "합류 구간 예외: 고속화도로 진입로 등에서는 본선 흐름에 맞춰 충분히 가속하는 것이 안전합니다.",
            ),
            steps = listOf(
                EducationStep(1, "브레이크 해제 후 0.5초 여유", "브레이크에서 발을 뗀 직후 차량이 부드럽게 움직이기 시작할 때 가속 페달을 점진적으로 밟습니다."),
                EducationStep(2, "일정한 가속도 유지", "페달을 밟았다 뗐다 반복하지 않고 목표 속도까지 매끄러운 기울기로 가속합니다."),
                EducationStep(3, "정속 주행 전환", "목표 속도에 도달하면 페달을 일정하게 유지해 속도 변동(Oscillation)을 줄입니다."),
            ),
            cautionNotice = "교통 흐름에 맞추기 위한 합류 가속은 안전 운전에 필수적이므로 무조건 느리게 가속하는 것이 정답은 아닙니다.",
            defaultSpeedParam = 60f,
            defaultControlParam = 0.80f,
            speedParamLabel = "목표 주행 속도 (km/h)",
            controlParamLabel = "페달 조작 완만도",
        ),
        EducationTopic(
            id = "unprotected_left_intersection",
            title = "5. 비보호 좌회전 및 교차로 상황 판단",
            subtitle = "신호·대향 차량·보행자 확인 순서와 교차로별 표지 우선성 이해",
            categoryBadge = "교차로 안전 판단",
            estimatedMinutes = 4,
            simType = EducationSimType.UNPROTECTED_LEFT_INTERSECTION,
            whyItMatters = "비보호 좌회전 구간과 복합 교차로는 초보 운전자가 가장 긴장하는 환경 중 하나입니다. 조향 조작 이전에 충분한 시야 확보와 대향차 속도 판단이 선행되어야 합니다.",
            keyConcepts = listOf(
                "비보호 좌회전 표지 확인: 비보호 좌회전 표지가 있는 교차로에서는 녹색 신호 시 반대편 직진 차량과 보행자에게 방해가 되지 않을 때 안전하게 좌회전할 수 있습니다.",
                "대향차와의 거리·속도 여유: 무리하게 틈새로 진입해 급가속·급조향을 하지 않도록 충분한 시간 여유를 확보합니다.",
                "현장 규제 및 신호 우선: 교차로마다 신호 체계와 시간제 규제가 다를 수 있으므로 현장 표지와 신호를 항상 최우선으로 확인해야 합니다.",
            ),
            steps = listOf(
                EducationStep(1, "교차로 표지 및 전방 신호 확인", "비보호 좌회전 허용 표지 유무와 현재 신호 상태를 명확히 인지합니다."),
                EducationStep(2, "대향 직진 차량 및 좌측 횡단보도 보행자 확인", "맞은편 직진 차량뿐만 아니라 내가 진입할 왼쪽 도로의 횡단보도 보행자까지 먼저 확인합니다."),
                EducationStep(3, "충분한 간격 확보 후 안정적인 궤적으로 회전", "급가속에 의존하지 않아도 될 만큼 여유가 있을 때 일정한 속도와 조향각으로 통과합니다."),
            ),
            cautionNotice = "본 콘텐츠는 일반적인 운전 안전 상식을 설명하기 위한 교육 자료이며 확정적인 법률 해석이 아닙니다. 실제 주행 시에는 도로교통법 및 현장 경찰공무원·신호기·안전표지의 지시를 최우선으로 따라야 합니다.",
            defaultSpeedParam = 22f,
            defaultControlParam = 0.70f,
            speedParamLabel = "교차로 통과 속도 (km/h)",
            controlParamLabel = "사전 시야 확인 및 여유도",
        ),
    )

    fun findById(id: String?): EducationTopic =
        topics.find { it.id == id } ?: topics.first()
}
