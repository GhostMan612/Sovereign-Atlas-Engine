// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.offline

import com.sovereignatlas.atlas.offline.OfflineBuiltinProviders
import com.sovereignatlas.atlas.offline.resolveTileUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the relocated transport's one behaviour that needs no network.
 *
 * The success path of [AndroidHttpDownloader] is an integration concern and is
 * exercised on device, not here: a unit test that reached a real tile server would
 * be a network test in a suite that promises to be offline.
 *
 * The no-template path is worth asserting precisely because it is silent. A
 * provider with `urlTemplate == null` - the local-bundle descriptor - cannot
 * serve tiles, and returning null here is what lets the downloader fail that pack
 * with a detail instead of attempting `URL(null)`.
 */
final class AndroidHttpDownloaderTest {

    @Test
    fun aProviderWithNoUrlTemplateYieldsNoChunkAndOpensNoConnection() {
        val localBundle = OfflineBuiltinProviders.localBundle
        assertNull(localBundle.urlTemplate)

        assertNull(AndroidHttpDownloader.httpChunk(localBundle, z = 0, x = 0, y = 0))
    }

    @Test
    fun everyBuiltInProviderTemplateResolvesExceptTheKnownCartoGap() {
        // A descriptor whose template still carries "{" after substitution would
        // send a literal placeholder to a tile server. Every provider resolves
        // cleanly except the CARTO pair, pinned separately below.
        val cartoIds = setOf("carto-positron", "carto-dark-matter")
        for (provider in OfflineBuiltinProviders.all) {
            if (provider.urlTemplate == null) continue
            if (provider.id in cartoIds) continue
            val url = resolveTileUrl(provider, 1, 2, 3)
            assertFalse("template left an unresolved placeholder: $url", url!!.contains("{"))
        }
    }

    @Test
    fun theCartoProvidersStillCarryAnUnresolvedKeyPlaceholder() {
        // KNOWN GAP, pinned deliberately. Both CARTO descriptors template
        // "?key={key}" but their params map supplies only "s", so resolveTileUrl
        // leaves {key} literal and a real request would send a placeholder as the
        // API key. CARTO requires a caller-supplied key, which is not present in
        // the repo and must not be invented (RULES 2.3).
        //
        // This test exists so the gap stays visible and so a future change cannot
        // quietly "fix" it by hardcoding a key. Delete it only when a real key is
        // supplied through a mechanism that does not commit the secret.
        for (id in listOf("carto-positron", "carto-dark-matter")) {
            val provider = OfflineBuiltinProviders.lookup(id)!!
            val url = resolveTileUrl(provider, 1, 2, 3)
            assertTrue("$id no longer has the known gap", url!!.contains("{key}"))
            assertNull("CARTO providers declare no key param", provider.params["key"])
        }
    }
}