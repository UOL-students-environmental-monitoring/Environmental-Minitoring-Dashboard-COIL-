@file:Suppress("InvalidPackageDeclaration")

package com.example

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlertEngineTest {
    @Test
    fun `evaluateWaterQuality returns normal when reading is within thresholds`() {
        val result = AlertEngine.evaluateWaterQuality(validPayload())

        assertEquals("normal", result.status)
        assertTrue(result.alerts.isEmpty())
    }

    @Test
    fun `evaluateWaterQuality returns warning when reading crosses warning threshold`() {
        val result = AlertEngine.evaluateWaterQuality(validPayload(pH = 8.6))

        assertEquals("warning", result.status)
        assertTrue(result.alerts.any { it.parameter == "pH" && it.severity == "warning" })
    }

    @Test
    fun `evaluateWaterQuality returns critical when reading crosses critical threshold`() {
        val result = AlertEngine.evaluateWaterQuality(validPayload(pH = 9.5))

        assertEquals("critical", result.status)
        assertTrue(result.alerts.any { it.parameter == "pH" && it.severity == "critical" })
    }

    @Test
    fun `evaluateWaterQuality adds contamination warning when turbidity and conductivity spike together`() {
        val result =
            AlertEngine.evaluateWaterQuality(
                validPayload(
                    turbidityNtu = 6.0,
                    conductivityPerCm = 600.0,
                ),
            )

        assertEquals("warning", result.status)
        assertTrue(result.alerts.any { it.parameter == "contamination" && it.severity == "warning" })
    }

    private fun validPayload(
        siteId: String = "site_upstream",
        timeStamp: String = "2026-04-22T10:15:30",
        pH: Double = 7.2,
        turbidityNtu: Double = 1.0,
        conductivityPerCm: Double = 250.0,
        waterTempC: Double = 12.0,
        waterLvlCm: Double = 30.0,
        lightLux: Double = 200.0,
    ): WaterQualityPayload =
        WaterQualityPayload(
            siteId = siteId,
            timeStamp = timeStamp,
            pH = pH,
            turbidityNtu = turbidityNtu,
            conductivityPerCm = conductivityPerCm,
            waterTempC = waterTempC,
            waterLvlCm = waterLvlCm,
            lightLux = lightLux,
        )
}
