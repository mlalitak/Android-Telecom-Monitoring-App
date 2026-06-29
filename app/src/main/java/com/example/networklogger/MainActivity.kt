package com.example.networklogger

import android.Manifest
import android.content.pm.PackageManager
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
//import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
//import java.util.concurrent.TimeUnit
//import androidx.work.OneTimeWorkRequestBuilder
import android.content.Context
import android.util.Log
//import com.github.mikephil.charting.charts.LineChart
//import com.github.mikephil.charting.data.Entry
//import com.github.mikephil.charting.data.LineData
//import com.github.mikephil.charting.data.LineDataSet
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import android.widget.Toast
import android.os.Environment
import android.content.ContentValues
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
//import androidx.work.OutOfQuotaPolicy

class MainActivity : AppCompatActivity() {

    lateinit var fusedLocationClient: FusedLocationProviderClient
//    lateinit var telephonyManager: TelephonyManager

    private lateinit var locationText: TextView
    private lateinit var networkText: TextView
    private lateinit var signalText: TextView

    private lateinit var cellInfoText: TextView

    private lateinit var rootStatusText: TextView

    private lateinit var loggingDurationText: TextView

    private lateinit var networkLogPathText: TextView
    private lateinit var backgroundLogPathText: TextView

    private lateinit var telephonyManager: TelephonyManager

//    private lateinit var signalChart: LineChart



    private var currentLat = "N/A"
    private var currentLon = "N/A"
    private var currentSignal = "N/A"

//    private var currentDbm = -100

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

//    private var chartX = 0f

    private val handler = Handler(Looper.getMainLooper())
//    private val signalEntries = ArrayList<Entry>()


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


//        val periodicWorkRequest =
//            PeriodicWorkRequestBuilder<NetworkWorker>(
//                15,
//                TimeUnit.MINUTES
//            ).build()
//
//        WorkManager.getInstance(this)
//            .enqueue(periodicWorkRequest)

//        val workRequest =
//            OneTimeWorkRequestBuilder<NetworkWorker>()
//                .build()
//
//        WorkManager.getInstance(this)
//            .enqueue(workRequest)




        locationText = findViewById(R.id.locationText)
        networkText = findViewById(R.id.networkText)
        signalText = findViewById(R.id.signalText)
        cellInfoText = findViewById(R.id.cellInfoText)
        rootStatusText = findViewById(R.id.rootStatusText)

        networkLogPathText =
            findViewById(R.id.networkLogPathText)

        backgroundLogPathText =
            findViewById(R.id.backgroundLogPathText)

//        val downloadsFolder =
//            getExternalFilesDir(null)

//        val networkLogPath =
//            File(downloadsFolder, "network_logs.csv").absolutePath
//
//        val backgroundLogPath =
//            File(downloadsFolder, "background_logs.csv").absolutePath
//
//        val appFilesDir = getExternalFilesDir(null)


        loggingDurationText =
            findViewById(R.id.loggingDurationText)

        val startButton = findViewById<Button>(R.id.startButton)

        val stopButton = findViewById<Button>(R.id.stopButton)

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        telephonyManager =
            getSystemService(Context.TELEPHONY_SERVICE)
                    as TelephonyManager

        val rootStatus =
            if (isDeviceRooted())
                "Rooted"
            else
                "Non-Rooted"

        rootStatusText.text =
            "Device Status: $rootStatus"

        Log.d("RootCheck", "Device Status: $rootStatus")

//        val dir = getExternalFilesDir(null)

        networkLogPathText.text =
            "network_logs.csv:\n/storage/emulated/0/Download/network_logs.csv"

        backgroundLogPathText.text =
            "background_logs.csv:\n/storage/emulated/0/Download/background_logs.csv"

        startButton.setOnClickListener {
            if (!isLogging) {

                loggingStartTime =
                    System.currentTimeMillis()

                isLogging = true
                Toast.makeText(
                    this,
                    "Logs are started",
                    Toast.LENGTH_SHORT
                ).show()
//                updateMeasurements()
//                saveLog()
                handler.post(logRunnable)
            }
        }


