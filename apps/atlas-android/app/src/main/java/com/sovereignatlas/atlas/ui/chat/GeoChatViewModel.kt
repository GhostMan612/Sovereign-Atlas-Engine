// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sovereignatlas.atlas.AtlasServices
import com.sovereignatlas.atlas.android.comms.CloudDeliveryStatus
import com.sovereignatlas.atlas.geo.cot.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
    private var sending = false

    val hasGpsFix: StateFlow<Boolean> = services.locationEngine.currentLocation
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = services.locationEngine.currentLocation.value != null,
        )

    val networkProfile = services.settingsRepository.networkProfile

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun sendMessage() {
        if (sending) return
        val input = _inputText.value
        val text = input.trim()
        if (text.isEmpty()) return

        val policySnapshot = services.settingsRepository.cloudPolicy.snapshot()
        if (policySnapshot.profile == com.sovereignatlas.atlas.android.settings.NetworkProfile.RADIO_SILENCE) {
            _errorMessage.value = "Radio Silence is active. Transmission blocked."
            return
        }

        val currentGps = services.locationEngine.currentLocation.value
        if (currentGps == null) {
            _errorMessage.value = "Waiting for GPS fix to send message."
            return
        }

        sending = true
        viewModelScope.launch {
            try {
                val sentMsg = withContext(Dispatchers.IO) {
                    services.atakBroadcaster.sendChatMessage(
                        text = text,
                        currentGeoPoint = currentGps,
                        snapshot = policySnapshot,
                        targetUid = null,
                    )
                }
                val delivered = sentMsg.multicastTransmitted || sentMsg.cloudResult?.isSuccess == true
                if (delivered) {
                    services.messages.addMessage(sentMsg.message)
                    if (_inputText.value == input) _inputText.value = ""
                }
                val cloudNotice = when (sentMsg.cloudDelivery?.status) {
                    CloudDeliveryStatus.BLOCKED -> "Cloud blocked by policy."
                    CloudDeliveryStatus.CANCELLED_BEFORE_SEND -> "Chat write canceled before submission."
                    CloudDeliveryStatus.SUBMITTED -> "Cloud submitted; delivery unconfirmed."
                    CloudDeliveryStatus.FAILED -> "Cloud failed; delivery unconfirmed."
                    CloudDeliveryStatus.CONFIRMED, null -> null
                }
                _errorMessage.value = if (cloudNotice != null && sentMsg.multicastTransmitted) {
                    "Multicast sent; $cloudNotice"
                } else cloudNotice ?: if (!delivered) "Multicast transmission unavailable." else null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("GeoChatViewModel", "Failed to send tactical message", error)
                _errorMessage.value = (error.message ?: "Failed to transmit payload.").take(60)
            } finally {
                sending = false
            }
        }
    }
}
