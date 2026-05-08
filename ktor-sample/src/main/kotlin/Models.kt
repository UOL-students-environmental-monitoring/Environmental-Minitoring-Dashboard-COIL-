package com.example

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

private const val SHORT_VARCHAR = 20
private const val MEDIUM_VARCHAR = 50
private const val LONG_VARCHAR = 255

/** defining the herds/sites (dataset values: herd_cattle_A, herd_goat_B) */
object Sites : Table() {
    val id = varchar("id", MEDIUM_VARCHAR)
    val description = varchar("description", LONG_VARCHAR)

    /** set the primary key to be id */
    override val primaryKey = PrimaryKey(id)
}

/** 15 minute intervals mapped from livestock_tracking.csv */
object LivestockReadings : Table() {
    val id = integer("id").autoIncrement()
    val timeStamp = datetime("timestamp")

    /** composite key - each reading belongs to a site/herd */
    val siteId = reference("site_id", Sites.id)

    /** GPS location of the animal at time of reading */
    val latitude = double("latitude")
    val longitude = double("longitude")

    /**
     * accelerometer magnitude - how much the animal is moving (in g-force)
     * low values during the day can indicate illness or heat stress
     */
    val accelMagG = double("accel_mag_g")

    /** ambient temperature around the animal at time of reading */
    val ambientTemperatureC = double("ambient_temperature_c")

    /** overall derived status: normal / warning / critical */
    val status = varchar("status", SHORT_VARCHAR)

    /**
     * individual alert flags stored as integers (0 = false, 1 = true)
     * alert_triggered: any alert was raised this reading
     */
    val alertTriggered = integer("alert_triggered")

    /** alert_low_activity: animal hasn't moved enough (possible illness) */
    val alertLowActivity = integer("alert_low_activity")

    /** alert_geofence: animal has left its designated area */
    val alertGeofence = integer("alert_geofence")

    /** alert_flee: accelerometer spiked, risk of animal may be fleeing */
    val alertFlee = integer("alert_flee")

    override val primaryKey = PrimaryKey(id)
}

/** alert history model as required by mark scheme: 'what was triggered', when, severity */
object AlertsLog : Table() {
    val id = integer("id").autoIncrement()
    val readingId = reference("reading_id", LivestockReadings.id)
    val siteId = varchar("site_id", MEDIUM_VARCHAR)
    val parameter = varchar("parameter", MEDIUM_VARCHAR)
    val severity = varchar("severity", SHORT_VARCHAR)
    val message = varchar("message", LONG_VARCHAR)
    val timeStamp = datetime("timestamp")
    override val primaryKey = PrimaryKey(id)
}
