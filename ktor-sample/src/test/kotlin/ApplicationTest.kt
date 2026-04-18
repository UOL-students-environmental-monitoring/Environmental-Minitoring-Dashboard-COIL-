package com.example

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    // a normal, healthy livestock reading , no alerts should be raised
    private val validPayload = """
        {
            "siteId": "herd_cattle_A",
            "timeStamp": "2025-05-01T08:00:00",
            "latitude": -32.77,
            "longitude": 26.84,
            "accelMagG": 1.0,
            "ambientTemperatureC": 20.0
        }
    """.trimIndent()

    /**
     * Runs before every single test.
     * Clears the two tables that tests write to, so each test
     * starts with a clean slate. Sites are NOT cleared because
     * they are seeded once at startup and tests depend on them existing.
     */
    @BeforeTest
    fun clearTables() {
        /**
         * We need the app to have initialised the DB at least once before we can clear it.
         *  testApplication handles that, but @BeforeTest runs outside of it
         * , so we guard with a try/catch.
         **/
        try {
            transaction {
                AlertsLog.deleteAll()
                LivestockReadings.deleteAll()
            }
        } catch (e: Exception) {
            // Tables may not exist yet on the very first run , that is fine
        }
    }

    // Root

    @Test
    fun `GET root returns 200 or redirect`() = testApplication {
        application { module() }
        val response = client.get("/")
        assertTrue(
            response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Found,
            "Expected 200 or 302 but got ${response.status}"
        )
    }

    // POST /api/ingest , happy path

    @Test
    fun `POST ingest with valid payload returns 201`() = testApplication {
        application { module() }

        val response = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(validPayload)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("saved"))
    }

    @Test
    fun `POST ingest normal reading returns derived status normal`() = testApplication {
        application { module() }

        val response = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(validPayload)
        }

        assertTrue(response.bodyAsText().contains("normal"))
    }

    @Test
    fun `POST ingest critical temperature returns derived status critical`() = testApplication {
        application { module() }

        // 38°C is above the critical threshold of 35°C
        val criticalPayload = validPayload.replace("\"ambientTemperatureC\": 20.0", "\"ambientTemperatureC\": 38.0")

        val response = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(criticalPayload)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("critical"))
    }

    @Test
    fun `POST ingest warning temperature returns derived status warning`() = testApplication {
        application { module() }

        // 32°C is between the warning (30°C) and critical (35°C) thresholds
        val warningPayload = validPayload.replace("\"ambientTemperatureC\": 20.0", "\"ambientTemperatureC\": 32.0")

        val response = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(warningPayload)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("warning"))
    }

    @Test
    fun `POST ingest critical low activity returns derived status critical`() = testApplication {
        application { module() }

        // 0.1g is below the critical low-activity threshold of 0.3g
        val criticalPayload = validPayload.replace("\"accelMagG\": 1.0", "\"accelMagG\": 0.1")

        val response = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(criticalPayload)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("critical"))
    }

    @Test
    fun `POST ingest flee event returns derived status critical`() = testApplication {
        application { module() }

        // 5.0g is above the critical flee threshold of 4.0g
        val fleePayload = validPayload.replace("\"accelMagG\": 1.0", "\"accelMagG\": 5.0")

        val response = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(fleePayload)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("critical"))
    }

    // POST /api/ingest , validation failures

    @Test
    fun `POST ingest with negative accelMagG returns 400`() = testApplication {
        application { module() }

        val response = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(validPayload.replace("\"accelMagG\": 1.0", "\"accelMagG\": -1.0"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST ingest with latitude out of range returns 400`() = testApplication {
        application { module() }

        val response = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(validPayload.replace("\"latitude\": -32.77", "\"latitude\": -200.0"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST ingest with longitude out of range returns 400`() = testApplication {
        application { module() }

        val response = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(validPayload.replace("\"longitude\": 26.84", "\"longitude\": 999.0"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST ingest with temperature out of physical range returns 400`() = testApplication {
        application { module() }

        val response = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(validPayload.replace("\"ambientTemperatureC\": 20.0", "\"ambientTemperatureC\": 100.0"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST ingest with unknown siteId returns 400`() = testApplication {
        application { module() }

        val response = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(validPayload.replace("\"siteId\": \"herd_cattle_A\"", "\"siteId\": \"herd_ghost\""))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Unknown siteId"))
    }

    @Test
    fun `POST ingest with malformed JSON returns 400`() = testApplication {
        application { module() }

        val response = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody("this is not json at all {{{")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST ingest with missing fields returns 400`() = testApplication {
        application { module() }

        val response = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody("""{"siteId": "herd_cattle_A"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // GET /api/alerts

    @Test
    fun `GET alerts returns 200 and a JSON array`() = testApplication {
        application { module() }

        val response = client.get("/api/alerts")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.startsWith("["), "Expected JSON array, got: $body")
    }

    @Test
    fun `GET alerts after ingest with critical temperature contains an alert`() = testApplication {
        application { module() }

        client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(validPayload.replace("\"ambientTemperatureC\": 20.0", "\"ambientTemperatureC\": 38.0"))
        }

        val body = client.get("/api/alerts").bodyAsText()
        assertTrue(body.contains("critical"))
        assertTrue(body.contains("herd_cattle_A"))
    }

    @Test
    fun `GET alerts filtered by site returns only that site`() = testApplication {
        application { module() }

        // Ingest a critical reading for herd_cattle_A only
        client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(validPayload.replace("\"ambientTemperatureC\": 20.0", "\"ambientTemperatureC\": 38.0"))
        }

        // Filter to herd_goat_B , should return empty array
        val response = client.get("/api/alerts?site=herd_goat_B")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText().trim())
    }

    @Test
    fun `GET alerts filtered by severity=critical only shows critical`() = testApplication {
        application { module() }

        client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(validPayload.replace("\"ambientTemperatureC\": 20.0", "\"ambientTemperatureC\": 38.0"))
        }

        val body = client.get("/api/alerts?severity=critical").bodyAsText()
        assertTrue(body.contains("critical"))
    }

    @Test
    fun `GET alerts with no matching filter returns empty array`() = testApplication {
        application { module() }

        // Clear any alerts that other tests may have written to the shared DB
        transaction {
            AlertsLog.deleteAll()
        }

        val response = client.get("/api/alerts?severity=warning")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText().trim())
    }

    // GET /api/readings

    @Test
    fun `GET readings without site param returns 400`() = testApplication {
        application { module() }

        val response = client.get("/api/readings")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("site"))
    }

    @Test
    fun `GET readings for unknown site returns 404`() = testApplication {
        application { module() }

        val response = client.get("/api/readings?site=herd_ghost")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET readings for valid site returns 200 and array`() = testApplication {
        application { module() }

        transaction {
            AlertsLog.deleteAll()
            LivestockReadings.deleteAll()
        }

        val ingestResponse = client.post("/api/ingest") {
            contentType(ContentType.Application.Json)
            setBody(validPayload)
        }

        // Print what the ingest actually returned so we can see it in test output
        val ingestBody = ingestResponse.bodyAsText()
        println("INGEST STATUS: ${ingestResponse.status}")
        println("INGEST BODY: $ingestBody")

        val response = client.get("/api/readings?site=herd_cattle_A")
        val body = response.bodyAsText()

        println("READINGS STATUS: ${response.status}")
        println("READINGS BODY: $body")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.startsWith("["), "Expected JSON array but got: $body")
        assertTrue(body.contains("herd_cattle_A"), "Expected siteId in body but got: $body")
    }

    @Test
    fun `GET readings for valid site with no data returns empty array`() = testApplication {
        application { module() }

        // herd_goat_B exists but we cleared all readings in @BeforeTest
        val response = client.get("/api/readings?site=herd_goat_B")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText().trim())
    }

    @Test
    fun `GET readings with invalid from date returns 400`() = testApplication {
        application { module() }

        val response = client.get("/api/readings?site=herd_cattle_A&from=not-a-date")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("from"))
    }

    // GET /api/sites

    @Test
    fun `GET sites returns 200 and contains seeded sites`() = testApplication {
        application { module() }

        val response = client.get("/api/sites")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertTrue(body.contains("herd_cattle_A"))
        assertTrue(body.contains("herd_goat_B"))
    }

    // AlertEngine unit tests

    @Test
    fun `AlertEngine normal reading returns normal status and no alerts`() {
        val data = LivestockPayload(
            siteId = "herd_cattle_A", timeStamp = "2025-05-01T08:00:00",
            latitude = -32.77, longitude = 26.84,
            accelMagG = 1.0, ambientTemperatureC = 20.0
        )
        val result = AlertEngine.evaluateLivestock(data)
        assertEquals("normal", result.status)
        assertTrue(result.alerts.isEmpty())
    }

    @Test
    fun `AlertEngine temperature above 35 returns critical`() {
        val data = LivestockPayload(
            siteId = "herd_cattle_A", timeStamp = "2025-05-01T08:00:00",
            latitude = -32.77, longitude = 26.84,
            accelMagG = 1.0, ambientTemperatureC = 38.0
        )
        val result = AlertEngine.evaluateLivestock(data)
        assertEquals("critical", result.status)
        assertTrue(result.alerts.any { it.parameter == "temperature" && it.severity == "critical" })
    }

    @Test
    fun `AlertEngine temperature between 30 and 35 returns warning`() {
        val data = LivestockPayload(
            siteId = "herd_cattle_A", timeStamp = "2025-05-01T08:00:00",
            latitude = -32.77, longitude = 26.84,
            accelMagG = 1.0, ambientTemperatureC = 32.0
        )
        val result = AlertEngine.evaluateLivestock(data)
        assertEquals("warning", result.status)
        assertTrue(result.alerts.any { it.parameter == "temperature" && it.severity == "warning" })
    }

    @Test
    fun `AlertEngine accelMagG below 0_3 returns critical low activity`() {
        val data = LivestockPayload(
            siteId = "herd_cattle_A", timeStamp = "2025-05-01T08:00:00",
            latitude = -32.77, longitude = 26.84,
            accelMagG = 0.1, ambientTemperatureC = 20.0
        )
        val result = AlertEngine.evaluateLivestock(data)
        assertEquals("critical", result.status)
        assertTrue(result.alerts.any { it.parameter == "low_activity" && it.severity == "critical" })
    }

    @Test
    fun `AlertEngine accelMagG above 4 returns critical flee`() {
        val data = LivestockPayload(
            siteId = "herd_cattle_A", timeStamp = "2025-05-01T08:00:00",
            latitude = -32.77, longitude = 26.84,
            accelMagG = 5.0, ambientTemperatureC = 20.0
        )
        val result = AlertEngine.evaluateLivestock(data)
        assertEquals("critical", result.status)
        assertTrue(result.alerts.any { it.parameter == "flee" && it.severity == "critical" })
    }

    @Test
    fun `AlertEngine combined high temperature and low activity adds heat collapse alert`() {
        val data = LivestockPayload(
            siteId = "herd_cattle_A", timeStamp = "2025-05-01T08:00:00",
            latitude = -32.77, longitude = 26.84,
            accelMagG = 0.4, ambientTemperatureC = 33.0
        )
        val result = AlertEngine.evaluateLivestock(data)
        assertTrue(result.alerts.any { it.parameter == "heat_collapse" })
    }
}
