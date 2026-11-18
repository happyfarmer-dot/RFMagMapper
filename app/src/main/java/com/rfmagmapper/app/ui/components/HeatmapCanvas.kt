package com.rfmagmapper.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rfmagmapper.app.dsp.HeatmapInterpolator
import com.rfmagmapper.app.model.GridCell
import com.rfmagmapper.app.model.HeatmapLayer
import com.rfmagmapper.app.model.HotspotMarker
import com.rfmagmapper.app.model.PdrState
import com.rfmagmapper.app.ui.theme.BgDeep
import com.rfmagmapper.app.ui.theme.CyanAccent
import com.rfmagmapper.app.ui.theme.GreenSafe
import com.rfmagmapper.app.ui.theme.RedHotspot
import com.rfmagmapper.app.ui.theme.SurfaceCard
import com.rfmagmapper.app.ui.theme.SurfaceElevated
import com.rfmagmapper.app.ui.theme.TextSecondary
import com.rfmagmapper.app.ui.theme.YellowWarn
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HeatmapCanvas(
    cells: List<GridCell>,
    hotspots: List<HotspotMarker>,
    pdrState: PdrState,
    selectedLayer: HeatmapLayer,
    modifier: Modifier = Modifier
) {
    var scalePxPerMeter by remember { mutableFloatStateOf(48f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val labelBgPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(210, 15, 23, 42)
        }
    }
    val labelTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgDeep)
            .border(1.dp, SurfaceElevated, RoundedCornerShape(16.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scalePxPerMeter = (scalePxPerMeter * zoom).coerceIn(16f, 160f)
                        panOffset += pan
                    }
                }
        ) {
            val centerX = size.width / 2f + panOffset.x
            val centerY = size.height / 2f + panOffset.y

            fun worldToScreen(wx: Float, wy: Float): Offset {
                return Offset(
                    x = centerX + wx * scalePxPerMeter,
                    y = centerY - wy * scalePxPerMeter
                )
            }

            // 1. Draw 0.5m x 0.5m architectural grid
            val gridStepMeters = HeatmapInterpolator.GRID_RESOLUTION_METERS
            val minWorldX = ((-centerX) / scalePxPerMeter) - 1f
            val maxWorldX = ((size.width - centerX) / scalePxPerMeter) + 1f
            val minWorldY = ((centerY - size.height) / scalePxPerMeter) - 1f
            val maxWorldY = (centerY / scalePxPerMeter) + 1f

            val startGx = kotlin.math.floor((minWorldX / gridStepMeters).toDouble()).toInt()
            val endGx = kotlin.math.ceil((maxWorldX / gridStepMeters).toDouble()).toInt()
            val startGy = kotlin.math.floor((minWorldY / gridStepMeters).toDouble()).toInt()
            val endGy = kotlin.math.ceil((maxWorldY / gridStepMeters).toDouble()).toInt()

            for (gx in startGx..endGx) {
                val wx = gx * gridStepMeters
                val sx = centerX + wx * scalePxPerMeter
                val isMajor = gx % 2 == 0
                drawLine(
                    color = if (isMajor) Color(0xFF1E293B) else Color(0xFF131C2E),
                    start = Offset(sx, 0f),
                    end = Offset(sx, size.height),
                    strokeWidth = if (isMajor) 1.5f else 0.8f
                )
            }
            for (gy in startGy..endGy) {
                val wy = gy * gridStepMeters
                val sy = centerY - wy * scalePxPerMeter
                val isMajor = gy % 2 == 0
                drawLine(
                    color = if (isMajor) Color(0xFF1E293B) else Color(0xFF131C2E),
                    start = Offset(0f, sy),
                    end = Offset(size.width, sy),
                    strokeWidth = if (isMajor) 1.5f else 0.8f
                )
            }

            // Origin crosshair (0, 0)
            val origin = worldToScreen(0f, 0f)
            drawCircle(
                color = TextSecondary.copy(alpha = 0.4f),
                radius = 6f,
                center = origin,
                style = Stroke(width = 2f)
            )

            // 2. Draw IDW Gaussian-style heatmap cells (Green -> Yellow -> Red)
            val blobRadius = gridStepMeters * scalePxPerMeter * 0.92f
            for (cell in cells) {
                val pos = worldToScreen(cell.worldX, cell.worldY)
                if (pos.x < -blobRadius || pos.x > size.width + blobRadius ||
                    pos.y < -blobRadius || pos.y > size.height + blobRadius
                ) continue

                val c = heatColorCompose(cell.normalizedIntensity)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c.copy(alpha = 0.52f), c.copy(alpha = 0.0f)),
                        center = pos,
                        radius = blobRadius
                    ),
                    radius = blobRadius,
                    center = pos
                )
            }

            // 3. Draw walking trajectory on current floor
            val floorTraj = pdrState.trajectory.filter { it.floor == pdrState.currentFloor }
            if (floorTraj.size >= 2) {
                val path = Path()
                val first = worldToScreen(floorTraj.first().x, floorTraj.first().y)
                path.moveTo(first.x, first.y)
                for (i in 1 until floorTraj.size) {
                    val pt = worldToScreen(floorTraj[i].x, floorTraj[i].y)
                    path.lineTo(pt.x, pt.y)
                }
                drawPath(
                    path = path,
                    color = CyanAccent,
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // 4. Draw automatic Red-Zone Hotspot Markers with value labels
            for (hs in hotspots) {
                val pos = worldToScreen(hs.worldX, hs.worldY)
                drawCircle(
                    color = RedHotspot.copy(alpha = 0.35f),
                    radius = 22f,
                    center = pos
                )
                drawCircle(
                    color = RedHotspot,
                    radius = 9f,
                    center = pos
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5f,
                    center = pos
                )
                drawContext.canvas.nativeCanvas.apply {
                    val textW = labelTextPaint.measureText(hs.label)
                    val left = pos.x - textW / 2f - 12f
                    val right = pos.x + textW / 2f + 12f
                    val top = pos.y - 58f
                    val bottom = pos.y - 22f
                    drawRoundRect(left, top, right, bottom, 10f, 10f, labelBgPaint)
                    drawText(hs.label, pos.x - textW / 2f, pos.y - 31f, labelTextPaint)
                }
            }

            // 5. Draw current user position & gyro heading arrow
            val curPos = worldToScreen(pdrState.xMeters, pdrState.yMeters)
            drawCircle(
                color = CyanAccent.copy(alpha = 0.25f),
                radius = 24f,
                center = curPos
            )
            drawCircle(
                color = CyanAccent,
                radius = 9f,
                center = curPos
            )
            val arrowLen = 28f
            val tipX = curPos.x + arrowLen * sin(pdrState.headingRad)
            val tipY = curPos.y - arrowLen * cos(pdrState.headingRad)
            drawLine(
                color = Color.White,
                start = curPos,
                end = Offset(tipX, tipY),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        // Top-left overlay: Layer + Floor + Scale indicator
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(SurfaceCard.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "${selectedLayer.shortTitle} · ${pdrState.currentFloor}F",
                style = MaterialTheme.typography.labelMedium,
                color = CyanAccent
            )
            Text(
                text = "網格 0.5m × 0.5m · 熱點 ${hotspots.size}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        // Top-right button: Center on current position
        IconButton(
            onClick = {
                val targetScreenX = -pdrState.xMeters * scalePxPerMeter
                val targetScreenY = pdrState.yMeters * scalePxPerMeter
                panOffset = Offset(targetScreenX, targetScreenY)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(36.dp)
                .background(SurfaceCard.copy(alpha = 0.85f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "置中",
                tint = CyanAccent,
                modifier = Modifier.size(18.dp)
            )
        }

        // Bottom legend bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp)
                .background(SurfaceCard.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("低", style = MaterialTheme.typography.labelSmall, color = GreenSafe)
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(GreenSafe, YellowWarn, RedHotspot)
                        )
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("高熱區", style = MaterialTheme.typography.labelSmall, color = RedHotspot)
        }
    }
}

private fun heatColorCompose(tRaw: Float): Color {
    val t = tRaw.coerceIn(0f, 1f)
    return if (t < 0.5f) {
        val k = t / 0.5f
        Color(
            red = k,
            green = (230 + (234 - 230) * k) / 255f,
            blue = (118 * (1f - k)) / 255f,
            alpha = 1f
        )
    } else {
        val k = (t - 0.5f) / 0.5f
        Color(
            red = 1f,
            green = (234 + (23 - 234) * k) / 255f,
            blue = (68 * k) / 255f,
            alpha = 1f
        )
    }
}
