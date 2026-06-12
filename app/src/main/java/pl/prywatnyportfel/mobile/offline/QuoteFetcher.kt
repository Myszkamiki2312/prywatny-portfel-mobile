package pl.prywatnyportfel.mobile.offline

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

data class QuoteFetchResult(
    val ticker: String,
    val price: Double,
    val currency: String,
    val provider: String
)

object QuoteFetcher {
    private const val CONNECT_TIMEOUT_MS = 4000
    private const val READ_TIMEOUT_MS = 4000

    fun fetch(ticker: String): QuoteFetchResult? {
        val cleanTicker = ticker.trim().uppercase()
        if (cleanTicker.isBlank()) {
            return null
        }

        val candidates = candidateSymbols(cleanTicker)
        for (symbol in candidates) {
            val result = fetchFromStooq(cleanTicker, symbol)
            if (result != null) {
                return result
            }
        }
        return null
    }

    private fun fetchFromStooq(originalTicker: String, stooqSymbol: String): QuoteFetchResult? {
        return try {
            val encoded = URLEncoder.encode(stooqSymbol.lowercase(), StandardCharsets.UTF_8.name())
            val url = URL("https://stooq.com/q/l/?s=$encoded&f=sd2t2ohlcv&h&e=csv")
            // Bounded I/O: without timeouts a stalled connection hangs the refresh coroutine forever.
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                // Don't silently follow a redirect to an arbitrary host (MITM/DNS hijack of stooq).
                instanceFollowRedirects = false
            }
            val content = try {
                if (connection.responseCode !in 200..299) {
                    // Drain the error body so the socket can be released cleanly.
                    connection.errorStream?.use { it.readBytes() }
                    return null
                }
                connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } finally {
                connection.disconnect()
            }
            val lines = content.lines().filter { it.isNotBlank() }
            if (lines.size < 2) {
                return null
            }
            val row = lines[1].split(',')
            if (row.size < 7) {
                return null
            }
            val close = row[6].trim().replace(',', '.').toDoubleOrNull() ?: return null
            if (close <= 0.0) {
                return null
            }
            val currency = if (stooqSymbol.endsWith(".PL", ignoreCase = true)) "PLN" else "USD"
            QuoteFetchResult(
                ticker = originalTicker,
                price = close,
                currency = currency,
                provider = "stooq"
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun candidateSymbols(ticker: String): List<String> {
        if (ticker.contains('.')) {
            return listOf(ticker)
        }
        return listOf(
            ticker,
            "$ticker.US",
            "$ticker.PL"
        )
    }
}
