package com.olaf.rereminder.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val scheduledTimeMillis: Long,
    val isEnabled: Boolean = true,
    val isCompleted: Boolean = false,
    val modifiedAt: Long = System.currentTimeMillis(),
    val dismissedAt: Long? = null
)
