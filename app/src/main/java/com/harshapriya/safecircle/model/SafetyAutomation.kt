package com.harshapriya.safecircle.model

enum class TriggerType {
    MISSED_CHECK_IN,
    ETA_OVERDUE,
    ROUTE_DEVIATION,
    LOW_BATTERY,
    DURESS_PHRASE
}

enum class EscalationAction {
    PROMPT_USER,
    NOTIFY_PRIMARY_GUARDIAN,
    NOTIFY_BACKUP_GUARDIAN,
    SHARE_SAFETY_CAPSULE
}

data class SafetyAutomation(
    val id: String,
    val trigger: TriggerType,
    val thresholdMinutes: Int = 0,
    val actions: List<EscalationAction>,
    val enabled: Boolean = true,
)

object DefaultSafetyAutomations {
    val progressiveEscalation = SafetyAutomation(
        id = "progressive_escalation",
        trigger = TriggerType.ETA_OVERDUE,
        thresholdMinutes = 0,
        actions = listOf(
            EscalationAction.PROMPT_USER,
            EscalationAction.NOTIFY_PRIMARY_GUARDIAN,
            EscalationAction.NOTIFY_BACKUP_GUARDIAN,
            EscalationAction.SHARE_SAFETY_CAPSULE,
        ),
    )
}
