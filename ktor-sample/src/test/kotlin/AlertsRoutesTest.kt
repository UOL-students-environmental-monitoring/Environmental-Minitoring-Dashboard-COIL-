@file:Suppress("InvalidPackageDeclaration")

package com.example

import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlertsRoutesTest {
    @Test
    fun `get alerts returns empty list when there are no alerts`() =
        testApplication {
            application {
                module()
            }
            startApplication()
            clearStoredReadings()

            val response = client.get("/api/alerts")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("[]", response.bodyAsText())
        }

    @Test
    fun `get alerts filters by site and severity`() =
        testApplication {
            application {
                module()
            }
            startApplication()
            clearStoredReadings()

            client.post("/api/ingest") {
                accept(ContentType.Application.Json)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(validPayloadJson(siteId = "site_upstream", pH = 8.6))
            }
            client.post("/api/ingest") {
                accept(ContentType.Application.Json)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(validPayloadJson(siteId = "site_downstream", pH = 9.5))
            }

            val response = client.get("/api/alerts?site=site_upstream&severity=warning")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("\"siteId\":\"site_upstream\""))
            assertTrue(body.contains("\"severity\":\"warning\""))
            assertFalse(body.contains("\"siteId\":\"site_downstream\""))
        }

    @Test
    fun `get alerts returns not found for unknown site filter`() =
        testApplication {
            application {
                module()
            }
            startApplication()
            clearStoredReadings()

            val response = client.get("/api/alerts?site=site_unknown")

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertTrue(response.bodyAsText().contains("Unknown site ID"))
        }

    private fun clearStoredReadings() {
        transaction {
            AlertsLog.deleteAll()
            WaterQualityReadings.deleteAll()
        }
    }

    private fun validPayloadJson(
        siteId: String = "site_upstream",
        timeStamp: String = "2026-04-22T10:15:30",
        pH: Double = 7.2,
        turbidityNtu: Double = 1.0,
        conductivityPerCm: Double = 250.0,
        waterTempC: Double = 12.0,
        waterLvlCm: Double = 30.0,
        lightLux: Double = 200.0,
    ): String =
        """
        {
          "siteId": "$siteId",
          "timeStamp": "$timeStamp",
          "pH": $pH,
          "turbidityNtu": $turbidityNtu,
          "conductivityPerCm": $conductivityPerCm,
          "waterTempC": $waterTempC,
          "waterLvlCm": $waterLvlCm,
          "lightLux": $lightLux
        }
        """.trimIndent()
}
