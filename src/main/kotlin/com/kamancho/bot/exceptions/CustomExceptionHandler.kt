package com.kamancho.bot.exceptions

import com.kamancho.bot.repository.GlobalRepo
import io.github.dehuckakpyt.telegrambot.TelegramBot
import io.github.dehuckakpyt.telegrambot.exception.chat.ChatException
import io.github.dehuckakpyt.telegrambot.exception.handler.ExceptionHandlerImpl
import io.github.dehuckakpyt.telegrambot.model.telegram.Chat
import io.github.dehuckakpyt.telegrambot.template.MessageTemplate
import io.github.dehuckakpyt.telegrambot.template.Templater

class CustomExceptionHandler(bot: TelegramBot, template: MessageTemplate, templater: Templater) :
    ExceptionHandlerImpl(bot, template, templater) {

    override suspend fun caught(chat: Chat, ex: Throwable) {
        // Track error in Mixpanel
        if(ex.message?.contains("Unexpected message") == false) {
            GlobalRepo.getAnalytics()?.trackError(
                errorType = ex.javaClass.simpleName,
                errorMessage = ex.message ?: "Unknown error",
                userId = chat.id,
                chatId = chat.id,
                context = mapOf("chatType" to chat.type)
            )
        }

        when (ex) {
            is CustomException -> bot.sendMessage(chat.id, ex.localizedMessage)
            is ChatException -> if(ex.message?.contains("Unexpected message") == false) super.caught(chat, ex)
            else -> super.caught(chat, ex)
        }
    }
}

class CustomException(message: String) : RuntimeException(message)