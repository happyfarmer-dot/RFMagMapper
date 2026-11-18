package com.rfmagmapper.app.dsp

import com.rfmagmapper.app.model.GridCell
import com.rfmagmapper.app.model.HeatmapLayer
import com.rfmagmapper.app.model.HotspotMarker
import com.rfmagmapper.app.model.SamplePoint
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow

object HeatmapInterpolator {

    const val GRID_RESOLUTION_METERS = 0.5f
    private const val IDW_RADIUS_METERS = 3.0f
    private const val IDW_POWER = 2.0
    private const val EPSILON = 1e-4

    data class InterpolationResult(
        val cells: List<GridCell>,
        val hotspots: List<HotspotMarker>,
        val minVal: Float,
        val maxVal: Float
    )

    fun extractLayerValue(sample: SamplePoint, layer: HeatmapLayer): Float {
        return when (layer) {
            HeatmapLayer.COMPOSITE -> sample.compositeIndex
            HeatmapLayer.WIFI -> sample.wifiTotalDbm.toFloat()
            HeatmapLayer.BLE -> sample.bleCount.toFloat()
            HeatmapLayer.MAG_60HZ -> sample.acMag60HzRmsUt
        }
    }

    fun normalizeValue(value: Float, layer: HeatmapLayer): Float {
        return when (layer) {
            HeatmapLayer.COMPOSITE -> (value / 100f).coerceIn(0f, 1f)
            HeatmapLayer.WIFI -> ((value + 90f) / 60f).coerceIn(0f, 1f) // -90 dBm -> 0, -30 dBm -> 1
            HeatmapLayer.BLE -> (value / 25f).coerceIn(0f, 1f)          // 0 -> 0, 25+ -> 1
            HeatmapLayer.MAG_60HZ -> (value / 15f).coerceIn(0f, 1f)     // 0 uT -> 0, 15+ uT -> 1
        }
    }

    fun formatValueLabel(value: Float, layer: HeatmapLayer): String {
        return when (layer) {
            HeatmapLayer.COMPOSITE -> String.format(Locale.US, "%s %.0f", layer.hotspotPrefix, value)
            HeatmapLayer.WIFI -> String.format(Locale.US, "%s %.0f dBm", layer.hotspotPrefix, value)
            HeatmapLayer.BLE -> String.format(Locale.US, "%s %.0f 台", layer.hotspotPrefix, value)
            HeatmapLayer.MAG_60HZ -> String.format(Locale.US, "%s %.1f μT", layer.hotspotPrefix, value)
        }
    }

    /**
     * Performs Inverse Distance Weighting (IDW) interpolation on a 0.5 m x 0.5 m grid
     * for all samples on [activeFloor]. Also identifies local peaks in the red zone (>= 0.68).
     */
    fun interpolate(
        samples: List<SamplePoint>,
        layer: HeatmapLayer,
        activeFloor: Int
    ): InterpolationResult {
        val floorSamples = samples.filter { it.floor == activeFloor }
        if (floorSamples.isEmpty()) {
            return InterpolationResult(emptyList(), emptyList(), 0f, 0f)
        }

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (s in floorSamples) {
            if (s.x < minX) minX = s.x
            if (s.x > maxX) maxX = s.x
            if (s.y < minY) minY = s.y
            if (s.y > maxY) maxY = s.y
        }

        val pad = IDW_RADIUS_METERS * 0.75f
        val startGx = floor((minX - pad) / GRID_RESOLUTION_METERS).toInt()
        val endGx = ceil((maxX + pad) / GRID_RESOLUTION_METERS).toInt()
        val startGy = floor((minY - pad) / GRID_RESOLUTION_METERS).toInt()
        val endGy = ceil((maxY + pad) / GRID_RESOLUTION_METERS).toInt()

        val cells = ArrayList<GridCell>()
        val cellMap = HashMap<Pair<Int, Int>, GridCell>()
        var minVal = Float.MAX_VALUE
        var maxVal = -Float.MAX_VALUE

        for (gx in startGx..endGx) {
            val wx = gx * GRID_RESOLUTION_METERS
            for (gy in startGy..endGy) {
                val wy = gy * GRID_RESOLUTION_METERS
                var weightedSum = 0.0
                var weightTotal = 0.0
                var hasNearby = false

                for (s in floorSamples) {
                    val dist = hypot((wx - s.x).toDouble(), (wy - s.y).toDouble())
                    if (dist <= IDW_RADIUS_METERS) {
                        hasNearby = true
                        val w = 1.0 / (dist.pow(IDW_POWER) + EPSILON)
                        weightedSum += w * extractLayerValue(s, layer)
                        weightTotal += w
                    }
                }

                if (hasNearby && weightTotal > 0.0) {
                    val rawVal = (weightedSum / weightTotal).toFloat()
                    val norm = normalizeValue(rawVal, layer)
                    if (rawVal < minVal) minVal = rawVal
                    if (rawVal > maxVal) maxVal = rawVal
                    val cell = GridCell(gx, gy, wx, wy, rawVal, norm)
                    cells.add(cell)
                    cellMap[gx to gy] = cell
                }
            }
        }

        // Detect Red-Zone Hotspot peaks (normalized >= 0.65, local maximum within 2.5m)
        val redCandidates = cells
            .filter { it.normalizedIntensity >= 0.65f }
            .sortedByDescending { it.normalizedIntensity }

        val hotspots = ArrayList<HotspotMarker>()
        for (candidate in redCandidates) {
            val tooClose = hotspots.any { existing ->
                hypot(
                    (existing.worldX - candidate.worldX).toDouble(),
                    (existing.worldY - candidate.worldY).toDouble()
                ) < 2.5
            }
            if (!tooClose) {
                hotspots.add(
                    HotspotMarker(
                        worldX = candidate.worldX,
                        worldY = candidate.worldY,
                        rawValue = candidate.rawValue,
                        normalizedIntensity = candidate.normalizedIntensity,
                        label = formatValueLabel(candidate.rawValue, layer)
                    )
                )
                if (hotspots.size >= 5) break
            }
        }

        if (minVal == Float.MAX_VALUE) minVal = 0f
        if (maxVal == -Float.MAX_VALUE) maxVal = 0f

        return InterpolationResult(cells, hotspots, minVal, maxVal)
    }
}
