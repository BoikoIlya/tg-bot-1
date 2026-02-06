package com.kamancho.bot

import io.github.dehuckakpyt.telegrambot.handler.BotHandler

//@Factory
class StartHandler : BotHandler({
    command("/start") {
        sendMessage("Hello, my name is ${bot.username} :-)")
    }
})