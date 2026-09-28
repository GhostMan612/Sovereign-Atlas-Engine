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

enum class NetworkProfile(val displayName: String) {
    RADIO_SILENCE("Radio Silence (EMCON)"),
    MESH_ONLY("Off-Grid Mesh Only"),
    CLOUD_ONLY("Cloud-Only Chat"),
    HYBRID_BRIDGE("Hybrid Bridge Mode")
}

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("atlas_tactical_settings", Context.MODE_PRIVATE)

    private val _callsign = MutableStateFlow(prefs.getString("callsign", "Atlas") ?: "Atlas")
    val callsign: StateFlow<String> = _callsign.asStateFlow()

    private val _teamColor = MutableStateFlow(prefs.getString("teamColor", "Cyan") ?: "Cyan")
    val teamColor: StateFlow<String> = _teamColor.asStateFlow()

    private val defaultProfile = if (prefs.getBoolean("isMeshActive", true)) {
        NetworkProfile.MESH_ONLY.name
    } else {
        NetworkProfile.RADIO_SILENCE.name
    }

    private val _networkProfile = MutableStateFlow(
        runCatching {
            NetworkProfile.valueOf(
                prefs.getString("networkProfile", defaultProfile) ?: defaultProfile
            )
        }.getOrDefault(NetworkProfile.MESH_ONLY)
    )
    val networkProfile: StateFlow<NetworkProfile> = _networkProfile.asStateFlow()

    private val _activeMbtilesPacks = MutableStateFlow(
        prefs.getStringSet("activeMbtilesPacks", emptySet())?.toSet() ?: emptySet()
    )
    val activeMbtilesPacks: StateFlow<Set<String>> = _activeMbtilesPacks.asStateFlow()

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

    fun setNetworkProfile(profile: NetworkProfile) {
        prefs.edit()
            .putString("networkProfile", profile.name)
            .remove("isMeshActive")
            .apply()
        _networkProfile.value = profile
    }

    fun setActiveMbtilesPacks(packs: Set<String>) {
        prefs.edit().putStringSet("activeMbtilesPacks", packs.toSet()).apply()
        _activeMbtilesPacks.value = packs.toSet()
    }
}
