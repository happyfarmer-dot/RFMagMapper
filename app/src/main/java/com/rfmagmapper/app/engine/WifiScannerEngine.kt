package com.rfmagmapper.app.engine

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import com.rfmagmapper.app.dsp.RfPhysics
import com.rfmagmapper.app.model.WifiApInfo
import com.rfmagmapper.app.model.WifiBand
import com.rfmagmapper.app.model.WifiScanSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Module A: Wi-Fi 7 Tri-Band (2.4G / 5G / 6GHz) RF Energy Scanner.
 * Continuously triggers WifiManager.startScan() and listens to SCAN_RESULTS_AVAILABLE_ACTION.
 * Performs physically accurate linear mW power summation (never arithmetic averaging of dBm).
 */
class WifiScannerEngine(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _state = MutableStateFlow(WifiScanSummary())
    val state: StateFlow<WifiScanSummary> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var receiverRegistered = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                processScanResults(success)
            }
        }
    }

    fun start(scope: CoroutineScope) {
        if (!receiverRegistered) {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(scanReceiver, filter)
            }
            receiverRegistered = true
        }

        // Initial read of cached scan results
        processScanResults(true)

        scanJob?.cancel()
        scanJob = scope.launch {
            while (isActive) {
                try {
                    @Suppress("DEPRECATION")
                    wifiManager.startScan()
                } catch (_: SecurityException) {
                    // Permission not yet granted
                } catch (_: Exception) {
                }
                delay(1500L)
            }
        }
    }

    fun stop() {
        scanJob?.cancel()
        scanJob = null
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(scanReceiver)
            } catch (_: IllegalArgumentException) {
            }
            receiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun processScanResults(scanUpdated: Boolean) {
        val results: List<ScanResult> = try {
            wifiManager.scanResults ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }

        val apInfos = results.map { sr ->
            val ssidStr = extractSsid(sr)
            val band = RfPhysics.classifyWifiBand(sr.frequency)
            val mw = RfPhysics.dbmToMilliwatts(sr.level.toDouble())
            WifiApInfo(
                ssid = ssidStr,
                bssid = sr.BSSID ?: "--:--:--:--:--:--",
                rssiDbm = sr.level,
                frequencyMhz = sr.frequency,
                band = band,
                powerMw = mw
            )
        }

        val (totalMw, totalDbm) = RfPhysics.sumRssiInLinearPower(apInfos.map { it.rssiDbm })

        var c24 = 0
        var c5 = 0
        var c6 = 0
        for (ap in apInfos) {
            when (ap.band) {
                WifiBand.BAND_2_4G -> c24++
                WifiBand.BAND_5G -> c5++
                WifiBand.BAND_6G -> c6++
                WifiBand.UNKNOWN -> Unit
            }
        }

        val top3 = apInfos.sortedByDescending { it.rssiDbm }.take(3)

        _state.value = WifiScanSummary(
            apCount = apInfos.size,
            totalPowerMw = totalMw,
            totalEquivalentDbm = totalDbm,
            band24Count = c24,
            band5Count = c5,
            band6Count = c6,
            top3Aps = top3,
            lastScanTimestampMs = System.currentTimeMillis(),
            scanSuccess = scanUpdated
        )
    }

    private fun extractSsid(sr: ScanResult): String {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sr.wifiSsid?.toString()?.removeSurrounding("\"")
        } else {
            @Suppress("DEPRECATION")
            sr.SSID
        }
        return if (raw.isNullOrBlank()) "<Hidden SSID>" else raw
    }
}
