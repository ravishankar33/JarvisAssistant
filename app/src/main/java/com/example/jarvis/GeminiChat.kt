package com.example.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object GeminiChat {

    private val executor = Executors.newSingleThreadExecutor()
    private val history = JSONArray()

    fun sendMessage(
        message: String,
        callback: (Result<String>) -> Unit
    ) {
        executor.execute {
            try {
                val key = BuildConfig.GEMINI_API_KEY
                require(key.isNotBlank()) { "Gemini API key is missing" }

                val contents = JSONArray()

                for (i in 0 until history.length()) {
                    contents.put(history.getJSONObject(i))
                }

                val userMessage = JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(
                        JSONObject().put("text", message)
                    ))

                contents.put(userMessage)

                val body = JSONObject().put("contents", contents)

                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
                )

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-goog-api-key", key)
                connection.doOutput = true
                connection.connectTimeout = 20000
                connection.readTimeout = 60000

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
                val answer = response
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                history.put(userMessage)
                history.put(
                    JSONObject()
                        .put("role", "model")
                        .put("parts", JSONArray().put(
                            JSONObject().put("text", answer)
                        ))
                )

                callback(Result.success(answer))

            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }
}
