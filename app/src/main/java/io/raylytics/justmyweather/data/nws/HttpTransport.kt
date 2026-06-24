package io.raylytics.justmyweather.data.nws

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** One GET worth of HTTP, decoupled from the client so retry/parse logic can
 * be tested with a fake transport. Mirrors the TS client's injectable fetch. */
fun interface HttpTransport {
    suspend fun get(url: String, headers: Map<String, String>): HttpResult
}

/** The minimal slice of a response [NwsClient] needs: status, body, Retry-After. */
data class HttpResult(
    val status: Int,
    val body: String,
    val retryAfter: String?,
)

/** Production transport over OkHttp with NWS's recommended 10s timeout. */
class OkHttpTransport(
    private val client: OkHttpClient = defaultClient,
) : HttpTransport {
    override suspend fun get(url: String, headers: Map<String, String>): HttpResult =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url).get()
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                HttpResult(
                    status = resp.code,
                    body = resp.body?.string() ?: "",
                    retryAfter = resp.header("Retry-After"),
                )
            }
        }

    companion object {
        val defaultClient: OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(10, TimeUnit.SECONDS)
                .build()
    }
}
