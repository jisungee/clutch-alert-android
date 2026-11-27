package com.clutchalarm.app.network

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** No redirects, request/response logging, cleartext, or embedded server secrets. */
internal data class ApiResponse(val status: Int, val body: JsonObject = JsonObject(emptyMap()))
internal fun apiBody(vararg fields: Pair<String, String>): JsonObject = buildJsonObject {
    fields.forEach { (key, value) -> put(key, value) }
}

internal fun interface ApiTransport {
    suspend fun request(method: String, path: String, body: JsonObject?, bearer: String?): ApiResponse
}

internal class HttpApi(private val origin: String) : ApiTransport {
    override suspend fun request(method: String, path: String, body: JsonObject?, bearer: String?): ApiResponse =
        withContext(Dispatchers.IO) {
            require(origin.startsWith("https://") && path.startsWith("/api/"))
            val connection = URL(origin.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = if (path.endsWith("/logout")) 2000 else 10000
                connection.readTimeout = if (path.endsWith("/logout")) 2000 else 70000
                connection.setRequestProperty("Accept", "application/json")
                if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
                if (body != null) {
                    val bytes = body.toString().toByteArray(Charsets.UTF_8)
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.use { input ->
                    val buffer = ByteArray(4096)
                    val output = java.io.ByteArrayOutputStream()
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (output.size() + n > 65536) throw IOException("Response too large")
                        output.write(buffer, 0, n)
                    }
                    output.toString("UTF-8")
                }.orEmpty()
                val parsed = if (raw.isBlank()) JsonObject(emptyMap()) else try {
                    Json.parseToJsonElement(raw) as? JsonObject ?: JsonObject(emptyMap())
                } catch (_: IllegalArgumentException) { JsonObject(emptyMap()) }
                ApiResponse(status, parsed)
            } finally { connection.disconnect() }
        }
}
