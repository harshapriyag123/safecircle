package com.harshapriya.safecircle.reliability

data class DeliveryReceipt(
    val eventId: String,
    val channel: String,
    val recipientId: String,
    val acceptedAt: Long,
    val deliveredAt: Long? = null,
    val status: Status
) {
    enum class Status { QUEUED, ACCEPTED, DELIVERED, FAILED }
}
