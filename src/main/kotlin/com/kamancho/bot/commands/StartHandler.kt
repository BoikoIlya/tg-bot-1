package com.kamancho.bot.commands

import com.kamancho.bot.model.SubscriptionType
import com.kamancho.bot.repository.GlobalRepo
import io.github.dehuckakpyt.telegrambot.factory.keyboard.inlineKeyboard
import io.github.dehuckakpyt.telegrambot.handling.BotHandling
import io.github.dehuckakpyt.telegrambot.model.telegram.KeyboardButton
import io.github.dehuckakpyt.telegrambot.model.telegram.ReplyKeyboardMarkup

fun BotHandling.startCommand() {
    // Handle /start command
    command("/start") {
        val userId = chat.id
        // Extract parameters from command text (e.g., "/start test_tt_PROMO123" -> userSource="test_tt", promocode="PROMO123")
        val commandText = message.text?.substringAfter("/start")?.trim()
        val parts = commandText?.split("_")?.filter { it.isNotEmpty() } ?: emptyList()
        val userSource = parts.getOrNull(0)
        val promocode = parts.getOrNull(1)?.uppercase()
        val user = GlobalRepo.getOrCreateUser(userId, chat.username, chat.firstName, chat.lastName, userSource)
        val countryCode = from.languageCode

        // Track command usage
        GlobalRepo.getAnalytics()?.trackCommand("/start", userId, chat.id, chat.type, chat.username, countryCode, userSource)

        // Handle promocode if provided
        var promoApplied = false
        var promoDuration = 0
        if (promocode != null) {
            val validatedPromo = GlobalRepo.validatePromoCode(promocode)
            if (validatedPromo != null && !GlobalRepo.hasUserUsedPromoCode(userId, promocode)) {
                val activeSubType = GlobalRepo.getSubscriptionType(userId)
                if (activeSubType != SubscriptionType.PROMO) {
                    GlobalRepo.activatePromoCodeSubscription(
                        userId = userId,
                        promoCode = promocode,
                        durationDays = validatedPromo.durationDays
                    )
                    promoApplied = true
                    promoDuration = validatedPromo.durationDays
                }
            }
        }

        if (GlobalRepo.isSubscriptionActive(userId)) {
            val baseMessage = if (promoApplied) {
                """
                ✅ **Promo code applied!**
                Your trial is valid for $promoDuration days.
                
                👋 Hi, ${chat.firstName ?: "friend"}!
                
                I'm your personal Spanish practice assistant! 🎯

                🎤 **How it works:**
                1. Send me a voice message in Spanish
                2. I'll analyze your speech
                3. Get feedback and an audio response

                ✅ Your subscription is active.
                Send voice messages in spanish for analysis! 🎤
                """.trimIndent()
            } else {
                """
                👋 Hi, ${chat.firstName ?: "friend"}!

                ✅ Your subscription is active.
                Send voice messages in spanish for analysis! 🎤
                """.trimIndent()
            }
            sendMessage(
                baseMessage,
                replyMarkup = ReplyKeyboardMarkup(
                    keyboard = listOf(
                        listOf(KeyboardButton("🏠 Main Menu"), KeyboardButton("❓ Help"))
                    ),
                    resizeKeyboard = true,
                    oneTimeKeyboard = false
                )
            )
        } else {
            val baseMessage = if (promoApplied) {
                """
                ✅ **Promo code applied!**
                Your subscription is valid for $promoDuration days.
                
                👋 Hi, ${chat.firstName ?: "friend"}!

                I'm your personal Spanish practice assistant! 🎯

                🎤 **How it works:**
                1. Send me a voice message in Spanish
                2. I'll analyze your speech
                3. Get feedback and an audio response

                ✅ Your subscription is now active!
                Send voice messages in spanish for analysis! 🎤
                """.trimIndent()
            } else {
                """
                👋 Hi, ${chat.firstName ?: "friend"}!

                I'm your personal Spanish practice assistant! 🎯

                🎤 **How it works:**
                1. Send me a voice message in Spanish
                2. I'll analyze your speech
                3. Get feedback and an audio response

                💰 **Subscription prices are in Telegram Stars**

                💡 **Tip:** The cheapest way to buy Stars is through:
                • Telegram Web version (web.telegram.org)
                • Telegram Desktop (PC version)

                No commission fees = more Stars for your money! 🌟

                🌟 **Choose a plan:**
                """.trimIndent()
            }
            sendMessage(
                baseMessage,
                parseMode = "Markdown",
                replyMarkup = if (promoApplied) {
                    ReplyKeyboardMarkup(
                        keyboard = listOf(
                            listOf(KeyboardButton("🏠 Main Menu"), KeyboardButton("❓ Help"))
                        ),
                        resizeKeyboard = true,
                        oneTimeKeyboard = false
                    )
                } else {
                    inlineKeyboard(
                        callbackButton("🌟 Yearly — 2499 Stars (Save 60%)", next = "sub_yearly"),
                        callbackButton("🌟 Monthly — 499 Stars", next = "sub_monthly"),
                        callbackButton("💎 Enter Promo Code", next = "promo")
                    )
                }
            )
        }
    }
}
