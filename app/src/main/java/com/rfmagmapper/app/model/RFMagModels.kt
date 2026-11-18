package com.rfmagmapper.app.model

enum class WifiBand(val label: String) {
    BAND_2_4G("2.4G"),
    BAND_5G("5G"),
    BAND_6G("6G"),
    UNKNOWN("?")
}

data class WifiApInfo(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val band: WifiBand,
    val powerMw: Double
)

data class WifiScanSummary(
    val apCount: Int = 0,
    val totalPowerMw: Double = 0.0,
    val totalEquivalentDbm: Double = -110.0,
    val band24Count: Int = 0,
    val band5Count: Int = 0,
    val band6Count: Int = 0,
    val top3Aps: List<WifiApInfo> = emptyList(),
    val lastScanTimestampMs: Long = 0L,
    val scanSuccess: Boolean = true
)

data class BleSummary(
    val activeEmitterCount: Int = 0,
    val strongestRssiDbm: Int = -110,
    val strongestDeviceName: String = "--",
    val totalPowerDensityMw: Double = 0.0,
    val totalEquivalentDbm: Double = -110.0,
    val lastUpdateMs: Long = 0L
)

data class MagReading(
    val rawX: Float = 0f,
    val rawY: Float = 0f,
    val rawZ: Float = 0f,
    val totalFluxUt: Float = 0f,
    val ac60HzRmsUt: Float = 0f,
    val dcBaselineUt: Float = 0f,
    val dcAnomalyUt: Float = 0f,
    val sampleRateHz: Float = 0f,
    val isBaselineReady: Boolean = false
)

data class PdrState(
    val xMeters: Float = 0f,
    val yMeters: Float = 0f,
    val headingRad: Float = 0f,
    val headingDeg: Float = 0f,
    val stepCount: Int = 0,
    val stepLengthMeters: Float = 0.65f,
    val pressureHpa: Float = 1013.25f,
    val deltaHeightMeters: Float = 0f,
    val currentFloor: Int = 1,
    val trajectory: List<TrajectoryPoint> = listOf(TrajectoryPoint(0f, 0f, 1, 0L))
)

data class TrajectoryPoint(
    val x: Float,
    val y: Float,
    val floor: Int,
    val timestampMs: Long
)

data class GnssAnchor(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeMeters: Double = 0.0,
    val accuracyMeters: Float = 0f,
    val avgCn0DbHz: Float = 0f,
    val l1SatCount: Int = 0,
    val l5SatCount: Int = 0,
    val isDualFrequencyL1L5: Boolean = false,
    val hasFix: Boolean = false,
    val timestampMs: Long = 0L
)

enum class HeatmapLayer(
    val title: String,
    val shortTitle: String,
    val unit: String,
    val hotspotPrefix: String
) {
    COMPOSITE("綜合無線與電磁環境指數", "綜合指數", "pt", "高負荷綜合熱區"),
    WIFI("Wi-Fi 射頻強度熱區", "Wi-Fi RF", "dBm", "Wi-Fi 強訊號區"),
    BLE("藍牙 BLE 裝置密度熱區", "BLE 密度", "台", "BLE 高密度區"),
    MAG_60HZ("60 Hz 低頻交流磁場熱區", "60Hz 磁場", "μT", "60Hz 變壓器熱區")
}

data class SamplePoint(
    val x: Float,
    val y: Float,
    val floor: Int,
    val timestampMs: Long,
    val wifiTotalDbm: Double,
    val wifiTotalMw: Double,
    val wifiApCount: Int,
    val wifiTop3: List<WifiApInfo>,
    val bleCount: Int,
    val bleMaxRssi: Int,
    val bleTotalMw: Double,
    val acMag60HzRmsUt: Float,
    val dcMagAnomalyUt: Float,
    val compositeIndex: Float
)

data class GridCell(
    val gridX: Int,
    val gridY: Int,
    val worldX: Float,
    val worldY: Float,
    val rawValue: Float,
    val normalizedIntensity: Float // 0f (Green) -> 0.5f (Yellow) -> 1f (Red)
)

data class HotspotMarker(
    val worldX: Float,
    val worldY: Float,
    val rawValue: Float,
    val normalizedIntensity: Float,
    val label: String
)
