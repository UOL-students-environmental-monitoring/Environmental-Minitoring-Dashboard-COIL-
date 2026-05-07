@file:Suppress("WildcardImport", "NoWildcardImports")

package com.example

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

private const val MIN_LATITUDE = -90.0
private const val MAX_LATITUDE = 90.0
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0
private const val MIN_AMBIENT_TEMP_C = -50.0
private const val MAX_AMBIENT_TEMP_C = 60.0
private const val ALERTS_PAGE_SIZE = 50

fun Application.configureRouting() {
    routing {
        staticResources("/static", "static")
        get("/") { call.respondRedirect("/static/index.html") }
        ingestRoute()
        alertsRoute()
        readingsRoute()
        sitesRoute()
    }
}

// returns an error message string if validation fails, null if the payload is valid
private fun validatePayload(payload: LivestockPayload): String? {
    if (payload.siteId.isBlank()) return "siteId is blank. Cannot be blank"
    if (payload.latitude < MIN_LATITUDE || payload.latitude > MAX_LATITUDE) {
        return "latitude must be between -90.0 and 90.0"
    }
    if (payload.longitude < MIN_LONGITUDE || payload.longitude > MAX_LONGITUDE) {
        return "longitude must be between -180.0 and 180.0"
    }
    if (payload.accelMagG < 0.0) return "accelMagG is negative. Cannot be negative"
    if (payload.ambientTemperatureC < MIN_AMBIENT_TEMP_C || payload.ambientTemperatureC > MAX_AMBIENT_TEMP_C) {
        return "ambientTemperatureC must be between $MIN_AMBIENT_TEMP_C and $MAX_AMBIENT_TEMP_C"
    }
    return null
}

private fun saveReading(
    payload: LivestockPayload,
    parsedTime: LocalDateTime,
    evaluation: AlertEngine.EvaluationResult,
) {
    val hasLowActivity =
        evaluation.alerts.any { a ->
            a.parameter == "low_activity" || a.parameter == "heat_collapse"
        }
    transaction {
        val insertedReadingId =
            LivestockReadings.insert {
                it[siteId] = payload.siteId
                it[timeStamp] = parsedTime
                it[latitude] = payload.latitude
                it[longitude] = payload.longitude
                it[accelMagG] = payload.accelMagG
                it[ambientTemperatureC] = payload.ambientTemperatureC
                it[status] = evaluation.status
                it[alertTriggered] = if (evaluation.alerts.isNotEmpty()) 1 else 0
                it[alertLowActivity] = if (hasLowActivity) 1 else 0
                it[alertGeofence] = 0
                it[alertFlee] = if (evaluation.alerts.any { a -> a.parameter == "flee" }) 1 else 0
            } get LivestockReadings.id

        evaluation.alerts.forEach { alert ->
            AlertsLog.insert {
                it[readingId] = insertedReadingId
                it[siteId] = payload.siteId
                it[parameter] = alert.parameter
                it[severity] = alert.severity
                it[message] = alert.message
                it[timeStamp] = parsedTime
            }
        }
    }
}

private fun Route.ingestRoute() {
    post("/api/ingest") {
        try {
            val payload = call.receive<LivestockPayload>()
            val validationError = validatePayload(payload)
            if (validationError != null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to validationError))
                return@post
            }
            val siteExists =
                transaction {
                    Sites.selectAll().where { Sites.id eq payload.siteId }.count() > 0
                }
            if (!siteExists) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown siteId: ${payload.siteId}"))
                return@post
            }
            val parsedTime = LocalDateTime.parse(payload.timeStamp)
            val evaluation = AlertEngine.evaluateLivestock(payload)
            saveReading(payload, parsedTime, evaluation)
            call.respond(HttpStatusCode.Created, mapOf("status" to "saved", "derived_status" to evaluation.status))
        } catch (e: Exception) {
            println("POST /api/ingest failed: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid payload"))
        }
    }
}

