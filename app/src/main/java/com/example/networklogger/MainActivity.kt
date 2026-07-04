// MainActivity.kt

package com.example.networklogger

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.work.WorkManager
import android.content.Context
import android.util.Log
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import android.widget.Toast
import android.os.Environment
import android.content.ContentValues
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

class MainActivity : AppCompatActivity() {

    lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationText: TextView
    private lateinit var networkText: TextView
    private lateinit var signalText: TextView
    private lateinit var cellInfoText: TextView
    private lateinit var rootStatusText: TextView
    private lateinit var rootStatusText2: TextView          // ← was missing
    private lateinit var loggingDurationText: TextView
    private lateinit var networkLogPathText: TextView
    private lateinit var backgroundLogPathText: TextView
    private lateinit var telephonyManager: TelephonyManager

    private var currentLat = "N/A"
    private var currentLon = "N/A"
    private var currentSignal = "N/A"
    private var currentNetwork = "N/A"
    private var isLogging = false
    private var currentAltitude = "N/A"
    private var currentSpeed = "N/A"
    private var currentAccuracy = "N/A"
    private var currentTime = "N/A"
    private var currentOperator = "N/A"
    private var currentPCI = "N/A"
    private var currentTAC = "N/A"
    private var currentNCI = "N/A"
    private var currentRSRP = "N/A"
    private var currentRSRQ = "N/A"
    private var currentSINR = "N/A"
    private var currentNeighborCount = "0"
    private var loggingStartTime = 0L

    private val handler = Handler(Looper.getMainLooper())

