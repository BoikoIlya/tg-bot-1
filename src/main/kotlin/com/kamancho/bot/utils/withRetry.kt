package com.kamancho.bot.utils

import kotlinx.coroutines.delay
import org.postgresql.util.PSQLException
import java.net.SocketTimeoutException

suspend fun <T> withRetry(
    maxRetries: Int = 3,
    operation: suspend () -> T
): T {
    var lastException: Exception? = null
    var attempt = 1

    while (attempt <= maxRetries) {
        try {
            return operation()
        } catch (e: Exception) {
            lastException = e
            println("⚠️ Operation failed (attempt $attempt/$maxRetries): ${e.message}")

            if (attempt < maxRetries && isConnectionError(e)) {
                val delaySeconds = (1 shl (attempt - 1)) * 1 // 1s, 2s, 4s
                println("⏳ Waiting $delaySeconds seconds...")
                delay(delaySeconds * 1000L)
            } else {
                throw e
            }
            attempt++
        }
    }

    throw lastException ?: RuntimeException("Operation failed after $maxRetries attempts")
}

 fun isConnectionError(e: Exception): Boolean {
    return e is PSQLException ||
            e.message?.contains("connection") == true ||
            e.message?.contains("timeout") == true ||
            e is SocketTimeoutException
}