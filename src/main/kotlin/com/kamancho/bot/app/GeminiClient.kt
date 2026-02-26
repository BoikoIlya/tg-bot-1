package com.kamancho.bot.app

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Client for Google Gemini API
 * Handles text analysis and text-to-speech generation
 * 
 * @param apiKey Google Gemini API key
 * @param timeoutMs Request timeout in milliseconds (default: 60 seconds)
 */
object NetworkClient{
    private val apiKey: String = System.getenv("GEMINI_API_KEY")
    private val logger = LoggerFactory.getLogger(NetworkClient::class.java)

    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = false
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Analyzes voice message and generates audio response
     * Two-step process:
     * 1. Analyze voice with gemini-2.0-flash-exp (text analysis + dialogue)
     * 2. Generate speech with gemini-2.5-flash-preview-tts
     * 
     * @param base64Audio Base64 encoded audio (OGG format)
     * @param onAnalysisResult Callback for text analysis (Russian feedback)
     * @param onTtsResult Callback for TTS result (base64 audio + dialogue text)
     * @return ResultData with analysis, dialogue, and audio
     * @throws Exception if API call fails
     */
    suspend fun analyzeSpanishAndGenerateAudio(
        base64Audio: String,
        onAnalysisResult: suspend (String) -> Unit,
        onTtsResult: suspend (String, String) -> Unit
    ): ResultData {
        logger.info("Starting voice analysis for audio size: ${base64Audio.length}")
        
        // Step 1: Text analysis
        val analysisResult = analyzeVoice(base64Audio)
        logger.info("Analysis complete: ${analysisResult.textAnalysis}")
        
        // Notify user with text analysis
        onAnalysisResult(analysisResult.textAnalysis)
        
        // Step 2: Text-to-speech
        val audioBase64 = generateSpeech(analysisResult.dialogueToSpeak)
        logger.info("TTS complete: audio size ${audioBase64.length}")
        
        // Notify user with voice response
        onTtsResult(audioBase64, analysisResult.dialogueToSpeak)
        
        return analysisResult.copy(audioBase64 = audioBase64)
    }
    
    /**
     * Step 1: Analyze voice message with Gemini
     * Returns text feedback (Russian) + dialogue to speak (English)
     */
    private suspend fun analyzeVoice(base64Audio: String): ResultData {
        logger.debug("Sending audio to Gemini for analysis")
        
        val response = httpClient.post(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=$apiKey"
        ) {
            contentType(ContentType.Application.Json)
            setBody(buildAnalysisRequest(base64Audio))
        }
        
        if (response.status != HttpStatusCode.OK) {
            val errorBody = response.bodyAsText()
            logger.error("Gemini analysis failed: ${response.status} - $errorBody")
            throw ApiException("Gemini analysis failed: ${response.status}", errorBody)
        }
        
        val responseText = response.bodyAsText()
        return parseAnalysisResponse(responseText)
    }
    
    /**
     * Step 2: Generate speech from text using Gemini TTS
     */
    private suspend fun generateSpeech(text: String): String {
        logger.debug("Generating speech for text: $text")
        
        val response = httpClient.post(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent?key=$apiKey"
        ) {
            contentType(ContentType.Application.Json)
            setBody(buildTtsRequest(text))
        }
        
        if (response.status != HttpStatusCode.OK) {
            val errorBody = response.bodyAsText()
            logger.error("Gemini TTS failed: ${response.status} - $errorBody")
            throw ApiException("Gemini TTS failed: ${response.status}", errorBody)
        }
        
        val responseText = response.bodyAsText()
        return parseTtsResponse(responseText)
    }
    
    // ==================== REQUEST BUILDERS ====================
    
    private fun buildAnalysisRequest(base64Audio: String): String {
        return """
            {
              "system_instruction": {
                "parts": [
                  {
                    "text": "${System.getenv("MAIN_PROMPT")}"
                  }
                ]
              },
              "contents": [
                {
                  "role": "user",
                  "parts": [
                    {
                      "inline_data": {
                        "mime_type": "audio/ogg",
                        "data": "$base64Audio"
                      }
                    },
                    {
                      "text": "Here is my audio recording."
                    }
                  ]
                }
              ],
              "generationConfig": {
                "response_mime_type": "application/json",
                "temperature": 0.5
              }
            }
        """.trimIndent()
    }
    
