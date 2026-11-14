package com.example.jarvis

import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object GeminiChat {

    private val executor = Executors.newSingleThreadExecutor()
    private var previousInteractionId: String? = null

    fun sendMessage(
        message: String,
        callback: (Result<String>) -> Unit
    ) {
        executor.execute {
            try {
                val key = BuildConfig.GEMINI_API_KEY
                require(key.isNotBlank()) { "Gemini API key is missing" }

                val body = JSONObject()
                    .put("model", "gemini-3.8-flash")
                    .put("input", message)

                previousInteractionId?.let {
                    body.put("previous_interaction_id", it)
                }

                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/interactions"
                )

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-goog-api-key", key)
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 90000
                connection.setRequestProperty("Connection", "close")
                connection.setRequestProperty("Accept", "application/json")

                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val status = connection.responseCode
                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val responseText = stream.bufferedReader().use { it.readText() }
                connection.disconnect()

                if (status !in 200..299) {
                    throw Exception("Gemini API error $status: $responseText")
                }

                val response = JSONObject(responseText)
                val steps = response.optJSONArray("steps")
                    ?: throw Exception("No response steps: $responseText")

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

            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }
}
