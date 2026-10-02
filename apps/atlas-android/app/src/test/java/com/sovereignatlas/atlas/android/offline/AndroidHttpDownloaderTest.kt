// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.offline

import com.sovereignatlas.atlas.offline.OfflineBuiltinProviders
import com.sovereignatlas.atlas.offline.resolveTileUrl
import com.sovereignatlas.atlas.offline.withApiKey
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
        // STILL TRUE, and still the assertion that matters — but for a different
        // reason than when it was written. The registry descriptors carry no key
        // because a credential must NEVER live in the pure registry: it is
        // injected per download by withApiKey from the encrypted KeyProvider.
        //
        // So this now pins the SECURITY property rather than a defect: the shared
        // singleton holds no secret, and any code path that forgets to inject gets
        // an unsubstituted {key} that the provider rejects, rather than a request
        // made with no credential or a guessed one.
        for (id in listOf("carto-positron", "carto-dark-matter")) {
            val provider = OfflineBuiltinProviders.lookup(id)!!
            val url = resolveTileUrl(provider, 1, 2, 3)
            assertTrue("$id must not have a baked-in key", url!!.contains("{key}"))
            assertNull("$id must not declare a key param", provider.params["key"])
        }
    }

    @Test
    fun anInjectedKeyMakesThePrefetchUrlRequestable() {
        // The counterpart: the gap above is only safe BECAUSE injection exists and
        // works. Without this, a caller could inject a key and still send {key}.
        val resolved = resolveTileUrl(
            OfflineBuiltinProviders.cartoPositron.withApiKey("runtime-key"),
            10, 1, 2,
        )
        assertFalse(
            "an injected key must not leave the placeholder behind",
            resolved!!.contains("{key}"),
        )
        assertTrue(resolved.contains("key=runtime-key"))
    }
}