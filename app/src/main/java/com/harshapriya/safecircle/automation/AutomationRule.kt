package com.harshapriya.safecircle.automation

import com.harshapriya.safecircle.model.EscalationAction
import com.harshapriya.safecircle.model.TriggerType
import java.util.UUID

data class AutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trigger: TriggerType,
    val thresholdMinutes: Int,
    val actions: List<EscalationAction>,
    val enabled: Boolean = true,
) {
    init {
        require(name.isNotBlank())
        require(thresholdMinutes >= 0)
        require(actions.isNotEmpty())
    }
}
