// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePipelineContractTest {
    private fun findRepoRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".").canonicalFile
        val root = generateSequence(start) { it.parentFile }.firstOrNull { dir ->
            File(dir, ".github/workflows/atlas-ci-safe.yml").isFile &&
                File(dir, "apps/atlas-android/app/build.gradle").isFile
        }
        assertNotNull("Expected repository root containing .github/workflows/atlas-ci-safe.yml", root)
        return root!!
    }

    private fun readWorkflow(): String {
        return File(findRepoRoot(), ".github/workflows/atlas-ci-safe.yml")
            .readText(Charsets.UTF_8)
            .replace("\r\n", "\n")
    }

    @Test
    fun workflowRunsOnlyDevelopmentVerification() {
        val workflow = readWorkflow()
        val header = workflow.substringBefore("\njobs:")
        assertTrue(header.contains("- main"))
        assertFalse(header.contains("tags:"))
        assertFalse(header.contains("workflow_dispatch:"))
        assertTrue(header.contains("permissions:\n  contents: read"))
    }

    @Test
    fun candidateWorkflowCannotSignOrPublishReleaseArtifacts() {
        val workflow = readWorkflow()
        assertTrue(workflow.contains("\n  verify:\n"))
        assertTrue(workflow.contains("\n  sdk-consumer-maven:\n"))
        assertFalse(workflow.contains("\n  release:\n"))
        assertFalse(workflow.contains("contents: write"))
        assertFalse(workflow.contains("gh release create"))
        assertFalse(workflow.contains(":app:assemblePlayRelease"))
        assertFalse(workflow.contains(":app:assembleEnterpriseRelease"))
        assertFalse(workflow.contains("secrets."))
    }

    @Test
    fun workflowHasNoDirectUntrustedInputInterpolationInRunScripts() {
        val lines = readWorkflow().lines()
        var inRunBlock = false
        var runIndent = -1
        for ((index, rawLine) in lines.withIndex()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            val indent = rawLine.indexOfFirst { !it.isWhitespace() }
            if (inRunBlock) {
                if (indent <= runIndent) {
                    inRunBlock = false
                } else {
                    assertFalse(
                        "Line ${index + 1} directly interpolates workflow input inside run script",
                        trimmed.contains("\${{ inputs.") || trimmed.contains("\${{ github.event.inputs."),
                    )
                    continue
                }
            }
            if (trimmed.startsWith("run:") && trimmed.contains("|")) {
                inRunBlock = true
                runIndent = indent
            }
        }
    }

    @Test
    fun candidateWorkflowDoesNotAccessReleaseCredentials() {
        val workflow = readWorkflow()
        for (name in listOf("RELEASE_KEYSTORE_BASE64", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD", "GOOGLE_SERVICES_JSON_BASE64")) {
            assertFalse("Candidate workflow must not access $name", workflow.contains(name))
        }
    }

    @Test
    fun safeCiDoesNotPersistCheckoutTokensOrUseMutableActionRefs() {
        val workflow = readWorkflow()
        val uses = Regex("(?m)^\\s*-?\\s*uses:\\s*([^\\s]+)").findAll(workflow).map { it.groupValues[1] }.toList()
        assertTrue("Expected third-party actions in CI", uses.isNotEmpty())
        for (ref in uses) {
            assertTrue("Action must use an immutable commit SHA: $ref", Regex("^[^@]+@[0-9a-f]{40}$").matches(ref))
        }
        assertEquals(2, Regex("(?m)^\\s*persist-credentials:\\s*false\\s*$").findAll(workflow).count())
        assertFalse(File(findRepoRoot(), ".github/workflows/atlas.yaml").exists())
        assertTrue(workflow.contains("python3 -B -m unittest discover -s tools/a4/tests -v"))
    }

    @Test
    fun standaloneConsumerPipelinePropagatesGradleFailure() {
        val step = readWorkflow().substringAfter("- name: Verify standalone Maven consumer in isolated directory")
            .substringBefore("- name: Upload Standalone Consumer Test Reports")
        val pipeline = "./gradlew test --no-daemon --no-build-cache --rerun-tasks"
        assertTrue("Expected standalone consumer test pipeline", step.contains(pipeline))
        assertTrue("Pipe failures must affect the captured status", step.substringBefore(pipeline).contains("set -o pipefail"))
        assertTrue("Expected captured pipeline status", step.substringAfter(pipeline).contains("EXIT=\$?"))
        assertTrue("Expected captured pipeline status to determine step result", step.contains("exit \"\$EXIT\""))
    }

    @Test
    fun appBuildGradleEnforcesCompleteFailClosedReleaseSigning() {
        val gradle = File(findRepoRoot(), "apps/atlas-android/app/build.gradle").readText(Charsets.UTF_8)

        assertTrue(gradle.contains("gradle.taskGraph.whenReady"))
        assertTrue(gradle.contains("if (isRelease && !hasReleaseSigning)"))
        assertTrue(gradle.contains("FAIL-CLOSED:"))
        assertTrue(gradle.contains("hasValidKeystoreFile"))
        assertTrue(gradle.contains("releaseStorePassword != null && !releaseStorePassword.isEmpty()"))
        assertTrue(gradle.contains("releaseKeyAlias != null && !releaseKeyAlias.isEmpty()"))
        assertTrue(gradle.contains("releaseKeyPassword != null && !releaseKeyPassword.isEmpty()"))
        assertTrue(gradle.contains("resolvedKeystoreFile.isFile() && resolvedKeystoreFile.length() > 0L"))
        assertTrue(gradle.contains("signingConfig signingConfigs.release"))

        val literalSecretRegex = Regex("""(?:storePassword|keyPassword)\s*(?:=|\s)\s*["'][^"']+["']""")
        assertFalse("Hardcoded keystore password must never exist in build.gradle", literalSecretRegex.containsMatchIn(gradle))
    }

    @Test
    fun repositoryKeepsCredentialsGitignoredAndAbsentFromTree() {
        val root = findRepoRoot()
        val gitignore = File(root, ".gitignore").readText(Charsets.UTF_8).lines().map { it.trim() }.toSet()
        val requiredIgnores = listOf(
            "*.jks",
            "*.keystore",
            "keystore.properties",
            "google-services.json",
            ".env",
            "*.pem",
            "*.key",
            "secrets/",
        )
        for (pattern in requiredIgnores) {
            assertTrue("Missing .gitignore rule for $pattern", gitignore.contains(pattern))
        }

        val appRoot = File(root, "apps/atlas-android")
        val forbiddenFiles = listOf("keystore.properties", ".env")
        val forbiddenExts = setOf("jks", "keystore", "pem", "key")
        val violations = mutableListOf<String>()
        appRoot.walkTopDown()
            .onEnter { dir ->
                val name = dir.name
                name != ".git" && name != ".gradle" && name != "build" && name != ".kotlin"
            }
            .forEach { file ->
                if (file.isFile) {
                    val lower = file.name.lowercase()
                    val ext = file.extension.lowercase()
                    if (lower in forbiddenFiles || lower.startsWith(".env.") || ext in forbiddenExts) {
                        violations += file.relativeTo(root).path
                    }
                }
            }
        assertTrue("Unexpected credential artifacts found: $violations", violations.isEmpty())
    }
}
