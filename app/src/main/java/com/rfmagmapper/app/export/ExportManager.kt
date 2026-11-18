package com.rfmagmapper.app.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.rfmagmapper.app.dsp.HeatmapInterpolator
import com.rfmagmapper.app.model.GnssAnchor
import com.rfmagmapper.app.model.GridCell
import com.rfmagmapper.app.model.HeatmapLayer
import com.rfmagmapper.app.model.HotspotMarker
import com.rfmagmapper.app.model.SamplePoint
import com.rfmagmapper.app.model.TrajectoryPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object ExportManager {

    data class ExportResult(
        val success: Boolean,
        val displayPath: String,
        val shareUri: Uri? = null,
        val errorMessage: String? = null
    )

    fun exportHeatmapPng(
        context: Context,
        layer: HeatmapLayer,
        cells: List<GridCell>,
        hotspots: List<HotspotMarker>,
        trajectory: List<TrajectoryPoint>,
        activeFloor: Int,
        gnssAnchor: GnssAnchor
    ): ExportResult {
        return try {
            val width = 1440
            val height = 1600
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Dark scientific background
            canvas.drawColor(Color.parseColor("#0B1220"))

            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 42f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 26f
            }

            val tsStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            canvas.drawText("RFMagMapper — ${layer.title} (${activeFloor}F)", 48f, 70f, headerPaint)
            val gnssText = if (gnssAnchor.hasFix) {
                String.format(
                    Locale.US,
                    "GNSS: %.6f, %.6f | L1:%d L5:%d | CN0: %.1f dB-Hz | %s",
                    gnssAnchor.latitude, gnssAnchor.longitude,
                    gnssAnchor.l1SatCount, gnssAnchor.l5SatCount,
                    gnssAnchor.avgCn0DbHz, tsStr
                )
            } else {
                "GNSS Anchor: Indoor / No Fix | Exported: $tsStr"
            }
            canvas.drawText(gnssText, 48f, 114f, subPaint)

            // Compute world bounds
            val mapTop = 150f
            val mapBottom = 1440f
            val mapLeft = 48f
            val mapRight = width - 48f
            val mapW = mapRight - mapLeft
            val mapH = mapBottom - mapTop

            var minX = -3f
            var maxX = 3f
            var minY = -3f
            var maxY = 3f
            for (c in cells) {
                minX = min(minX, c.worldX - 1f)
                maxX = max(maxX, c.worldX + 1f)
                minY = min(minY, c.worldY - 1f)
                maxY = max(maxY, c.worldY + 1f)
            }
            for (t in trajectory.filter { it.floor == activeFloor }) {
                minX = min(minX, t.x - 1f)
                maxX = max(maxX, t.x + 1f)
                minY = min(minY, t.y - 1f)
                maxY = max(maxY, t.y + 1f)
            }

            val worldSpanX = max(4f, maxX - minX)
            val worldSpanY = max(4f, maxY - minY)
            val scale = min(mapW / worldSpanX, mapH / worldSpanY)
            val offsetX = mapLeft + (mapW - worldSpanX * scale) / 2f
            val offsetY = mapTop + (mapH - worldSpanY * scale) / 2f

            fun toScreenX(wx: Float) = offsetX + (wx - minX) * scale
            fun toScreenY(wy: Float) = mapBottom - (wy - minY) * scale - (mapH - worldSpanY * scale) / 2f

            // Draw 0.5m grid
            val gridPaint = Paint().apply {
                color = Color.parseColor("#1E293B")
                strokeWidth = 1.5f
            }
            var gx = kotlin.math.floor(minX.toDouble()).toFloat()
            while (gx <= maxX) {
                val sx = toScreenX(gx)
                canvas.drawLine(sx, mapTop, sx, mapBottom, gridPaint)
                gx += 0.5f
            }
            var gy = kotlin.math.floor(minY.toDouble()).toFloat()
            while (gy <= maxY) {
                val sy = toScreenY(gy)
                canvas.drawLine(mapLeft, sy, mapRight, sy, gridPaint)
                gy += 0.5f
            }

            // Draw heatmap cells
            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val halfCellPx = (HeatmapInterpolator.GRID_RESOLUTION_METERS * scale) * 0.72f
            for (c in cells) {
                val sx = toScreenX(c.worldX)
                val sy = toScreenY(c.worldY)
                cellPaint.color = heatColorArgb(c.normalizedIntensity, alpha = 180)
                canvas.drawCircle(sx, sy, halfCellPx, cellPaint)
            }

            // Draw trajectory
            val floorTraj = trajectory.filter { it.floor == activeFloor }
            if (floorTraj.size >= 2) {
                val trajPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#00E5FF")
                    strokeWidth = 5f
                    style = Paint.Style.STROKE
                }
                val path = Path()
                path.moveTo(toScreenX(floorTraj.first().x), toScreenY(floorTraj.first().y))
                for (i in 1 until floorTraj.size) {
                    path.lineTo(toScreenX(floorTraj[i].x), toScreenY(floorTraj[i].y))
                }
                canvas.drawPath(path, trajPaint)
            }

            // Draw hotspots
            val hsFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF1744") }
            val hsBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CC0F172A") }
            val hsText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            for (h in hotspots) {
                val sx = toScreenX(h.worldX)
                val sy = toScreenY(h.worldY)
                canvas.drawCircle(sx, sy, 14f, hsFill)
                val tw = hsText.measureText(h.label)
                canvas.drawRoundRect(sx - tw / 2f - 12f, sy - 56f, sx + tw / 2f + 12f, sy - 20f, 10f, 10f, hsBg)
                canvas.drawText(h.label, sx - tw / 2f, sy - 30f, hsText)
            }

            // Legend bar at bottom
            val barLeft = 48f
            val barRight = width - 48f
            val barTop = 1480f
            val barBottom = 1515f
            val steps = 100
            val stepW = (barRight - barLeft) / steps
            for (i in 0 until steps) {
                val t = i / (steps - 1f)
                cellPaint.color = heatColorArgb(t, 255)
                canvas.drawRect(barLeft + i * stepW, barTop, barLeft + (i + 1) * stepW + 1f, barBottom, cellPaint)
            }
            canvas.drawText("低輻射 (Green)", barLeft, 1555f, subPaint)
            canvas.drawText("中度 (Yellow)", (barLeft + barRight) / 2f - 80f, 1555f, subPaint)
            canvas.drawText("高強度熱區 (Red)", barRight - 220f, 1555f, subPaint)

            val fileName = "RFMagMapper_${layer.name}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"

            // Save to MediaStore Pictures/RFMagMapper
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RFMagMapper")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            // Also save cache copy for FileProvider sharing
            val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cacheFile = File(cacheDir, fileName)
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)

            ExportResult(
                success = true,
                displayPath = "Pictures/RFMagMapper/$fileName",
                shareUri = shareUri
            )
        } catch (e: Exception) {
            ExportResult(success = false, displayPath = "", errorMessage = e.message)
        }
    }

    fun exportRawJson(
        context: Context,
        samples: List<SamplePoint>,
        gnssAnchor: GnssAnchor
    ): ExportResult {
        return try {
            val root = JSONObject().apply {
                put("app", "RFMagMapper")
                put("deviceTarget", "Samsung Galaxy S24 Ultra")
                put("exportedAtIso", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()))
                put("gridResolutionMeters", HeatmapInterpolator.GRID_RESOLUTION_METERS)
                put("gnssAnchor", JSONObject().apply {
                    put("hasFix", gnssAnchor.hasFix)
                    put("latitude", gnssAnchor.latitude)
                    put("longitude", gnssAnchor.longitude)
                    put("altitudeMeters", gnssAnchor.altitudeMeters)
                    put("accuracyMeters", gnssAnchor.accuracyMeters)
                    put("avgCn0DbHz", gnssAnchor.avgCn0DbHz)
                    put("l1SatCount", gnssAnchor.l1SatCount)
                    put("l5SatCount", gnssAnchor.l5SatCount)
                    put("isDualFrequencyL1L5", gnssAnchor.isDualFrequencyL1L5)
                })

                val arr = JSONArray()
                for (s in samples) {
                    val obj = JSONObject().apply {
                        put("xMeters", s.x)
                        put("yMeters", s.y)
                        put("floor", s.floor)
                        put("timestampMs", s.timestampMs)
                        put("wifiTotalDbm", s.wifiTotalDbm)
                        put("wifiTotalMw", s.wifiTotalMw)
                        put("wifiApCount", s.wifiApCount)
                        put("bleCount", s.bleCount)
                        put("bleMaxRssi", s.bleMaxRssi)
                        put("bleTotalMw", s.bleTotalMw)
                        put("acMag60HzRmsUt", s.acMag60HzRmsUt)
                        put("dcMagAnomalyUt", s.dcMagAnomalyUt)
                        put("compositeIndex", s.compositeIndex)
                        val top3Arr = JSONArray()
                        for (ap in s.wifiTop3) {
                            top3Arr.put(JSONObject().apply {
                                put("ssid", ap.ssid)
                                put("bssid", ap.bssid)
                                put("rssiDbm", ap.rssiDbm)
                                put("frequencyMhz", ap.frequencyMhz)
                                put("band", ap.band.label)
                                put("powerMw", ap.powerMw)
                            })
                        }
                        put("wifiTop3", top3Arr)
                    }
                    arr.put(obj)
                }
                put("samples", arr)
            }

            val fileName = "RFMagMapper_Data_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.json"
            val jsonBytes = root.toString(2).toByteArray(Charsets.UTF_8)

            val values = ContentValues().apply {
                put(MediaStore.Files.getContentUri("external").let { MediaStore.MediaColumns.DISPLAY_NAME }, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/RFMagMapper")
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(jsonBytes) }
            }

            val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cacheFile = File(cacheDir, fileName)
            FileOutputStream(cacheFile).use { it.write(jsonBytes) }
            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)

            ExportResult(
                success = true,
                displayPath = "Documents/RFMagMapper/$fileName",
                shareUri = shareUri
            )
        } catch (e: Exception) {
            ExportResult(success = false, displayPath = "", errorMessage = e.message)
        }
    }

    fun shareFile(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "匯出分享"))
    }

    fun heatColorArgb(tRaw: Float, alpha: Int = 180): Int {
        val t = tRaw.coerceIn(0f, 1f)
        val r: Int
        val g: Int
        val b: Int
        if (t < 0.5f) {
            // Green (#00E676) -> Yellow (#FFEA00)
            val k = t / 0.5f
            r = (0 + (255 - 0) * k).toInt()
            g = (230 + (234 - 230) * k).toInt()
            b = (118 + (0 - 118) * k).toInt()
        } else {
            // Yellow (#FFEA00) -> Red (#FF1744)
            val k = (t - 0.5f) / 0.5f
            r = 255
            g = (234 + (23 - 234) * k).toInt()
            b = (0 + (68 - 0) * k).toInt()
        }
        return Color.argb(alpha, r, g, b)
    }
}
