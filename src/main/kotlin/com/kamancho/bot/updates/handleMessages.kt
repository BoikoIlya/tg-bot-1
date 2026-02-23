package com.kamancho.bot.updates

import com.kamancho.bot.app.NetworkClient
import com.kamancho.bot.commands.toDisplayName
import com.kamancho.bot.model.SubscriptionType
import com.kamancho.bot.repository.GlobalRepo
import com.kamancho.bot.utils.convertPcmBase64ToOggByteArray
import com.kamancho.bot.utils.escapeMarkdownV2
import io.github.dehuckakpyt.telegrambot.TelegramBot
import io.github.dehuckakpyt.telegrambot.factory.keyboard.inlineKeyboard
import io.github.dehuckakpyt.telegrambot.handling.BotUpdateHandling
import io.github.dehuckakpyt.telegrambot.model.telegram.KeyboardButton
import io.github.dehuckakpyt.telegrambot.model.telegram.ReplyKeyboardMarkup
import io.github.dehuckakpyt.telegrambot.model.telegram.input.ByteArrayContent
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

fun BotUpdateHandling.handleSubscriptionMessages(bot: TelegramBot) {
    message {
        val chatId = chat.id
        val text = text
        val voice = voice
        val payment = successfulPayment

        // Handle different message types
        when {
            // 1. PAYMENT SUCCESS
            payment != null -> handlePayment(bot, chatId, payment)
            
            // 2. PROMO CODE (only if user is in waiting list)
            text != null && GlobalRepo.promoWaiting.contains(chatId) -> handlePromoCode(bot, chatId, text)
            
            // 3. VOICE MESSAGE
            voice != null -> handleVoiceMessage(bot, chatId, voice)

            // 4. MENU BUTTON PRESSED
            text == "🏠 Main Menu" -> handleMenuRequest(bot, chatId)

            // 5. HELP BUTTON PRESSED
            text == "❓ Help" -> handleHelpRequest(bot, chatId)

            // 6. RESEND BUTTON PRESSED
            text == "🔄 Retry" -> handleResendRequest(bot, chatId)
        }
    }
}

/**
 * Handle successful payment
 */
private suspend fun BotUpdateHandling.handlePayment(
    bot: TelegramBot,
    chatId: Long,
    payment: io.github.dehuckakpyt.telegrambot.model.telegram.SuccessfulPayment
) {
    println("[PAYMENT] Processing payment for chat $chatId")

    val chargeId = payment.telegramPaymentChargeId
    val payload = payment.invoicePayload
    val amount = payment.totalAmount

    println("SUCCESS: chargeId=$chargeId, payload=$payload, amount=$amount")

    val subscriptionType = when {
        payload.startsWith("sub_monthly") -> SubscriptionType.MONTHLY to 30
        payload.startsWith("sub_yearly") -> SubscriptionType.YEARLY to 365
        else -> null
    }

    if (subscriptionType != null) {
        GlobalRepo.activateSubscription(
            userId = chatId,
            type = subscriptionType.first,
            durationDays = subscriptionType.second,
            paymentChargeId = chargeId
        )

        // Track successful payment with Mixpanel
        GlobalRepo.getAnalytics()?.trackPayment(
            userId = chatId,
            chatId = chatId,
            amount = amount.toLong(),
            currency = "USD",
            subscriptionType = subscriptionType.first.name,
            chargeId = chargeId
        )

        // Track subscription activated
        GlobalRepo.getAnalytics()?.trackSubscriptionActivated(
            userId = chatId,
            subscriptionType = subscriptionType.first.name,
            durationDays = subscriptionType.second,
            method = "payment"
        )

        bot.sendMessage(
            chatId = chatId,
            text = """
                ✅ **Payment successful!**

                Your subscription is activated.
                Type: ${subscriptionType.first.toDisplayName()}

                You can now send voice messages for analysis! 🎤
                """.trimIndent(),
            parseMode = "Markdown",
            replyMarkup = ReplyKeyboardMarkup(
                keyboard = listOf(
                    listOf(KeyboardButton("🏠 Main Menu"), KeyboardButton("❓ Help"))
                ),
                resizeKeyboard = true,
                oneTimeKeyboard = false
            )
        )
    } else {
        bot.sendMessage(chatId = chatId, text = "✅ Payment successful!")
    }
}