// ---------------------------------------------
// GET /api/alerts
// returns the last 50 alerts.
// both query parameters are optional filters.
// ---------------------------------------------
private fun Route.alertsRoute() {
    get("/api/alerts") {
        val siteFilter = call.request.queryParameters["site"]
        val severityFilter = call.request.queryParameters["severity"]
        val alerts =
            transaction {
                var query = AlertsLog.selectAll()
                if (siteFilter != null) query = query.andWhere { AlertsLog.siteId eq siteFilter }
                if (severityFilter != null) query = query.andWhere { AlertsLog.severity eq severityFilter }
                query.orderBy(AlertsLog.timeStamp to SortOrder.DESC).limit(ALERTS_PAGE_SIZE).map { row ->
                    AlertDTO(
                        id = row[AlertsLog.id],
                        siteId = row[AlertsLog.siteId],
                        parameter = row[AlertsLog.parameter],
                        severity = row[AlertsLog.severity],
                        message = row[AlertsLog.message],
                        timeStamp = row[AlertsLog.timeStamp].toString()
                    )
                }
            }
        call.respond(alerts)
    }
}

// ----------------------------------------------
// GET /api/readings
// returns readings for a given site, optionally.
// filtered by a date-time range.
// ----------------------------------------------
private fun Route.readingsRoute() {
    get("/api/readings") {
        val siteParameter = call.request.queryParameters["site"]
        val fromParameter = call.request.queryParameters["from"]
        val toParameter = call.request.queryParameters["to"]

        if (siteParameter == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Required query parameter missing: site"))
            return@get
        }
        val siteExists =
            transaction {
                Sites.selectAll().where { Sites.id eq siteParameter }.count() > 0
            }
        if (!siteExists) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "No site found: $siteParameter"))
            return@get
        }
        val fromTime =
            try {
                if (fromParameter != null) LocalDateTime.parse(fromParameter) else null
            } catch (e: DateTimeParseException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid 'from' datetime: ${e.message}"))
                return@get
            }
        val toTime =
            try {
                if (toParameter != null) LocalDateTime.parse(toParameter) else null
            } catch (e: DateTimeParseException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid 'to' datetime: ${e.message}"))
                return@get
            }
        val readings =
            transaction {
                var query = LivestockReadings.selectAll().andWhere { LivestockReadings.siteId eq siteParameter }
                if (fromTime != null) query = query.andWhere { LivestockReadings.timeStamp greaterEq fromTime }
                if (toTime != null) query = query.andWhere { LivestockReadings.timeStamp lessEq toTime }
                query.orderBy(LivestockReadings.timeStamp to SortOrder.ASC).map { row ->
                    ReadingDTO(
                        id = row[LivestockReadings.id],
                        siteId = row[LivestockReadings.siteId],
                        timeStamp = row[LivestockReadings.timeStamp].toString(),
                        latitude = row[LivestockReadings.latitude],
                        longitude = row[LivestockReadings.longitude],
                        accelMagG = row[LivestockReadings.accelMagG],
                        ambientTemperatureC = row[LivestockReadings.ambientTemperatureC],
                        status = row[LivestockReadings.status],
                        alertTriggered = row[LivestockReadings.alertTriggered] == 1,
                        alertLowActivity = row[LivestockReadings.alertLowActivity] == 1,
                        alertGeofence = row[LivestockReadings.alertGeofence] == 1,
                        alertFlee = row[LivestockReadings.alertFlee] == 1
                    )
                }
            }
        call.respond(readings)
    }
}

// ----------------------------------------------
// GET /api/sites
// returns all registered monitoring sites/herds.
// ----------------------------------------------
private fun Route.sitesRoute() {
    get("/api/sites") {
        val sites =
            transaction {
                Sites.selectAll().map { row ->
                    mapOf("id" to row[Sites.id], "description" to row[Sites.description])
                }
            }
        call.respond(sites)
    }
}
