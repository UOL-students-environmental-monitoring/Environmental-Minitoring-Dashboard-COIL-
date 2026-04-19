package com.example

// this file will handle the alert logic giving us the exact conditions for alerts

object AlertEngine{

    // list of alerts triggered and specific alerts triggered
    data class EvaluationResult(val status:String,val alerts:List<AlertTrigger>)
    data class AlertTrigger(val parameter:String,val severity: String,val message:String)

    // take in data from the sensor using a function
    fun evaluateWaterQuality(data: WaterQualityPayload): EvaluationResult{

        // if one of the conditions underneath accepted append AlertTrigger to triggers
        val alertsTriggered = mutableListOf<AlertTrigger>()
        // initially water is set to normal until proven with the conditions below
        var ovrStatus = "normal"

        // checking pH to see if safe to drink
        if (data.pH < 6.0 || data.pH > 9.0) {
            alertsTriggered.add(AlertTrigger("pH", "critical", "pH is less than 6.0 or more than 9.0 - water might be corrosive/scaling"))
        } else if (data.pH < 6.5 || data.pH > 8.5) {
            alertsTriggered.add(AlertTrigger("pH", "warning", "pH is less than 6.5 or more than 8.5 - outside comfortable drinking range"))
        }

        // checking turbidity conditions
        // 2. Turbidity Rules
        if (data.turbidityNtu > 10.0) {
            alertsTriggered.add(AlertTrigger("turbidity", "critical", "Turbidity is more than 10 NTU - significant pathogen transport risk"))
        } else if (data.turbidityNtu > 5.0) {
            alertsTriggered.add(AlertTrigger("turbidity", "warning", "Turbidity is more than 5 NTU - visibly cloudy, reduced disinfection"))
        }

        // checking conductivity conditions
        if (data.conductivityPerCm > 1500.0) {
            alertsTriggered.add(AlertTrigger("conductivity", "critical", "Conductivity is more than 1500 µS/cm - increased salinity"))
        } else if (data.conductivityPerCm > 500.0) {
            alertsTriggered.add(AlertTrigger("conductivity", "warning", "Conductivity is more than 500 µS/cm - elevated dissolved solids"))
        }

        // checking for combined conditions as recomended from the readme file
        if (data.turbidityNtu > 5.0 && data.conductivityPerCm > 500.0) {
            alertsTriggered.add(AlertTrigger("contamination", "warning", "Simultaneous turbidity & conductivity spike indicates possible agricultural problem such as chemical contamination"))
        }

        // overall status changes to critical if any alerts triggered were critical
        // overall status changes to warning if there were any alerts triggered, but not critical
        // overall status stays as normal if there were no alerts triggered
        if (alertsTriggered.any { it.severity == "critical" }) {
            ovrStatus = "critical"
        } else if (alertsTriggered.isNotEmpty()) {
            ovrStatus = "warning"
        }
        return EvaluationResult(ovrStatus,alertsTriggered)
    }
}