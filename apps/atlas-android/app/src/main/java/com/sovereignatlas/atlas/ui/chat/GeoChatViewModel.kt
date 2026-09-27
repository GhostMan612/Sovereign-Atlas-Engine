// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovereignatlas.atlas.AtlasServices
import com.sovereignatlas.atlas.geo.cot.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GeoChatViewModel(private val services: AtlasServices) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = services.messages.messages

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val hasGpsFix: StateFlow<Boolean> = services.locationEngine.currentLocation
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = services.locationEngine.currentLocation.value != null,
        )

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        val currentGps = services.locationEngine.currentLocation.value
        if (currentGps == null) {
            _errorMessage.value = "Waiting for GPS fix to send message."
            return
        }

        viewModelScope.launch {
            try {
                val sentMsg = withContext(Dispatchers.IO) {
                    services.atakBroadcaster.sendChatMessage(
                        text = text,
                        currentGeoPoint = currentGps,
                        targetUid = null,
                    )
                }
                services.messages.addMessage(sentMsg)
                _inputText.value = ""
                _errorMessage.value = null
            } catch (error: Exception) {
                Log.e("GeoChatViewModel", "Failed to send tactical message", error)
                _errorMessage.value = (error.message ?: "Failed to transmit payload.").take(60)
            }
        }
    }
}
