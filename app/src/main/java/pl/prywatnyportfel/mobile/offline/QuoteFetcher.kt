package pl.prywatnyportfel.mobile.offline

import org.json.JSONObject
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

    // Yahoo v8 chart endpoint with a browser User-Agent is the reliable free quote source (the v7
    // quote endpoint is blocked/429). Try the currency-matched exchange first to avoid ticker
    // collisions (e.g. a PLN "DNP" is Dino Polska on GPW -> DNP.WA, not the US-listed DNP fund).
    private val EXCHANGE_SUFFIXES = listOf(".WA", ".DE", ".L", ".PA", ".MI", ".AS", ".SW", ".US")
    private val SUFFIX_FOR_CURRENCY = mapOf("PLN" to ".WA", "GBP" to ".L", "GBX" to ".L", "CHF" to ".SW")

    fun fetch(ticker: String, currency: String? = null): QuoteFetchResult? {
        val cleanTicker = ticker.trim().uppercase()
        if (cleanTicker.isBlank()) {
            return null
        }
        for (symbol in yahooCandidates(cleanTicker, currency)) {
            val result = fetchFromYahoo(cleanTicker, symbol)
            if (result != null) {
                return result
            }
        }
        // Fallback: Stooq CSV (works well from a phone's residential IP).
        for (symbol in candidateSymbols(cleanTicker)) {
            val result = fetchFromStooq(cleanTicker, symbol)
            if (result != null) {
                return result
            }
        }
        return null
    }

    private fun yahooCandidates(ticker: String, currency: String?): List<String> {
        if (ticker.contains('.')) {
            return listOf(ticker)
        }
        val preferred = SUFFIX_FOR_CURRENCY[currency?.trim()?.uppercase()]
        return if (preferred != null) {
            listOf(ticker + preferred, ticker) + EXCHANGE_SUFFIXES.filter { it != preferred }.map { ticker + it }
        } else {
            listOf(ticker) + EXCHANGE_SUFFIXES.map { ticker + it }
        }
    }

    private fun fetchFromYahoo(originalTicker: String, symbol: String): QuoteFetchResult? {
        return try {
            val encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8.name())
            val url = URL("https://query1.finance.yahoo.com/v8/finance/chart/$encoded?interval=1d&range=2d")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0")
                setRequestProperty("Accept", "application/json")
            }
            val content = try {
                if (connection.responseCode !in 200..299) {
                    connection.errorStream?.use { it.readBytes() }
                    return null
                }
                connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } finally {
                connection.disconnect()
            }
            val meta = JSONObject(content)
                .optJSONObject("chart")
                ?.optJSONArray("result")
                ?.optJSONObject(0)
                ?.optJSONObject("meta")
                ?: return null
            val price = meta.optDouble("regularMarketPrice", 0.0)
            if (price <= 0.0) {
                return null
            }
            val currency = meta.optString("currency", "").ifBlank {
                if (symbol.endsWith(".WA", ignoreCase = true)) "PLN" else "USD"
            }
            QuoteFetchResult(
                ticker = originalTicker,
                price = price,
                currency = currency.uppercase(),
                provider = "yahoo"
            )
        } catch (_: Exception) {
            null
        }
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
