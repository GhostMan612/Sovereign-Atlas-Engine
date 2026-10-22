// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.sovereignatlas.atlas.geo.navigation.VoiceGuidance
import java.util.Locale

class AndroidVoiceGuidance(context: Context) : VoiceGuidance {
    private var isMuted: Boolean = false
    private var isInitialized: Boolean = false
    private var tts: TextToSpeech? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.US)
                    isInitialized = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                } else {
                    Log.w("AndroidVoiceGuidance", "TTS initialization failed: $status")
                }
            }
        } catch (e: Exception) {
            Log.w("AndroidVoiceGuidance", "Failed to construct TextToSpeech", e)
        }
    }

    override fun speak(text: String) {
        if (isMuted || !isInitialized || tts == null) return
        try {
            tts?.speak(
                text,
                TextToSpeech.QUEUE_ADD,
                null,
                "nav_${System.currentTimeMillis()}",
            )
        } catch (e: Exception) {
            Log.w("AndroidVoiceGuidance", "TTS speak failed", e)
        }
    }

    override fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w("AndroidVoiceGuidance", "TTS stop failed", e)
        }
    }

    override fun isMuted(): Boolean = isMuted

    override fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            stop()
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.w("AndroidVoiceGuidance", "TTS shutdown failed", e)
        }
    }
}