        stopButton.setOnClickListener {

            if (isLogging) {

                isLogging = false

                handler.removeCallbacks(logRunnable)

                val elapsedMillis =
                    System.currentTimeMillis() - loggingStartTime

                val seconds = (elapsedMillis / 1000) % 60
                val minutes = (elapsedMillis / (1000 * 60)) % 60
                val hours = elapsedMillis / (1000 * 60 * 60)

                signalText.text =
                    String.format(
                        "Stopped after %02d:%02d:%02d",
                        hours,
                        minutes,
                        seconds
                    )
            }
        }



        requestPermissions()
        setupSignalListener()
        requestBatteryOptimizationExemption()
        showRealmeBatteryDialog()
        startBackgroundMonitoring()

//        signalChart = findViewById(R.id.signalChart)
//
//        signalChart.description.isEnabled = false
//
//        signalChart.setTouchEnabled(true)
//
//        signalChart.setPinchZoom(true)
//
//        signalChart.axisRight.isEnabled = false
//
//        signalChart.xAxis.granularity = 1f
//
//        signalChart.axisLeft.axisMinimum = 0f
//
//        signalChart.axisLeft.axisMaximum = 4f
    }



    private fun requestBatteryOptimizationExemption() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    ).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                // Rooted ROMs with custom power managers may not support this intent
                Log.w("BatteryOpt", "Battery optimization exemption not available: ${e.message}")
            }
        }
    }


    private fun showRealmeBatteryDialog() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val shown = prefs.getBoolean("autostart_dialog_shown", false)

        if (!shown) {
            try {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Enable Background Logging")
                    .setMessage(
                        "For background_logs.csv to save properly:\n\n" +
                                "1. Go to Phone Settings\n" +
                                "2. Search 'Autostart'\n" +
                                "3. Enable Autostart for NetworkLogger\n\n" +
                                "Also go to Battery → App Battery Saver → Set NetworkLogger to 'No Restrictions'"
                    )
                    .setPositiveButton("Open Settings") { _, _ ->
                        try {
                            startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                            )
                        } catch (e: Exception) {
                            Log.w("Dialog", "Could not open settings: ${e.message}")
                        }
                    }
                    .setNegativeButton("Later", null)
                    .show()
            } catch (e: Exception) {
                Log.w("Dialog", "Could not show dialog: ${e.message}")
            }

            prefs.edit().putBoolean("autostart_dialog_shown", true).apply()
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )

        ActivityCompat.requestPermissions(this, permissions, 1)
    }


    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su"
        )
        for (path in paths) {
            try {
                if (File(path).exists()) return true
            } catch (e: Exception) {
                Log.w("RootCheck", "Cannot check path $path: ${e.message}")
            }
        }

        // Check Magisk
        try {
            packageManager.getPackageInfo("com.topjohnwu.magisk", 0)
            return true
        } catch (e: Exception) { }

        // Check build tags
        try {
            val buildTags = android.os.Build.TAGS
            if (buildTags != null && buildTags.contains("test-keys")) return true
        } catch (e: Exception) { }

        return false
    }


    private fun getNeighborCellInfo() {

        try {

            val cellInfoList = telephonyManager.allCellInfo

            if (cellInfoList.isNullOrEmpty()) {

                Log.d("NeighborCells", "No neighbor cells found")
                return
            }

            Log.d(
                "NeighborCells",
                "Total Cells Found: ${cellInfoList.size}"
            )

            for (cellInfo in cellInfoList) {

                when (cellInfo) {

                    is android.telephony.CellInfoLte -> {

                        val identity = cellInfo.cellIdentity
                        val signal = cellInfo.cellSignalStrength

                        Log.d(
                            "NeighborCells",
                            "LTE PCI: ${identity.pci}"
                        )

                        Log.d(
                            "NeighborCells",
                            "LTE RSRP: ${signal.rsrp}"
                        )
                    }

                    is android.telephony.CellInfoNr -> {

                        val identity = cellInfo.cellIdentity as CellIdentityNr
                        val signal = cellInfo.cellSignalStrength as CellSignalStrengthNr

                        Log.d(
                            "NeighborCells",
                            "5G PCI: ${identity.pci}"
                        )

                        Log.d(
                            "NeighborCells",
                            "5G RSRP: ${signal.ssRsrp}"
                        )
                    }
                }
            }

        } catch (e: Exception) {

            Log.e(
                "NeighborCells",
                "Error collecting neighbor cells",
                e
            )
        }
    }


    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        // Use getCurrentLocation() — only fires once, no background trigger
        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { location ->
            if (location != null) {
                currentLat = "%.7f".format(location.latitude)
                currentLon = "%.7f".format(location.longitude)
                currentAltitude = "%.1f".format(location.altitude)
                currentSpeed = "%.2f".format(location.speed)
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
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G"

            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"

            TelephonyManager.NETWORK_TYPE_LTE -> "4G"

            TelephonyManager.NETWORK_TYPE_NR -> "5G"

            else -> "Unknown"
        }
    }

    private fun updateMeasurements() {
        getLocation()

        // Wrap networkType read — can throw SecurityException on rooted OnePlus
        currentNetwork = try {
            getReadableNetworkType(telephonyManager.networkType)
        } catch (e: Exception) {
            Log.w("Network", "Cannot read networkType: ${e.message}")
            "Unknown"
        }

        networkText.text = "Network: $currentNetwork"

        currentOperator = try {
            telephonyManager.networkOperatorName
        } catch (e: Exception) { "Unknown" }

        currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        getCellTowerInfo()
        getNeighborCellInfo()
    }

    private fun setupSignalListener() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Android 12+ — use new TelephonyCallback API
                val executor = mainExecutor
                telephonyManager.registerTelephonyCallback(
                    executor,
                    object : android.telephony.TelephonyCallback(),
                        android.telephony.TelephonyCallback.SignalStrengthsListener {
                        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
//                            updateSignalChart(currentDbm)
                            if (!isLogging) {
                                val level = signalStrength.level
                                signalText.text = "Signal: " + when (level) {
                                    0 -> "Very Weak"
                                    1 -> "Weak"
                                    2 -> "Medium"
                                    3 -> "Strong"
                                    4 -> "Very Strong"
                                    else -> "Unknown"
                                }
                            }
                        }
                    }
                )
            } else {
                // Android 11 and below — use old PhoneStateListener
                @Suppress("DEPRECATION")
                telephonyManager.listen(
                    object : PhoneStateListener() {
                        @Suppress("DEPRECATION")
                        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                            super.onSignalStrengthsChanged(signalStrength)
//                            updateSignalChart(currentDbm)
                            if (!isLogging) {
                                val level = signalStrength.level
                                signalText.text = "Signal: " + when (level) {
                                    0 -> "Very Weak"
                                    1 -> "Weak"
                                    2 -> "Medium"
                                    3 -> "Strong"
                                    4 -> "Very Strong"
                                    else -> "Unknown"
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
            // App continues without signal listener — not fatal
        }
    }


    private fun getCellTowerInfo() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
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

            val cellInfo = cellInfos[0]

            when (cellInfo) {
                is CellInfoLte -> {
                    val identity = cellInfo.cellIdentity
                    val signal = cellInfo.cellSignalStrength

                    val mcc = identity.mccString
                    val mnc = identity.mncString
                    val ci = identity.ci
                    val tac = identity.tac
                    val earfcn = identity.earfcn

                    val rsrp = signal.rsrp
                    val rsrq = if (signal.rsrq == Int.MAX_VALUE) "N/A" else "${signal.rsrq} dB"

//                    currentDbm = rsrp
                    currentSignal = getRsrpSignalLabel(signal.rsrp)

                    currentPCI = identity.pci.toString()
                    currentTAC = tac.toString()
                    currentNCI = ci.toString()
                    currentRSRP = rsrp.toString()
                    currentRSRQ = rsrq.replace(" dB", "")
                    currentSINR = "N/A"
                    currentNeighborCount = telephonyManager.allCellInfo.size.toString()

                    cellInfoText.text =
                        "MCC: $mcc\n" +
                                "MNC: $mnc\n" +
                                "CID: $ci\n" +
                                "TAC: $tac\n" +
                                "EARFCN: $earfcn\n" +
                                "RSRP: $rsrp dBm\n" +
                                "RSRQ: $rsrq"
                }

                is CellInfoNr -> {
                    val identity = cellInfo.cellIdentity as CellIdentityNr
                    val signal = cellInfo.cellSignalStrength as CellSignalStrengthNr

//                    currentDbm = signal.dbm

                    val mcc = identity.mccString
                    val mnc = identity.mncString
                    val nrarfcn = identity.nrarfcn

                    val rsrq = when {
                        signal.ssRsrq != Int.MAX_VALUE -> "${signal.ssRsrq} dB"
                        signal.csiRsrq != Int.MAX_VALUE -> "${signal.csiRsrq} dB"
                        else -> "N/A"
                    }

                    val snr = when {
                        signal.ssSinr != Int.MAX_VALUE -> "${signal.ssSinr} dB"
                        signal.csiSinr != Int.MAX_VALUE -> "${signal.csiSinr} dB"
                        else -> "N/A"
                    }

                    val neighborCount = telephonyManager.allCellInfo.size

                    currentSignal = getRsrpSignalLabel(signal.dbm)
                    currentPCI = identity.pci.toString()
                    currentTAC = identity.tac.toString()
                    currentNCI = identity.nci.toString()
                    currentRSRP = signal.dbm.toString()
                    currentRSRQ = rsrq.replace(" dB", "")
                    currentSINR = snr.replace(" dB", "")
                    currentNeighborCount = neighborCount.toString()

                    val neighborDetails = StringBuilder()

                    for ((index, info) in telephonyManager.allCellInfo.withIndex()) {
                        when (info) {
                            is CellInfoNr -> {
                                val id = info.cellIdentity as CellIdentityNr
                                val sig = info.cellSignalStrength as CellSignalStrengthNr

                                neighborDetails.append(
                                    "\nNeighbor ${index + 1}\n" +
                                            "PCI: ${id.pci}\n" +
                                            "NCI: ${id.nci}\n" +
                                            "RSRP: ${sig.dbm} dBm\n"
                                )
                            }

                            is CellInfoLte -> {
                                val id = info.cellIdentity
                                val sig = info.cellSignalStrength

                                neighborDetails.append(
                                    "\nNeighbor ${index + 1}\n" +
                                            "PCI: ${id.pci}\n" +
                                            "CID: ${id.ci}\n" +
                                            "RSRP: ${sig.rsrp} dBm\n"
                                )
                            }
                        }
                    }

                    cellInfoText.text =
                        "5G Cell\n" +
                                "NCI: ${identity.nci}\n" +
                                "PCI: ${identity.pci}\n" +
                                "TAC: ${identity.tac}\n" +
                                "RSRP: ${signal.dbm} dBm\n" +
                                "MCC: $mcc\n" +
                                "MNC: $mnc\n" +
                                "Operator: $currentOperator\n" +
                                "NRARFCN: $nrarfcn\n" +
                                "RSRQ: $rsrq\n" +
                                "SINR: $snr\n" +
                                "Time: $currentTime\n" +
                                "Altitude: $currentAltitude m\n" +
                                "Speed: $currentSpeed m/s\n" +
                                "GPS Accuracy: $currentAccuracy m\n" +
                                "Neighbor Cells: $neighborCount\n" +
                                "Level: ${signal.level}\n" +
                                neighborDetails.toString()
                }

                else -> {
                    cellInfoText.text = "Cell Info: Not Available"
                }
            }

        } catch (e: Exception) {
            Log.e("CellInfo", "getCellTowerInfo failed: ${e.message}", e)
            cellInfoText.text = "Cell Info: Error reading data"
        }
    }




    // Add this helper function to MainActivity.kt
    private fun getRsrpSignalLabel(rsrp: Int): String {
        return when {
            rsrp >= -80  -> "Very Strong"
            rsrp >= -90  -> "Strong"
            rsrp >= -100 -> "Medium"
            rsrp >= -110 -> "Weak"
            else         -> "Very Weak"
        }
    }




//    private fun updateSignalChart(dbm: Int) {
//
//        signalEntries.add(
//            Entry(chartX, dbm.toFloat())
//        )
//
//        chartX++
//
//        val dataSet = LineDataSet(
//            signalEntries,
//            "Signal dBm"
//        )
//
//        dataSet.lineWidth = 2f
//
//        dataSet.circleRadius = 2f
//
//        dataSet.setDrawValues(false)
//
//        val lineData = LineData(dataSet)
//
//        signalChart.data = lineData
//
//        signalChart.description.isEnabled = false
//
//        signalChart.axisRight.isEnabled = false
//
//        signalChart.axisLeft.axisMinimum = -130f
//
//        signalChart.axisLeft.axisMaximum = -50f
//
//        signalChart.setVisibleXRangeMaximum(20f)
//
//        signalChart.moveViewToX(chartX)
//
//        signalChart.invalidate()
//    }


    private fun startBackgroundMonitoring() {
        // Run ONCE immediately on launch — creates background_logs.csv right away
        val immediateRequest = androidx.work.OneTimeWorkRequestBuilder<NetworkWorker>()
            .build()
        WorkManager.getInstance(this).enqueue(immediateRequest)

        // Then continue running every 15 minutes in background
        val periodicRequest = PeriodicWorkRequestBuilder<NetworkWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "NetworkMonitoringWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
    }


    private fun updateLoggingDuration() {

        if (!isLogging) return

        val elapsedMillis =
            System.currentTimeMillis() - loggingStartTime

        val seconds = (elapsedMillis / 1000) % 60
        val minutes = (elapsedMillis / (1000 * 60)) % 60
        val hours = elapsedMillis / (1000 * 60 * 60)

        loggingDurationText.text =
            String.format(
                "Logging Duration: %02d:%02d:%02d",
                hours,
                minutes,
                seconds
            )
    }


    private fun saveLog() {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val logLine = "$time,$currentLat,$currentLon,$currentNetwork,$currentSignal," +
                "$currentOperator,$currentPCI,$currentTAC,$currentNCI," +
                "$currentRSRP,$currentRSRQ,$currentSINR,$currentNeighborCount\n"

        val header = "time,lat,lon,network,signal,operator,pci,tac,nci,rsrp,rsrq,sinr,neighborCount\n"

        try {
            val fileName = "network_logs.csv"
            val resolver = contentResolver
            val collection = MediaStore.Downloads.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )

            // Query for existing file
            val existingUri: Uri? = resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?" +
                        " AND ${MediaStore.Downloads.IS_PENDING} = 0",
                arrayOf(fileName),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(
                        cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    )
                    ContentUris.withAppendedId(collection, id)
                } else null
            }

            if (existingUri != null) {
                // File exists — append directly, no IS_PENDING needed
                resolver.openOutputStream(existingUri, "wa")?.use { outputStream ->
                    outputStream.write(logLine.toByteArray())
                }
                Log.d("CSV", "Appended to: $existingUri")
            } else {
                // File does not exist — create WITHOUT IS_PENDING
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    // NO IS_PENDING here
                }
                val uri = resolver.insert(collection, contentValues)!!
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(header.toByteArray())
                    outputStream.write(logLine.toByteArray())
                }
                Log.d("CSV", "Created new file: $uri")
            }

        } catch (e: Exception) {
            Log.e("CSV", "saveLog() failed: ${e.message}", e)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(logRunnable)
    }
}