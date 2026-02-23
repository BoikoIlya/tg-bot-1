package com.kamancho.bot.model

import java.time.LocalDateTime

/**
 * User entity - contains only user profile data
 * Subscription data is stored in Subscriptions table (normalized)
 */
data class AppUser(
    val id: Long,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class SubscriptionType {
    MONTHLY,
    YEARLY,
    PROMO
}

data class PromoCode(
    val code: String,
    val durationDays: Int,
    val maxUses: Int,
    val currentUses: Int = 0,
    val isActive: Boolean = true
)
