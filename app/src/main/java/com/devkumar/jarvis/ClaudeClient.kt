package com.devkumar.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Anthropic Messages API का सीधा, बिना-लाइब्रेरी क्लाइंट। */
class ClaudeClient(private val apiKey: String, private val model: String) {

    fun send(system: String, messages: JSONArray, tools: JSONArray): JSONObject {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 1024)
            .put("system", system)
            .put("tools", tools)
            .put("messages", messages)
            .toString()

        var last = "API त्रुटि"
        for (attempt in 1..3) {
            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                conn.doOutput = true
                conn.setRequestProperty("content-type", "application/json")
                conn.setRequestProperty("x-api-key", apiKey)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code in 200..299) return JSONObject(text)

                last = friendly(code, text)
                val retryable = code == 429 || code == 529 || code >= 500
                if (retryable && attempt < 3) {
                    Thread.sleep(2000L * attempt)
                    continue
                }
                throw IOException(last)
            } finally {
                conn.disconnect()
            }
        }
        throw IOException(last)
    }

    private fun friendly(code: Int, text: String): String {
        val detail = try {
            JSONObject(text).optJSONObject("error")?.optString("message") ?: ""
        } catch (e: Exception) {
            ""
        }
        return when (code) {
            401 -> "API key गलत है (401)। सेटिंग्स में सही key डालें।"
            403 -> "इस key को इस मॉडल की अनुमति नहीं है (403)। $detail"
            404 -> "मॉडल का नाम गलत है (404)। सेटिंग्स में जाँचें। $detail"
            429 -> "बहुत ज़्यादा रिक्वेस्ट या लिमिट पूरी (429)। थोड़ी देर बाद कोशिश करें।"
            else -> "API त्रुटि $code: $detail"
        }
    }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    }
}
