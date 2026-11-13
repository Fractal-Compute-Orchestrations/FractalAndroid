package com.example.fractal

import AppBackend.ResourceManagement.OperationControl.OperationControl
import AppGlobal.app_config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationControlTest {

    private fun healthyConfig() = app_config(
        onWifi = true,
        onData = false,
        idleTimeUtilization = false,
        minChargeLimit = 30,
        onChargingExclusive = false,
    )

    @Test
    fun grantsPermitWhenAllBatteryThermalAndNetworkConditionsAreHealthy() {
        val (allowed, msg) = OperationControl.evaluateConditions(
            config = healthyConfig(),
            isInteractive = true,
            batteryPct = 78,
            isCharging = false,
            batteryTempTenthsC = 335,
            isThermalSevere = false,
            isWifi = true,
            isCellular = false,
        )
        assertTrue(allowed)
        assertEquals("Optimal conditions met.", msg)
    }

    @Test
    fun deniesPermitWhenBatteryTemperatureExceeds40C() {
        val (allowed, msg) = OperationControl.evaluateConditions(
            config = healthyConfig(),
            isInteractive = false,
            batteryPct = 90,
            isCharging = true,
            batteryTempTenthsC = 415,
            isThermalSevere = false,
            isWifi = true,
            isCellular = true,
        )
        assertFalse(allowed)
        assertTrue(msg.contains("temperature", ignoreCase = true))
    }

    @Test
    fun deniesPermitWhenSocThermalThrottlingIsSevere() {
        val (allowed, msg) = OperationControl.evaluateConditions(
            config = healthyConfig(),
            isInteractive = false,
            batteryPct = 85,
            isCharging = true,
            batteryTempTenthsC = 360,
            isThermalSevere = true,
            isWifi = true,
            isCellular = false,
        )
        assertFalse(allowed)
        assertTrue(msg.contains("thermal throttling", ignoreCase = true))
    }

    @Test
    fun enforcesBatteryChargingIdleAndWifiConstraints() {
        val idleCfg = healthyConfig().apply {
            idleTimeUtilization = true
            onChargingExclusive = true
        }
        val (screenOn, _) = OperationControl.evaluateConditions(
            config = idleCfg,
            isInteractive = true,
            batteryPct = 80,
            isCharging = true,
            batteryTempTenthsC = 300,
            isThermalSevere = false,
            isWifi = true,
            isCellular = false,
        )
        assertFalse(screenOn)

        val (noCharger, _) = OperationControl.evaluateConditions(
            config = idleCfg,
            isInteractive = false,
            batteryPct = 80,
            isCharging = false,
            batteryTempTenthsC = 300,
            isThermalSevere = false,
            isWifi = true,
            isCellular = false,
        )
        assertFalse(noCharger)

        val (cellularOnly, _) = OperationControl.evaluateConditions(
            config = healthyConfig(),
            isInteractive = false,
            batteryPct = 80,
            isCharging = true,
            batteryTempTenthsC = 300,
            isThermalSevere = false,
            isWifi = false,
            isCellular = true,
        )
        assertFalse(cellularOnly)
    }
}
