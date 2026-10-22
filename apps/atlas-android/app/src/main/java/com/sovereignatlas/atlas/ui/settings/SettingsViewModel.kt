// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.settings

import androidx.lifecycle.ViewModel
import com.sovereignatlas.atlas.android.settings.SettingsRepository

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val callsign = repository.callsign
    val teamColor = repository.teamColor
    val networkProfile = repository.networkProfile
    val darkTheme = repository.darkTheme
    val reducedMotion = repository.reducedMotion
    val explorerWorkspaceEnabled = repository.explorerWorkspaceEnabled
    val tacticalWorkspaceEnabled = repository.tacticalWorkspaceEnabled
    val studioWorkspaceEnabled = repository.studioWorkspaceEnabled

    fun updateCallsign(newCallsign: String) = repository.setCallsign(newCallsign)
    fun updateTeamColor(newColor: String) = repository.setTeamColor(newColor)
    fun updateNetworkProfile(profile: com.sovereignatlas.atlas.android.settings.NetworkProfile) =
        repository.setNetworkProfile(profile)
    fun updateDarkTheme(enabled: Boolean) = repository.setDarkTheme(enabled)
    fun updateReducedMotion(enabled: Boolean) = repository.setReducedMotion(enabled)
    fun updateExplorerWorkspace(enabled: Boolean) = repository.setExplorerWorkspaceEnabled(enabled)
    fun updateTacticalWorkspace(enabled: Boolean) = repository.setTacticalWorkspaceEnabled(enabled)
    fun updateStudioWorkspace(enabled: Boolean) = repository.setStudioWorkspaceEnabled(enabled)
}

