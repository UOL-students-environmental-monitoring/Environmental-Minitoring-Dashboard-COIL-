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

        // serve frontend html pages in static
        staticResources("/static", "static")

        // root will show the dashboard
        get("/") {
            call.respondRedirect("/static/index.html")
        }

        // -------------------------------------------------------------------
        // POST /api/ingest
        // looks out for a JSON structure for LivestockPayload and accepts it
        // runs the alert engine once validated and saves it
        // readings or any triggered alerts get sent to the db
        // -------------------------------------------------------------------
        post("/api/ingest") {
            // try/catch means if ANYTHING goes wrong we return a clean error message instead of crashing the server
            try {
                // call.receive() reads the JSON body and converts it into a kotlin object
                val payload = call.receive<LivestockPayload>()

                // server side validation
                // all validation occurs in backend

                // if siteId is empty
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
                    // eq cannot be replaced for '==', it compares database column definitions
                    Sites.selectAll().where { Sites.id eq payload.siteId }.count() > 0
                }
                if ( !siteExists ) {
                    call.respond(HttpStatusCode.BadRequest,mapOf("error" to "Unknown siteId: ${payload.siteId}"))
                    return@post
                }

                // Parsing timeStamp
                // LocalDateTime.parse() throws if the string is not a valid ISO datetime catch that below
                val parsedTime = LocalDateTime.parse(payload.timeStamp)

                // Running the Alert Engine
                // returns an EvaluationResult with an overall status string and a list of individual AlertTriggers
                val evaluation = AlertEngine.evaluateLivestock(payload)

                // Database transaction structure
                // transaction { } is Exposed's way of grouping DB operations
                // if one fails, the whole block is reverted so we can never have partially saved readings
                transaction {
                    // insert the sensor reading row
                    val insertedReadingId = LivestockReadings.insert {
                        it[siteId]              = payload.siteId
                        it[timeStamp]           = parsedTime
                        it[latitude]            = payload.latitude
                        it[longitude]           = payload.longitude
                        it[accelMagG]           = payload.accelMagG
                        it[ambientTemperatureC] = payload.ambientTemperatureC
                        it[status]              = evaluation.status
                        // convert the alert flags from the evaluation into 0/1 integers boolean for the DB
                        // if any alert was triggered, alertTriggered = 1
                        // using lambda expressions
                        it[alertTriggered]  = if ( evaluation.alerts.isNotEmpty() ) 1 else 0
                        // 1 if there is a low_activity or heat_collapse alert in the list, otherwise 0
                        it[alertLowActivity] = if ( evaluation.alerts.any { a -> a.parameter == "low_activity" || a.parameter == "heat_collapse" } ) 1 else 0
                        // 1 if there is a geofence alert, not evaluated here yet, always 0 for now
                        it[alertGeofence] = 0
                        // 1 if there is a flee alert in the list, otherwise 0
                        it[alertFlee] = if ( evaluation.alerts.any { a -> a.parameter == "flee" } ) 1 else 0

                    } get LivestockReadings.id // get back the auto-generated integer ID for the AlertsLog foreign key

                    // for each alert the engine raised, write a row to AlertsLog
                    // forEach is like a for-loop but written as a lambda
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

                // 201 Created = standard HTTP response for "new resource saved"
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
            // call.request.queryParameters["site"] returns null if not provided
            val siteFilter = call.request.queryParameters["site"]
            val severityFilter = call.request.queryParameters["severity"]

            val alerts = transaction {
                // start with all rows, then progressively narrow down
                // using Exposed's query
                var query = AlertsLog.selectAll()

                // .andWhere adds an extra SQL WHERE condition only if the client wanted that filter
                if ( siteFilter != null ) {
                    query = query.andWhere { AlertsLog.siteId eq siteFilter }
                }
                if ( severityFilter != null ) {
                    query = query.andWhere { AlertsLog.severity eq severityFilter }
                }

                // preparing database data from backend to move to frontend
                // orders alerts from newest (top) to oldest (bottom)
                // chooses the first 50
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

            // returns [] (empty JSON array) if no alerts match - not an error
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

            // site is required for this endpoint
            if ( siteParameter == null ) {
                call.respond(HttpStatusCode.BadRequest,mapOf("error" to "Required query parameter missing: site"))
                return@get
            }

            // check the site exists before querying readings
            val siteExists = transaction {
                Sites.selectAll().where { Sites.id eq siteParameter }.count() > 0
            }
            if (!siteExists) {
                call.respond(HttpStatusCode.NotFound,mapOf("error" to "No site found: $siteParameter"))
                return@get
            }

            // Parse optional date range where if the format is wrong, return a clear error
            // if fromTime has an invalid format
            val fromTime = try {
                if ( fromParameter != null ) {
                    LocalDateTime.parse(fromParameter)
                } else null
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest,mapOf("error" to "Could not parse 'from' datetime format"))
                return@get
            }
            // if toTime has an invalid format
            val toTime = try {
                if ( toParameter != null ) {
                    LocalDateTime.parse(toParameter)
                } else null
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest,mapOf("error" to "Could not parse 'to' datetime format"))
                return@get
            }

            val readings = transaction {
                // grabs all the readings for the specified site
                var query = LivestockReadings.selectAll().andWhere { LivestockReadings.siteId eq siteParameter }
                // grabs all readings after or equal to specific time
                if ( fromTime != null ) {
                    query = query.andWhere { LivestockReadings.timeStamp greaterEq fromTime }
                }
                // grabs all readings before or equal to specific time
                if ( toTime != null ) {
                    query = query.andWhere { LivestockReadings.timeStamp lessEq toTime }
                }

                // mapping to ReadingDTO, kotlinx.serialization knows how to handle this
                // @Serializable annotation on the data class enables this kotlinx link
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
                        // convert the stored 0/1 integer back into a true/false boolean for the frontend
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
