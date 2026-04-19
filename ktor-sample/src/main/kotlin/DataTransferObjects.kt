package com.example

// this file will handle all the incoming data from the json files gathered from the sensors
// outputs it to the frontend

import kotlinx.serialization.Serializable

@Serializable
data class WaterQualityPayload(
    val siteId: String,
    val timeStamp: String,
    val pH: Double,
    val turbidityNtu: Double,
    val conductivityPerCm: Double,
    val waterTempC: Double,
    val waterLvlCm: Double,
    val lightLux: Double
)

@Serializable
data class AlertDTO(
    val id: Int,
    val siteId: String,
    val parameter: String,
    val severity: String,
    val message: String,
    val timeStamp: String
)