/**
 * Handle promo code input
 */
private suspend fun BotUpdateHandling.handlePromoCode(
    bot: TelegramBot,
    chatId: Long,
    text: String
) {
    println("[PROMO] Processing promo code for chat $chatId")

    GlobalRepo.promoWaiting.remove(chatId)

    val user = GlobalRepo.getUser(chatId)
    if (user != null) {
        val activeSub = GlobalRepo.getSubscriptionType(chatId)
        if (activeSub == SubscriptionType.PROMO) {
            bot.sendMessage(chatId, "❌ You have already activated a promo code.")
            return
        }
    }

    val promoCode = text.trim().uppercase()
    val validatedPromo = GlobalRepo.validatePromoCode(promoCode)

    if (validatedPromo == null) {
        // Track failed promo code attempt
        GlobalRepo.getAnalytics()?.trackPromoCode(
            userId = chatId,
            code = promoCode,
            success = false
        )
        bot.sendMessage(chatId, "❌ Invalid or inactive promo code.")
        return
    }

    if (GlobalRepo.hasUserUsedPromoCode(chatId, promoCode)) {
        GlobalRepo.getAnalytics()?.trackPromoCode(
            userId = chatId,
            code = promoCode,
            success = false
        )
        bot.sendMessage(chatId, "❌ You have already used this promo code.")
        return
    }

    GlobalRepo.activatePromoCodeSubscription(
        userId = chatId,
        promoCode = promoCode,
        durationDays = validatedPromo.durationDays
    )

    // Track successful promo code activation
    GlobalRepo.getAnalytics()?.trackPromoCode(
        userId = chatId,
        code = promoCode,
        success = true,
        durationDays = validatedPromo.durationDays
    )
    
    GlobalRepo.getAnalytics()?.trackSubscriptionActivated(
        userId = chatId,
        subscriptionType = SubscriptionType.PROMO.name,
        durationDays = validatedPromo.durationDays,
        method = "promo_code"
    )

    bot.sendMessage(
        chatId = chatId,
        text = """
            ✅ **Promo code activated!**

            Your subscription is valid for ${validatedPromo.durationDays} days.
            You can now send voice messages for analysis! 🎤
            """.trimIndent(),
        parseMode = "Markdown",
        replyMarkup = ReplyKeyboardMarkup(
            keyboard = listOf(
                listOf(KeyboardButton("🏠 Main Menu"), KeyboardButton("❓ Help"))
            ),
            resizeKeyboard = true,
            oneTimeKeyboard = false
        )
    )
}

/**
 * Handle voice message processing
 */
private suspend fun BotUpdateHandling.handleVoiceMessage(
    bot: TelegramBot,
    chatId: Long,
    voice: io.github.dehuckakpyt.telegrambot.model.telegram.Voice
) {
    println("[VOICE] Processing voice message for chat $chatId")

    // Track voice message received
    GlobalRepo.getAnalytics()?.trackMessage(chatId, chatId, "private", "voice")

    // Check subscription
    if (!GlobalRepo.isSubscriptionActive(chatId)) {
        bot.sendMessage(
            chatId = chatId,
            text = """
                ⚠️ **No active subscription**

                You need an active subscription to use this feature.

                Click /start to subscribe!
                """.trimIndent(),
            parseMode = "Markdown",
            replyMarkup = inlineKeyboard(
                callbackButton("🌟 Yearly — 2499 Stars (Save 60%)", next = "sub_yearly"),
                callbackButton("🌟 Monthly — 499 Stars", next = "sub_monthly"),
                callbackButton("💎 Enter Promo Code", next = "promo")
            )
        )
        return
    }

    // Process voice in background
    withContext(Dispatchers.IO) {
        processVoiceWithGemini(bot, chatId, voice)
    }
}

/**
 * Process voice message with Gemini API
 */
