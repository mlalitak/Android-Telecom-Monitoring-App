package com.example.networklogger

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.telephony.TelephonyManager
//import android.telephony.PhoneStateListener
//import android.telephony.SignalStrength

class NetworkWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private var batteryPercent = 0

    private var totalStorageGB = 0L
    private var usedStorageGB = 0L
    private var freeStorageGB = 0L

    private var totalRamGB = 0L
    private var usedRamGB = 0L
    private var availableRamGB = 0L

    private var networkType = "Unknown"
    private var operatorName = "Unknown"
    private var signalLevel = "Unknown"

    override fun doWork(): Result {

        Log.d("NetworkWorker", "Background work running")

        logBatteryInfo()

        logStorageInfo()

        logRamInfo()

        logTelecomInfo()

        saveBackgroundLog()

        return Result.success()
    }

    private fun logStorageInfo() {

        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)

        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalStorage = totalBlocks * blockSize
        val freeStorage = availableBlocks * blockSize
        val usedStorage = totalStorage - freeStorage

        totalStorageGB = totalStorage / (1024 * 1024 * 1024)
        freeStorageGB = freeStorage / (1024 * 1024 * 1024)
        usedStorageGB = usedStorage / (1024 * 1024 * 1024)

        Log.d("StorageInfo", "Total Storage: ${totalStorageGB} GB")
        Log.d("StorageInfo", "Used Storage: ${usedStorageGB} GB")
        Log.d("StorageInfo", "Free Storage: ${freeStorageGB} GB")
    }

    private fun logRamInfo() {

        val activityManager =
            applicationContext.getSystemService(Context.ACTIVITY_SERVICE)
                    as ActivityManager

        val memoryInfo = ActivityManager.MemoryInfo()

        activityManager.getMemoryInfo(memoryInfo)

        val totalRam = memoryInfo.totalMem
        val availableRam = memoryInfo.availMem
        val usedRam = totalRam - availableRam

        totalRamGB = totalRam / (1024 * 1024 * 1024)
        availableRamGB = availableRam / (1024 * 1024 * 1024)
        usedRamGB = usedRam / (1024 * 1024 * 1024)

        Log.d("RAMInfo", "Total RAM: ${totalRamGB} GB")
        Log.d("RAMInfo", "Used RAM: ${usedRamGB} GB")
        Log.d("RAMInfo", "Available RAM: ${availableRamGB} GB")
    }


    private fun logBatteryInfo() {

        val batteryIntent = applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_LEVEL,
            -1
        ) ?: -1

        val scale = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_SCALE,
            -1
        ) ?: -1

        batteryPercent = (level * 100) / scale

        Log.d("BatteryInfo", "Battery Percentage: $batteryPercent%")
    }


    private fun logTelecomInfo() {

        val telephonyManager =
            applicationContext.getSystemService(Context.TELEPHONY_SERVICE)
                    as TelephonyManager

        operatorName = telephonyManager.networkOperatorName

        networkType = when (telephonyManager.dataNetworkType) {

            TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "3G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"

            else -> "Unknown"
        }

        signalLevel = "N/A"

        Log.d("TelecomInfo", "Operator: $operatorName")
        Log.d("TelecomInfo", "Network Type: $networkType")
        Log.d("TelecomInfo", "Signal Level: $signalLevel")
    }



    private fun saveBackgroundLog() {

        try {

            val downloadsFolder =
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )

            val file = File(
                downloadsFolder,
                "background_logs.csv"
            )

            if (!file.exists()) {

                file.appendText(
                    "Time,Battery,NetworkType,Operator,SignalLevel,TotalStorage,UsedStorage,FreeStorage,TotalRAM,UsedRAM,AvailableRAM\n"
                )
            }

            val timeStamp = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
            ).format(Date())

            val logData =
                "$timeStamp," +
                        "$batteryPercent," +
                        "$networkType," +
                        "$operatorName," +
                        "$signalLevel," +
                        "$totalStorageGB," +
                        "$usedStorageGB," +
                        "$freeStorageGB," +
                        "$totalRamGB," +
                        "$usedRamGB," +
                        "$availableRamGB\n"

            file.appendText(logData)

            Log.d("CSV", "Background log saved")
            Log.d("CSV", "File Path: ${file.absolutePath}")

        } catch (e: Exception) {

            Log.e("CSV", "Error saving CSV", e)
        }
    }
}