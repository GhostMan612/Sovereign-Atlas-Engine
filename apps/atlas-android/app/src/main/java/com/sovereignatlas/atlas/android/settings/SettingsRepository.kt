// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("atlas_tactical_settings", Context.MODE_PRIVATE)

    private val _callsign = MutableStateFlow(
        prefs.getString("callsign", null)
            ?: migrateLegacyCallsign(context)
            ?: "Atlas",
    )
    val callsign: StateFlow<String> = _callsign.asStateFlow()

    private val _teamColor = MutableStateFlow(prefs.getString("teamColor", "Cyan") ?: "Cyan")
    val teamColor: StateFlow<String> = _teamColor.asStateFlow()

    private val _isMeshActive = MutableStateFlow(prefs.getBoolean("isMeshActive", true))
    val isMeshActive: StateFlow<Boolean> = _isMeshActive.asStateFlow()

    fun setCallsign(newCallsign: String) {
        val trimmed = newCallsign.trim()
        if (trimmed.isEmpty()) return
        prefs.edit().putString("callsign", trimmed).apply()
        _callsign.value = trimmed
    }

    fun setTeamColor(newColor: String) {
        prefs.edit().putString("teamColor", newColor).apply()
        _teamColor.value = newColor
    }

    fun setMeshActive(isActive: Boolean) {
        prefs.edit().putBoolean("isMeshActive", isActive).apply()
        _isMeshActive.value = isActive
    }

    private fun migrateLegacyCallsign(context: Context): String? {
        // One-time carry-over from the pre-settings callsign key.
        val legacy = context.getSharedPreferences("atlas_prefs", Context.MODE_PRIVATE)
            .getString("PREF_CALLSIGN", null)
        if (!legacy.isNullOrEmpty()) {
            prefs.edit().putString("callsign", legacy).apply()
        }
        return legacy?.takeIf { it.isNotEmpty() }
    }
}
