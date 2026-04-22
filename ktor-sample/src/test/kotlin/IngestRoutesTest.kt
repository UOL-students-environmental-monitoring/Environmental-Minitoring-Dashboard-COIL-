@file:Suppress("InvalidPackageDeclaration")

package com.example

import io.ktor.client.request.accept
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
import kotlin.test.assertTrue

class IngestRoutesTest {
    @Test
    fun `post ingest stores a valid reading`() =
        testApplication {
            application {
                module()
            }
            startApplication()
            clearStoredReadings()

            val response =
                client.post("/api/ingest") {
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(validPayloadJson())
                }

            assertEquals(HttpStatusCode.Created, response.status)
            assertTrue(response.bodyAsText().contains("\"derivedState\":\"normal\""))
        }

    @Test
    fun `post ingest rejects malformed timestamp`() =
        testApplication {
            application {
                module()
            }
            startApplication()
            clearStoredReadings()

            val response =
                client.post("/api/ingest") {
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(validPayloadJson(timeStamp = "not-a-timestamp"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Timestamp is malformed"))
        }

    @Test
    fun `post ingest rejects out of range ph`() =
        testApplication {
            application {
                module()
            }
            startApplication()
            clearStoredReadings()

            val response =
                client.post("/api/ingest") {
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(validPayloadJson(pH = 20.0))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("Outside of sensor range"))
        }

    @Test
    fun `post ingest returns not found for unknown site`() =
        testApplication {
            application {
                module()
            }
            startApplication()
            clearStoredReadings()

            val response =
                client.post("/api/ingest") {
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(validPayloadJson(siteId = "site_unknown"))
                }

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
