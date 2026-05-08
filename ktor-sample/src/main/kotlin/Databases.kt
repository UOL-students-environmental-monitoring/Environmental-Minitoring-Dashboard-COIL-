@file:Suppress("WildcardImport", "NoWildcardImports", "ktlint:standard:no-wildcard-imports")

package com.example

import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** boiler plate */
fun Application.configureDatabases() {
    val embedded =
        environment.config
            .propertyOrNull("postgres.embedded")
            ?.getString()
            ?.toBoolean() ?: true
    connectToPostgres(embedded)

    Database.connect(
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver",
        user = "root",
        password = "",
    )

    /** creation of the tables */
    transaction {
        /** create the tables if they are non-existent */
        SchemaUtils.create(Sites, LivestockReadings, AlertsLog)

        /**
         * pre-fill the database with the known monitoring sites/herds
         * these match the site_id values found in livestock_tracking.csv
         */
        if (Sites.selectAll().empty()) {
            Sites.insert {
                it[id] = "herd_cattle_A"
                it[description] = "Cattle herd in the eastern grazing pasture"
            }
            Sites.insert {
                it[id] = "herd_goat_B"
                it[description] = "Goat herd in the northern hillside enclosure"
            }

            /** seed sensor readings from bundled CSV on first run */
            if (LivestockReadings.selectAll().empty()) {
                seedTableFromCsv()
            }
        }
    }
}

private const val CSV_MIN_COLUMNS = 11
private const val CSV_IDX_LONGITUDE = 3
private const val CSV_IDX_ACCEL = 4
private const val CSV_IDX_TEMP = 5

/**
 * reads the CSV file that's bundled inside our project's resources folder
 * and inserts each row into the LivestockReadings database table.
 */
private fun seedTableFromCsv() {
    /** a formatter tells Kotlin how to read a date/time string. */
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val stream = object {}.javaClass.getResourceAsStream("/livestock_tracking.csv") ?: return
    /** bufferedReader() wraps the file stream so we can read it line by line efficiently. */
    stream.bufferedReader().useLines { lines ->
        transaction {
            /** .drop(1) skips the first line, being the csv header row */
            lines.drop(1).forEach { line ->
                /** split by commas */
                val p = line.split(",")
                /**
                 * a valid row must have at least 11 columns
                 * if not valid then return@forEach skips that iteration
                 */
                if (p.size < CSV_MIN_COLUMNS) return@forEach
                /** encapsulated in a try catch so a invalid row doesn't crash whole process */
                try {
                    val parsedTime = LocalDateTime.parse(p[0].trim(), formatter)
                    val rowSiteId = p[1].trim()
                    val lat = p[2].trim().toDouble()
                    val lon = p[CSV_IDX_LONGITUDE].trim().toDouble()
                    val accel = p[CSV_IDX_ACCEL].trim().toDouble()
                    val temp = p[CSV_IDX_TEMP].trim().toDouble()

                    val payload =
                        LivestockPayload(
                            siteId = rowSiteId,
                            timeStamp = parsedTime.toString(),
                            latitude = lat,
                            longitude = lon,
                            accelMagG = accel,
                            ambientTemperatureC = temp,
                        )
                    val evaluation = AlertEngine.evaluateLivestock(payload)
                    val hasLowActivity =
                        evaluation.alerts.any { a ->
                            a.parameter == "low_activity" || a.parameter == "heat_collapse"
                        }

                    val insertedId =
                        LivestockReadings.insert {
                            it[timeStamp] = parsedTime
                            it[siteId] = rowSiteId
                            it[latitude] = lat
                            it[longitude] = lon
                            it[accelMagG] = accel
                            it[ambientTemperatureC] = temp
                            it[status] = evaluation.status
                            it[alertTriggered] = if (evaluation.alerts.isNotEmpty()) 1 else 0
                            it[alertLowActivity] = if (hasLowActivity) 1 else 0
                            it[alertGeofence] = if (evaluation.alerts.any { a -> a.parameter == "geofence" }) 1 else 0
                            it[alertFlee] = if (evaluation.alerts.any { a -> a.parameter == "flee" }) 1 else 0
                        } get LivestockReadings.id

                    evaluation.alerts.forEach { alert ->
                        AlertsLog.insert {
                            it[readingId] = insertedId
                            it[siteId] = rowSiteId
                            it[parameter] = alert.parameter
                            it[severity] = alert.severity
                            it[message] = alert.message
                            it[timeStamp] = parsedTime
                        }
                    }
                } catch (e: Exception) {
                    println("Skipping invalid CSV row: ${e.message}")
                }
            }
        }
    }
}

fun Application.connectToPostgres(embedded: Boolean): Connection {
    Class.forName("org.postgresql.Driver")
    if (embedded) {
        println("Using embedded H2 database for testing; replace this flag to use postgres")
        return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "root", "")
    } else {
        val url = environment.config.property("postgres.url").getString()
        println("Connecting to postgres database at $url")
        val user = environment.config.property("postgres.user").getString()
        val password = environment.config.property("postgres.password").getString()

        return DriverManager.getConnection(url, user, password)
    }
}