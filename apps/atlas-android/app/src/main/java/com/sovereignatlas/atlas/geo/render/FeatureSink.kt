// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.render

/**
 * Every layer this application can draw, as a closed set.
 *
 * WHY A SEALED HIERARCHY RATHER THAN AN ENUM OF STRINGS. `AtlasLayerIds` declared 52
 * string constants. A string is not checked against anything, so a typo in a layer id
 * produced a write to a source that does not exist — and the audit found the failure
 * was silent in two of its three places, meaning a feature set could vanish with
 * nothing in the log. A sealed hierarchy makes an unrenderable layer unrepresentable:
 * the compiler rejects it before there is a chance to log nothing.
 *
 * THE RENDERABLE UNITS ARE SOURCE+LAYER PAIRS, NOT JUST LAYERS. A MapLibre layer
 * reads from exactly one source, and 28 push sites all wrote a source. Naming only
 * the layers would leave every call site reaching past the seam for a source id,
 * which is the coupling being removed.
 *
 * NOT EVERY INSTALLED LAYER APPEARS. The historical raster and mbtiles layers are
 * built from tile sets rather than GeoJSON, mounted and unmounted by id prefix, and
 * have no source this sink writes. They are deliberately absent: adding a variant
 * here for something that is not feature-driven would invite a caller to send
 * features at a layer that cannot accept them.
 *
 * `sourceId` and `layerId` are the existing string values, kept byte-identical, so
 * this type replaces the constants without changing the style document. The mbtiles
 * and historical prefixes remain string constants in `AtlasLayerIds` — they are
 * id-construction templates, not layers.
 */
sealed class AtlasLayer(
    val sourceId: String,
    val layerId: String,
) {
    /** Operator-created waypoints. */
    data object Waypoints : AtlasLayer("atlas-waypoints", "atlas-waypoints-layer")

    /** Stored and live-recorder tracks, merged into one layer. */
    data object Track : AtlasLayer("atlas-track", "atlas-track-layer")

    /** The scrub head while drawing a track. */
    data object Scrub : AtlasLayer("atlas-scrub", "atlas-scrub-layer")

    /** The measured segment, and its two endpoint dots. */
    data object Measure : AtlasLayer("atlas-measure", "atlas-measure-layer")

    data object MeasureDots : AtlasLayer("atlas-measure-dots", "atlas-measure-dots-layer")

    data object Graticule : AtlasLayer("atlas-graticule", "atlas-graticule-layer")

    data object RangeRings : AtlasLayer("atlas-rings", "atlas-rings-layer")

    /** Own position, bearing-aware. */
    data object Position : AtlasLayer("atlas-position", "atlas-position-layer")

    data object GoTo : AtlasLayer("atlas-goto", "atlas-goto-layer")

    data object Fence : AtlasLayer("atlas-fence", "atlas-fence-layer")

    data object MgrsLines : AtlasLayer("atlas-mgrs-lines", "atlas-mgrs-lines-layer")

    data object MgrsLabels : AtlasLayer("atlas-mgrs-labels", "atlas-mgrs-labels-layer")

    data object Route : AtlasLayer("atlas-tactical-route", "atlas-tactical-route-layer")

    /** Friendly / blue-force PLI. */
    data object Pli : AtlasLayer("atlas-tactical-pli", "atlas-tactical-pli-layer")

    /** Hostile, neutral, unknown and PIR/SAR markers. */
    data object Marker : AtlasLayer("cot-marker-source", "cot-marker-layer")

    /** Live mesh PLI and tracks from peers. */
    data object MeshTrack : AtlasLayer("mesh-track-source", "mesh-track-layer")

    /** Operator tactical drawings: zone fills and tactical lines, split by filter. */
    data object OpsGraphics : AtlasLayer("ops-graphics-source", "ops-zone-layer")

    /**
     * Survey parcels from the local catalogue.
     *
     * STILL PRODUCES MapLibre GEOMETRY. This one source is written by a mapper that
     * has not been converted to [RenderFeatureCollection] yet, so it needs
     * `MapLibreFeatureSink.replaceRaw`. That is a deliberate, marked gap rather than a
     * loophole: it is the only remaining caller of a raw write, and it is one of 22.
     */
    data object AssetParcels : AtlasLayer("historical-patents-source", "historical-patents-fill")

    /** The LoS observer puck. Its image was unregistered until commit 47c5890. */
    data object LosObserver : AtlasLayer("atlas-los-observer", "atlas-los-observer-layer")

    data object LosTarget : AtlasLayer("atlas-los-target", "atlas-los-target-layer")

    /** The LoS ray. Its colour carries the verdict, so it is also repainted. */
    data object LosRay : AtlasLayer("atlas-los-ray", "atlas-los-ray-layer")

    /** The first blocking point, when the engine found one. */
    data object LosBlock : AtlasLayer("atlas-los-block", "atlas-los-block-layer")

    companion object {
        /**
         * The source a layer reads from, for the handful of places that genuinely
         * need the id and are not a sink call — chiefly style installation.
         *
         * Null for an unknown layer rather than an exception: the audit found two
         * `getSourceAs` sites that returned silently on null, and this is the seam
         * that makes such a path impossible to write by accident.
         */
        fun sourceIdFor(layer: AtlasLayer): String? = layer.sourceId
    }
}

/**
 * Where renderable features go.
 *
 * THE SEAM ITSELF, AND DELIBERATELY NARROW. One method. The audit found 28 push
 * sites all doing the same thing — build a collection, overwrite a source — and every
 * one of them was reaching for `Style` to do it. One method absorbs all of them.
 *
 * What is NOT here, on purpose:
 *
 * - No layer installation. Adding a source or layer is a style-construction concern
 *   with an ordering dependency (the standing fact that `addLayerBelow` has produced
 *   layers that exist and never paint), and folding it in would put ordering rules
 *   behind an interface that cannot express them.
 * - No camera. `Style` has nothing to do with where the map is looking.
 * - No paint mutation. The LoS ray colour is set directly on its `LineLayer`; that
 *   is a style concern, not a feature-stream one.
 * - No visibility toggling. Same reasoning, and it has its own ordered dependency on
 *   layer type.
 *
 * Those remain in the UI tier where they belong. An interface that grows to cover
 * everything MapLibre can do has not decoupled anything; it has just moved the
 * problem one file to the left.
 *
 * `replace` rather than `add`: every existing call site overwrote, and two pushed an
 * empty collection to clear. A sink that could append would make "clear this layer"
 * ambiguous.
 */
fun interface FeatureSink {
    /**
     * Replaces the entire contents of [layer] with [features].
     *
     * Passing [RenderFeatureCollection.EMPTY] clears the layer. That is a distinct
     * and supported operation, not an edge case: the graticule clears itself when the
     * viewport is invalid or the line budget is blown, and a sink that treated empty
     * as a no-op would leave a stale grid on screen.
     *
     * Must not throw for a missing layer. A sink that threw would turn a styling bug
     * into a crash on the map, and the audit's standing fact is precisely that these
     * failures are silent today — the correct fix is a log line, not an exception.
     */
    fun replace(layer: AtlasLayer, features: RenderFeatureCollection)
}
