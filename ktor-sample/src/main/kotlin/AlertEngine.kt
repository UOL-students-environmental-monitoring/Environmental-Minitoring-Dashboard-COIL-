package com.example

// this file will handle the alert logic giving us the exact conditions for alerts
// based on livestock sensor data: GPS location, accelerometer movement, and temperature

object AlertEngine {
    private const val CRITICAL_TEMP_C = 35.0
    private const val WARNING_TEMP_C = 30.0
    private const val CRITICAL_ACCEL_G = 0.3
    private const val WARNING_ACCEL_G = 0.6
    private const val CRITICAL_FLEE_G = 4.0
    private const val WARNING_FLEE_G = 3.0

    // list of alerts triggered and specific alerts triggered
    data class EvaluationResult(
        val status: String,
        val alerts: List<AlertTrigger>
    )

    data class AlertTrigger(
        val parameter: String,
        val severity: String,
        val message: String
    )

    // uses a function to accept data within livestock sensor
    fun evaluateLivestock(data: LivestockPayload): EvaluationResult {
        // add AlertTrigger to triggers, if conditions are met
        val alerts = mutableListOf<AlertTrigger>()
        checkTemperature(data, alerts)
        checkActivity(data, alerts)
        checkFlee(data, alerts)
        checkHeatCollapse(data, alerts)

        // initially status is normal, until conditions met to change it
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
        alerts: MutableList<AlertTrigger>
    ) {
        // Temperature Rules
        // temperature above 30°C is dangerous, animals sensitive to heat.
        if (data.ambientTemperatureC > CRITICAL_TEMP_C) {
            alerts.add(
                AlertTrigger(
                    "temperature",
                    "critical",
                    "Ambient temperature above ${CRITICAL_TEMP_C}°C — critical heat stress risk to livestock"
                )
            )
        } else if (data.ambientTemperatureC > WARNING_TEMP_C) {
            alerts.add(
                AlertTrigger(
                    "temperature",
                    "warning",
                    "Ambient temperature above ${WARNING_TEMP_C}°C — monitor for signs of heat stress"
                )
            )
        }
    }

    // Low Activity Rules
    // accelMagG measures movement in g-force.
    // stationary animal is around 1.0g.
    // below 1.0 during the day, the animal may be unhealthy.
    // values less than 0.5 g infer the animal is almost completely motionless.
    private fun checkActivity(
        data: LivestockPayload,
        alerts: MutableList<AlertTrigger>
    ) {
        if (data.accelMagG < CRITICAL_ACCEL_G) {
            alerts.add(
                AlertTrigger(
                    "low_activity",
                    "critical",
                    "Accelerometer below ${CRITICAL_ACCEL_G}g — animal may be collapsed or severely ill"
                )
            )
        } else if (data.accelMagG < WARNING_ACCEL_G) {
            alerts.add(
                AlertTrigger(
                    "low_activity",
                    "warning",
                    "Accelerometer below ${WARNING_ACCEL_G}g — reduced movement, monitor for illness"
                )
            )
        }
    }

    // Flee / Rustling Event Rules
    // random spike in accelerometer infer the animal is running at speed
    // above 3.0g indicate a flee event, possible livestock theft (rustling)
    private fun checkFlee(
        data: LivestockPayload,
        alerts: MutableList<AlertTrigger>
    ) {
        if (data.accelMagG > CRITICAL_FLEE_G) {
            alerts.add(
                AlertTrigger(
                    "flee",
                    "critical",
                    "Accelerometer above ${CRITICAL_FLEE_G}g — flee event possible, check for rustling or predator"
                )
            )
        } else if (data.accelMagG > WARNING_FLEE_G) {
            alerts.add(
                AlertTrigger(
                    "flee",
                    "warning",
                    "Accelerometer above ${WARNING_FLEE_G}g — unusual movement detected, monitor closely"
                )
            )
        }
    }

    // Combined Condition — heat stress and low activity together
    // an animal that is both very hot and barely moving is a medical emergency
    private fun checkHeatCollapse(
        data: LivestockPayload,
        alerts: MutableList<AlertTrigger>
    ) {
        if (data.ambientTemperatureC > WARNING_TEMP_C && data.accelMagG < WARNING_ACCEL_G) {
            alerts.add(
                AlertTrigger(
                    "heat_collapse",
                    "critical",
                    "High temperature with low activity — animal may be suffering heat stroke"
                )
            )
        }
    }
}
