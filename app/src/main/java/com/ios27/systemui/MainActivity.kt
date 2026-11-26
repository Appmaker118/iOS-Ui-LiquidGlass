package com.ios27.systemui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ios27.systemui.databinding.ActivityMainBinding
import com.ios27.systemui.physics.IosSpringPhysics
import com.ios27.systemui.service.StatusBarOverlayService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkOverlayAndStart()
    }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startStatusBarService()
        } else {
            binding.statusText.text = "Please allow 'Appear on top' permission in Settings."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Attach bouncy spring physics to buttons
            IosSpringPhysics.attachBouncyTouch(binding.btnEnableStatusBar) {
                requestRequiredPermissions()
            }

            IosSpringPhysics.attachBouncyTouch(binding.btnStopStatusBar) {
                stopService(Intent(this, StatusBarOverlayService::class.java))
                binding.statusText.text = "Status bar stopped."
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestRequiredPermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            checkOverlayAndStart()
        }
    }

    private fun checkOverlayAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayLauncher.launch(intent)
            } catch (e: Exception) {
                binding.statusText.text = "Open Settings > Apps > iOS 27 SystemUI > Appear on top"
            }
        } else {
            startStatusBarService()
        }
    }

    private fun startStatusBarService() {
        try {
            val serviceIntent = Intent(this, StatusBarOverlayService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            binding.statusText.text = "iOS 27 Status Bar is Active!"
        } catch (e: Exception) {
            binding.statusText.text = "Error starting overlay: ${e.message}"
        }
    }
}
