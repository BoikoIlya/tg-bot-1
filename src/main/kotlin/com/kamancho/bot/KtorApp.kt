package com.kamancho.bot

import io.github.dehuckakpyt.telegrambot.config.TelegramBotConfig
import io.github.dehuckakpyt.telegrambot.exception.handler.ExceptionHandler
import io.github.dehuckakpyt.telegrambot.ext.config.receiver.longPolling
import io.github.dehuckakpyt.telegrambot.factory.TelegramBotFactory
import io.github.dehuckakpyt.telegrambot.factory.keyboard.inlineKeyboard
import io.github.dehuckakpyt.telegrambot.handling.BotHandling
import io.github.dehuckakpyt.telegrambot.handling.BotUpdateHandling
import io.github.dehuckakpyt.telegrambot.model.telegram.Chat
import io.github.dehuckakpyt.telegrambot.model.telegram.InlineQueryResultArticle
import io.github.dehuckakpyt.telegrambot.model.telegram.InputTextMessageContent
import io.github.dehuckakpyt.telegrambot.model.telegram.input.ByteArrayContent
import io.github.dehuckakpyt.telegrambot.model.telegram.input.Input
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.random.Random

private var MY_CHAT_ID: Long? = null

suspend fun main(args: Array<String>): Unit {
    val config = TelegramBotConfig().apply {
        token = "8267456501:AAFFXROfzjt2YFeAEZYbA7ntV2l8WPwqVQ8"
        username = "@kab412bot"

        receiving {
            longPolling {
                limit = 10
                timeout = 25
            }

            exceptionHandler = {
                object : ExceptionHandler {
                    override suspend fun execute(
                        chat: Chat,
                        block: suspend () -> Unit
                    ) {
                        try {
                            block()
                        } catch (e: Exception) {
                            println("Ошибка в чате ${chat.id}: ${e.message}")
                        }
                    }
                }
            }

            handling = {
                sstartCommand()
                exceptionCommand()
//                myInfoCommand() // Добавляем новую команду
            }

            updateHandling = {
                onSomeEvent()
            }
        }
    }

    val context = TelegramBotFactory.createTelegramBotContext(config)
    val bot = context.telegramBot
    val updateReceiver = context.updateReceiver

    // Запускаем receiver перед отправкой сообщений
    updateReceiver.start()

    // Вместо отправки сообщения боту, выводим в консоль
    println("Telegram Bot ${bot.username} started! v2")
//    println("Чтобы получить ваш chat ID, отправьте боту команду /myid")

    // Ждем ввода для остановки
    readlnOrNull()
    updateReceiver.stop()
}

fun BotHandling.sstartCommand() {
    command("/start") {


        sendMessage(
            "🤖 *Добро пожаловать в бот 412 кабинета!*\n\n" +
                    "Выберите нужную опцию:",
            replyMarkup = inlineKeyboard(
                callbackButton(
                    text = "🎤 Вызвать Mellstroy",
                    next = "call_mellstroy"
                )))
//                callbackButton(
//                    text = "ℹ️ Информация о кабинете",
//                    next = "cabinet_info"
//                ),
//                callbackButton(
//                    text = "📞 Контакты",
//                    next = "show_contacts"
//                ),
//                callbackButton(
//                    text = "🕒 График работы",
//                    next = "show_schedule"
//                )

    }

    callback("call_mellstroy") {
        val chatId = chat.id
        if (chatId != 8267456501L) { // Проверяем, что это не ID бота
            try {

                val audioBytes = getRandomAudioFile().readBytes()

                println(audioBytes.size)

                bot.sendVoice(
                    chatId = chatId,
                    voice = ByteArrayContent(audioBytes))

            } catch (e: Exception) {
                sendMessage("Не удалось отправить тестовое сообщение: ${e.message}")
            }
        }
    }
}

fun BotHandling.myInfoCommand() {
    command("/myid") {
        val chatId = chat.id
        MY_CHAT_ID = chatId // Сохраняем для дальнейшего использования

        sendMessage(
            "Ваш chat ID: $chatId\n" +
                    "Ваше имя: ${from?.firstName} ${from?.lastName ?: ""}\n" +
                    "Username: @${from?.username ?: "не указан"}"
        )

        // Теперь можно отправить себе сообщение, зная свой ID
        if (chatId != 8267456501L) { // Проверяем, что это не ID бота
            try {
                bot.sendMessage(chatId, "Это тестовое сообщение, отправленное на ваш chat ID!")
                println("Отправлено сообщение в чат: $chatId")
            } catch (e: Exception) {
                sendMessage("Не удалось отправить тестовое сообщение: ${e.message}")
            }
        }
    }
}

fun BotHandling.exceptionCommand() {
    command("/mellstroy") {
        val chatId = chat.id
        if (chatId != 8267456501L) { // Проверяем, что это не ID бота
            try {
//                bot.sendMessage(chatId, "Умный в гору не пойдет, умный гору обойдет, иди нахуй животное!")

                val audioBytes = File("audio/mell.mp3").readBytes()

                println(audioBytes.size)

              bot.sendVoice(
                  chatId = chatId,
                    voice = ByteArrayContent(audioBytes),
//                    caption = "Это голосовое сообщение с текстом 👇"
                )
//                println("Отправлено сообщение в чат: $chatId")
            } catch (e: Exception) {
                sendMessage("Не удалось отправить тестовое сообщение: ${e.message}")
            }
        }
    }
}

fun BotUpdateHandling.onSomeEvent() {
    val logger = LoggerFactory.getLogger("BotEventHandling")

    message {
        logger.info("Получено сообщение от ${from?.firstName}: $text")

        // Пример обработки обычных сообщений
        if (text?.startsWith("Привет") == true) {
            bot.sendMessage(chat.id, "И тебе привет, ${from?.firstName}!")
        }
    }

    // Обработка inline-запросов
    inlineQuery {
        logger.info("Inline query: $query")

        // Создаем результаты для inline-режима
        val results = listOf(
            InlineQueryResultArticle(
                id = "1",
                title = "Пример результата",
                inputMessageContent = InputTextMessageContent("Вы выбрали: $query")
            )
        )

        bot.answerInlineQuery(inlineQueryId = id, results = results)
    }
}

fun getRandomAudioFile(): File {
    val audioDir = File("audio")


    val audioFiles = audioDir.listFiles { file ->
        println(file.name)
        file.isFile && file.extension.lowercase() in listOf("mp3", "ogg", "wav", "m4a")
    }


    // Выбираем случайный файл
    val randomIndex = Random.nextInt(0, audioFiles.size)
    return audioFiles[randomIndex]
}