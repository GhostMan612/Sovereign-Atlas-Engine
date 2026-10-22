// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSigningGuardTest {

    private fun resolveAppModuleDir(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        if (File(cwd, "build.gradle").isFile && cwd.name == "app") return cwd
        val nested = File(cwd, "apps/atlas-android/app")
        if (File(nested, "build.gradle").isFile) return nested
        val sibling = File(cwd, "app")
        if (File(sibling, "build.gradle").isFile) return sibling
        return cwd
    }

    @Test
    fun appBuildGradleEnforcesFailClosedReleaseSigningAndNoCommittedKeystore() {
        val appDir = resolveAppModuleDir()
        val buildGradle = File(appDir, "build.gradle")
        assertTrue(buildGradle.isFile)
        val content = buildGradle.readText(Charsets.UTF_8)
        assertTrue(content.contains("FAIL-CLOSED: Release signing credentials missing. Halting before packaging."))
        assertTrue(content.contains("gradle.taskGraph.whenReady"))

        assertFalse(File(appDir, "keystore.properties").exists())
        val keystoreFiles = appDir.listFiles { f ->
            f.isFile && (f.name.endsWith(".jks") || f.name.endsWith(".keystore"))
        }.orEmpty()
        assertTrue(keystoreFiles.isEmpty())
    }
}
