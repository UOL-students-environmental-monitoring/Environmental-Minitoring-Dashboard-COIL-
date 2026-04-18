package com.example

// this file will handle the alert logic giving us the exact conditions for alerts
// based on livestock sensor data: GPS location, accelerometer movement, and temperature

object AlertEngine {

    // list of alerts triggered and specific alerts triggered
    data class EvaluationResult(val status: String, val alerts: List<AlertTrigger>)
    data class AlertTrigger(val parameter: String, val severity: String, val message: String)

    // uses a function to accept data within livestock sensor
    fun evaluateLivestock(data: LivestockPayload): EvaluationResult {

        // add AlertTrigger to triggers, if conditions are met
        val alertsTriggered = mutableListOf<AlertTrigger>()
        // initially status is normal, until conditions met to change it
        var overallStatus = "normal"

        // Temperature Rules
        // temperature above 30°C is dangerous, animals sensitive to heat.
        if ( data.ambientTemperatureC > 35.0 ) {
            alertsTriggered.add(AlertTrigger("temperature","critical","Ambient temperature is greater than 35°C — critical heat stress risk to livestock"))
        }
        else if ( data.ambientTemperatureC > 30.0 ) {
            alertsTriggered.add(AlertTrigger("temperature","warning","Ambient temperature is greater than 30°C but less than 35°C — monitor for signs of heat stress"))
        }

        // Low Activity Rules
        // accelMagG measures movement in g-force.
        // stationary animal is around 1.0g.
        // below 1.0 during the day, the animal may be unhealthy.
        // values less than 0.5 g infer the animal is almost completely motionless.
        if ( data.accelMagG < 0.3 ) {
            alertsTriggered.add(AlertTrigger("low_activity","critical","Accelerometer reading is less than 0.3g — animal may be collapsed or severely ill"))
        }
        else if ( data.accelMagG < 0.6 ) {
            alertsTriggered.add(AlertTrigger("low_activity","warning","Accelerometer reading is less than 0.6g but greater than 0.3g — animal showing reduced movement, possible illness"))
        }

        // Flee / Rustling Event Rules
        // random spike in accelerometer infer the animal is running at speed
        // above 3.0g indicate a flee event, possible livestock theft (rustling)
        if ( data.accelMagG > 4.0 ) {
            alertsTriggered.add(AlertTrigger("flee","critical","Accelerometer spiked above 4.0g — possible flee event detected, check for rustling or predator immediately"))
        }
        else if ( data.accelMagG > 3.0 ) {
            alertsTriggered.add(AlertTrigger("flee","warning","Accelerometer spike above 3.0g — unusual movement detected, monitor situation closely"))
        }

        // Combined Condition — heat stress AND low activity together
        // an animal that is both very hot and barely moving is a medical emergency
        if (data.ambientTemperatureC > 30.0 && data.accelMagG < 0.6) {
            alertsTriggered.add(AlertTrigger("heat_collapse", "critical", "High temperature with low activity — animal may be suffering heat stroke"))
        }

        // overall status changes to critical if any alerts triggered were critical
        // overall status changes to warning if there were any alerts triggered, but not critical
        // overall status stays as normal if there were no alerts triggered
        if (alertsTriggered.any { it.severity == "critical" }) {
            overallStatus = "critical"
        } else if (alertsTriggered.isNotEmpty()) {
            overallStatus = "warning"
        }

        return EvaluationResult(overallStatus, alertsTriggered)
    }
}