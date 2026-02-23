package com.kamancho.bot.app

import io.ktor.server.application.Application

@Suppress("unused")
fun Application.module() {
    configureDependencyInjection()
    configureTelegramBot()
}
