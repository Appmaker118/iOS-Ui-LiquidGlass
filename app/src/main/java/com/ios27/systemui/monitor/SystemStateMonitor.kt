package com.ios27.systemui.monitor

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StatusBarState(
    val timeText: String = "9:41",
    val isDndOn: Boolean = false,
    val hasCellularService: Boolean = false,
    val signalLevel: Int = 0,
    val isMobileDataOn: Boolean = false,
    val networkTypeLabel: String = "",
    val isWifiConnected: Boolean = false,
    val wifiLevel: Int = 0,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val isLowPowerMode: Boolean = false
)

class SystemStateMonitor(
    private val context: Context,
    private val onStateChanged: (StatusBarState) -> Unit
) {
    private var currentState = StatusBarState()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val timeFormat = SimpleDateFormat("H:mm", Locale.getDefault())

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_TIME_TICK,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED -> updateTime()

                Intent.ACTION_BATTERY_CHANGED -> updateBattery(intent)

                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    updateState(currentState.copy(isLowPowerMode = powerManager.isPowerSaveMode))
                }

                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> updateDnd()

                WifiManager.RSSI_CHANGED_ACTION,
                WifiManager.WIFI_STATE_CHANGED_ACTION -> updateWifi()
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshConnectivity()
        override fun onLost(network: Network) = refreshConnectivity()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            refreshConnectivity()
    }

    @Suppress("DEPRECATION")
    private var legacyPhoneListener: PhoneStateListener? = null
    private var modernTelephonyCallback: Any? = null

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            context,
            systemReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
        }

        registerTelephonyListener()

        updateTime()
        updateDnd()
        val batteryIntent = ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (batteryIntent != null) updateBattery(batteryIntent)
        updateState(currentState.copy(isLowPowerMode = powerManager.isPowerSaveMode))
        refreshConnectivity()
    }

    fun stop() {
        try {
            context.unregisterReceiver(systemReceiver)
            connectivityManager.unregisterNetworkCallback(networkCallback)
            unregisterTelephonyListener()
        } catch (_: Exception) {
        }
    }

    private fun updateTime() {
        updateState(currentState.copy(timeText = timeFormat.format(Date())))
    }

    private fun updateDnd() {
        val filter = notificationManager.currentInterruptionFilter
        val dndActive = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        updateState(currentState.copy(isDndOn = dndActive))
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        updateState(currentState.copy(batteryLevel = pct, isCharging = charging))
    }

    private fun updateWifi() {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val level = if (wifiConnected) {
            @Suppress("DEPRECATION")
            val rssi = wifiManager.connectionInfo?.rssi ?: -100
            @Suppress("DEPRECATION")
            WifiManager.calculateSignalLevel(rssi, 4).coerceIn(1, 3)
        } else {
            0
        }

        updateState(currentState.copy(isWifiConnected = wifiConnected, wifiLevel = level))
    }

    private fun refreshConnectivity() {
        updateWifi()
        val isDataEnabled = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telephonyManager.isDataEnabled
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
        val netLabel = if (isDataEnabled) resolveNetworkType() else ""
        updateState(
            currentState.copy(
                isMobileDataOn = isDataEnabled,
                networkTypeLabel = netLabel
            )
        )
    }

    private fun resolveNetworkType(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "4G"
        }
        return try {
            @Suppress("DEPRECATION")
            when (telephonyManager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "E"
                else -> "4G"
            }
        } catch (_: Exception) {
            "4G"
        }
    }

    private fun registerTelephonyListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(),
                TelephonyCallback.SignalStrengthsListener,
                TelephonyCallback.ServiceStateListener,
                TelephonyCallback.DisplayInfoListener {

                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    updateState(currentState.copy(signalLevel = signalStrength.level))
                }

                override fun onServiceStateChanged(serviceState: ServiceState) {
                    val inService = serviceState.state == ServiceState.STATE_IN_SERVICE
                    updateState(currentState.copy(hasCellularService = inService))
                }

                override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                    val is5g = displayInfo.overrideNetworkType ==
                            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                            displayInfo.overrideNetworkType ==
                            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
                    val label = if (is5g) "5G" else resolveNetworkType()
                    updateState(currentState.copy(networkTypeLabel = label))
                }
            }
            modernTelephonyCallback = callback
            try {
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            } catch (_: Exception) {
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
@Deprecated("Deprecated in Java")
override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
val lvl = signalStrength?.level ?: 0
updateState(currentState.copy(signalLevel = lvl))
}
@Deprecated("Deprecated in Java")
override fun onServiceStateChanged(serviceState: ServiceState?) {
val inService = serviceState?.state == ServiceState.STATE_IN_SERVICE
updateState(currentState.copy(hasCellularService = inService))
}
}
legacyPhoneListener = listener
try {
@Suppress("DEPRECATION")
telephonyManager.listen(
listener,
PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_SERVICE_STATE
)
} catch (_: Exception) {
}
}
}
private fun unregisterTelephonyListener() {
try {
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
(modernTelephonyCallback as? TelephonyCallback)?.let {
telephonyManager.unregisterTelephonyCallback(it)
}
} else {
@Suppress("DEPRECATION")
legacyPhoneListener?.let {
telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
}
}
} catch (_: Exception) {
}
}
private fun updateState(newState: StatusBarState) {
if (newState != currentState) {
currentState = newState
onStateChanged(newState)
}
}
}
