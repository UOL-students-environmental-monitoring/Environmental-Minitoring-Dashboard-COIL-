@file:Suppress("WildcardImport", "NoWildcardImports", "ktlint:standard:no-wildcard-imports")

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
private const val DASHBOARD_CRITICAL_ALERTS_LIMIT = 20

fun Application.configureRouting() {
    routing {
        staticResources("/static", "static")
        get("/") { call.respondRedirect("/static/index.html") }
        ingestRoute()
        alertsRoute()
        dashboardCriticalAlertsRoute()
        readingsRoute()
        sitesRoute()
    }
}

/**
 * checks that the incoming sensor payload makes physical sense before we touch the database
 * returns a plain-English error message if looks wrong, null if everything is fine.
 */
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

/**
 * writes a validated reading and any alerts it triggered to the database
 * we work out which alert flags to set here that way insert block stays readable
 */
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
                it[alertGeofence] = if (evaluation.alerts.any { a -> a.parameter == "geofence" }) 1 else 0
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

/**
 * POST /api/ingest
 *
 * main entry point for sensor data, validates the payload, checks the site exists,
 * runs it through the alert engine, then saves the reading and any triggered alerts.
 * returns 201 with the derived status on success, or 400 if anything looks wrong.
 */
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

/**
 * GET /api/alerts
 *
 * returns the most recent 50 alerts across all sites. You can narrow things down with
 * optional query parameters:
 * - [site] - specific herd filter, e.g. `?site=herd_cattle_A`
 * - [severity] - severity level filter, e.g. `?severity=critical`
 */
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
                        timeStamp = row[AlertsLog.timeStamp].toString(),
                    )
                }
            }
        call.respond(alerts)
    }
}

/**
 * GET /api/dashboard/critical-alerts
 *
 * feeds the critical alerts panel on the dashboard. Only returns critical-severity alerts
 * so the panel stays focused on things that need immediate attention.
 * Optional filters:
 * - [site] - limit to one herd
 * - [from] / [to] - date-time range
 */
private fun Route.dashboardCriticalAlertsRoute() {
    get("/api/dashboard/critical-alerts") {
        val siteFilter = call.request.queryParameters["site"]
        val fromFilter = call.request.queryParameters["from"]
        val toFilter = call.request.queryParameters["to"]
        val alerts =
            transaction {
                var query = AlertsLog.selectAll().andWhere { AlertsLog.severity eq "critical" }
                if (siteFilter != null) query = query.andWhere { AlertsLog.siteId eq siteFilter }
                if (fromFilter != null) {
                    try {
                        val from = LocalDateTime.parse(fromFilter)
                        query = query.andWhere { AlertsLog.timeStamp greaterEq from }
                    } catch (_: DateTimeParseException) {
                    }
                }
                if (toFilter != null) {
                    try {
                        val to = LocalDateTime.parse(toFilter)
                        query = query.andWhere { AlertsLog.timeStamp lessEq to }
                    } catch (_: DateTimeParseException) {
                    }
                }
                query.orderBy(AlertsLog.timeStamp to SortOrder.DESC).limit(DASHBOARD_CRITICAL_ALERTS_LIMIT).map { row ->
                    DashboardAlertDTO(
                        id = row[AlertsLog.id],
                        siteId = row[AlertsLog.siteId],
                        parameter = row[AlertsLog.parameter],
                        severity = row[AlertsLog.severity],
                        message = row[AlertsLog.message],
                        timeStamp = row[AlertsLog.timeStamp].toString(),
                        source = "readings",
                    )
                }
            }
        call.respond(alerts)
    }
}

/**
 * GET /api/readings
 *
 * returns all sensor readings for a given site, ordered oldest-first so charts render correctly.
 * the [site] parameter is required - we return 400 if it's missing and 404 if the site doesn't exist.
 * you can also pass [from] and [to] as date-times to limit the time window.
 */
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
                        alertFlee = row[LivestockReadings.alertFlee] == 1,
                    )
                }
            }
        call.respond(readings)
    }
}

/**
 * GET /api/sites
 *
 * returns every registered site so frontend can populate its dropdown menu
 * each entry has an [id] and a human-readable [description]
 */
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
