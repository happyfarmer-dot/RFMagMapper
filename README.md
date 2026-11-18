# RFMagMapper — 室內無線射頻 RF 與 60Hz 低頻磁場熱區測繪儀

專為 **Samsung Galaxy S24 Ultra** (Android 14 / Target SDK 34, Min SDK 31) 打造的原生 Kotlin + Jetpack Compose 室內電磁與射頻環境測繪應用，並附帶 **瀏覽器互動模擬與 JSON 回放分析版**。

- 🌐 **線上瀏覽器版 (GitHub Pages)**：https://happyfarmer-dot.github.io/RFMagMapper/
- 📖 **完整操作說明書**：[MANUAL.md](MANUAL.md)
- 📦 **Android APK 下載 (S24 Ultra)**：[Google Drive (RFMagMapper_S24Ultra.apk)](https://drive.google.com/file/d/1uWd505tMAyAwpCE2Mz0x0q61R0EVrQPR/view?usp=drivesdk)

## 核心五大採集模組
1. **模組 A · Wi-Fi 7 三頻段 (2.4G / 5G / 6GHz) 射頻能量掃描 (`WifiScannerEngine`)**
   - 連續觸發 `WifiManager.startScan()` 並監聽 `SCAN_RESULTS_AVAILABLE_ACTION`。
   - 依物理定義將每顆 AP 的 RSSI 轉換為線性毫瓦功率 $P_{\text{mW}} = 10^{\text{dBm}/10}$ 加總後再轉回等效總功率指數，並列出訊號最強前 3 名熱點與頻段。
2. **模組 B · 藍牙 BLE 環境射頻掃描 (`BleScannerEngine`)**
   - 使用 `SCAN_MODE_LOW_LATENCY` 連續掃描，統計活躍發射源數量、最強單一裝置 RSSI 與線性總功率密度。
3. **模組 C · 三軸磁力計雙通道低頻磁場分析 @ 100 Hz (`MagnetometerEngine`)**
   - 採用 `Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED` (100 Hz / 10,000 μs)。
   - **60 Hz 交流電變壓器／暗管磁場**：利用 100 Hz 採樣率（Nyquist 50 Hz）的混疊效應將 60 Hz 折疊至 40 Hz，套用中心頻率 40 Hz（38–42 Hz）二階 IIR 窄頻帶通濾波器計算 RMS 強度（μT）。
   - **靜態直流磁場異常**：計算相對於初始基準值的總磁通量偏移量 $\Delta B$（μT）。
4. **模組 D · 室內 PDR 航位推算與氣壓樓層偵測 (`PdrBarometerEngine`)**
   - 結合 `TYPE_STEP_DETECTOR`（預設步長 0.65 m 可調）與 `TYPE_GAME_ROTATION_VECTOR`（純陀螺儀+加速度計航向，不受室內磁場干擾），即時計算 `(x, y)` 座標（0.5m × 0.5m 網格）。
   - 透過 `TYPE_PRESSURE` 計算高度變化 $\Delta h$，每 3.2 公尺自動標記樓層切換。
5. **模組 E · 雙頻 GNSS (L1+L5) 戶外基準錨點 (`GnssAnchorEngine`)**
   - 透過 `LocationManager` 與 `GnssMeasurementsEvent` 記錄 L1 + L5 雙頻定位座標與衛星訊噪比（CN0）。

## 視覺化與匯出
- **互動式 2D 熱區地圖 (Compose Canvas)**：反距離加權插值 (IDW)、綠 → 黃 → 紅高斯熱區色塊、即時軌跡線、紅區熱點自動標註。
- **四圖層即時切換**：綜合指數 (0–100) / Wi-Fi RF / BLE 密度 / 60Hz 交流磁場。
- **一鍵匯出**：支援匯出 PNG 熱區圖（`Pictures/RFMagMapper`）與 JSON 原始感測器數據（`Documents/RFMagMapper`）。

## 編譯指令
```bash
./gradlew assembleDebug
```
