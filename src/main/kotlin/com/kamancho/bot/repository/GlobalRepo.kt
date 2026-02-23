package com.kamancho.bot.repository

import com.kamancho.bot.analytics.MixpanelAnalytics
import com.kamancho.bot.database.DatabaseManager
import com.kamancho.bot.model.AppUser
import com.kamancho.bot.model.PromoCode
import com.kamancho.bot.model.SubscriptionType
import java.util.concurrent.ConcurrentHashMap

object GlobalRepo {
    val promoWaiting = ConcurrentHashMap.newKeySet<Long>()

    // Store failed voice messages for retry (chatId -> base64 audio)
    val failedVoiceMessages = ConcurrentHashMap<Long, String>()

    private lateinit var databaseManager: DatabaseManager
    private lateinit var mixpanelAnalytics: MixpanelAnalytics

    fun init() {
        databaseManager = DatabaseManager()
        databaseManager.init()
        
        // Initialize Mixpanel if token is provided
        mixpanelAnalytics = MixpanelAnalytics.create(System.getenv("MIXPANEL_TOKEN"))

    }

    fun getDatabaseManager(): DatabaseManager {
        if (!::databaseManager.isInitialized) {
            init()
        }
        return databaseManager
    }

    fun getAnalytics(): MixpanelAnalytics? {
        if (!::mixpanelAnalytics.isInitialized) {
            return null
        }
        return mixpanelAnalytics
    }
    
    // ==================== USER OPERATIONS ====================
    fun getUser(userId: Long): AppUser? {
        return getDatabaseManager().getUser(userId)
    }
    
    fun getOrCreateUser(userId: Long, username: String?, firstName: String?, lastName: String?): AppUser {
        return getDatabaseManager().getOrCreateUser(userId, username, firstName, lastName)
    }
    
    // ==================== SUBSCRIPTION OPERATIONS ====================
    fun isSubscriptionActive(userId: Long): Boolean {
        return getDatabaseManager().isSubscriptionActive(userId)
    }
    
    fun activateSubscription(
        userId: Long,
        type: SubscriptionType,
        durationDays: Int,
        paymentChargeId: String? = null
    ) {
        getDatabaseManager().activateSubscription(userId, type, durationDays, paymentChargeId)
    }
    
    fun getSubscriptionExpiryDate(userId: Long): java.time.LocalDateTime? {
        return getDatabaseManager().getSubscriptionExpiryDate(userId)
    }
    
    fun getSubscriptionType(userId: Long): SubscriptionType? {
        return getDatabaseManager().getSubscriptionType(userId)
    }
    
    // ==================== PROMO CODE OPERATIONS ====================
    fun validatePromoCode(code: String): PromoCode? {
        return getDatabaseManager().validatePromoCode(code)
    }
    
    fun hasUserUsedPromoCode(userId: Long, code: String): Boolean {
        return getDatabaseManager().hasUserUsedPromoCode(userId, code)
    }
    
    fun activatePromoCodeSubscription(userId: Long, promoCode: String, durationDays: Int) {
        getDatabaseManager().activatePromoCodeSubscription(userId, promoCode, durationDays)
    }
}
