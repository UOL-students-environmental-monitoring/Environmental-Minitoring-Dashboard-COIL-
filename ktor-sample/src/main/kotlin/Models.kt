package com.example

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

// defining the sites (dataset values: site_upstream, site_downstream, site_reservoir)
object Sites: Table() {
    val id = varchar("id",50)
    val description =  varchar("description", 255)

    // set the primary key to be id
    override val primaryKey = PrimaryKey(id)
}

// 15 minute intervals mapped from water_quality.csv
object WaterQualityReadings: Table(){
    val id = integer("id").autoIncrement()
    val timeStamp = datetime("timestamp")
    // composite key
    val siteId = reference("site_id",Sites.id)

    // sensor values
    val pH = double("ph")
    val turbidityNtu = double("turbidity_ntu")
    val conductivityPerCm = double("conductivity_Us_cm")
    val waterTempC = double("water_temp_c")
    val  waterLvlCm = double("water_level_cm")
    val lightLux = double("light_lux")
    // columns from Readme
    val status  = varchar("status",20)
    override val primaryKey = PrimaryKey(id)
}

// alert history model as required by mark scheme: 'what was triggered', when, severity
object AlertsLog: Table(){
    val id = integer("id").autoIncrement()
    val readingId = reference("reading_id", WaterQualityReadings.id)
    val siteId = varchar("site_id",50)
    val parameter = varchar("parameter",50)
    val severity = varchar("severity",20)
    val message = varchar("message", 255)
    val timeStamp = datetime("timestamp")
    override val primaryKey = PrimaryKey(id)
}

