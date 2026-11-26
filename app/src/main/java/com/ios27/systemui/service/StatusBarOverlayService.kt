package com.ios27.systemui.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.ios27.systemui.databinding.ViewIosStatusBarBinding
import com.ios27.systemui.monitor.StatusBarState
import com.ios27.systemui.monitor.SystemStateMonitor

class StatusBarOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var binding: ViewIosStatusBarBinding
    private var stateMonitor: SystemStateMonitor? = null
    private var isViewAttached = false
    private var currentTintColor: Int = Color.WHITE

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            binding = ViewIosStatusBarBinding.inflate(LayoutInflater.from(this))

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                dpToPx(34),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            binding.root.setOnApplyWindowInsetsListener { view, insets ->
                val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    insets.displayCutout
                } else null

                var leftPad = dpToPx(16)
                var rightPad = dpToPx(16)
                var barHeight = dpToPx(34)

                if (cutout != null) {
                    if (cutout.safeInsetTop > barHeight) {
                        barHeight = cutout.safeInsetTop + dpToPx(4)
                    }
                    for (rect in cutout.boundingRects) {
                        if (rect.left < resources.displayMetrics.widthPixels / 3) {
                            leftPad = rect.right + dpToPx(8)
                        }
                        if (rect.right > (resources.displayMetrics.widthPixels * 2) / 3) {
                            rightPad = (resources.displayMetrics.widthPixels - rect.left) + dpToPx(8)
                        }
                    }
                }

                view.setPadding(leftPad, 0, rightPad, 0)
                if (params.height != barHeight) {
                    params.height = barHeight
                    if (isViewAttached) {
                        windowManager.updateViewLayout(binding.root, params)
                    }
                }
                insets
            }

            windowManager.addView(binding.root, params)
            isViewAttached = true

            detectAdaptiveTint()

            stateMonitor = SystemStateMonitor(this) { state ->
                renderState(state)
            }
            stateMonitor?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        detectAdaptiveTint()
    }

    /**
     * Automatically adapts status bar icons to White or Black depending on Wallpaper luminance
     * and system Dark/Light mode (just like iOS adaptive status bar).
     */
    private fun detectAdaptiveTint() {
        var useDarkIcons = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val wm = WallpaperManager.getInstance(this)
                val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                if (colors != null) {
                    val supportsDarkText =
                        (colors.colorHints and WallpaperManager.HINT_SUPPORTS_DARK_TEXT) != 0
                    useDarkIcons = supportsDarkText
                }
            } catch (_: Exception) {
            }
        }

        val tint = if (useDarkIcons) Color.BLACK else Color.WHITE
        applyIconTint(tint)
    }

    fun applyIconTint(color: Int) {
        if (!isViewAttached || currentTintColor == color) return
        currentTintColor = color

        val shadowColor = if (color == Color.WHITE) Color.parseColor("#45000000") else Color.TRANSPARENT

        binding.iosSignalView.iconColor = color
        binding.iosWifiView.iconColor = color
        binding.iosBatteryView.baseColor = color
        binding.iosDndMoonView.iconColor = color

        binding.networkTypeText.setTextColor(color)
        binding.networkTypeText.setShadowLayer(2f, 0f, 1f, shadowColor)

        binding.iosClockText.setTextColor(color)
        binding.iosClockText.setShadowLayer(2f, 0f, 1f, shadowColor)
    }

    private fun renderState(state: StatusBarState) {
        if (!isViewAttached) return
        binding.iosClockText.text = state.timeText
        binding.iosDndMoonView.visibility = if (state.isDndOn) View.VISIBLE else View.GONE

        if (!state.hasCellularService) {
            binding.iosSignalView.visibility = View.GONE
            binding.networkTypeText.visibility = View.VISIBLE
            binding.networkTypeText.text = "No Service"
        } else {
            binding.iosSignalView.visibility = View.VISIBLE
            binding.iosSignalView.signalLevel = state.signalLevel

            if (state.isMobileDataOn && state.networkTypeLabel.isNotEmpty()) {
                binding.networkTypeText.visibility = View.VISIBLE
                binding.networkTypeText.text = state.networkTypeLabel
            } else {
                binding.networkTypeText.visibility = View.GONE
            }
        }

        if (state.isWifiConnected) {
            binding.iosWifiView.visibility = View.VISIBLE
            binding.iosWifiView.wifiLevel = state.wifiLevel
        } else {
            binding.iosWifiView.visibility = View.GONE
        }

        binding.iosBatteryView.batteryLevel = state.batteryLevel
        binding.iosBatteryView.isCharging = state.isCharging
        binding.iosBatteryView.isLowPowerMode = state.isLowPowerMode
    }

    private fun startForegroundNotification() {
        val channelId = "ios27_statusbar_channel"
        val channel = NotificationChannel(
            channelId,
            "iOS 27 Status Bar Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("iOS 27 UI Active")
            .setContentText("Custom status bar running")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                101,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(101, notification)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        stateMonitor?.stop()
        if (isViewAttached && ::binding.isInitialized) {
            try {
                windowManager.removeView(binding.root)
            } catch (_: Exception) {
            }
            isViewAttached = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
