package com.kamancho.bot.app

//import org.koin.ksp.generated.defaultModule
import com.kamancho.bot.commands.startCommand
import com.kamancho.bot.commands.subscriptionCommand
import com.kamancho.bot.exceptions.CustomExceptionHandler
import com.kamancho.bot.repository.GlobalRepo
import com.kamancho.bot.updates.handleSubscriptionMessages
import com.kamancho.bot.updates.handleSubscriptionPreCheckout
import io.github.dehuckakpyt.telegrambot.ext.config.receiver.handling
import io.github.dehuckakpyt.telegrambot.ext.config.receiver.longPolling
import io.github.dehuckakpyt.telegrambot.ext.config.receiver.updateHandling
import io.github.dehuckakpyt.telegrambot.plugin.TelegramBot
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.calllogging.CallLogging
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level

fun main(args: Array<String>): Unit = EngineMain.main(args)


fun Application.configureDependencyInjection() {
    install(Koin) {
        slf4jLogger()
//        modules(
////            defaultModule
//        )
    }
    
    // Install call logging for HTTP request analytics
    install(CallLogging) {
        level = Level.INFO
    }
}

fun Application.configureTelegramBot() {
    // Get Mixpanel token from environment variable or config
    // Set it in your environment: export MIXPANEL_TOKEN=your_token_here

    // Initialize database and analytics
    GlobalRepo.init()

    install(TelegramBot) {
        receiving {
            longPolling {
                limit = 10
                timeout = 30
            }
            exceptionHandler = {
                CustomExceptionHandler(
                    telegramBot,
                    receiving.messageTemplate,
                    templater
                )
            }

            handling {
                startCommand()
                subscriptionCommand()
            }

            updateHandling {
                handleSubscriptionMessages(this.bot)
                handleSubscriptionPreCheckout(this.bot)
            }
        }
    }
}

