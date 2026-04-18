package com.example

// this file will handle all the incoming data from the json files gathered from the sensors
// outputs it to the frontend

import kotlinx.serialization.Serializable

@Serializable
data class LivestockPayload(
    val siteId: String,
    val timeStamp: String,
    val latitude: Double,
    val longitude: Double,
    // accelerometer magnitude in g-force — measures how much the animal is moving
    val accelMagG: Double,
    val ambientTemperatureC: Double
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

@Serializable
data class ReadingDTO(
    val id: Int,
    val siteId: String,
    val timeStamp: String,
    val latitude: Double,
    val longitude: Double,
    val accelMagG: Double,
    val ambientTemperatureC: Double,
    val status: String,

    // alert flags, 1/true if that alert was raised for this reading
    val alertTriggered: Boolean,
    val alertLowActivity: Boolean,
    val alertGeofence: Boolean,
    val alertFlee: Boolean
)