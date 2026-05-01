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
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// boiler plate
fun Application.configureDatabases() {
    val embedded = environment.config.propertyOrNull("postgres.embedded")?.getString()?.toBoolean() ?: true
    connectToPostgres(embedded)

    Database.connect(
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver",
        user = "root",
        password = ""
    )

    // creation of the tables
    transaction {
        // create the tables if they are non-existent
        SchemaUtils.create(Sites, LivestockReadings, AlertsLog)

        // pre-fill the database with the known monitoring sites/herds
        // these match the site_id values found in livestock_tracking.csv
        if (Sites.selectAll().empty()) {
            Sites.insert { it[id] = "herd_cattle_A"; it[description] = "Cattle herd in the eastern grazing pasture" }
            Sites.insert { it[id] = "herd_goat_B"; it[description] = "Goat herd in the northern hillside enclosure" }

            // seed sensor readings from bundled CSV on first run
            if (LivestockReadings.selectAll().empty()) {
                seedTableFromCsv()
            }
        }
    }
}

// reads the CSV file that's bundled inside our project's resources folder
// and inserts each row into the LivestockReadings database table.
    private fun seedTableFromCsv() {
    // a formatter tells Kotlin how to read a date/time string.
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val stream = object {}.javaClass.getResourceAsStream("/livestock_tracking.csv") ?: return
    // bufferedReader() wraps the file stream so we can read it line by line efficiently.
    stream.bufferedReader().useLines { lines ->
        transaction {
            // .drop(1) skips the first line, being the csv header row
            lines.drop(1).forEach { line ->
                // split by commas
                val p = line.split(",")
                // a valid row must have at least 11 columns
                // if not valid then return@forEach skips that iteration
                if (p.size < 11) return@forEach
                // encapsulated in a try catch so a invalid row doesn't crash whole process
                try {
                    // inserting values from row into tables
                    // parse converts the string and trim removes whitespace
                    LivestockReadings.insert {
                        it[timeStamp] = LocalDateTime.parse(p[0].trim(), formatter)
                        it[siteId] = p[1].trim()
                        it[latitude] = p[2].trim().toDouble()
                        it[longitude] = p[3].trim().toDouble()
                        it[accelMagG] = p[4].trim().toDouble()
                        it[ambientTemperatureC] = p[5].trim().toDouble()
                        it[status] = p[6].trim()
                        it[alertTriggered] = p[7].trim().toInt()
                        it[alertLowActivity] = p[8].trim().toInt()
                        it[alertGeofence] = p[9].trim().toInt()
                        it[alertFlee] = p[10].trim().toInt()
                    }
                } catch (e: Exception) {
                }
            }
        }
    }
}

fun Application.connectToPostgres(embedded: Boolean): Connection {
    Class.forName("org.postgresql.Driver")
    if (embedded) {
        log.info("Using embedded H2 database for testing; replace this flag to use postgres")
        return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "root", "")
    } else {
        val url = environment.config.property("postgres.url").getString()
        log.info("Connecting to postgres database at $url")
        val user = environment.config.property("postgres.user").getString()
        val password = environment.config.property("postgres.password").getString()

        return DriverManager.getConnection(url, user, password)
    }
}
