@file:Suppress("InvalidPackageDeclaration")

package com.example

import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.sql.DriverManager

/** Configures the database connection and seeds the known monitoring sites. */
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

    transaction {
        SchemaUtils.create(Sites, WaterQualityReadings, AlertsLog)

        if (Sites.selectAll().empty()) {
            Sites.insert {
                it[id] = "site_upstream"
                it[description] = "Reference point above agricultural land"
            }
            Sites.insert {
                it[id] = "site_downstream"
                it[description] = "Below farming activity"
            }
            Sites.insert {
                it[id] = "site_reservoir"
                it[description] = "Community dam"
            }
        }
    }
}

/** Opens the configured JDBC connection for local H2 or PostgreSQL environments. */
fun Application.connectToPostgres(embedded: Boolean): Connection {
    Class.forName("org.postgresql.Driver")
    if (embedded) {
        environment.log.info("Using embedded H2 database for testing; replace this flag to use postgres")
        return DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "root", "")
    } else {
        val url = environment.config.property("postgres.url").getString()
        environment.log.info("Connecting to postgres database at $url")
        val user = environment.config.property("postgres.user").getString()
        val password = environment.config.property("postgres.password").getString()

        return DriverManager.getConnection(url, user, password)
    }
}
