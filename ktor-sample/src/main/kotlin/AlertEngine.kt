package com.example

/**
 * this file will handle the alert logic giving us the exact conditions for alerts
 * based on livestock sensor data: GPS location, accelerometer movement, and temperature
 */

object AlertEngine {
    private const val CRITICAL_TEMP_C = 35.0
    private const val WARNING_TEMP_C = 30.0
    private const val CRITICAL_ACCEL_G = 0.3
    private const val WARNING_ACCEL_G = 0.6
    private const val CRITICAL_FLEE_G = 4.0
    private const val WARNING_FLEE_G = 3.0

    /**
     * stores centre point and critical radius for a site's permitted area
     * criticalRadius is in degrees - the animal must cross this distance from the centre
     * to trigger an alert. the threshold is set high so only clear breaches are flagged.
     */
    private data class GeofenceBoundary(
        val centerLat: Double,
        val centerLon: Double,
        val criticalRadius: Double,
    )

    /**
     * one entry per registered site
     * each herd have different permitted radius
     */
    private val GEOFENCE_ZONES =
        mapOf(
            "herd_cattle_A" to
                GeofenceBoundary(
                    centerLat = -32.780,
                    centerLon = 26.840,
                    criticalRadius = 0.012,
                ),
            "herd_goat_B" to
                GeofenceBoundary(
                    centerLat = -32.780,
                    centerLon = 26.840,
                    criticalRadius = 0.015,
                ),
        )

    /**
     * called by Databases.kt when seeding the CSV so the threshold logic lives in
     * one place and returns "critical" if the animal is outside the permitted area
     * null if it is still inside - no alert needed
     */
    fun geofenceSeverity(
        siteId: String,
        lat: Double,
        lon: Double,
    ): String? {
        val zone = GEOFENCE_ZONES[siteId] ?: return null
        val latDiff = lat - zone.centerLat
        val lonDiff = lon - zone.centerLon
        val dist = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff)
        return if (dist >= zone.criticalRadius) "critical" else null
    }

    /** list of alerts triggered and specific alerts triggered */
    data class EvaluationResult(
        val status: String,
        val alerts: List<AlertTrigger>,
    )

    data class AlertTrigger(
        val parameter: String,
        val severity: String,
        val message: String,
    )

    /** uses a function to accept data within livestock sensor */
    fun evaluateLivestock(data: LivestockPayload): EvaluationResult {
        /** add AlertTrigger to triggers, if conditions are met */
        val alerts = mutableListOf<AlertTrigger>()
        checkTemperature(data, alerts)
        checkActivity(data, alerts)
        checkFlee(data, alerts)
        checkHeatCollapse(data, alerts)
        checkGeofence(data, alerts)

        /** initially status is normal, until conditions met to change it */
        val status =
            when {
                alerts.any { it.severity == "critical" } -> "critical"
                alerts.isNotEmpty() -> "warning"
                else -> "normal"
            }
        return EvaluationResult(status, alerts)
    }

    private fun checkTemperature(
        data: LivestockPayload,
        alerts: MutableList<AlertTrigger>,
    ) {
        /**
         * Temperature Rules
         * temperature above 30°C is dangerous, animals sensitive to heat
         */
        if (data.ambientTemperatureC > CRITICAL_TEMP_C) {
            alerts.add(
                AlertTrigger(
                    "temperature",
                    "critical",
                    "Ambient temperature above ${CRITICAL_TEMP_C}°C - critical heat stress risk to livestock",
                ),
            )
        } else if (data.ambientTemperatureC > WARNING_TEMP_C) {
            alerts.add(
                AlertTrigger(
                    "temperature",
                    "warning",
                    "Ambient temperature above ${WARNING_TEMP_C}°C - monitor for signs of heat stress",
                ),
            )
        }
    }

    /**
     * Low Activity Rules
     * accelMagG measures movement in g-force
     * stationary animal is around 1.0g
     * below 1.0 during the day, the animal may be unhealthy
     * values less than 0.5 g infer the animal is almost completely motionless
     */
    private fun checkActivity(
        data: LivestockPayload,
        alerts: MutableList<AlertTrigger>,
    ) {
        if (data.accelMagG < CRITICAL_ACCEL_G) {
            alerts.add(
                AlertTrigger(
                    "low_activity",
                    "critical",
                    "Accelerometer below ${CRITICAL_ACCEL_G}g - animal may be collapsed or severely ill",
                ),
            )
        } else if (data.accelMagG < WARNING_ACCEL_G) {
            alerts.add(
                AlertTrigger(
                    "low_activity",
                    "warning",
                    "Accelerometer below ${WARNING_ACCEL_G}g - reduced movement, monitor for illness",
                ),
            )
        }
    }

    /**
     * Flee / Rustling Event Rules
     * random spike in accelerometer infer the animal is running at speed
     * above 3.0g indicate a flee event, possible livestock theft (rustling)
     */
    private fun checkFlee(
        data: LivestockPayload,
        alerts: MutableList<AlertTrigger>,
    ) {
        if (data.accelMagG > CRITICAL_FLEE_G) {
            alerts.add(
                AlertTrigger(
                    "flee",
                    "critical",
                    "Accelerometer above ${CRITICAL_FLEE_G}g - flee event possible, check for rustling or predator",
                ),
            )
        } else if (data.accelMagG > WARNING_FLEE_G) {
            alerts.add(
                AlertTrigger(
                    "flee",
                    "warning",
                    "Accelerometer above ${WARNING_FLEE_G}g - unusual movement detected, monitor closely",
                ),
            )
        }
    }

    /**
     * Geofence Rules
     * uses a straight-line (Euclidean) distance in degrees from the permitted zone centre
     * only clear breaches are reported - critical alerts
     */
    private fun checkGeofence(
        data: LivestockPayload,
        alerts: MutableList<AlertTrigger>,
    ) {
        /**
         * if the site doesn't have a defined zone, skip the check entirely
         * since we defined site zones
         */
        val zone = GEOFENCE_ZONES[data.siteId] ?: return

        val latDiff = data.latitude - zone.centerLat
        val lonDiff = data.longitude - zone.centerLon
        val dist = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff)

        if (dist >= zone.criticalRadius) {
            alerts.add(
                AlertTrigger(
                    "geofence",
                    "critical",
                    "Animal has left the permitted area - investigate immediately",
                ),
            )
        }
    }

    /**
     * Combined Condition - heat stress and low activity together
     * an animal that is both very hot and barely moving is a medical emergency
     */
    private fun checkHeatCollapse(
        data: LivestockPayload,
        alerts: MutableList<AlertTrigger>,
    ) {
        if (data.ambientTemperatureC > WARNING_TEMP_C && data.accelMagG < WARNING_ACCEL_G) {
            alerts.add(
                AlertTrigger(
                    "heat_collapse",
                    "critical",
                    "High temperature with low activity - animal may be suffering heat stroke",
                ),
            )
        }
    }
}
