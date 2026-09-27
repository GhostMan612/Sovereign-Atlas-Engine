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
    val isMeshActive = repository.isMeshActive

    fun updateCallsign(newCallsign: String) = repository.setCallsign(newCallsign)
    fun updateTeamColor(newColor: String) = repository.setTeamColor(newColor)
    fun toggleMeshActive(isActive: Boolean) = repository.setMeshActive(isActive)
}
