package com.harshapriya.safecircle.sync

data class GuardianInviteLink(
    val inviteId: String,
    val url: String,
    val expiresAt: Long
)
