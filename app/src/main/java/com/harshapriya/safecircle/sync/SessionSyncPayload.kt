package com.harshapriya.safecircle.sync

data class SessionSyncPayload(
    val id: String,
    val ownerId: String,
    val mode: String,
    val destination: String?,
    val startedAt: Long,
    val expectedEndAt: Long,
    val lastCheckInAt: Long,
    val state: String,
    val batteryPercent: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracy: Float?,
    val privacyMode: String,
    val resolved: Boolean,
    val resolvedAt: Long? = null
)
