package com.example.jarvis

import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object GeminiChat {

    private val executor = Executors.newSingleThreadExecutor()
    private var previousInteractionId: String? = null

    private val models = listOf(
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash-lite"
    )

    fun sendMessage(
        message: String,
        callback: (Result<String>) -> Unit
    ) {
        executor.execute {
            try {
                val key = BuildConfig.GEMINI_API_KEY
                require(key.isNotBlank()) { "Gemini API key is missing" }

                var lastError = "All Gemini models failed"

                for (model in models) {
                    var connection: HttpURLConnection? = null

                    try {
                        val body = JSONObject()
                            .put("model", model)
                            .put("input", message)

                        previousInteractionId?.let {
                            body.put("previous_interaction_id", it)
                        }

                        connection = URL(
                            "https://generativelanguage.googleapis.com/v1beta/interactions"
                        ).openConnection() as HttpURLConnection

                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("x-goog-api-key", key)
                        connection.setRequestProperty("Accept", "application/json")
                        connection.doOutput = true
                        connection.connectTimeout = 30000
                        connection.readTimeout = 90000

                        connection.outputStream.use {
                            it.write(body.toString().toByteArray(Charsets.UTF_8))
                        }

                        val status = connection.responseCode
                        val stream = if (status in 200..299) {
                            connection.inputStream
                        } else {
                            connection.errorStream
                        }

                        val responseText = stream?.bufferedReader()?.use {
                            it.readText()
                        } ?: ""

                        if (status !in 200..299) {
                            lastError = "Gemini $model error $status: $responseText"

                            if (status == 503 || status == 429) {
                                continue
                            }

                            throw Exception(lastError)
                        }

                        val response = JSONObject(responseText)
                        val steps = response.optJSONArray("steps")
                            ?: throw Exception("No response steps")

                        val answerParts = mutableListOf<String>()

                        for (i in 0 until steps.length()) {
                            val step = steps.getJSONObject(i)

                            if (step.optString("type") == "model_output") {
                                val content = step.optJSONArray("content") ?: JSONArray()

                                for (j in 0 until content.length()) {
                                    val part = content.getJSONObject(j)

                                    if (part.optString("type") == "text") {
                                        answerParts.add(part.optString("text"))
                                    }
                                }
                            }
                        }

                        val answer = answerParts.joinToString("\n").trim()

                        if (answer.isBlank()) {
                            throw Exception("Gemini returned an empty answer")
                        }

                        previousInteractionId = response.optString("id")
                            .takeIf { it.isNotBlank() }

                        callback(Result.success(answer))
                        return@execute

                    } catch (e: Exception) {
                        lastError = e.message ?: "Unknown Gemini error"
                        if (!lastError.contains("503") &&
                            !lastError.contains("429")) {
                            throw e
                        }
                    } finally {
                        connection?.disconnect()
                    }
                }

                throw Exception("All Gemini models unavailable. $lastError")

            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }
}
