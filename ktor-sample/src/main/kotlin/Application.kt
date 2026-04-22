@file:Suppress("InvalidPackageDeclaration")

package com.example

import io.ktor.server.application.Application

/** Starts the Ktor server using the configured engine. */
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

/** Registers application modules used by the environmental monitoring backend. */
fun Application.module() {
    configureDatabases()
    configureSerialization()
    configureTemplating()
    configureRouting()
}