private suspend fun processVoiceWithGemini(
    bot: TelegramBot,
    chatId: Long,
    voice: io.github.dehuckakpyt.telegrambot.model.telegram.Voice?,  // Nullable for retry
    base64: String? = null  // Optional: reuse existing base64 on retry
) {
    val audioBase64 = base64 ?: run {
        try {
            // Download voice file
            val fileId = voice!!.fileId
            val file = bot.getFile(fileId)
            val filePath = file.filePath!!
            val fileUrl = "https://api.telegram.org/file/bot${System.getenv("TELEGRAM_BOT_TOKEN")}/$filePath"

            val bytes: ByteArray = NetworkClient.httpClient.get(fileUrl).body()
            Base64.getEncoder().encodeToString(bytes)
        } catch (e: Exception) {
            println("[VOICE] Error downloading voice: ${e.message}")
            bot.sendMessage(
                chatId = chatId,
                text = """
                    ❌ **Download error**
                    
                    Failed to download your voice message.
                    
                    Please try sending it again.
                    """.trimIndent(),
                parseMode = "Markdown",
                replyMarkup = ReplyKeyboardMarkup(
                    keyboard = listOf(
                        listOf(KeyboardButton("🏠 Main Menu"), KeyboardButton("❓ Help"))
                    ),
                    resizeKeyboard = true,
                    oneTimeKeyboard = false
                )
            )
            return
        }
    }

    try {
        println("[VOICE] Processing audio, size: ${audioBase64.length}")

        // Notify user
        bot.sendMessage(
            chatId = chatId,
            text = "🔄 Analyzing your message... (this may take a few seconds)",
            replyMarkup = ReplyKeyboardMarkup(
                keyboard = listOf(
                    listOf(KeyboardButton("🏠 Main Menu"), KeyboardButton("❓ Help"))
                ),
                resizeKeyboard = true,
                oneTimeKeyboard = false
            )
        )

        // Call Gemini API

            NetworkClient.analyzeSpanishAndGenerateAudio(
                audioBase64,
                onAnalysisResult = { analysisText ->
                    // Clear failed message on success
                    GlobalRepo.failedVoiceMessages.remove(chatId)
                    
                    bot.sendMessage(
                        chatId = chatId,
                        text = analysisText,
                        replyMarkup = ReplyKeyboardMarkup(
                            keyboard = listOf(
                                listOf(KeyboardButton("🏠 Main Menu"), KeyboardButton("❓ Help"))
                            ),
                            resizeKeyboard = true,
                            oneTimeKeyboard = false
                        )
                    )
                },
                onTtsResult = { audio, text ->
                    // Clear failed message on success
                    GlobalRepo.failedVoiceMessages.remove(chatId)
                    
                    // Send voice response
                    bot.sendVoice(
                        chatId = chatId,
                        voice = ByteArrayContent(convertPcmBase64ToOggByteArray(audio)),
                    )
                    
                    // Send text transcription
                    bot.sendMessage(
                        chatId = chatId,
                        text = "📝 Response text:\n||${escapeMarkdownV2(text)}||",
                        parseMode = "MarkdownV2",
                        replyMarkup = ReplyKeyboardMarkup(
                            keyboard = listOf(
                                listOf(KeyboardButton("🏠 Main Menu"), KeyboardButton("❓ Help"))
                            ),
                            resizeKeyboard = true,
                            oneTimeKeyboard = false
                        )
                    )
                }
            )
    } catch (e: Exception) {
        println("[VOICE] Error processing voice: ${e.message}")
        e.printStackTrace()

        // Track error with Mixpanel
        GlobalRepo.getAnalytics()?.trackError(
            errorType = e.javaClass.simpleName,
            errorMessage = e.message ?: "Unknown error",
            userId = chatId,
            chatId = chatId,
            context = mapOf("feature" to "voice_processing")
        )

        // Store the base64 for retry
        GlobalRepo.failedVoiceMessages[chatId] = audioBase64

        // Show error with resend button
        bot.sendMessage(
            chatId = chatId,
            text = """
                ❌ **Processing error**

                An error occurred while analyzing your voice message.

                Click "🔄 Retry" to try again with the same audio.
                """.trimIndent(),
            parseMode = "Markdown",
            replyMarkup = ReplyKeyboardMarkup(
                keyboard = listOf(
                    listOf(KeyboardButton("🔄 Retry")),
                    listOf(KeyboardButton("🏠 Main Menu"), KeyboardButton("❓ Help"))
                ),
                resizeKeyboard = true,
                oneTimeKeyboard = false
            )
        )
    }
}

