// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.core.KeyProvider
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    keyProvider: KeyProvider,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val storedKey by keyProvider.cartoKey.collectAsState(initial = null)
    var field by remember(storedKey) { mutableStateOf(storedKey ?: "") }
    var revealed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (storedKey != null) "Saved" else "Empty") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Settings") },
        text = {
            Column {
                Text("API Keys")
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = { Text("CARTO key") },
                    visualTransformation = if (revealed) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { revealed = !revealed },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = if (revealed) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = if (revealed) {
                                    "Hide API key"
                                } else {
                                    "Show API key"
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(status)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = field.trim()
                    if (value.isEmpty()) return@TextButton
                    scope.launch {
                        keyProvider.setCartoKey(value)
                        status = "Saved"
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    field = ""
                    scope.launch {
                        keyProvider.clearCartoKey()
                        status = "Empty"
                    }
                },
            ) {
                Text("Clear")
            }
        },
    )
}
