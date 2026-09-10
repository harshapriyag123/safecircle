package com.harshapriya.safecircle.model

enum class SessionMode(val label: String, val minutes: Int) {
    WALK_HOME("Walk Home", 20),
    RIDESHARE("Rideshare", 30),
    MEET_SOMEONE("Meet Someone", 60),
    STAY_WITH_ME("Stay With Me", 15)
}

enum class SafetyState {
    NORMAL, ATTENTION, CHECK_IN, CONCERN, ESCALATED, RESOLVED
}

data class SafetySession(
    val id: String,
    val mode: SessionMode,
    val startedAt: Long,
    val expectedEndAt: Long,
    val lastCheckInAt: Long,
    val missedCheckIns: Int = 0,
    val routeDeviation: Boolean = false,
    val batteryPercent: Int = 72,
    val state: SafetyState = SafetyState.NORMAL,
    val resolved: Boolean = false,
    val destinationLabel: String? = null
)

data class Guardian(
    val name: String,
    val relation: String,
    val channel: String,
    val primary: Boolean = false
)

data class SafetySnapshot(
    val score: Int,
    val state: SafetyState,
    val reasons: List<String>
)
