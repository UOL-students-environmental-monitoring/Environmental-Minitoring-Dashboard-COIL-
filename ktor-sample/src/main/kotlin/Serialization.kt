@file:Suppress("InvalidPackageDeclaration")

package com.example

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

/** Enables JSON serialization for API requests and responses. */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}
