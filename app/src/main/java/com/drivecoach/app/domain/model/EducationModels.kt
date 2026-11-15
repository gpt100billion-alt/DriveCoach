package com.drivecoach.app.domain.model

enum class EducationSimType {
    LOW_SPEED_LEFT_TURN,
    LOW_SPEED_RIGHT_TURN,
    SMOOTH_BRAKING,
    SMOOTH_ACCELERATION,
    UNPROTECTED_LEFT_INTERSECTION,
}

data class EducationStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
)

data class EducationTopic(
    val id: String,
    val title: String,
    val subtitle: String,
    val categoryBadge: String,
    val estimatedMinutes: Int,
    val simType: EducationSimType,
    val whyItMatters: String,
    val keyConcepts: List<String>,
    val steps: List<EducationStep>,
    val cautionNotice: String,
    val defaultSpeedParam: Float = 20f,
    val defaultControlParam: Float = 0.5f,
    val speedParamLabel: String = "진입 속도 (km/h)",
    val controlParamLabel: String = "조작 타이밍 / 완만함",
)
