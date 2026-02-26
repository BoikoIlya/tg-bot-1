package com.kamancho.bot.analytics

import com.mixpanel.mixpanelapi.ClientDelivery
import com.mixpanel.mixpanelapi.MessageBuilder
import com.mixpanel.mixpanelapi.MixpanelAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.slf4j.LoggerFactory

/**
 * Mixpanel Analytics Service using official Mixpanel Java library
 * 
 * Setup:
 * 1. Go to https://mixpanel.com and create a free account
 * 2. Create a new project
 * 3. Get your PROJECT TOKEN from project settings
 * 4. Set the token in environment: export MIXPANEL_TOKEN=your_token
 * 
 * Free tier: 10M events/month
 */
class MixpanelAnalytics(
    private val projectToken: String
) {
    private val logger = LoggerFactory.getLogger(MixpanelAnalytics::class.java)
    
    private val messageBuilder = MessageBuilder(projectToken)
    private val mixpanel = MixpanelAPI()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Track an event
     */
    fun track(
        eventName: String,
        distinctId: String,
        userId: String? = null,
        properties: Map<String, Any> = emptyMap()
    ) {
        scope.launch {
            try {
                val props = JSONObject()
                userId?.let { props.put("\$user_id", it) }
                properties.forEach { (key, value) ->
                    props.put(key, value)
                }
                
                val event = messageBuilder.event(distinctId, eventName, props)
                val delivery = ClientDelivery()
                delivery.addMessage(event)
                mixpanel.deliver(delivery)
            } catch (e: Exception) {
                logger.error("Mixpanel track error: ${e.message}", e)
            }
        }
    }
    
    /**
     * Track a command
     */
    fun trackCommand(
        command: String,
        userId: Long,
        chatId: Long,
        chatType: String? = null,
        username: String? = null,
        countryCode: String? = null
    ) {
        track(
            eventName = "command_used",
            distinctId = userId.toString(),
            userId = userId.toString(),
            properties = mapOf(
                "command" to command,
                "chat_id" to chatId,
                "chat_type" to (chatType ?: "unknown"),
                "username" to (username ?: "anonymous"),
                "country_code" to (countryCode ?: "unknown")
            )
        )
    }
    
    /**
     * Track a callback button press
     */
    fun trackCallback(
        callbackData: String,
        userId: Long,
        chatId: Long,
        chatType: String? = null,
        countryCode: String? = null
    ) {
        track(
            eventName = "callback_pressed",
            distinctId = userId.toString(),
            userId = userId.toString(),
            properties = mapOf(
                "callback_data" to callbackData,
                "chat_id" to chatId,
                "chat_type" to (chatType ?: "private"),
                "country_code" to (countryCode ?: "unknown")
            )
        )
    }
    
    /**
     * Track a message received
     */
    fun trackMessage(
        userId: Long,
        chatId: Long,
        chatType: String = "private",
        messageType: String = "text",
        countryCode: String? = null
    ) {
        track(
            eventName = "message_received",
            distinctId = userId.toString(),
            userId = userId.toString(),
            properties = mapOf(
                "chat_id" to chatId,
                "chat_type" to chatType,
                "message_type" to messageType,
                "country_code" to (countryCode ?: "unknown")
            )
        )
    }
    
    /**
     * Track a payment
     */
    fun trackPayment(
        userId: Long,
        chatId: Long,
        amount: Long,
        currency: String = "USD",
        subscriptionType: String,
        chargeId: String? = null,
        countryCode: String? = null
    ) {
        // Track payment event
        track(
            eventName = "payment_completed",
            distinctId = userId.toString(),
            userId = userId.toString(),
            properties = mapOf(
                "amount" to amount,
                "currency" to currency,
                "subscription_type" to subscriptionType,
                "charge_id" to (chargeId ?: "unknown"),
                "revenue" to (amount / 100.0), // Convert cents to dollars
                "country_code" to (countryCode ?: "unknown")
            )
        )

        // Track revenue event (Mixpanel special event)
        scope.launch {
            try {
                val revenueProps = JSONObject()
                revenueProps.put("\$user_id", userId.toString())
                revenueProps.put("\$amount", amount / 100.0)
                revenueProps.put("subscription_type", subscriptionType)
                revenueProps.put("country_code", countryCode ?: "unknown")

                val revenueEvent = messageBuilder.event(userId.toString(), "\$revenue", revenueProps)
                val delivery = ClientDelivery()
                delivery.addMessage(revenueEvent)
                mixpanel.deliver(delivery)
            } catch (e: Exception) {
                logger.error("Mixpanel revenue track error: ${e.message}", e)
            }
        }
    }
    
    /**
     * Track an error
     */
    fun trackError(
        errorType: String,
        errorMessage: String,
        userId: Long? = null,
        chatId: Long? = null,
        context: Map<String, Any> = emptyMap(),
        countryCode: String? = null
    ) {
        val distinctId = userId?.toString() ?: chatId?.toString() ?: "unknown"

        track(
            eventName = "error_occurred",
            distinctId = distinctId,
            userId = userId?.toString(),
            properties = mapOf(
                "error_type" to errorType,
                "error_message" to errorMessage,
                "chat_id" to (chatId ?: "unknown"),
                "timestamp" to System.currentTimeMillis(),
                "country_code" to (countryCode ?: "unknown")
            ) + context
        )
    }
    
    /**
     * Track subscription activated
     */
    fun trackSubscriptionActivated(
        userId: Long,
        subscriptionType: String,
        durationDays: Int,
        method: String, // "payment", "promo_code"
        countryCode: String? = null
    ) {
        track(
            eventName = "subscription_activated",
            distinctId = userId.toString(),
            userId = userId.toString(),
            properties = mapOf(
                "subscription_type" to subscriptionType,
                "duration_days" to durationDays,
                "activation_method" to method,
                "country_code" to (countryCode ?: "unknown")
            )
        )
    }
    
    /**
     * Track promo code usage
     */
    fun trackPromoCode(
        userId: Long,
        code: String,
        success: Boolean,
        durationDays: Int? = null,
        countryCode: String? = null
    ) {
        track(
            eventName = if (success) "promo_code_success" else "promo_code_failed",
            distinctId = userId.toString(),
            userId = userId.toString(),
            properties = mapOf(
                "promo_code" to code,
                "duration_days" to (durationDays ?: 0),
                "success" to success,
                "country_code" to (countryCode ?: "unknown")
            )
        )
    }
    
    /**
     * Set user properties (for people analytics)
     */
    fun setUserProperties(
        userId: String,
        properties: Map<String, Any>
    ) {
        scope.launch {
            try {
                val props = JSONObject()
                props.put("\$user_id", userId)
                properties.forEach { (key, value) ->
                    props.put(key, value)
                }
                
                val profileUpdate = messageBuilder.set(userId, props)
                mixpanel.sendMessage(profileUpdate)
            } catch (e: Exception) {
                logger.error("Mixpanel people set error: ${e.message}", e)
            }
        }
    }
    
    companion object {
        private val logger = LoggerFactory.getLogger(MixpanelAnalytics::class.java)
        
        /**
         * Create MixpanelAnalytics instance
         * 
         * @param projectToken Your Mixpanel project token (from project settings)
         */
        fun create(projectToken: String = ""): MixpanelAnalytics {
            logger.info("Mixpanel analytics initialized with official Java library")
            return MixpanelAnalytics(projectToken)
        }
    }
}
