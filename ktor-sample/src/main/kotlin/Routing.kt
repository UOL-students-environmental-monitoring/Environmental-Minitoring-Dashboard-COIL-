@file:Suppress("InvalidPackageDeclaration")

package com.example

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

private const val PH_MIN = 0.0
private const val PH_MAX = 14.0
private const val MIN_ACCEPTED_VALUE = 0.0
private const val MAX_SITE_ID_LENGTH = 255
private const val MAX_ALERT_RESULTS = 50

/** Registers HTTP routes for static content, ingest, and alert retrieval. */
fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        staticResources("/static", "static")

        route("/api") {
            post("/ingest") { handleIngest(call) }
            get("/alerts") { handleAlerts(call) }
        }
    }
}

@Suppress("ReturnCount")
private suspend fun handleIngest(call: ApplicationCall) {
    val payload =
        try {
            call.receive<WaterQualityPayload>()
        } catch (_: ContentTransformationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Payload is corrupt or there are missing fields"),
            )
            return
        }

    val parsedTimestamp =
        try {
            validatePayload(payload)
        } catch (exception: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(exception.message ?: "Payload is corrupt or there are missing fields"),
            )
            return
        }

    if (!siteExists(payload.siteId)) {
        call.respond(
            HttpStatusCode.NotFound,
            ErrorResponse("Unknown site ID: ${payload.siteId}"),
        )
        return
    }

    val evaluation = AlertEngine.evaluateWaterQuality(payload)
    persistReading(payload, parsedTimestamp, evaluation)

    call.respond(
        HttpStatusCode.Created,
        IngestResponse(message = "successfully saved", derivedState = evaluation.status),
    )
}

private suspend fun handleAlerts(call: ApplicationCall) {
    val siteFilter = call.request.queryParameters["site"]
    val severityFilter = call.request.queryParameters["severity"]?.lowercase()

    if (siteFilter != null && !siteExists(siteFilter)) {
        call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown site ID: $siteFilter"))
        return
    }

    call.respond(fetchAlerts(siteFilter, severityFilter))
}

private fun persistReading(
    payload: WaterQualityPayload,
    parsedTimestamp: LocalDateTime,
    evaluation: AlertEngine.EvaluationResult,
) {
    transaction {
        val insertedReadingId =
            WaterQualityReadings.insert {
                it[siteId] = payload.siteId
                it[timeStamp] = parsedTimestamp
                it[pH] = payload.pH
                it[turbidityNtu] = payload.turbidityNtu
                it[conductivityPerCm] = payload.conductivityPerCm
                it[waterTempC] = payload.waterTempC
                it[waterLvlCm] = payload.waterLvlCm
                it[lightLux] = payload.lightLux
                it[status] = evaluation.status
            } get WaterQualityReadings.id

        evaluation.alerts.forEach { alert ->
            AlertsLog.insert {
                it[readingId] = insertedReadingId
                it[siteId] = payload.siteId
                it[parameter] = alert.parameter
                it[severity] = alert.severity
                it[message] = alert.message
                it[timeStamp] = parsedTimestamp
            }
        }
    }
}

private fun fetchAlerts(
    siteFilter: String?,
    severityFilter: String?,
): List<AlertDTO> =
    transaction {
        filteredAlertsQuery(siteFilter, severityFilter)
            .orderBy(AlertsLog.timeStamp to SortOrder.DESC)
            .limit(MAX_ALERT_RESULTS)
            .map {
                AlertDTO(
                    id = it[AlertsLog.id],
                    siteId = it[AlertsLog.siteId],
                    parameter = it[AlertsLog.parameter],
                    severity = it[AlertsLog.severity],
                    message = it[AlertsLog.message],
                    timeStamp = it[AlertsLog.timeStamp].toString(),
                )
            }
    }

private fun filteredAlertsQuery(
    siteFilter: String?,
    severityFilter: String?,
): Query {
    var query: Query = AlertsLog.selectAll()

    if (siteFilter != null) {
        query = query.andWhere { AlertsLog.siteId eq siteFilter }
    }
    if (severityFilter != null) {
        query = query.andWhere { AlertsLog.severity eq severityFilter }
    }

    return query
}

private fun validatePayload(payload: WaterQualityPayload): LocalDateTime {
    require(payload.siteId.isNotBlank()) { "Site ID is required" }
    require(payload.siteId.length <= MAX_SITE_ID_LENGTH) { "Site ID is too long" }
    require(payload.pH in PH_MIN..PH_MAX) { "Outside of sensor range" }

    requireNonNegative("turbidity", payload.turbidityNtu)
    requireNonNegative("conductivity", payload.conductivityPerCm)
    requireNonNegative("water temperature", payload.waterTempC)
    requireNonNegative("water level", payload.waterLvlCm)
    requireNonNegative("light", payload.lightLux)

    return try {
        LocalDateTime.parse(payload.timeStamp)
    } catch (_: Exception) {
        throw IllegalArgumentException("Timestamp is malformed")
    }
}

private fun requireNonNegative(
    fieldName: String,
    value: Double,
) {
    require(value >= MIN_ACCEPTED_VALUE) { "$fieldName must be non-negative" }
}

private fun siteExists(siteId: String): Boolean =
    transaction {
        Sites
            .selectAll()
            .where { Sites.id eq siteId }
            .limit(1)
            .any()
    }
