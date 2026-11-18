package com.rfmagmapper.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rfmagmapper.app.model.BleSummary
import com.rfmagmapper.app.model.GnssAnchor
import com.rfmagmapper.app.model.MagReading
import com.rfmagmapper.app.model.PdrState
import com.rfmagmapper.app.model.WifiScanSummary
import com.rfmagmapper.app.ui.theme.CyanAccent
import com.rfmagmapper.app.ui.theme.GreenSafe
import com.rfmagmapper.app.ui.theme.RedHotspot
import com.rfmagmapper.app.ui.theme.SurfaceCard
import com.rfmagmapper.app.ui.theme.TextPrimary
import com.rfmagmapper.app.ui.theme.TextSecondary
import com.rfmagmapper.app.ui.theme.YellowWarn
import java.util.Locale

@Composable
fun TelemetryDashboard(
    wifi: WifiScanSummary,
    ble: BleSummary,
    mag: MagReading,
    pdr: PdrState,
    gnss: GnssAnchor,
    onStepLengthChange: (Float) -> Unit,
    onZeroHeading: () -> Unit,
    onRezeroMagBaseline: () -> Unit,
    onManualStep: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Module A: Wi-Fi 7 Tri-Band
        SectionCard(
            title = "模組 A · Wi-Fi 7 三頻段射頻能量",
            icon = Icons.Default.Wifi,
            accent = CyanAccent
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("總等效功率", String.format(Locale.US, "%.1f dBm", wifi.totalEquivalentDbm))
                MetricItem("線性總功率", String.format(Locale.US, "%.3e mW", wifi.totalPowerMw))
                MetricItem("熱點數", "${wifi.apCount}")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "頻段分布: 2.4G (${wifi.band24Count}) · 5G (${wifi.band5Count}) · 6G (${wifi.band6Count})",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            if (wifi.top3Aps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "訊號最強 Top 3:",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanAccent,
                    fontWeight = FontWeight.SemiBold
                )
                wifi.top3Aps.forEachIndexed { idx, ap ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${idx + 1}. ${ap.ssid} [${ap.band.label}]",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${ap.rssiDbm} dBm",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ap.rssiDbm >= -50) RedHotspot else GreenSafe,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Module B: BLE
        SectionCard(
            title = "模組 B · 藍牙 BLE 環境射頻密度",
            icon = Icons.Default.Bluetooth,
            accent = Color(0xFF60A5FA)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("活躍發射源", "${ble.activeEmitterCount} 台")
                MetricItem("最強單一 RSSI", "${ble.strongestRssiDbm} dBm")
                MetricItem("線性總密度", String.format(Locale.US, "%.2e mW", ble.totalPowerDensityMw))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "最強裝置: ${ble.strongestDeviceName}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Module C: 100Hz Dual-Channel Magnetometer
        SectionCard(
            title = "模組 C · 雙通道低頻磁場 (@ ${String.format(Locale.US, "%.0f", mag.sampleRateHz)} Hz)",
            icon = Icons.Default.ElectricBolt,
            accent = if (mag.ac60HzRmsUt >= 8f) RedHotspot else YellowWarn,
            trailingAction = {
                TextButton(onClick = onRezeroMagBaseline) {
                    Text("重設 DC 基準", style = MaterialTheme.typography.labelSmall, color = CyanAccent)
                }
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    "60Hz AC RMS",
                    String.format(Locale.US, "%.2f μT", mag.ac60HzRmsUt),
                    valueColor = if (mag.ac60HzRmsUt >= 8f) RedHotspot else YellowWarn
                )
                MetricItem(
                    "DC 磁場異常 ΔB",
                    String.format(Locale.US, "%.2f μT", mag.dcAnomalyUt)
                )
                MetricItem(
                    "總磁通量 |B|",
                    String.format(Locale.US, "%.1f μT", mag.totalFluxUt)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "40 Hz 混疊帶通濾波 (38–42 Hz IIR) · 基準值: ${String.format(Locale.US, "%.1f", mag.dcBaselineUt)} μT",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        // Module D: PDR + Barometer
        SectionCard(
            title = "模組 D · 室內 PDR 航位推算與氣壓樓層",
            icon = Icons.Default.DirectionsWalk,
            accent = GreenSafe,
            trailingAction = {
                Row {
                    TextButton(onClick = onManualStep) {
                        Text("+1 步", style = MaterialTheme.typography.labelSmall, color = GreenSafe)
                    }
                    TextButton(onClick = onZeroHeading) {
                        Text("歸零航向", style = MaterialTheme.typography.labelSmall, color = CyanAccent)
                    }
                }
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("座標 (X, Y)", String.format(Locale.US, "(%.1f, %.1f)m", pdr.xMeters, pdr.yMeters))
                MetricItem("步數 / 航向", "${pdr.stepCount} / ${pdr.headingDeg.toInt()}°")
                MetricItem("樓層 / Δh", "${pdr.currentFloor}F (${String.format(Locale.US, "%+.1f", pdr.deltaHeightMeters)}m)")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "步長: ${String.format(Locale.US, "%.2f", pdr.stepLengthMeters)} m",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.width(82.dp)
                )
                Slider(
                    value = pdr.stepLengthMeters,
                    onValueChange = onStepLengthChange,
                    valueRange = 0.45f..0.90f,
                    colors = SliderDefaults.colors(
                        thumbColor = GreenSafe,
                        activeTrackColor = GreenSafe
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Module E: Dual-Frequency GNSS L1+L5 Anchor
        SectionCard(
            title = "模組 E · 雙頻 GNSS (L1+L5) 戶外基準錨點",
            icon = Icons.Default.SatelliteAlt,
            accent = if (gnss.isDualFrequencyL1L5) GreenSafe else TextSecondary
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    "雙頻鎖定",
                    if (gnss.isDualFrequencyL1L5) "L1+L5 雙頻" else if (gnss.hasFix) "單頻" else "室內無訊號",
                    valueColor = if (gnss.isDualFrequencyL1L5) GreenSafe else YellowWarn
                )
                MetricItem("平均 CN0", String.format(Locale.US, "%.1f dB-Hz", gnss.avgCn0DbHz))
                MetricItem("L1 / L5 衛星", "${gnss.l1SatCount} / ${gnss.l5SatCount}")
            }
            if (gnss.hasFix) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "基準座標: %.6f, %.6f (±%.1fm)",
                        gnss.latitude, gnss.longitude, gnss.accuracyMeters
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                trailingAction?.invoke()
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
