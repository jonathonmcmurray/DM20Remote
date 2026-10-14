package com.dm20remote

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.dm20remote.model.ConsoleSession
import com.dm20remote.ui.AppRoot
import com.dm20remote.ui.Dm20Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // targetSdk 35+ forces edge-to-edge on Android 15+, so opt in on every
        // version to keep one consistent layout. The UI is always dark, hence
        // the light system-bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        ConsoleSession.ensureStarted()
        setContent {
            Dm20Theme {
                val state by ConsoleSession.state.collectAsState()
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* The notification is a convenience; the session works without it. */ }

                // Ask for the notification permission only once a console is
                // connected, i.e. when the foreground service and its ongoing
                // notification actually become relevant.
                LaunchedEffect(state.controlConnected) {
                    if (state.controlConnected && needsNotificationPermission()) {
                        runCatching {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                AppRoot()
            }
        }
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
}
