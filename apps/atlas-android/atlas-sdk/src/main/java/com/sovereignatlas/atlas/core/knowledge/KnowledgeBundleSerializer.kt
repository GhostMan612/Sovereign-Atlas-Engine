// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core.knowledge

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class KnowledgeBundle(
    val bundleId: String,
    val title: String,
    val version: Int,
    val license: String,
    val dossiers: List<HistoricalDossier>,
    val sha256Checksum: String? = null,
)

object KnowledgeBundleSerializer {

    fun computeSha256(content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun verifyChecksum(content: ByteArray, expectedSha256: String): Boolean {
        val computed = computeSha256(content)
        return computed.equals(expectedSha256, ignoreCase = true)
    }

    fun atomicWriteVerified(
        targetFile: File,
        content: ByteArray,
        expectedSha256: String,
    ): Boolean {
        if (!verifyChecksum(content, expectedSha256)) return false
        val parent = targetFile.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val stagingFile = File(parent, "${targetFile.name}.tmp")
        return try {
            stagingFile.writeBytes(content)
            val diskBytes = stagingFile.readBytes()
            if (!verifyChecksum(diskBytes, expectedSha256)) {
                stagingFile.delete()
                false
            } else {
                Files.move(
                    stagingFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
                true
            }
        } catch (error: Throwable) {
            stagingFile.delete()
            false
        }
    }

    fun renderJson(bundle: KnowledgeBundle): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"bundleId\": \"").append(escape(bundle.bundleId)).append("\",\n")
        sb.append("  \"title\": \"").append(escape(bundle.title)).append("\",\n")
        sb.append("  \"version\": ").append(bundle.version).append(",\n")
        sb.append("  \"license\": \"").append(escape(bundle.license)).append("\",\n")
        sb.append("  \"dossiers\": [\n")

        for (i in bundle.dossiers.indices) {
            val d = bundle.dossiers[i]
            sb.append("    {\n")
            sb.append("      \"id\": \"").append(escape(d.id)).append("\",\n")
            sb.append("      \"name\": \"").append(escape(d.name)).append("\",\n")
            sb.append("      \"entityType\": \"").append(d.entityType.name).append("\",\n")
            sb.append("      \"validFromYear\": ").append(d.temporal.validFromYear ?: "null").append(",\n")
            sb.append("      \"validToYear\": ").append(d.temporal.validToYear ?: "null").append(",\n")
            sb.append("      \"latitude\": ").append(d.latitude).append(",\n")
            sb.append("      \"longitude\": ").append(d.longitude).append(",\n")
            sb.append("      \"description\": \"").append(escape(d.description)).append("\",\n")
            sb.append("      \"confidence\": \"").append(d.confidence.name).append("\",\n")
            sb.append("      \"citationTitle\": \"").append(escape(d.primaryCitation.title)).append("\",\n")
            sb.append("      \"citationArchive\": \"").append(escape(d.primaryCitation.authorOrArchive)).append("\",\n")
            sb.append("      \"citationLicense\": \"").append(escape(d.primaryCitation.license)).append("\"\n")
            sb.append("    }")
            if (i < bundle.dossiers.size - 1) sb.append(",")
            sb.append("\n")
        }

        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    private fun escape(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
