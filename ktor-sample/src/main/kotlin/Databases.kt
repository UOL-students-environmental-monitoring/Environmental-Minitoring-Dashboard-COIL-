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
