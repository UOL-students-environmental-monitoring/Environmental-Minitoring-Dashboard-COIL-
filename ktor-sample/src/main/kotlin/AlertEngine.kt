@file:Suppress("InvalidPackageDeclaration")

package com.example

private const val MIN_CRITICAL_PH = 6.0
private const val MAX_CRITICAL_PH = 9.0
private const val MIN_WARNING_PH = 6.5
private const val MAX_WARNING_PH = 8.5
private const val WARNING_TURBIDITY_NTU = 5.0
private const val CRITICAL_TURBIDITY_NTU = 10.0
private const val WARNING_CONDUCTIVITY_US_CM = 500.0
private const val CRITICAL_CONDUCTIVITY_US_CM = 1500.0
private const val STATUS_NORMAL = "normal"
private const val STATUS_WARNING = "warning"
private const val STATUS_CRITICAL = "critical"
private const val PH_CRITICAL_MESSAGE =
    "pH is less than 6.0 or more than 9.0 - water might be corrosive/scaling"
private const val PH_WARNING_MESSAGE =
    "pH is less than 6.5 or more than 8.5 - outside comfortable drinking range"
private const val TURBIDITY_CRITICAL_MESSAGE =
    "Turbidity is more than 10 NTU - significant pathogen transport risk"
private const val TURBIDITY_WARNING_MESSAGE =
    "Turbidity is more than 5 NTU - visibly cloudy, reduced disinfection"
private const val CONDUCTIVITY_CRITICAL_MESSAGE =
    "Conductivity is more than 1500 µS/cm - increased salinity"
private const val CONDUCTIVITY_WARNING_MESSAGE =
    "Conductivity is more than 500 µS/cm - elevated dissolved solids"
private const val CONTAMINATION_WARNING_MESSAGE =
    "Simultaneous turbidity & conductivity spike indicates possible agricultural problem such as chemical contamination"

/** Evaluates water-quality readings against threshold-based alert rules. */
object AlertEngine {
    /** Result of evaluating a reading against the configured alert rules. */
    data class EvaluationResult(
        val status: String,
        val alerts: List<AlertTrigger>,
    )

    /** Describes a single alert raised by the rules engine. */
    data class AlertTrigger(
        val parameter: String,
        val severity: String,
        val message: String,
    )

    /** Applies the current water-quality rules and returns the resulting alerts. */
    fun evaluateWaterQuality(data: WaterQualityPayload): EvaluationResult {
        val alertsTriggered = mutableListOf<AlertTrigger>()
        addPhAlert(data, alertsTriggered)
        addTurbidityAlert(data, alertsTriggered)
        addConductivityAlert(data, alertsTriggered)
        addCombinedAlert(data, alertsTriggered)

        return EvaluationResult(status = deriveStatus(alertsTriggered), alerts = alertsTriggered)
    }
}

private fun addPhAlert(
    data: WaterQualityPayload,
    alertsTriggered: MutableList<AlertEngine.AlertTrigger>,
) {
    when {
        data.pH < MIN_CRITICAL_PH || data.pH > MAX_CRITICAL_PH -> {
            alertsTriggered.add(
                AlertEngine.AlertTrigger("pH", STATUS_CRITICAL, PH_CRITICAL_MESSAGE),
            )
        }

        data.pH < MIN_WARNING_PH || data.pH > MAX_WARNING_PH -> {
            alertsTriggered.add(
                AlertEngine.AlertTrigger("pH", STATUS_WARNING, PH_WARNING_MESSAGE),
            )
        }
    }
}

private fun addTurbidityAlert(
    data: WaterQualityPayload,
    alertsTriggered: MutableList<AlertEngine.AlertTrigger>,
) {
    when {
        data.turbidityNtu > CRITICAL_TURBIDITY_NTU -> {
            alertsTriggered.add(
                AlertEngine.AlertTrigger("turbidity", STATUS_CRITICAL, TURBIDITY_CRITICAL_MESSAGE),
            )
        }

        data.turbidityNtu > WARNING_TURBIDITY_NTU -> {
            alertsTriggered.add(
                AlertEngine.AlertTrigger("turbidity", STATUS_WARNING, TURBIDITY_WARNING_MESSAGE),
            )
        }
    }
}

private fun addConductivityAlert(
    data: WaterQualityPayload,
    alertsTriggered: MutableList<AlertEngine.AlertTrigger>,
) {
    when {
        data.conductivityPerCm > CRITICAL_CONDUCTIVITY_US_CM -> {
            alertsTriggered.add(
                AlertEngine.AlertTrigger(
                    "conductivity",
                    STATUS_CRITICAL,
                    CONDUCTIVITY_CRITICAL_MESSAGE,
                ),
            )
        }

        data.conductivityPerCm > WARNING_CONDUCTIVITY_US_CM -> {
            alertsTriggered.add(
                AlertEngine.AlertTrigger(
                    "conductivity",
                    STATUS_WARNING,
                    CONDUCTIVITY_WARNING_MESSAGE,
                ),
            )
        }
    }
}

private fun addCombinedAlert(
    data: WaterQualityPayload,
    alertsTriggered: MutableList<AlertEngine.AlertTrigger>,
) {
    if (
        data.turbidityNtu > WARNING_TURBIDITY_NTU &&
        data.conductivityPerCm > WARNING_CONDUCTIVITY_US_CM
    ) {
        alertsTriggered.add(
            AlertEngine.AlertTrigger(
                "contamination",
                STATUS_WARNING,
                CONTAMINATION_WARNING_MESSAGE,
            ),
        )
    }
}

private fun deriveStatus(alertsTriggered: List<AlertEngine.AlertTrigger>): String =
    when {
        alertsTriggered.any { it.severity == STATUS_CRITICAL } -> STATUS_CRITICAL
        alertsTriggered.isNotEmpty() -> STATUS_WARNING
        else -> STATUS_NORMAL
    }
