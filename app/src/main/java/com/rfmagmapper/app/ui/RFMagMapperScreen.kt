package com.rfmagmapper.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rfmagmapper.app.model.HeatmapLayer
import com.rfmagmapper.app.ui.components.HeatmapCanvas
import com.rfmagmapper.app.ui.components.TelemetryDashboard
import com.rfmagmapper.app.ui.components.WifiThrottleGuideCard
import com.rfmagmapper.app.ui.theme.BgDeep
import com.rfmagmapper.app.ui.theme.CyanAccent
import com.rfmagmapper.app.ui.theme.GreenSafe
import com.rfmagmapper.app.ui.theme.RedHotspot
import com.rfmagmapper.app.ui.theme.SurfaceCard
import com.rfmagmapper.app.ui.theme.SurfaceElevated
import com.rfmagmapper.app.ui.theme.TextPrimary
import com.rfmagmapper.app.ui.theme.TextSecondary
import com.rfmagmapper.app.viewmodel.RFMagViewModel

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.ACTIVITY_RECOGNITION
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RFMagMapperScreen(viewModel: RFMagViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val wifi by viewModel.wifiState.collectAsState()
    val ble by viewModel.bleState.collectAsState()
    val mag by viewModel.magState.collectAsState()
    val pdr by viewModel.pdrState.collectAsState()
    val gnss by viewModel.gnssState.collectAsState()
    val isMapping by viewModel.isMapping.collectAsState()
    val selectedLayer by viewModel.selectedLayer.collectAsState()
    val gridCells by viewModel.gridCells.collectAsState()
    val hotspots by viewModel.hotspots.collectAsState()
    val samples by viewModel.samples.collectAsState()
    val showGuide by viewModel.showWifiThrottleGuide.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()

    var hasAllPermissions by remember {
        mutableStateOf(checkAllPermissions(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasAllPermissions = result.values.all { it } || checkAllPermissions(context)
        if (hasAllPermissions) {
            viewModel.startEnginesAndMapping()
        }
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    Scaffold(
        containerColor = BgDeep,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "RFMagMapper",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Wi-Fi 7 / BLE / 60Hz 磁場熱區測繪儀 · 採樣點 ${samples.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceCard),
                actions = {
                    IconButton(onClick = { viewModel.exportPng(context) }) {
                        Icon(Icons.Default.Image, contentDescription = "匯出 PNG", tint = CyanAccent)
                    }
                    IconButton(onClick = { viewModel.exportJson(context) }) {
                        Icon(Icons.Default.DataObject, contentDescription = "匯出 JSON", tint = CyanAccent)
                    }
                    IconButton(onClick = { viewModel.clearMap() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清除地圖", tint = TextSecondary)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Permission Gate Banner if not granted
            if (!hasAllPermissions) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = RedHotspot)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "需要感測器與射頻掃描權限",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "請授權精確定位 (Wi-Fi / GNSS L1+L5)、藍牙掃描與身體活動偵測 (PDR 步數)，才能進行即時室內熱區測繪。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { permissionLauncher.launch(REQUIRED_PERMISSIONS) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                        ) {
                            Text("立即授權所有權限", color = BgDeep, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // S24 Ultra Wi-Fi Scan Throttling Guidance Card
            if (showGuide) {
                WifiThrottleGuideCard(onDismiss = { viewModel.dismissWifiThrottleGuide() })
            }

            // 4-Layer Real-Time Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HeatmapLayer.entries.forEach { layer ->
                    val selected = layer == selectedLayer
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectLayer(layer) },
                        label = {
                            Text(
                                text = layer.shortTitle,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent,
                            selectedLabelColor = BgDeep,
                            containerColor = SurfaceCard,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Interactive 2D Indoor Heatmap Canvas
            HeatmapCanvas(
                cells = gridCells,
                hotspots = hotspots,
                pdrState = pdr,
                selectedLayer = selectedLayer,
                modifier = Modifier.height(340.dp)
            )

            // Primary Mapping Controls + Export Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (!hasAllPermissions) {
                            permissionLauncher.launch(REQUIRED_PERMISSIONS)
                        } else if (isMapping) {
                            viewModel.stopMapping()
                        } else {
                            viewModel.startEnginesAndMapping()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMapping) RedHotspot else GreenSafe
                    ),
                    modifier = Modifier.weight(1.4f)
                ) {
                    Icon(
                        imageVector = if (isMapping) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = BgDeep,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isMapping) "停止測繪" else "開始測繪",
                        color = BgDeep,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.exportPng(context) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("匯出 PNG", style = MaterialTheme.typography.labelMedium, color = CyanAccent)
                }

                OutlinedButton(
                    onClick = { viewModel.exportJson(context) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("匯出 JSON", style = MaterialTheme.typography.labelMedium, color = CyanAccent)
                }
            }

            // Live 5-Module Telemetry Dashboard
            TelemetryDashboard(
                wifi = wifi,
                ble = ble,
                mag = mag,
                pdr = pdr,
                gnss = gnss,
                onStepLengthChange = { viewModel.setStepLength(it) },
                onZeroHeading = { viewModel.zeroHeading() },
                onRezeroMagBaseline = { viewModel.rezeroMagBaseline() },
                onManualStep = { viewModel.triggerManualStep() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun checkAllPermissions(context: android.content.Context): Boolean {
    return REQUIRED_PERMISSIONS.all { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }
}
