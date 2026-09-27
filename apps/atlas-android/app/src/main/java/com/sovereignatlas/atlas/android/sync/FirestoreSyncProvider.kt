// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sovereignatlas.atlas.geo.cot.ChatMessage
import com.sovereignatlas.atlas.geo.cot.SyncProvider
import kotlinx.coroutines.tasks.await

class FirestoreSyncProvider(
    private val localDeviceUid: String,
    private val callsignProvider: () -> String,
) : SyncProvider {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    @Volatile
    override var isReady: Boolean = false
        private set

    override suspend fun ensureAuthenticated(): Result<Unit> = runCatching {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Anonymous auth returned no user")
        isReady = true
    }.onFailure { Log.e("FirestoreSyncProvider", "Auth failed", it) }

    override suspend fun pushChatMessage(message: ChatMessage): Result<Unit> = runCatching {
        if (!isReady) throw IllegalStateException("SyncProvider not authenticated")
        val doc = hashMapOf(
            "messageId" to message.messageId,
            "senderUid" to message.senderUid,
            "senderCallsign" to message.senderCallsign,
            "chatroom" to message.chatroom,
            "remarksTo" to message.remarksTo,
            "text" to message.text,
            "timestampMillis" to message.timestampMillis,
            "deviceUid" to localDeviceUid,
        )
        firestore.collection("chat_messages").document(message.messageId).set(doc).await()
        Unit
    }.onFailure { Log.e("FirestoreSyncProvider", "pushChatMessage failed", it) }
}