/**
 * Handle menu button press
 */
private suspend fun BotUpdateHandling.handleMenuRequest(
    bot: TelegramBot,
    chatId: Long
) {
    println("[MENU] Showing main menu for chat $chatId")
    
    val user = GlobalRepo.getOrCreateUser(chatId, null, null, null)
    
    if (GlobalRepo.isSubscriptionActive(chatId)) {
        val expiryDate = GlobalRepo.getSubscriptionExpiryDate(chatId)
        val subType = GlobalRepo.getSubscriptionType(chatId)
        
        bot.sendMessage(
            chatId = chatId,
            text = """
                🎯 **Spanish Practice Bot**
                
                👋 Hi, ${user.firstName ?: "friend"}!
                
                ✅ **Your subscription is active**
                Type: ${subType?.toDisplayName() ?: "Unknown"}
                Valid until: ${expiryDate?.toString()?.replace("T", " ")?.substringBefore(".") ?: "Unknown"}
                
                🎤 **Send a voice message** to practice Spanish!
                """.trimIndent(),
            parseMode = "Markdown",
            replyMarkup = ReplyKeyboardMarkup(
                keyboard = listOf(
                    listOf(KeyboardButton("🏠 Main Menu"), KeyboardButton("❓ Help"))
                ),
                resizeKeyboard = true,
                oneTimeKeyboard = false
            )
        )
    } else {
        bot.sendMessage(
            chatId = chatId,
            text = """
                🎯 **Spanish Practice Bot**
                
                👋 Hi, ${user.firstName ?: "friend"}!

                I'll help you practice Spanish!

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

/**
 * Handle help button press
 */
private suspend fun BotUpdateHandling.handleHelpRequest(
    bot: TelegramBot,
    chatId: Long
) {
    println("[HELP] Showing help message for chat $chatId")

    bot.sendMessage(
        chatId = chatId,
        text = """
            Need Help?

            You can write about your problem here: @kamancho_dev

            Our support team will assist you!
            """.trimIndent(),
        replyMarkup = ReplyKeyboardMarkup(
            keyboard = listOf(
                listOf(KeyboardButton("🏠 Main Menu"), KeyboardButton("❓ Help"))
            ),
            resizeKeyboard = true,
            oneTimeKeyboard = false
        )
    )
}

/**
 * Handle resend button press - reuse the failed voice message
 */
private suspend fun BotUpdateHandling.handleResendRequest(
    bot: TelegramBot,
    chatId: Long
) {
    println("[RESEND] Retrying failed voice message for chat $chatId")
    
    val storedBase64 = GlobalRepo.failedVoiceMessages[chatId]
    
    if (storedBase64 != null) {
        println("[RESEND] Found stored voice message, retrying...")
        // Reuse the stored base64 audio
        withContext(Dispatchers.IO) {
            processVoiceWithGemini(
                bot = bot,
                chatId = chatId,
                voice = null,  // No voice object needed when we have base64
                base64 = storedBase64
            )
        }
    } else {
        println("[RESEND] No stored voice message found")
        bot.sendMessage(
            chatId = chatId,
            text = """
                🎤 **Send a voice message**
                
                I don't have a saved message to retry.
                
                Please send a new voice message.
                """.trimIndent(),
            parseMode = "Markdown",
            replyMarkup = ReplyKeyboardMarkup(
                keyboard = listOf(
                    listOf(KeyboardButton("🏠 Main Menu"), KeyboardButton("❓ Help"))
                ),
                resizeKeyboard = true,
                oneTimeKeyboard = false
            )
        )
    }
}


fun BotUpdateHandling.handleSubscriptionPreCheckout(bot: TelegramBot) {
    preCheckoutQuery {
        val isValid = invoicePayload.startsWith("sub_")
        bot.answerPreCheckoutQuery(
            preCheckoutQueryId = id,
            ok = isValid,
            errorMessage = if (!isValid) "Payment error" else null
        )
    }
}
