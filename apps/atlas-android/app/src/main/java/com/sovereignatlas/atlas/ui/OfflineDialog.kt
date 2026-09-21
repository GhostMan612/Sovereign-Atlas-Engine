// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.offline.DownloadResult
import com.sovereignatlas.atlas.offline.OfflineBuiltinProviders
import com.sovereignatlas.atlas.offline.OfflineDownloader
import com.sovereignatlas.atlas.offline.OfflinePackLifecycle
import com.sovereignatlas.atlas.offline.OfflinePackRecord
import com.sovereignatlas.atlas.offline.OfflineStore
import com.sovereignatlas.atlas.offline.PlanOutcome
import com.sovereignatlas.atlas.offline.formatBytes
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@Composable
fun OfflineDialog(
    store: OfflineStore,
    onClose: () -> Unit,
) {
    val tab = remember { mutableStateOf(0) }
    val cancels = remember { mutableMapOf<String, AtomicBoolean>() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    AlertDialog(
        onDismissRequest = {
            cancels.values.forEach { it.set(true) }
            onClose()
        },
        title = { Text("Offline Areas") },
        text = {
            Column {
                Row {
                    TextButton(onClick = { tab.value = 0 }) { Text("Packs") }
                    TextButton(onClick = { tab.value = 1 }) { Text("Plan") }
                    TextButton(onClick = { tab.value = 2 }) { Text("Providers") }
                }
                when (tab.value) {
                    0 -> PacksTab(store, cancels, mainHandler)
                    1 -> PlanTab(store)
                    else -> ProvidersTab()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun PacksTab(
    store: OfflineStore,
    cancels: MutableMap<String, AtomicBoolean>,
    mainHandler: Handler,
) {
    val packs = store.packs()
    val error = store.lastErrorOrNull()
    if (error != null) Text("Store unavailable: $error")
    var totalBytes = 0L
    for (pack in packs) totalBytes += pack.receivedBytes
    Text("Packs: ${packs.size} • Stored: ${formatBytes(totalBytes)}")
    if (packs.isEmpty()) {
        Text("No packs yet. Use Plan to schedule one.")
    } else {
        LazyColumn {
            items(packs, key = { it.packId }) { pack ->
                ListItem(
                    headlineContent = {
                        Text("${pack.packId} • ${pack.providerTitle}")
                    },
                    supportingContent = {
                        Text(
                            "${pack.lifecycle.name} • " +
                                "${pack.receivedTiles}/${pack.tileCount} tiles • " +
                                formatBytes(pack.receivedBytes) +
                                if (pack.failureDetail.isNotEmpty()) {
                                    " • ${pack.failureDetail}"
                                } else {
                                    ""
                                },
                        )
                    },
                    trailingContent = {
                        Row {
                            if (pack.lifecycle == OfflinePackLifecycle.planned ||
                                pack.lifecycle == OfflinePackLifecycle.failed ||
                                pack.lifecycle == OfflinePackLifecycle.cancelled
                            ) {
                                TextButton(
                                    onClick = {
                                        startDownload(store, pack, cancels, mainHandler)
                                    },
                                ) {
                                    Text("Get")
                                }
                            }
                            if (pack.lifecycle == OfflinePackLifecycle.downloading) {
                                TextButton(
                                    onClick = {
                                        cancels[pack.packId]?.set(true)
                                    },
                                ) {
                                    Text("Stop")
                                }
                            }
                            TextButton(
                                onClick = { store.removePack(pack.packId) },
                            ) {
                                Text("Del")
                            }
                        }
                    },
                )
            }
        }
    }
    val events = store.events().take(8)
    if (events.isNotEmpty()) {
        Text("Recent:")
        for (event in events) Text(event)
    }
}

private fun startDownload(
    store: OfflineStore,
    pack: OfflinePackRecord,
    cancels: MutableMap<String, AtomicBoolean>,
    mainHandler: Handler,
) {
    val provider = OfflineBuiltinProviders.lookup(pack.providerId) ?: return
    val cancel = AtomicBoolean(false)
    cancels[pack.packId] = cancel
    pack.lifecycle = OfflinePackLifecycle.downloading
    store.notifyChanged()
    thread(isDaemon = true) {
        val downloader = OfflineDownloader(OfflineDownloader::httpChunk)
        val result = downloader.download(
            record = pack,
            descriptor = provider,
            dir = store.packDir(pack.packId),
            onProgress = { _, _ ->
                mainHandler.post { store.notifyChanged() }
            },
            isCancelled = { cancel.get() },
        )
        when (result) {
            is DownloadResult.Complete -> store.log(
                "done ${pack.packId} ${result.tiles} tiles",
            )
            is DownloadResult.Cancelled -> store.log("cancel ${pack.packId}")
            is DownloadResult.Failed -> store.log(
                "fail ${pack.packId} ${result.detail}",
            )
        }
        store.persist()
        mainHandler.post { store.notifyChanged() }
    }
}

@Composable
private fun PlanTab(store: OfflineStore) {
    val providerId = remember { mutableStateOf("esri-imagery") }
    val expanded = remember { mutableStateOf(false) }
    val zMin = remember { mutableStateOf("10") }
    val zMax = remember { mutableStateOf("12") }
    val xMin = remember { mutableStateOf("1") }
    val xMax = remember { mutableStateOf("2") }
    val yMin = remember { mutableStateOf("2") }
    val yMax = remember { mutableStateOf("3") }
    val approvedBulk = remember { mutableStateOf(false) }
    val prefetch = remember { mutableStateOf(false) }
    val message = remember { mutableStateOf("") }
    val current = OfflineBuiltinProviders.lookup(providerId.value)
    Text(
        current?.title ?: providerId.value,
        modifier = Modifier.clickable { expanded.value = true },
    )
    DropdownMenu(
        expanded = expanded.value,
        onDismissRequest = { expanded.value = false },
    ) {
        for (provider in OfflineBuiltinProviders.all) {
            DropdownMenuItem(
                text = { Text(provider.title) },
                onClick = {
                    providerId.value = provider.id
                    expanded.value = false
                },
            )
        }
    }
    for (row in listOf("z" to (zMin to zMax), "x" to (xMin to xMax), "y" to (yMin to yMax))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${row.first} ")
            OutlinedTextField(
                value = row.second.first.value,
                onValueChange = { row.second.first.value = it.filter { c -> c.isDigit() } },
                label = { Text("min") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = row.second.second.value,
                onValueChange = { row.second.second.value = it.filter { c -> c.isDigit() } },
                label = { Text("max") },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = approvedBulk.value,
            onCheckedChange = { approvedBulk.value = it },
        )
        Text("Approve bulk")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = prefetch.value,
            onCheckedChange = { prefetch.value = it },
        )
        Text("Prefetch")
    }
    if (message.value.isNotEmpty()) Text(message.value)
    Button(
        onClick = {
            val outcome = store.plan(
                providerId = providerId.value,
                zMin = zMin.value.toIntOrNull() ?: 0,
                zMax = zMax.value.toIntOrNull() ?: 0,
                xMin = xMin.value.toIntOrNull() ?: 0,
                xMax = xMax.value.toIntOrNull() ?: 0,
                yMin = yMin.value.toIntOrNull() ?: 0,
                yMax = yMax.value.toIntOrNull() ?: 0,
                approvedBulk = approvedBulk.value,
                isPrefetch = prefetch.value,
            )
            message.value = when (outcome) {
                is PlanOutcome.Planned -> "Planned ${outcome.record.packId}."
                is PlanOutcome.Refused -> "Refused: ${outcome.reason}"
                is PlanOutcome.Blocked -> "Blocked: ${outcome.reason}"
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text("Plan pack")
    }
}

@Composable
private fun ProvidersTab() {
    LazyColumn {
        items(OfflineBuiltinProviders.all, key = { it.id }) { provider ->
            ListItem(
                headlineContent = { Text(provider.title) },
                supportingContent = {
                    Text(
                        "${provider.attribution} • ${provider.license} • " +
                            "z${provider.minZoom}-${provider.maxZoom} • " +
                            if (provider.prefetchAllowed) {
                                "prefetch ok"
                            } else {
                                "approval required"
                            },
                    )
                },
            )
        }
    }
}
