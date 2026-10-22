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

    fun verifyFileWithSidecar(file: File): Boolean {
        if (!file.isFile) return false
        val sidecar = File(file.parentFile, "${file.name}.sha256")
        if (!sidecar.isFile) return true
        val expected = sidecar.readText(Charsets.UTF_8).trim().split(Regex("\\s+")).firstOrNull()
            ?: return false
        if (expected.isEmpty()) return false
        return verifyChecksum(file.readBytes(), expected)
    }

    fun renderPack(bundle: KnowledgeBundle): String {
        val sb = StringBuilder()
        sb.append("BUNDLE|")
            .append(sanitizeField(bundle.bundleId)).append("|")
            .append(sanitizeField(bundle.title)).append("|")
            .append(bundle.version).append("|")
            .append(sanitizeField(bundle.license)).append("\n")
        for (d in bundle.dossiers) {
            sb.append("DOSSIER|")
                .append(sanitizeField(d.id)).append("|")
                .append(sanitizeField(d.name)).append("|")
                .append(d.entityType.name).append("|")
                .append(d.temporal.validFromYear ?: "").append("|")
                .append(d.temporal.validToYear ?: "").append("|")
                .append(d.latitude).append("|")
                .append(d.longitude).append("|")
                .append(sanitizeField(d.description)).append("|")
                .append(d.confidence.name).append("|")
                .append(sanitizeField(d.primaryCitation.citationId)).append("|")
                .append(sanitizeField(d.primaryCitation.title)).append("|")
                .append(sanitizeField(d.primaryCitation.authorOrArchive)).append("|")
                .append(d.primaryCitation.publicationYear ?: "").append("|")
                .append(sanitizeField(d.primaryCitation.urlOrShelfmark)).append("|")
                .append(sanitizeField(d.primaryCitation.license)).append("|")
                .append(d.tags.joinToString(",") { sanitizeField(it) })
                .append("\n")
        }
        return sb.toString()
    }

    fun parsePack(raw: String): KnowledgeBundle? {
        var bundleId = ""
        var title = ""
        var version = 1
        var license = ""
        val dossiers = mutableListOf<HistoricalDossier>()
        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split("|")
            when (parts[0]) {
                "BUNDLE" -> {
                    if (parts.size < 5) return null
                    bundleId = parts[1].trim()
                    title = parts[2].trim()
                    version = parts[3].trim().toIntOrNull() ?: return null
                    license = parts[4].trim()
                }
                "DOSSIER" -> {
                    if (parts.size < 17) return null
                    val id = parts[1].trim()
                    val name = parts[2].trim()
                    val entityType = runCatching { EntityType.valueOf(parts[3].trim()) }
                        .getOrDefault(EntityType.PLACE)
                    val fromYear = parts[4].trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                    val toYear = parts[5].trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                    val lat = parts[6].trim().toDoubleOrNull() ?: return null
                    val lon = parts[7].trim().toDoubleOrNull() ?: return null
                    val description = parts[8].trim()
                    val confidence = runCatching { ConfidenceScore.valueOf(parts[9].trim()) }
                        .getOrDefault(ConfidenceScore.UNCERTAIN)
                    val citationId = parts[10].trim()
                    val citationTitle = parts[11].trim()
                    val citationArchive = parts[12].trim()
                    val pubYear = parts[13].trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                    val shelfmark = parts[14].trim()
                    val citationLicense = parts[15].trim()
                    val tags = parts[16].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (id.isEmpty() || name.isEmpty() || citationId.isEmpty() || citationTitle.isEmpty() || citationLicense.isEmpty()) {
                        return null
                    }
                    dossiers.add(
                        HistoricalDossier(
                            id = id,
                            name = name,
                            entityType = entityType,
                            temporal = TemporalInterval(fromYear, toYear),
                            latitude = lat,
                            longitude = lon,
                            description = description,
                            confidence = confidence,
                            primaryCitation = SourceCitation(
                                citationId = citationId,
                                title = citationTitle,
                                authorOrArchive = citationArchive,
                                publicationYear = pubYear,
                                urlOrShelfmark = shelfmark,
                                license = citationLicense,
                            ),
                            tags = tags,
                        ),
                    )
                }
            }
        }
        if (bundleId.isEmpty() || license.isEmpty() || dossiers.isEmpty()) return null
        return KnowledgeBundle(
            bundleId = bundleId,
            title = title,
            version = version,
            license = license,
            dossiers = dossiers,
            sha256Checksum = computeSha256(raw.toByteArray(Charsets.UTF_8)),
        )
    }

    fun loadVerifiedFile(file: File): KnowledgeBundle? {
        if (!verifyFileWithSidecar(file)) return null
        return runCatching {
            parsePack(file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    fun loadVerifiedDirectory(dir: File?, index: HistoricalKnowledgeIndex): Int {
        if (dir == null || !dir.isDirectory) return 0
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".pack") }
            ?.sortedBy { it.name }
            ?: return 0
        var count = 0
        for (file in files) {
            val bundle = loadVerifiedFile(file) ?: continue
            for (dossier in bundle.dossiers) {
                index.insert(dossier)
                count++
            }
        }
        return count
    }

    private fun sanitizeField(s: String): String {
        return s.replace("|", " ").replace("\n", " ").replace("\r", " ").trim()
    }

    private fun escape(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
