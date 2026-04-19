package com.example

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.Thymeleaf
import io.ktor.server.thymeleaf.ThymeleafContent
import java.sql.Connection
import java.sql.DriverManager
import org.jetbrains.exposed.sql.*
import org.thymeleaf.templateparser.markup.HTMLTemplateParser
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        // Static plugin. Try to access `/static/index.html`
        staticResources("/static", "static")

        // POST endpoint to send new data to server
        // wrap the entire process in catch block for error handling and no unexpected crashes
        post("/api/ingest") {
            try {
                // look at incoming JSON and transform it into WaterQualityPayload class
                val payload = call.receive<WaterQualityPayload>()

                // mark scheme requirement: Validate incoming data

                // pH must be between 0-14, turbidity must be positive
                if (payload.pH < 0.0 || payload.turbidityNtu < 0.0 || payload.pH > 14.0){
                    // return an error message
                    call.respond(HttpStatusCode.BadRequest, "Outside of sensor range")
                    return@post
                }

                // evaluating the data

                // pass the validated time to AlertEngine
                val evaluation = AlertEngine.evaluateWaterQuality(payload)
                // converts the string from the dataset into Java LocalDatetime object
                val parseTime = LocalDateTime.parse(payload.timeStamp)

                transaction {
                    // creation of a new row in WaterQualityReadings table
                    // for every reading store the following environmental parameters
                    val insertedReadingId = WaterQualityReadings.insert {
                        it[siteId] = payload.siteId
                        it[timeStamp] = parseTime
                        it[pH] = payload.pH
                        it[turbidityNtu] = payload.turbidityNtu
                        it[conductivityPerCm] = payload.conductivityPerCm
                        it[waterTempC] = payload.waterTempC
                        it[waterLvlCm] = payload.waterLvlCm
                        it[lightLux] = payload.lightLux
                        it[status] = evaluation.status
                    } get WaterQualityReadings.id       // after creation of new row assign row id in insertedReadingId

                    // AlertEngine returns a list of alerts which we loop through every alert
                    evaluation.alerts.forEach { alert ->
                        // for every triggered alert we insert a new row into AlertsLog table
                        AlertsLog.insert {
                            // linking back to the specific sensor that caused the alert
                            it[readingId] = insertedReadingId
                            it[siteId] = payload.siteId
                            it[parameter] = alert.parameter
                            it[severity] = alert.severity
                            it[message] = alert.message
                            it[timeStamp] = parseTime
                        }
                    }
                }
            // send 201 HTTP status code if success, if call.receive() failed send error message and code rather than crarshing
                call.respond(HttpStatusCode.Created,mapOf("waiting status..." to "successfully saved","derived_state" to evaluation.status))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest,"Payload is corrupt or there are missing fields")
            }

            // GET endpoint

            // retrieving and sending alerts from backend to frontend
            get("/api/alerts") {
                val activeAlerts = transaction {
                    AlertsLog.selectAll()
                        .orderBy(AlertsLog.timeStamp to SortOrder.DESC)
                        .limit(50)
                        .map {
                            AlertDTO(
                                id = it[AlertsLog.id],
                                siteId = it[AlertsLog.siteId],
                                parameter = it[AlertsLog.parameter],
                                severity = it[AlertsLog.severity],
                                message = it[AlertsLog.message],
                                timeStamp = it[AlertsLog.timeStamp].toString())
                        }
                }
                call.respond(activeAlerts)
            }
        }
    }
}
