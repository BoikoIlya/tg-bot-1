package com.kamancho.bot.commands

import com.kamancho.bot.model.SubscriptionType
import com.kamancho.bot.repository.GlobalRepo
import io.github.dehuckakpyt.telegrambot.ext.container.chatId
import io.github.dehuckakpyt.telegrambot.factory.keyboard.inlineKeyboard
import io.github.dehuckakpyt.telegrambot.handling.BotHandling
import io.github.dehuckakpyt.telegrambot.model.telegram.LabeledPrice

fun BotHandling.subscriptionCommand() {
    callback("sub_monthly") {
        val chatId = chat.id
        
        // Track callback
        GlobalRepo.getAnalytics()?.trackCallback("sub_monthly", chatId, chatId, chat.type)
        
        bot.sendInvoice(
            chatId = chatId,
            title = "🌟 Premium Monthly Subscription",
            description = "Get access to premium features for 30 days",
            payload = "sub_monthly_${chat.username}",
            currency = "XTR",
            prices = listOf(LabeledPrice("Monthly", 499)),
        )
    }

    callback("sub_yearly") {
        val chatId = chat.id
        
        // Track callback
        GlobalRepo.getAnalytics()?.trackCallback("sub_yearly", chatId, chatId, chat.type)
        
        bot.sendInvoice(
            chatId = chatId,
            title = "🌟 Premium Yearly Subscription",
            description = "(Save 58%) Get access to premium features for 1 year",
            payload = "sub_yearly_${chat.username}",
            currency = "XTR",
            prices = listOf(LabeledPrice("Yearly", 2499)),
        )
    }

    callback("promo") {
        // Track callback
        GlobalRepo.getAnalytics()?.trackCallback("promo", chatId, chatId, chat.type)
        
        GlobalRepo.promoWaiting.add(chatId)
        sendMessage("💎 Enter your promo code in the message:")
    }

    callback("back_to_menu") {
        // Track callback
        GlobalRepo.getAnalytics()?.trackCallback("back_to_menu", chatId, chatId, chat.type)
        showSubscriptionMenu()
    }
}

fun BotHandling.showSubscriptionMenu() {
    callback("show_menu") {
        val userId = chat.id
        val user = GlobalRepo.getOrCreateUser(userId, chat.username, chat.firstName, chat.lastName)

        if (GlobalRepo.isSubscriptionActive(userId)) {
            val expiryDate = GlobalRepo.getSubscriptionExpiryDate(userId)
            val subType = GlobalRepo.getSubscriptionType(userId)

            sendMessage(
                """
                ✅ **Your subscription is active!**
                
                Type: ${subType?.toDisplayName() ?: "Unknown"}
                Valid until: ${expiryDate?.toString()?.replace("T", " ")?.substringBefore(".") ?: "Unknown"}
                
                You can now send voice messages for analysis!
                """.trimIndent(),
                parseMode = "Markdown"
            )
        } else {
            sendMessage(
                """
                🎯 **Spanish Practice Bot**

                Hi! I'll help you practice Spanish!

                🎤 **How it works:**
                1. Send me a voice message in Spanish
                2. I'll analyze your speech with AI
                3. Get detailed feedback and an audio response

                🌟 **Choose a plan:**
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

fun SubscriptionType.toDisplayName(): String = when (this) {
    SubscriptionType.MONTHLY -> "Monthly"
    SubscriptionType.YEARLY -> "Yearly"
    SubscriptionType.PROMO -> "Promo Code"
}
