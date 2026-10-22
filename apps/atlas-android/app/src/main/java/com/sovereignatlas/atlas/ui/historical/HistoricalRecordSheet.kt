// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.historical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.core.HistoricalAsset
import com.sovereignatlas.atlas.core.HistoricalLicense
import com.sovereignatlas.atlas.core.HistoricalRecord
import com.sovereignatlas.atlas.core.LandPatent
import com.sovereignatlas.atlas.core.SanbornBlueprint
import com.sovereignatlas.atlas.core.knowledge.ConfidenceScore
import com.sovereignatlas.atlas.core.knowledge.HistoricalDossier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalRecordSheet(
    record: HistoricalRecord?,
    onDismissRequest: () -> Unit
) {
    if (record == null) return
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Date: ${record.date} | Source: ${record.sourceLayer}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = record.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Sheet for a resolved historical asset read from the catalogue.
 *
 * Every field is rendered from the domain object, never from the rendered feature,
 * because the feature is a lossy projection that omits unknown values entirely.
 * An absent field prints "Not recorded" rather than being hidden: on a patent
 * parcel, a missing patentee name and a name that was never transcribed are
 * different facts and the operator needs to see which one they are looking at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalAssetSheet(
    asset: HistoricalAsset?,
    onDismissRequest: () -> Unit,
) {
    if (asset == null) return
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = asset.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (asset.year == 0) NOT_RECORDED else asset.year.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            when (asset) {
                is LandPatent -> PatentFields(asset)
                is SanbornBlueprint -> BlueprintFields(asset)
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            AssetProvenance(asset)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PatentFields(patent: LandPatent) {
    DetailRow("Patent number", patent.patentNumber)
    DetailRow("Patentee", patent.patenteeName.orPlaceholder())
    DetailRow("Issue date", patent.issueDate.orPlaceholder())
    DetailRow("Acreage", patent.acreage?.let { "$it acres" }.orPlaceholder())
    DetailRow("Township", patent.township.orPlaceholder())
    DetailRow("State", patent.state.orPlaceholder())
    DetailRow("County", patent.county.orPlaceholder())
    DetailRow(
        "Legal description",
        patent.legalDescription.orPlaceholder(),
    )
}

@Composable
private fun BlueprintFields(blueprint: SanbornBlueprint) {
    DetailRow("Sheet", blueprint.sheet)
    DetailRow("Edition", blueprint.edition.toString())
    DetailRow(
        "Zoom range",
        if (blueprint.minZoom == null || blueprint.maxZoom == null) {
            NOT_RECORDED
        } else {
            "z${formatZoom(blueprint.minZoom)} to z${formatZoom(blueprint.maxZoom)}"
        },
    )
    DetailRow("Format", blueprint.format.orPlaceholder())
}

private fun formatZoom(zoom: Float): String =
    if (zoom == zoom.toInt().toFloat()) zoom.toInt().toString() else zoom.toString()

@Composable
private fun AssetProvenance(asset: HistoricalAsset) {
    DetailRow("Licence", asset.license.label())
    DetailRow("Attribution", asset.attribution.orPlaceholder())
    val box = asset.boundingBox
    DetailRow(
        "Extent",
        "%.4f, %.4f to %.4f, %.4f".format(box.south, box.west, box.north, box.east),
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(132.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val NOT_RECORDED = "Not recorded"

private fun String?.orPlaceholder(): String = this ?: NOT_RECORDED

private fun HistoricalLicense.label(): String = when (this) {
    is HistoricalLicense.PublicDomain -> "Public Domain"
    is HistoricalLicense.PublicDomainDedicated -> "Public Domain (dedicated: $dedication)"
    is HistoricalLicense.PublicDomainUsOnly -> "Public Domain ($jurisdiction only)"
    is HistoricalLicense.Unverified -> "Unverified: $note"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalDossierSheet(
    dossier: HistoricalDossier?,
    onDismissRequest: () -> Unit,
) {
    if (dossier == null) return
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dossier.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                ConfidenceBadge(dossier.confidence)
            }
            Spacer(modifier = Modifier.height(4.dp))
            val dateRangeStr = when {
                dossier.temporal.exactDateIso != null -> dossier.temporal.exactDateIso
                dossier.temporal.validFromYear != null && dossier.temporal.validToYear != null ->
                    "${dossier.temporal.validFromYear} \u2013 ${dossier.temporal.validToYear}"
                dossier.temporal.validFromYear != null -> "From ${dossier.temporal.validFromYear}"
                dossier.temporal.validToYear != null -> "Until ${dossier.temporal.validToYear}"
                else -> NOT_RECORDED
            }
            Text(
                text = "${dossier.entityType.name} | Period: $dateRangeStr",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = dossier.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            DetailRow("Coordinates", "%.4f, %.4f".format(dossier.latitude, dossier.longitude))
            DetailRow("Primary Citation", dossier.primaryCitation.title)
            DetailRow("Archive / Author", dossier.primaryCitation.authorOrArchive)
            dossier.primaryCitation.publicationYear?.let {
                DetailRow("Year", it.toString())
            }
            DetailRow("Shelfmark / URL", dossier.primaryCitation.urlOrShelfmark)
            DetailRow("License", dossier.primaryCitation.license)

            if (dossier.alternativeClaims.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Alternative / Disputed Interpretations",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                dossier.alternativeClaims.forEach { claim ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "\u2022 ${claim.claimDescription}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "  Source: ${claim.conflictingSource.title} (${claim.conflictingSource.authorOrArchive})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            if (dossier.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Tags: ${dossier.tags.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: ConfidenceScore) {
    val (label, containerColor, contentColor) = when (confidence) {
        ConfidenceScore.CONFIRMED -> Triple(
            "CONFIRMED",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ConfidenceScore.PROBABLE -> Triple(
            "PROBABLE",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        ConfidenceScore.UNCERTAIN -> Triple(
            "UNCERTAIN",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        ConfidenceScore.DISPUTED -> Triple(
            "DISPUTED",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

