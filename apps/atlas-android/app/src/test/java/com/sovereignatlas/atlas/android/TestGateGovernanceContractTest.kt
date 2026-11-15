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

class TestGateGovernanceContractTest {
    private val canonicalTasks = listOf(
        ":atlas-sdk:test",
        ":sdk-consumer:test",
        ":app:testPlayDebugUnitTest",
        ":app:testEnterpriseDebugUnitTest",
    )

    private fun findRepoRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".").canonicalFile
        val root = generateSequence(start) { it.parentFile }.firstOrNull { dir ->
            File(dir, "RULES.md").isFile &&
                File(dir, ".opencode/plugin/atlas-tools.ts").isFile
        }
        assertNotNull("Expected repository root containing RULES.md and atlas-tools.ts", root)
        return root!!
    }

    private fun readNormalized(relPath: String): String {
        return File(findRepoRoot(), relPath)
            .readText(Charsets.UTF_8)
            .replace("\r\n", "\n")
    }

    private fun extractSection(text: String, heading: String): String {
        val start = text.indexOf(heading)
        assertTrue("Expected section '$heading'", start >= 0)
        val afterHeading = start + heading.length
        val next = text.indexOf("\n## ", afterHeading)
        return if (next >= 0) text.substring(start, next) else text.substring(start)
    }

    @Test
    fun allAuthoritativeDocsAndAgentsDeclareTheFourSuiteHostGate() {
        val targets = listOf(
            "RULES.md",
            "AGENTS.md",
            "README.md",
            "SESSION_HANDOFF.md",
            ".opencode/commands/gates.md",
            ".opencode/commands/verify.md",
            ".opencode/agent/atlas-gatekeeper.md",
            ".opencode/agent/atlas-law-keeper.md",
            ".github/workflows/atlas-ci-safe.yml",
        )
        for (path in targets) {
            val content = readNormalized(path)
            for (task in canonicalTasks) {
                assertTrue("Expected '$task' in $path", content.contains(task))
            }
        }
    }

    @Test
    fun atlasToolsExecutesAndCountsAllFourHostModulesWithoutDroppingFailures() {
        val toolsText = readNormalized(".opencode/plugin/atlas-tools.ts")
        for (task in canonicalTasks) {
            assertTrue("Expected task '$task' in atlas-tools.ts", toolsText.contains("\"$task\""))
        }
        val requiredXmlDirs = listOf(
            "\\\\atlas-sdk\\\\build\\\\test-results\\\\test",
            "\\\\sdk-consumer\\\\build\\\\test-results\\\\test",
            "\\\\app\\\\build\\\\test-results\\\\testPlayDebugUnitTest",
            "\\\\app\\\\build\\\\test-results\\\\testEnterpriseDebugUnitTest",
        )
        for (xmlDir in requiredXmlDirs) {
            assertTrue("Expected XML discovery dir '$xmlDir' in atlas-tools.ts", toolsText.contains(xmlDir))
        }
        assertTrue(toolsText.contains("\"--continue\""))
        assertTrue(toolsText.contains("readNum(\"skipped\")"))
        assertTrue(toolsText.contains("FAILED TESTS:"))
        assertTrue(toolsText.contains("STALE/ABORTED SUITE WARNING:"))
    }

    @Test
    fun localCommandBlocksAndRulesPreserveBuildBoundaryAndDropStaleContradictions() {
        val agentsCommands = extractSection(readNormalized("AGENTS.md"), "## Commands")
        for (task in canonicalTasks) {
            assertTrue("Missing '$task' in AGENTS.md Commands", agentsCommands.contains(task))
        }

        val readmeVerification = extractSection(readNormalized("README.md"), "## Verification")
        for (task in canonicalTasks) {
            assertTrue("Missing '$task' in README.md Verification", readmeVerification.contains(task))
        }

        val rules = readNormalized("RULES.md")
        assertTrue(rules.contains("NEVER run builds here unless explicitly asked"))
        assertTrue(rules.contains("once per phase"))
        assertTrue(rules.contains("Stage by explicit path only"))

        val staleCleanupTargets = listOf(
            ".opencode/commands/gates.md",
            ".opencode/commands/verify.md",
            ".opencode/agent/atlas-gatekeeper.md",
        )
        for (path in staleCleanupTargets) {
            val content = readNormalized(path)
            assertFalse("Stale 352 test expectation remains in $path", content.contains("352"))
            assertFalse(
                "Obsolete unresolved-flake contradiction remains in $path",
                content.contains("unresolved harness") || content.contains("unresolved test-harness"),
            )
            assertTrue("Build boundary must stay documented in $path", content.contains("NEVER") || content.contains("never"))
        }

        val lawKeeper = readNormalized(".opencode/agent/atlas-law-keeper.md")
        assertTrue(lawKeeper.contains("there is no `:app:testDebugUnitTest`"))
    }
}
