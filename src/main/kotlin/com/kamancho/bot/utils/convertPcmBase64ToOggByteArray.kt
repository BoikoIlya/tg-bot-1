package com.kamancho.bot.utils

import org.bytedeco.ffmpeg.ffmpeg
import org.bytedeco.javacpp.Loader
import java.util.Base64

fun convertPcmBase64ToOggByteArray(base64Pcm: String): ByteArray {
    val pcmBytes = Base64.getDecoder().decode(base64Pcm)

    val isLocalLaunch  = System.getenv("DB_USER").equals("admin")
    val ffmpegPath = if(isLocalLaunch) Loader.load(ffmpeg::class.java)
                     else "/app/vendor/ffmpeg/ffmpeg"

    val pb = ProcessBuilder(
        ffmpegPath,
        "-f", "s16le",
        "-ar", "24000",
        "-ac", "1",
        "-i", "pipe:0",
        "-c:a", "libopus",
        "-f", "ogg",
        "pipe:1"
    )

    val process = pb.start()

    // Пишем PCM в stdin
    process.outputStream.use { it.write(pcmBytes); it.flush() }

    // Читаем Ogg из stdout как бинарь
    val oggBytes = process.inputStream.readAllBytes()

    process.waitFor()

    return oggBytes
}
