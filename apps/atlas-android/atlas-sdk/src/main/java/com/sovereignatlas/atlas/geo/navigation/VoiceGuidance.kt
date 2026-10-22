// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.navigation

interface VoiceGuidance {
    fun speak(text: String)
    fun stop()
    fun isMuted(): Boolean
    fun setMuted(muted: Boolean)
}

class FakeVoiceGuidance : VoiceGuidance {
    private var muted: Boolean = false
    val utterances = mutableListOf<String>()

    override fun speak(text: String) {
        if (!muted) {
            utterances.add(text)
        }
    }

    override fun stop() {}

    override fun isMuted(): Boolean = muted

    override fun setMuted(muted: Boolean) {
        this.muted = muted
    }
}