    private fun buildTtsRequest(text: String): String {
        return """
        {
          "contents": [{
            "role": "user",
            "parts": [{ "text": "$text" }]
          }],
          "generationConfig": {
            "responseModalities": ["AUDIO"],
            "speechConfig": {
              "voiceConfig": {
                "prebuiltVoiceConfig": {
                  "voiceName": "Aoede"
                }
              }
            }
          }
        }
        """.trimIndent()
    }

//    {
//        "system_instruction": {
//        "parts": [
//        {
//            "text": "
//            You are an expert Spanish language tutor.
//            1) Listen to the user.
//            2) Provide grammatical corrections and feedback in English.
//            3) If everything is ok, you can optionally suggest alternatives or slang, but you MUST NOT create nested JSON.
//            4) Write a natural conversational response in Spanish under 15-30 words.
//            STRICT CONSTRAINT:
//            - Return STRICTLY a JSON object with EXACTLY TWO keys:
//            1) \"text_analysis\" → must be a plain string with your feedback in English. NO objects, NO arrays.
//            2) \"dialogue_to_speak\" → must be a plain string.
//            - Do NOT include any other keys.
//            - Return ONLY raw JSON. No markdown, no explanations, no extra fields.
//            "
//        }
//        ]
//    },
//        "contents": [
//        {
//            "role": "user",
//            "parts": [
//            {
//                "inline_data": {
//                "mime_type": "audio/ogg",
//                "data": "$base64Audio"
//            }
//            },
//            {
//                "text": "Analyze my Spanish and reply to keep the conversation going, if there is no specific topic suggest random. At the end always should be a question"
//            }
//            ]
//        }
//        ],
//        "generationConfig": {
//        "response_mime_type": "application/json",
//        "temperature": 0.5
//    }
//    }
    
    // ==================== RESPONSE PARSERS ====================
    
    private fun parseAnalysisResponse(raw: String): ResultData {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val textBlock = root["candidates"]?.jsonArray?.get(0)
                ?.jsonObject?.get("content")?.jsonObject?.get("parts")
                ?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?: throw ParsingException("No text in response")
            
            val parsed = json.parseToJsonElement(textBlock).jsonObject
            
            val textAnalysis = parsed["text_analysis"]?.jsonPrimitive?.content
                ?: throw ParsingException("Missing text_analysis")
            
            val dialogueToSpeak = parsed["dialogue_to_speak"]?.jsonPrimitive?.content
                ?: throw ParsingException("Missing dialogue_to_speak")
            
            ResultData(
                textAnalysis = textAnalysis,
                dialogueToSpeak = dialogueToSpeak,
                audioBase64 = ""  // Will be filled by TTS
            )
        } catch (e: Exception) {
            logger.error("Failed to parse analysis response: $raw", e)
            throw ParsingException("Failed to parse analysis response: ${e.message}", e)
        }
    }
    
    private fun parseTtsResponse(raw: String): String {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            root["candidates"]?.jsonArray?.get(0)
                ?.jsonObject?.get("content")?.jsonObject?.get("parts")
                ?.jsonArray?.get(0)?.jsonObject?.get("inlineData")
                ?.jsonObject?.get("data")?.jsonPrimitive?.content
                ?: throw ParsingException("No audio data in TTS response")
        } catch (e: Exception) {
            logger.error("Failed to parse TTS response: $raw", e)
            throw ParsingException("Failed to parse TTS response: ${e.message}", e)
        }
    }

}

// ==================== DATA CLASSES ====================

/**
 * Result from Gemini API
 */
data class ResultData(
    val textAnalysis: String,      // Feedback in Russian
    val dialogueToSpeak: String,   // English response
    val audioBase64: String        // Generated speech (base64)
)

// ==================== EXCEPTIONS ====================

/**
 * API error from Gemini
 */
class ApiException(
    message: String,
    val responseBody: String
) : Exception(message)

/**
 * JSON parsing error
 */
class ParsingException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
