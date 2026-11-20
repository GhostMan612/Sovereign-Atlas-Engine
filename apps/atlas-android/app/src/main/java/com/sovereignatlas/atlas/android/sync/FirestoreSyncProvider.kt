// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.sync

import android.util.Log
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sovereignatlas.atlas.geo.cot.ChatMessage
import com.sovereignatlas.atlas.geo.cot.SyncProvider
import com.sovereignatlas.atlas.geo.cot.SyncOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FirestoreSyncProvider(
    private val context: Context,
    private val localDeviceUid: String,
    private val callsignProvider: () -> String,
) : SyncProvider {

    private val app: FirebaseApp by lazy {
        FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(context)
            ?: throw IllegalStateException("Cloud configuration unavailable")
    }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(app) }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance(app) }

    @Volatile
    override var isReady: Boolean = false
        private set

    override fun beginAuthentication(): SyncOperation {
        val task = if (auth.currentUser == null) auth.signInAnonymously() else null
        return {
            runCatching {
                task?.await()
                auth.currentUser?.uid ?: throw IllegalStateException("Anonymous auth returned no user")
                isReady = true
            }.onFailure {
                isReady = false
                if (it is CancellationException) throw it
                Log.e("FirestoreSyncProvider", "Auth failed", it)
            }
        }
    }

    override fun beginChatMessage(message: ChatMessage): SyncOperation {
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
        val task = firestore.collection("chat_messages").document(message.messageId).set(doc)
        return {
            runCatching {
                task.await()
                Unit
            }.onFailure {
                if (it is CancellationException) throw it
                Log.e("FirestoreSyncProvider", "pushChatMessage failed", it)
            }
        }
    }
}
