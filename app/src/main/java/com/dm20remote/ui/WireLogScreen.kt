package com.dm20remote.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.dm20remote.R
import com.dm20remote.debug.WireLog

/**
 * Full-screen viewer for the opt-in wire log: a live tail of recent lines plus
 * the actions that get the file off the device without `adb logcat` (share,
 * save to Downloads, copy to the clipboard).
 */
@Composable
fun WireLogScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lines by WireLog.tail.collectAsState()
    val enabled by WireLog.enabled.collectAsState()
    val scroll = rememberScrollState()
    var notice by remember { mutableStateOf("") }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) scroll.scrollTo(scroll.maxValue)
    }
    LaunchedEffect(notice) {
        if (notice.isNotBlank()) {
            Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
            notice = ""
        }
    }

    val fileName = WireLog.currentFileName()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.debug_screen_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        !enabled -> stringResource(R.string.debug_status_off)
                        fileName != null -> stringResource(R.string.debug_status_on, fileName)
                        else -> stringResource(R.string.debug_status_on_nofile)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.debug_close)) }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = { shareLogFile(context) { notice = it } }) {
                Text(stringResource(R.string.debug_share))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                TextButton(onClick = { saveLogToDownloads(context) { notice = it } }) {
                    Text(stringResource(R.string.debug_save))
                }
            }
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(WireLog.snapshotText()))
                notice = context.getString(R.string.debug_copied)
            }) {
                Text(stringResource(R.string.debug_copy))
            }
            TextButton(onClick = { WireLog.clear() }) {
                Text(stringResource(R.string.debug_clear))
            }
            Spacer(Modifier.weight(1f))
        }

        HorizontalDivider()

        if (lines.isEmpty()) {
            Text(
                stringResource(R.string.debug_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Text(
                text = lines.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** Shares the current log file through the Android share sheet (FileProvider). */
private fun shareLogFile(context: Context, onMessage: (String) -> Unit) {
    WireLog.flush()
    val file = WireLog.currentFile()
    if (file == null || !file.exists()) {
        onMessage(context.getString(R.string.debug_no_file))
        return
    }
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
    if (uri == null) {
        onMessage(context.getString(R.string.debug_save_failed))
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, file.name)) }
        .onFailure { onMessage(context.getString(R.string.debug_save_failed)) }
}

/** Copies the current log file into the public Downloads collection (API 29+). */
private fun saveLogToDownloads(context: Context, onMessage: (String) -> Unit) {
    WireLog.flush()
    val file = WireLog.currentFile()
    if (file == null || !file.exists()) {
        onMessage(context.getString(R.string.debug_no_file))
        return
    }
    val saved = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("no MediaStore target")
        context.contentResolver.openOutputStream(uri)?.use { it.write(file.readBytes()) }
            ?: error("no output stream")
    }.isSuccess
    onMessage(
        if (saved) context.getString(R.string.debug_saved, file.name)
        else context.getString(R.string.debug_save_failed),
    )
}
