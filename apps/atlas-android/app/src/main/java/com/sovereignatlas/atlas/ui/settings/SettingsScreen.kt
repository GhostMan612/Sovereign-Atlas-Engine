// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val ATAK_TEAM_COLORS = listOf(
    "Cyan", "Green", "Red", "Blue", "Yellow", "Purple", "Magenta", "White", "Orange", "Dark Blue",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val callsign by viewModel.callsign.collectAsStateWithLifecycle()
    val teamColor by viewModel.teamColor.collectAsStateWithLifecycle()
    val isMeshActive by viewModel.isMeshActive.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Tactical Node Configuration", style = MaterialTheme.typography.titleLarge)

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isMeshActive) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isMeshActive) "Mesh Transceiver: ONLINE" else "Mesh Transceiver: OFFLINE",
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = isMeshActive,
                    onCheckedChange = { viewModel.toggleMeshActive(it) },
                )
            }
        }

        HorizontalDivider()

        OutlinedTextField(
            value = callsign,
            onValueChange = { viewModel.updateCallsign(it) },
            label = { Text("Callsign") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = teamColor,
                onValueChange = {},
                readOnly = true,
                label = { Text("Team Color") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                ATAK_TEAM_COLORS.forEach { color ->
                    DropdownMenuItem(
                        text = { Text(color) },
                        onClick = {
                            viewModel.updateTeamColor(color)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
