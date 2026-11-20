// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.settings

import com.sovereignatlas.atlas.tactical.mesh.TransmissionPermit

data class CloudPolicySnapshot(val profile: NetworkProfile, val authorization: Long?, val meshAuthorization: Long?)

class CloudPolicyCoordinator(initial: NetworkProfile) {
    private val lock = Any()
    private var profile = initial
    private var generation = 0L

    fun transition(next: NetworkProfile, publish: () -> Unit = {}) = synchronized(lock) {
        if (next != profile) {
            generation++
            profile = next
        }
        publish()
    }

    fun authorization(expected: NetworkProfile): Long? = synchronized(lock) {
        if (profile == expected && cloudAllowed(profile)) generation else null
    }

    fun snapshot(): CloudPolicySnapshot = synchronized(lock) {
        CloudPolicySnapshot(profile, if (cloudAllowed(profile)) generation else null, if (meshAllowed(profile)) generation else null)
    }

    fun meshPermit(snapshot: CloudPolicySnapshot): TransmissionPermit = object : TransmissionPermit {
        override fun <T : Any> submit(action: () -> T): T? = synchronized(lock) {
            if (snapshot.meshAuthorization == null || snapshot.meshAuthorization != generation || !meshAllowed(profile)) null else action()
        }
    }

    fun <T : Any> submit(token: Long, action: () -> T): T? = synchronized(lock) {
        if (token != generation || !cloudAllowed(profile)) null else action()
    }

    private fun cloudAllowed(value: NetworkProfile) =
        value == NetworkProfile.CLOUD_ONLY || value == NetworkProfile.HYBRID_BRIDGE

    private fun meshAllowed(value: NetworkProfile) =
        value == NetworkProfile.MESH_ONLY || value == NetworkProfile.HYBRID_BRIDGE
}
