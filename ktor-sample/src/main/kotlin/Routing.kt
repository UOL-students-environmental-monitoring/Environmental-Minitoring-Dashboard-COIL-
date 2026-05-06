package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

fun Application.configureRouting() {
    routing {

        // Static dashboard and alerts pages live under src/main/resources/static.
        staticResources("/static", "static")

        get("/") {
            call.respondRedirect("/static/index.html")
        }

        get("/dashboard") {
            call.respondRedirect("/static/index.html")
        }

        get("/alerts") {
            call.respondRedirect("/static/alerts.html")
        }

        // -------------------------------------------------------------------
        // POST /api/ingest
        // looks out for a JSON structure for LivestockPayload and accepts it
        // runs the alert engine once validated and saves it
        // readings or any triggered alerts get sent to the db
        // -------------------------------------------------------------------
        post("/api/ingest") {
            // Keep bad client payloads as 400 responses instead of exposing parser or database errors.
            try {
                val payload = call.receive<LivestockPayload>()

                if ( payload.siteId.isBlank() ) {
                    call.respond(HttpStatusCode.BadRequest,mapOf("error" to "siteId is blank. Cannot be blank"))
                    return@post
                }

                // latitude must between -90 and 90 degrees for it to be a valid coordinate
                if ( payload.latitude < -90.0 || payload.latitude > 90.0 ) {
                    call.respond(HttpStatusCode.BadRequest,mapOf("error" to "latitude must be between -90.0 and 90.0"))
                    return@post
                }

                // longitude must be between -180 and 180 degrees for it to be a valid coordinate
                if ( payload.longitude < -180.0 || payload.longitude > 180.0 ) {
                    call.respond(HttpStatusCode.BadRequest,mapOf("error" to "longitude must be between -180.0 and 180.0"))
                    return@post
                }

                // accelerometer magnitude g-force is always positive, cannot be negative
                if ( payload.accelMagG < 0.0 ) {
                    call.respond(HttpStatusCode.BadRequest,mapOf("error" to "accelMagG is negative. Cannot be negative"))
                    return@post
                }

                // temperature must be within a physically plausible range in an external environment
                if ( payload.ambientTemperatureC < -50.0 || payload.ambientTemperatureC > 60.0 ) {
                    call.respond(HttpStatusCode.BadRequest,mapOf("error" to "ambientTemperatureC must be between -50.0 and 60.0"))
                    return@post
                }

                // check if site exists in our Sites table
                // this prevents phantom readings from unknown herd IDs
                val siteExists = transaction {
                    Sites.selectAll().where { Sites.id eq payload.siteId }.count() > 0
                }
                if ( !siteExists ) {
                    call.respond(HttpStatusCode.BadRequest,mapOf("error" to "Unknown siteId: ${payload.siteId}"))
                    return@post
                }

                // LocalDateTime.parse() keeps the API contract aligned with the frontend date filters.
                val parsedTime = LocalDateTime.parse(payload.timeStamp)

                val evaluation = AlertEngine.evaluateLivestock(payload)

                // Save the reading and alert rows together so the log cannot drift from the source reading.
                transaction {
                    val insertedReadingId = LivestockReadings.insert {
                        it[siteId]              = payload.siteId
                        it[timeStamp]           = parsedTime
                        it[latitude]            = payload.latitude
                        it[longitude]           = payload.longitude
                        it[accelMagG]           = payload.accelMagG
                        it[ambientTemperatureC] = payload.ambientTemperatureC
                        it[status]              = evaluation.status
                        it[alertTriggered]  = if ( evaluation.alerts.isNotEmpty() ) 1 else 0
                        it[alertLowActivity] = if ( evaluation.alerts.any { a -> a.parameter == "low_activity" || a.parameter == "heat_collapse" } ) 1 else 0
                        // 1 if there is a geofence alert, not evaluated here yet, always 0 for now
                        it[alertGeofence] = 0
                        it[alertFlee] = if ( evaluation.alerts.any { a -> a.parameter == "flee" } ) 1 else 0

                    } get LivestockReadings.id // get back the auto-generated integer ID for the AlertsLog foreign key

                    evaluation.alerts.forEach { alert ->
                        AlertsLog.insert {
                            it[readingId]  = insertedReadingId
                            it[siteId]     = payload.siteId
                            it[parameter]  = alert.parameter
                            it[severity]   = alert.severity
                            it[message]    = alert.message
                            it[timeStamp]  = parsedTime
                        }
                    }
                }

                call.respond(HttpStatusCode.Created,mapOf("status" to "saved","derived_status" to evaluation.status))

            } catch (e: Exception) {
                // catches: bad JSON shape, unparseable timestamp, DB errors, etc.
                call.respond(HttpStatusCode.BadRequest,mapOf("error" to "Invalid payload"))
            }
        }

        // ---------------------------------------------
        // GET /api/alerts
        // returns the last 50 alerts.
        // both query parameters are optional filters.
        // ---------------------------------------------
        get("/api/alerts") {
            val siteFilter = call.request.queryParameters["site"]
            val severityFilter = call.request.queryParameters["severity"]

            val alerts = transaction {
                var query = AlertsLog.selectAll()

                if ( siteFilter != null ) {
                    query = query.andWhere { AlertsLog.siteId eq siteFilter }
                }
                if ( severityFilter != null ) {
                    query = query.andWhere { AlertsLog.severity eq severityFilter }
                }

                // Keep the alerts page focused on the newest actionable items.
                query
                    .orderBy(AlertsLog.timeStamp to SortOrder.DESC)
                    .limit(50)
                    .map { row ->
                        AlertDTO(
                            id        = row[AlertsLog.id],
                            siteId    = row[AlertsLog.siteId],
                            parameter = row[AlertsLog.parameter],
                            severity  = row[AlertsLog.severity],
                            message   = row[AlertsLog.message],
                            timeStamp = row[AlertsLog.timeStamp].toString()
                        )
                    }
            }

            call.respond(alerts)
        }

        get("/api/dashboard/critical-alerts") {
            val siteFilter = call.request.queryParameters["site"]

            // The dashboard needs a tiny feed, not the whole readings table in the browser.
            val alerts = transaction {
                var query = LivestockReadings
                    .selectAll()
                    .where {
                        (LivestockReadings.status eq "critical") and
                            (LivestockReadings.alertTriggered eq 1)
                    }

                if ( siteFilter != null ) {
                    query = query.andWhere { LivestockReadings.siteId eq siteFilter }
                }

                query
                    .orderBy(LivestockReadings.timeStamp to SortOrder.DESC)
                    .limit(10)
                    .map { row ->
                        DashboardAlertDTO(
                            id = row[LivestockReadings.id],
                            siteId = row[LivestockReadings.siteId],
                            parameter = dashboardAlertParameter(row),
                            severity = row[LivestockReadings.status],
                            message = dashboardAlertMessage(row),
                            timeStamp = row[LivestockReadings.timeStamp].toString(),
                            source = "readings"
                        )
                    }
            }

            call.respond(alerts)
        }

        // ----------------------------------------------
        // GET /api/readings
        // returns readings for a given site, optionally.
        // filtered by a date-time range.
        // ----------------------------------------------
        get("/api/readings") {
            val siteParameter = call.request.queryParameters["site"]
            val fromParameter = call.request.queryParameters["from"]
            val toParameter = call.request.queryParameters["to"]

            if ( siteParameter == null ) {
                call.respond(HttpStatusCode.BadRequest,mapOf("error" to "Required query parameter missing: site"))
                return@get
            }

            val siteExists = transaction {
                Sites.selectAll().where { Sites.id eq siteParameter }.count() > 0
            }
            if ( !siteExists ) {
                call.respond(HttpStatusCode.NotFound,mapOf("error" to "No site found: $siteParameter"))
                return@get
            }

            val fromTime = try {
                if ( fromParameter != null ) {
                    LocalDateTime.parse(fromParameter)
                } else null
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest,mapOf("error" to "Could not parse 'from' datetime format"))
                return@get
            }

            val toTime = try {
                if ( toParameter != null ) {
                    LocalDateTime.parse(toParameter)
                } else null
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest,mapOf("error" to "Could not parse 'to' datetime format"))
                return@get
            }

            val readings = transaction {
                var query = LivestockReadings.selectAll().andWhere { LivestockReadings.siteId eq siteParameter }
                if ( fromTime != null ) {
                    query = query.andWhere { LivestockReadings.timeStamp greaterEq fromTime }
                }
                if ( toTime != null ) {
                    query = query.andWhere { LivestockReadings.timeStamp lessEq toTime }
                }

                query.orderBy(LivestockReadings.timeStamp to SortOrder.ASC).map { row ->
                    ReadingDTO(
                        id                   = row[LivestockReadings.id],
                        siteId               = row[LivestockReadings.siteId],
                        timeStamp            = row[LivestockReadings.timeStamp].toString(),
                        latitude             = row[LivestockReadings.latitude],
                        longitude            = row[LivestockReadings.longitude],
                        accelMagG            = row[LivestockReadings.accelMagG],
                        ambientTemperatureC  = row[LivestockReadings.ambientTemperatureC],
                        status               = row[LivestockReadings.status],
                        alertTriggered   = row[LivestockReadings.alertTriggered] == 1,
                        alertLowActivity = row[LivestockReadings.alertLowActivity] == 1,
                        alertGeofence    = row[LivestockReadings.alertGeofence] == 1,
                        alertFlee        = row[LivestockReadings.alertFlee] == 1
                    )
                }
            }

            call.respond(readings)
        }

        // ----------------------------------------------
        // GET /api/sites
        // returns all registered monitoring sites/herds.
        // ----------------------------------------------
        get("/api/sites") {
            val sites = transaction {
                Sites.selectAll().map { row ->
                    mapOf(
                        "id"          to row[Sites.id],
                        "description" to row[Sites.description]
                    )
                }
            }
            call.respond(sites)
        }
    }
}

private fun dashboardAlertParameter(row: ResultRow): String {
    return when {
        row[LivestockReadings.alertFlee] == 1 -> "flee"
        row[LivestockReadings.alertLowActivity] == 1 -> "low_activity"
        row[LivestockReadings.alertGeofence] == 1 -> "geofence"
        row[LivestockReadings.ambientTemperatureC] > 35.0 -> "temperature"
        else -> "critical"
    }
}

private fun dashboardAlertMessage(row: ResultRow): String {
    val parameter = dashboardAlertParameter(row)
    return when (parameter) {
        "flee" -> "Accelerometer ${row[LivestockReadings.accelMagG]} g - flee event detected."
        "low_activity" -> "Accelerometer ${row[LivestockReadings.accelMagG]} g - low activity, check the animal."
        "geofence" -> "Geofence breach detected - check location."
        "temperature" -> "Temperature ${row[LivestockReadings.ambientTemperatureC]} C - critical heat stress risk."
        else -> "Critical alert flag raised - check reading."
    }
}
