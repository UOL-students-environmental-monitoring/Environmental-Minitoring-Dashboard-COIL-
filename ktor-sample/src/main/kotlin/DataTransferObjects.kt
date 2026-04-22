@file:Suppress("InvalidPackageDeclaration")

package com.example

import kotlinx.serialization.Serializable

/** Incoming water quality reading submitted by a sensor client. */
@Serializable
data class WaterQualityPayload(
    val siteId: String,
    val timeStamp: String,
    val pH: Double,
    val turbidityNtu: Double,
    val conductivityPerCm: Double,
    val waterTempC: Double,
    val waterLvlCm: Double,
    val lightLux: Double,
)

/** Public alert payload returned to API consumers. */
@Serializable
data class AlertDTO(
    val id: Int,
    val siteId: String,
    val parameter: String,
    val severity: String,
    val message: String,
    val timeStamp: String,
)

/** Response body returned after a successful ingest request. */
@Serializable
data class IngestResponse(
    val message: String,
    val derivedState: String,
)

/** Error payload returned for invalid requests or server failures. */
@Serializable
data class ErrorResponse(
    val error: String,
)