    private val logRunnable = object : Runnable {
        override fun run() {
            if (!isLogging) return
            updateMeasurements()
            saveLog()
            updateLoggingDuration()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationText        = findViewById(R.id.locationText)
        networkText         = findViewById(R.id.networkText)
        signalText          = findViewById(R.id.signalText)
        cellInfoText        = findViewById(R.id.cellInfoText)
        rootStatusText      = findViewById(R.id.rootStatusText)
        rootStatusText2     = findViewById(R.id.rootStatusText2)   // ← now bound
        loggingDurationText = findViewById(R.id.loggingDurationText)
        networkLogPathText  = findViewById(R.id.networkLogPathText)
        backgroundLogPathText = findViewById(R.id.backgroundLogPathText)

        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton  = findViewById<Button>(R.id.stopButton)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        telephonyManager    = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // ── ROOT DETECTION ──────────────────────────────────────────────
        applyRootDetection()
        // ────────────────────────────────────────────────────────────────

        networkLogPathText.text =
            "network_logs.csv  →  /storage/emulated/0/Download/network_logs.csv"
        backgroundLogPathText.text =
            "background_logs.csv  →  /storage/emulated/0/Download/background_logs.csv"

        startButton.setOnClickListener {
            if (!isLogging) {
                loggingStartTime = System.currentTimeMillis()
                isLogging = true
                loggingDurationText.text = "00:00:00"
                Toast.makeText(this, "Logs are started", Toast.LENGTH_SHORT).show()
                handler.post(logRunnable)
            }
        }

        stopButton.setOnClickListener {
            if (isLogging) {
                isLogging = false
                handler.removeCallbacks(logRunnable)
                val elapsedMillis = System.currentTimeMillis() - loggingStartTime
                val seconds = (elapsedMillis / 1000) % 60
                val minutes = (elapsedMillis / (1000 * 60)) % 60
                val hours   = elapsedMillis / (1000 * 60 * 60)
                signalText.text = String.format("Stopped after %02d:%02d:%02d", hours, minutes, seconds)
            }
        }

        requestPermissions()
        setupSignalListener()
        requestBatteryOptimizationExemption()
        startBackgroundMonitoring()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ROOT DETECTION — runs all 7 checks, updates both badges with reason list
    // ─────────────────────────────────────────────────────────────────────────

    private fun applyRootDetection() {
        // Run each check independently so we can report which ones fired
        val detectionReasons = mutableListOf<String>()
        if (checkSuBinaries())       detectionReasons.add("su binary")
        if (checkRootPackages())     detectionReasons.add("root app")
        if (checkBuildTags())        detectionReasons.add("test-keys")
        if (checkDangerousProps())   detectionReasons.add("sys props")
        if (checkRWPaths())          detectionReasons.add("rw /system")
        if (checkRootCloakingApps()) detectionReasons.add("cloak app")
        if (tryExecuteSu())          detectionReasons.add("su exec")

        val isRooted = detectionReasons.isNotEmpty()

        Log.d("RootCheck", "Rooted: $isRooted | Triggers: $detectionReasons")

        if (isRooted) {
            // Header badge → red
            rootStatusText.text = "Rooted"
            rootStatusText.setTextColor(Color.parseColor("#EF4444"))
            rootStatusText.background =
                ContextCompat.getDrawable(this, R.drawable.badge_red)

            // Device card → shows which method triggered
            rootStatusText2.text = "Rooted\n(${detectionReasons.joinToString(", ")})"
            rootStatusText2.setTextColor(Color.parseColor("#EF4444"))

        } else {
            // Header badge → green (default)
            rootStatusText.text = "Non-Rooted"
            rootStatusText.setTextColor(Color.parseColor("#10B981"))
            rootStatusText.background =
                ContextCompat.getDrawable(this, R.drawable.badge_green)

            // Device card
            rootStatusText2.text = "Non-Rooted"
            rootStatusText2.setTextColor(Color.parseColor("#10B981"))
        }
    }

    private fun isDeviceRooted(): Boolean {
        return checkSuBinaries()
                || checkRootPackages()
                || checkBuildTags()
                || checkDangerousProps()
                || checkRWPaths()
                || checkRootCloakingApps()
                || tryExecuteSu()
    }

    private fun checkSuBinaries(): Boolean {
        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su",
            "/system/bin/.ext/su", "/system/usr/we-need-root/su",
            "/system/app/Superuser.apk", "/data/local/su",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su"
        )
        for (path in paths) {
            try {
                if (File(path).exists()) {
                    Log.d("RootCheck", "Su binary found at: $path")
                    return true
                }
            } catch (e: Exception) {
                Log.w("RootCheck", "Cannot check $path: ${e.message}")
            }
        }
        return false
    }

    private fun checkRootPackages(): Boolean {
        val rootPackages = arrayOf(
            "com.topjohnwu.magisk",
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot"
        )
        for (pkg in rootPackages) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                Log.d("RootCheck", "Root package found: $pkg")
                return true
            } catch (e: Exception) { }
        }
        return false
    }

    private fun checkBuildTags(): Boolean {
        return try {
            val tags = android.os.Build.TAGS
            val isTestKeys = tags != null && tags.contains("test-keys")
            if (isTestKeys) Log.d("RootCheck", "test-keys build tag found")
            isTestKeys
        } catch (e: Exception) { false }
    }

    private fun checkDangerousProps(): Boolean {
        val dangerousProps = mapOf(
            "ro.debuggable"    to "1",
            "ro.secure"        to "0",
            "ro.build.type"    to "eng",
            "service.adb.root" to "1"
        )
        for ((prop, dangerousValue) in dangerousProps) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("getprop", prop))
                val value = process.inputStream.bufferedReader().readLine()?.trim()
                if (value == dangerousValue) {
                    Log.d("RootCheck", "Dangerous prop: $prop = $value")
                    return true
                }
            } catch (e: Exception) {
                Log.w("RootCheck", "Cannot read prop $prop: ${e.message}")
            }
        }
        return false
    }

    private fun checkRWPaths(): Boolean {
        val rwPaths = arrayOf(
            "/system", "/system/bin", "/system/sbin",
            "/system/xbin", "/vendor/bin", "/sbin", "/etc"
        )
        return try {
            val process = Runtime.getRuntime().exec("mount")
            val output = process.inputStream.bufferedReader().readText()
            rwPaths.any { path ->
                val regex = Regex("""$path\s+\S+\s+\S+\s+\(rw""")
                regex.containsMatchIn(output).also {
                    if (it) Log.d("RootCheck", "RW path found: $path")
                }
            }
        } catch (e: Exception) {
            Log.w("RootCheck", "mount check failed: ${e.message}")
            false
        }
    }

    private fun checkRootCloakingApps(): Boolean {
        val cloakPackages = arrayOf(
            "com.devadvance.rootcloak",
            "com.devadvance.rootcloakplus",
            "de.robv.android.xposed.installer",
            "com.saurik.substrate",
            "com.zachspong.temprootremovejb",
            "com.amphoras.hidemyroot",
            "com.formyhm.hiderootPremium",
            "com.amphoras.hidemyrootadfree"
        )
        for (pkg in cloakPackages) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                Log.d("RootCheck", "Cloaking app found: $pkg")
                return true
            } catch (e: Exception) { }
        }
        return false
    }

    private fun tryExecuteSu(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readLine() ?: ""
            val isRoot = output.contains("uid=0")
            if (isRoot) Log.d("RootCheck", "su execution succeeded: $output")
            isRoot
        } catch (e: Exception) {
            Log.w("RootCheck", "su execution blocked (expected on non-rooted): ${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REST OF MAINACTIVITY (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    private fun requestBatteryOptimizationExemption() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    ).apply { data = android.net.Uri.parse("package:$packageName") }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.w("BatteryOpt", "Battery optimization exemption not available: ${e.message}")
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        ActivityCompat.requestPermissions(this, permissions, 1)
    }

    private fun getNeighborCellInfo() {
        try {
            val cellInfoList = telephonyManager.allCellInfo
            if (cellInfoList.isNullOrEmpty()) {
                Log.d("NeighborCells", "No neighbor cells found")
                return
            }
            Log.d("NeighborCells", "Total Cells Found: ${cellInfoList.size}")
            for (cellInfo in cellInfoList) {
                when (cellInfo) {
                    is android.telephony.CellInfoLte -> {
                        Log.d("NeighborCells", "LTE PCI: ${cellInfo.cellIdentity.pci}")
                        Log.d("NeighborCells", "LTE RSRP: ${cellInfo.cellSignalStrength.rsrp}")
                    }
                    is android.telephony.CellInfoNr -> {
                        val identity = cellInfo.cellIdentity as CellIdentityNr
                        val signal   = cellInfo.cellSignalStrength as CellSignalStrengthNr
                        Log.d("NeighborCells", "5G PCI: ${identity.pci}")
                        Log.d("NeighborCells", "5G RSRP: ${signal.ssRsrp}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NeighborCells", "Error collecting neighbor cells", e)
        }
    }

    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null
        ).addOnSuccessListener { location ->
            if (location != null) {
                currentLat      = "%.7f".format(location.latitude)
                currentLon      = "%.7f".format(location.longitude)
                currentAltitude = "%.1f".format(location.altitude)
                currentSpeed    = "%.2f".format(location.speed)
                currentAccuracy = "%.1f".format(location.accuracy)
                locationText.text = "Location: $currentLat , $currentLon"
                Log.d("GPS", "Location: $currentLat, $currentLon")
            }
        }.addOnFailureListener { e ->
            Log.w("GPS", "Location fetch failed: ${e.message}")
        }
    }

    private fun getReadableNetworkType(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE   -> "2G"
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP  -> "3G"
            TelephonyManager.NETWORK_TYPE_LTE    -> "4G"
            TelephonyManager.NETWORK_TYPE_NR     -> "5G"
            else -> "Unknown"
        }
    }

    private fun updateMeasurements() {
        getLocation()
        currentNetwork = try {
            getReadableNetworkType(telephonyManager.networkType)
        } catch (e: Exception) {
            Log.w("Network", "Cannot read networkType: ${e.message}")
            "Unknown"
        }
        networkText.text = "Network: $currentNetwork"
        currentOperator = try { telephonyManager.networkOperatorName } catch (e: Exception) { "Unknown" }
        currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        getCellTowerInfo()
        getNeighborCellInfo()
    }

    private fun setupSignalListener() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                telephonyManager.registerTelephonyCallback(
                    mainExecutor,
                    object : android.telephony.TelephonyCallback(),
                        android.telephony.TelephonyCallback.SignalStrengthsListener {
                        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                            if (!isLogging) {
                                signalText.text = "Signal: " + when (signalStrength.level) {
                                    0 -> "Very Weak"; 1 -> "Weak"; 2 -> "Medium"
                                    3 -> "Strong";   4 -> "Very Strong"; else -> "Unknown"
                                }
                            }
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(
                    object : PhoneStateListener() {
                        @Suppress("DEPRECATION")
                        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                            super.onSignalStrengthsChanged(signalStrength)
                            if (!isLogging) {
                                signalText.text = "Signal: " + when (signalStrength.level) {
                                    0 -> "Very Weak"; 1 -> "Weak"; 2 -> "Medium"
                                    3 -> "Strong";   4 -> "Very Strong"; else -> "Unknown"
                                }
                            }
                        }
                    },
                    @Suppress("DEPRECATION")
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                )
            }
        } catch (e: Exception) {
            Log.w("SignalListener", "Signal listener setup failed: ${e.message}")
        }
    }

    private fun getCellTowerInfo() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            cellInfoText.text = "Cell Info: Location permission required."
            return
        }
        try {
            val cellInfos = telephonyManager.allCellInfo
            if (cellInfos.isNullOrEmpty()) {
                cellInfoText.text = "Cell Info: Not Available"
                return
            }
            when (val cellInfo = cellInfos[0]) {
                is CellInfoLte -> {
                    val identity = cellInfo.cellIdentity
                    val signal   = cellInfo.cellSignalStrength
                    val rsrp = signal.rsrp
                    val rsrq = if (signal.rsrq == Int.MAX_VALUE) "N/A" else "${signal.rsrq} dB"
                    currentSignal       = getRsrpSignalLabel(rsrp)
                    currentPCI          = identity.pci.toString()
                    currentTAC          = identity.tac.toString()
                    currentNCI          = identity.ci.toString()
                    currentRSRP         = rsrp.toString()
                    currentRSRQ         = if (signal.rsrq == Int.MAX_VALUE) "N/A" else signal.rsrq.toString()
                    currentSINR         = "N/A"
                    currentNeighborCount = cellInfos.size.toString()   // ← fixed: use cached list
                    cellInfoText.text =
                        "MCC: ${identity.mccString}\nMNC: ${identity.mncString}\n" +
                                "CID: ${identity.ci}\nTAC: ${identity.tac}\n" +
                                "EARFCN: ${identity.earfcn}\nRSRP: $rsrp dBm\nRSRQ: $rsrq"
                }
                is CellInfoNr -> {
                    val identity = cellInfo.cellIdentity as CellIdentityNr
                    val signal   = cellInfo.cellSignalStrength as CellSignalStrengthNr
                    val rsrq = when {
                        signal.ssRsrq  != Int.MAX_VALUE -> "${signal.ssRsrq} dB"
                        signal.csiRsrq != Int.MAX_VALUE -> "${signal.csiRsrq} dB"
                        else -> "N/A"
                    }
                    val snr = when {
                        signal.ssSinr  != Int.MAX_VALUE -> "${signal.ssSinr} dB"
                        signal.csiSinr != Int.MAX_VALUE -> "${signal.csiSinr} dB"
                        else -> "N/A"
                    }
                    currentSignal        = getRsrpSignalLabel(signal.dbm)
                    currentPCI           = identity.pci.toString()
                    currentTAC           = identity.tac.toString()
                    currentNCI           = identity.nci.toString()
                    currentRSRP          = signal.dbm.toString()
                    currentRSRQ          = rsrq.replace(" dB", "")
                    currentSINR          = snr.replace(" dB", "")
                    currentNeighborCount = cellInfos.size.toString()   // ← fixed: use cached list

                    val neighborDetails = StringBuilder()
                    for ((index, info) in cellInfos.withIndex()) {
                        when (info) {
                            is CellInfoNr -> {
                                val id  = info.cellIdentity as CellIdentityNr
                                val sig = info.cellSignalStrength as CellSignalStrengthNr
                                neighborDetails.append("\nNeighbor ${index + 1}\nPCI: ${id.pci}\nNCI: ${id.nci}\nRSRP: ${sig.dbm} dBm\n")
                            }
                            is CellInfoLte -> {
                                val id  = info.cellIdentity
                                val sig = info.cellSignalStrength
                                neighborDetails.append("\nNeighbor ${index + 1}\nPCI: ${id.pci}\nCID: ${id.ci}\nRSRP: ${sig.rsrp} dBm\n")
                            }
                        }
                    }
                    cellInfoText.text =
                        "5G Cell\nNCI: ${identity.nci}\nPCI: ${identity.pci}\n" +
                                "TAC: ${identity.tac}\nRSRP: ${signal.dbm} dBm\n" +
                                "MCC: ${identity.mccString}\nMNC: ${identity.mncString}\n" +
                                "Operator: $currentOperator\nNRARFCN: ${identity.nrarfcn}\n" +
                                "RSRQ: $rsrq\nSINR: $snr\nTime: $currentTime\n" +
                                "Altitude: $currentAltitude m\nSpeed: $currentSpeed m/s\n" +
                                "GPS Accuracy: $currentAccuracy m\nNeighbor Cells: ${cellInfos.size}\n" +
                                "Level: ${signal.level}\n$neighborDetails"
                }
                else -> cellInfoText.text = "Cell Info: Not Available"
            }
        } catch (e: Exception) {
            Log.e("CellInfo", "getCellTowerInfo failed: ${e.message}", e)
            cellInfoText.text = "Cell Info: Error reading data"
        }
    }

    private fun getRsrpSignalLabel(rsrp: Int): String {
        return when {
            rsrp >= -80  -> "Very Strong"
            rsrp >= -90  -> "Strong"
            rsrp >= -100 -> "Medium"
            rsrp >= -110 -> "Weak"
            else         -> "Very Weak"
        }
    }

    private fun startBackgroundMonitoring() {
        val immediateRequest = androidx.work.OneTimeWorkRequestBuilder<NetworkWorker>().build()
        WorkManager.getInstance(this).enqueue(immediateRequest)

        val periodicRequest = PeriodicWorkRequestBuilder<NetworkWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "NetworkMonitoringWorker",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    private fun updateLoggingDuration() {
        if (!isLogging) return
        val elapsedMillis = System.currentTimeMillis() - loggingStartTime
        loggingDurationText.text = String.format(
            "%02d:%02d:%02d",
            elapsedMillis / (1000 * 60 * 60),
            (elapsedMillis / (1000 * 60)) % 60,
            (elapsedMillis / 1000) % 60
        )
    }

    private fun saveLog() {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logLine = "$time,$currentLat,$currentLon,$currentNetwork,$currentSignal," +
                "$currentOperator,$currentPCI,$currentTAC,$currentNCI," +
                "$currentRSRP,$currentRSRQ,$currentSINR,$currentNeighborCount\n"
        val header = "time,lat,lon,network,signal,operator,pci,tac,nci,rsrp,rsrq,sinr,neighborCount\n"
        try {
            val fileName   = "network_logs.csv"
            val resolver   = contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val existingUri: Uri? = resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.IS_PENDING} = 0",
                arrayOf(fileName), null
            )?.use { cursor ->
                if (cursor.moveToFirst())
                    ContentUris.withAppendedId(
                        collection,
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    )
                else null
            }
            if (existingUri != null) {
                val stream = resolver.openOutputStream(existingUri, "wa")
                if (stream != null) {
                    stream.use { it.write(logLine.toByteArray()) }
                } else {
                    resolver.delete(existingUri, null, null)
                    createNewCsvFile(resolver, collection, fileName, header, logLine)
                }
            } else {
                createNewCsvFile(resolver, collection, fileName, header, logLine)
            }
        } catch (e: Exception) {
            Log.e("CSV", "saveLog() failed: ${e.message}", e)
        }
    }

    private fun createNewCsvFile(
        resolver: android.content.ContentResolver,
        collection: Uri,
        fileName: String,
        header: String,
        firstRow: String
    ) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(collection, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { it.write(header.toByteArray()); it.write(firstRow.toByteArray()) }
            Log.d("CSV", "Created new file: $uri")
        } else {
            Log.e("CSV", "Failed to insert new file into MediaStore")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(logRunnable)
    }
}