@file:Suppress("InvalidPackageDeclaration")

package com.example

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

private const val SITE_ID_LENGTH = 50
private const val DESCRIPTION_LENGTH = 255
private const val STATUS_LENGTH = 20
private const val PARAMETER_LENGTH = 50

/** Known monitoring sites used by the water-quality system. */
object Sites : Table() {
    val id = varchar("id", SITE_ID_LENGTH)
    val description = varchar("description", DESCRIPTION_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

/** Water-quality readings captured from the monitored sites. */
object WaterQualityReadings : Table() {
    val id = integer("id").autoIncrement()
    val timeStamp = datetime("timestamp")
    val siteId = reference("site_id", Sites.id)

    val pH = double("ph")
    val turbidityNtu = double("turbidity_ntu")
    val conductivityPerCm = double("conductivity_Us_cm")
    val waterTempC = double("water_temp_c")
    val waterLvlCm = double("water_level_cm")
    val lightLux = double("light_lux")
    val status = varchar("status", STATUS_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

/** Persisted alert history derived from the readings table. */
object AlertsLog : Table() {
    val id = integer("id").autoIncrement()
    val readingId = reference("reading_id", WaterQualityReadings.id)
    val siteId = varchar("site_id", SITE_ID_LENGTH)
    val parameter = varchar("parameter", PARAMETER_LENGTH)
    val severity = varchar("severity", STATUS_LENGTH)
    val message = varchar("message", DESCRIPTION_LENGTH)
    val timeStamp = datetime("timestamp")

    override val primaryKey = PrimaryKey(id)
}
