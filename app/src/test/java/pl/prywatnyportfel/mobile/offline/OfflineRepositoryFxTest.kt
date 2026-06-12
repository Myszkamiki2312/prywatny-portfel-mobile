package pl.prywatnyportfel.mobile.offline

import androidx.test.core.app.ApplicationProvider
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the REAL offline engine (OfflineRepository + Room) on the JVM via Robolectric.
 * Verifies multi-currency FX parity: a USD-priced holding in a PLN-base portfolio must be
 * converted with state.meta.fxRates, so reports/metrics/scanner match the JS dashboard.
 *
 * Scenario: base PLN, USD/PLN = 4.0, deposit 5000 PLN, buy 10 @ 100 USD (price 110 USD now).
 *   buy cost (base)   = 10 * 100 USD * 4 = 4000 PLN
 *   cash              = 5000 - 4000      = 1000 PLN
 *   market value      = 10 * 110 USD * 4 = 4400 PLN
 *   net worth         = 4400 + 1000      = 5400 PLN
 *   total P/L         = 4400 - 4000      = 400 PLN  (unrealized)
 *   scanner price     = 110 USD * 4      = 440 PLN
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineRepositoryFxTest {

    private fun newRepo() = OfflineRepository(ApplicationProvider.getApplicationContext())

    private fun seedState(repo: OfflineRepository) = runBlocking {
        val state = JSONObject(
            """
            {
              "meta": { "baseCurrency": "PLN", "fxRates": { "USD/PLN": 4.0 } },
              "portfolios": [ { "id": "ptf-main", "name": "Glowny", "currency": "PLN" } ],
              "accounts": [ { "id": "acc-main", "name": "Konto", "currency": "PLN" } ],
              "assets": [
                { "id": "a1", "ticker": "AAPL", "name": "Apple", "currency": "USD", "currentPrice": 110.0, "risk": 5 }
              ],
              "operations": [
                { "id": "op1", "type": "Operacja gotowkowa", "portfolioId": "ptf-main", "accountId": "acc-main", "amount": 5000.0, "currency": "PLN" },
                { "id": "op2", "type": "Kupno waloru", "portfolioId": "ptf-main", "accountId": "acc-main", "assetId": "a1", "quantity": 10.0, "price": 100.0, "fee": 0.0, "currency": "USD" }
              ],
              "liabilities": [], "recurringOps": [], "alerts": []
            }
            """.trimIndent()
        )
        repo.dispatch("PUT", "/state", Parameters.Empty, JSONObject().put("state", state).toString())
    }

    @Test
    fun metrics_convertUsdHoldingToPlnBase() = runBlocking {
        val repo = newRepo()
        seedState(repo)

        val res = repo.dispatch("GET", "/metrics/portfolio", Parameters.Empty, "")
        assertEquals(200, res.status)
        val metrics = JSONObject(res.body).getJSONObject("metrics")

        assertEquals(4400.0, metrics.getDouble("marketValue"), 0.01)
        assertEquals(1000.0, metrics.getDouble("cashTotal"), 0.01)
        assertEquals(5400.0, metrics.getDouble("netWorth"), 0.01)
        assertEquals(400.0, metrics.getDouble("totalPL"), 0.01)

        // Negative control: 1100 is the pre-FX (raw native) market value — must NOT appear.
        assertNotEquals(1100.0, metrics.getDouble("marketValue"), 0.01)
    }

    @Test
    fun scanner_reportsPriceInBaseCurrency() = runBlocking {
        val repo = newRepo()
        seedState(repo)

        val res = repo.dispatch("POST", "/tools/scanner", Parameters.Empty, "{}")
        assertEquals(200, res.status)
        val items = JSONObject(res.body).getJSONArray("items")
        assertTrue("scanner should return the AAPL row", items.length() >= 1)

        val row = items.getJSONObject(0)
        assertEquals("AAPL", row.getString("ticker"))
        assertEquals(440.0, row.getDouble("price"), 0.01)
        assertEquals("PLN", row.getString("currency"))
    }

    @Test
    fun report_statistics_netWorthMatchesMetrics() = runBlocking {
        val repo = newRepo()
        seedState(repo)

        val res = repo.dispatch(
            "POST",
            "/reports/generate",
            Parameters.Empty,
            JSONObject().put("reportName", "Statystyki portfela").toString()
        )
        assertEquals(200, res.status)
        val rows = JSONObject(res.body).getJSONObject("report").getJSONArray("rows")
        // rows are [label, value] pairs; find "Wartosc netto" (net worth).
        var netWorth = Double.NaN
        for (i in 0 until rows.length()) {
            val pair = rows.getJSONArray(i)
            if (pair.getString(0).startsWith("Warto")) {
                if (pair.getString(0).contains("netto")) netWorth = pair.getDouble(1)
            }
        }
        assertEquals(5400.0, netWorth, 0.01)
    }
}
