// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePipelineContractTest {
    private fun findRepoRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".").canonicalFile
        val root = generateSequence(start) { it.parentFile }.firstOrNull { dir ->
            File(dir, ".github/workflows/atlas.yaml").isFile &&
                File(dir, "apps/atlas-android/app/build.gradle").isFile
        }
        assertNotNull("Expected repository root containing .github/workflows/atlas.yaml", root)
        return root!!
    }

    private fun readWorkflow(): String {
        return File(findRepoRoot(), ".github/workflows/atlas.yaml")
            .readText(Charsets.UTF_8)
            .replace("\r\n", "\n")
    }

    private fun releaseJobBlock(workflow: String): String {
        val idx = workflow.indexOf("\n  release:\n")
        assertTrue("Expected release job in .github/workflows/atlas.yaml", idx >= 0)
        return workflow.substring(idx)
    }

    @Test
    fun workflowDeclaresDispatchAndConstrainedReleaseTriggers() {
        val workflow = readWorkflow()
        val header = workflow.substringBefore("\njobs:")
        assertTrue(header.contains("workflow_dispatch:"))
        assertTrue(header.contains("dry_run:"))
        assertTrue(header.contains("release_version:"))
        assertTrue(header.contains("- main"))
        assertTrue(header.contains("- 'v*'"))
        assertFalse("Arbitrary wildcard tags must not trigger release", header.contains("- '**'"))
        assertTrue(header.contains("permissions:\n  contents: read"))
    }

    @Test
    fun releaseJobRequiresPassingTestGatesAndScopedBranches() {
        val workflow = readWorkflow()
        val release = releaseJobBlock(workflow)

        assertTrue(release.contains("- verify"))
        assertTrue(release.contains("- sdk-consumer-maven"))
        assertTrue(release.contains("startsWith(github.ref, 'refs/tags/v')"))
        assertTrue(release.contains("github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main'"))
        assertTrue(release.contains("contents: write"))
        assertTrue(release.contains("timeout-minutes:"))
        assertTrue(release.contains("if-no-files-found: error"))
        assertTrue(release.contains("retention-days: 90"))
        assertTrue(release.contains("is_dry_run != 'true' && steps.meta.outputs.is_tag_release == 'true'"))
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
    fun workflowEnforcesSecretRedactionCleanupAndApkSignatureVerification() {
        val release = releaseJobBlock(readWorkflow())
        val requiredFragments = listOf(
            "set +x",
            "umask 077",
            "mktemp -d",
            "trap cleanup EXIT",
            "rm -f app/google-services.json",
            "rm -f apps/atlas-android/app/google-services.json",
            ":app:assemblePlayRelease :app:assembleEnterpriseRelease",
            "*-unsigned.apk",
            "apksigner",
            "verify --verbose --print-certs",
            "SHA256SUMS.txt",
            "release-manifest.json",
            "FAIL-CLOSED",
            "dry-run-manifest.json",
            "apks_published",
        )
        for (fragment in requiredFragments) {
            assertTrue("Expected release pipeline guard '$fragment'", release.contains(fragment))
        }
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
