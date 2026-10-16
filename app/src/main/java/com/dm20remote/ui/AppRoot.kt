package com.dm20remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dm20remote.debug.WireLog
import com.dm20remote.model.ConsoleSession
import com.dm20remote.service.ConsoleService

/**
 * The only entry point into the UI.
 *
 * There are three screens and no navigation library: the wire log when it is
 * open, the connect screen while no console answers, and the console shell once
 * one does. The connect screen and the shell both run to the screen edges, so the
 * safe-drawing insets are applied once, here, rather than in each screen.
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val state by ConsoleSession.state.collectAsState()
    val debugEnabled by WireLog.enabled.collectAsState()
    var showLogs by remember { mutableStateOf(false) }

    LaunchedEffect(state.controlConnected) {
        if (state.controlConnected) ConsoleService.start(context) else ConsoleService.stop(context)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Dm20Colors.background) {
        if (showLogs) {
            WireLogScreen(onClose = { showLogs = false })
        } else {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (state.controlConnected) {
                    ConsoleShell(
                        state = state,
                        debugEnabled = debugEnabled,
                        onOpenLogs = { showLogs = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ConnectScreen(
                        state = state,
                        onConnect = { host -> if (host.isNotBlank()) ConsoleSession.connect(host) },
                        debugEnabled = debugEnabled,
                        onDebugToggle = { WireLog.setEnabled(it) },
                        onOpenLogs = { showLogs = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
