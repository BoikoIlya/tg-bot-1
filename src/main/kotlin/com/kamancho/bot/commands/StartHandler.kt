package com.kamancho.bot.commands

import com.kamancho.bot.repository.GlobalRepo
import io.github.dehuckakpyt.telegrambot.factory.keyboard.inlineKeyboard
import io.github.dehuckakpyt.telegrambot.handling.BotHandling
import io.github.dehuckakpyt.telegrambot.model.telegram.KeyboardButton
import io.github.dehuckakpyt.telegrambot.model.telegram.ReplyKeyboardMarkup

fun BotHandling.startCommand() {
    // Handle /start command
    command("/start") {
        val userId = chat.id
        // Extract user_source from command text (e.g., "/start test_tt" -> "test_tt")
        val userSource = message.text?.substringAfter("/start")?.trim()?.takeIf { it.isNotEmpty() }
        val user = GlobalRepo.getOrCreateUser(userId, chat.username, chat.firstName, chat.lastName, userSource)
        val countryCode = from.languageCode

        // Track command usage
        GlobalRepo.getAnalytics()?.trackCommand("/start", userId, chat.id, chat.type, chat.username, countryCode, userSource)

        if (GlobalRepo.isSubscriptionActive(userId)) {
            sendMessage(
                """
                👋 Hi, ${chat.firstName ?: "friend"}!

                ✅ Your subscription is active.
                Send voice messages for analysis! 🎤
                """.trimIndent(),
                replyMarkup = ReplyKeyboardMarkup(
                    keyboard = listOf(
                        listOf(KeyboardButton("🏠 Main Menu"), KeyboardButton("❓ Help"))
                    ),
                    resizeKeyboard = true,
                    oneTimeKeyboard = false
                )
            )
        } else {
            sendMessage(
                """
                👋 Hi, ${chat.firstName ?: "friend"}!

                I'm your personal Spanish practice assistant! 🎯

                🎤 **How it works:**
                1. Send me a voice message in Spanish
                2. I'll analyze your speech
                3. Get feedback and an audio response

                🌟 **You need a subscription to access features:**
                """.trimIndent(),
                parseMode = "Markdown",
                replyMarkup = inlineKeyboard(
                    callbackButton("🌟 Yearly — 2499 Stars (Save 60%)", next = "sub_yearly"),
                    callbackButton("🌟 Monthly — 499 Stars", next = "sub_monthly"),
                    callbackButton("💎 Enter Promo Code", next = "promo")
                )
            )
        }
    }
}
