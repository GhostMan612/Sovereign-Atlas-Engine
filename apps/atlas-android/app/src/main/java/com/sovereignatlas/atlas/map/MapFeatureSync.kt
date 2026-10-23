// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.core.content.ContextCompat
import com.sovereignatlas.atlas.AtlasServices
import com.sovereignatlas.atlas.R
import com.sovereignatlas.atlas.android.AndroidRoutingLoader
import com.sovereignatlas.atlas.core.AtlasBoundingBox
import com.sovereignatlas.atlas.core.LngLat
import com.sovereignatlas.atlas.core.knowledge.HistoricalKnowledgeIndex
import com.sovereignatlas.atlas.db.Track
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGrids
import com.sovereignatlas.atlas.geo.GeoPoint
import com.sovereignatlas.atlas.geo.LoSRequest
import com.sovereignatlas.atlas.geo.cot.CotMarker
import com.sovereignatlas.atlas.geo.cot.CotPli
import com.sovereignatlas.atlas.geo.graphics.OperationalGraphic
import com.sovereignatlas.atlas.geo.los.TerrainProfile
import com.sovereignatlas.atlas.geo.navigation.RoadRouteResult
import com.sovereignatlas.atlas.geo.render.AtlasLayer
import com.sovereignatlas.atlas.geo.render.FeatureSink
import com.sovereignatlas.atlas.geo.render.RenderFeature
import com.sovereignatlas.atlas.geo.render.RenderFeatureCollection
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import com.sovereignatlas.atlas.geo.routing.LoadProfile
import com.sovereignatlas.atlas.geo.routing.RoutingRequest
import com.sovereignatlas.atlas.geo.routing.RoutingResult
import com.sovereignatlas.atlas.location.AtlasLocationStatus
import com.sovereignatlas.atlas.map.cot.CotGeoJsonMapper
import com.sovereignatlas.atlas.map.graphics.GraphicsGeoJsonMapper
import com.sovereignatlas.atlas.tactical.RadialFence
import com.sovereignatlas.atlas.tactical.fencePolygon
import com.sovereignatlas.atlas.track.ProfilePoint
import com.sovereignatlas.atlas.track.TrackRecorder
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import kotlin.math.roundToInt

private val wpIconBitmap: Bitmap by lazy {
    val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.parseColor("#10151C")
    canvas.drawCircle(12f, 12f, 12f, paint)
    paint.color = Color.parseColor("#F4F7FA")
    canvas.drawCircle(12f, 12f, 10.5f, paint)
    paint.color = Color.parseColor("#E5484D")
    canvas.drawCircle(12f, 12f, 8.5f, paint)
    paint.color = Color.parseColor("#10151C")
    canvas.drawCircle(12f, 12f, 3.6f, paint)
    paint.color = Color.parseColor("#39C5F3")
    canvas.drawCircle(12f, 12f, 2.2f, paint)
    bitmap
}

fun ensureWaypointIcon(style: Style) {
    if (style.getImage("wp-icon") == null) {
        style.addImage("wp-icon", wpIconBitmap)
    }
}

fun ensureGpsPuck(style: Style, context: Context) {
    // Both names, one drawable. The LoS observer layer asks for "user-puck" and the
    // position layer asks for "gps-puck-icon"; they are the same puck seen from two
    // tools, and only the second was ever registered. The observer marker therefore
    // had features in its source and painted nothing — the standing-facts failure
    // mode (source has data, layer silently blank) with no error anywhere to
    // explain it.
    //
    // Aliased rather than given its own asset: a second drawable would be a second
    // thing to keep in sync for no visual gain. Registering both names from one
    // decode also means one bitmap object, not two.
    if (style.getImage("gps-puck-icon") == null || style.getImage(AtlasLayerIds.USER_PUCK_IMAGE) == null) {
        val bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_gps_puck_sdf,
        ) ?: return
        if (style.getImage("gps-puck-icon") == null) {
            style.addImage("gps-puck-icon", bitmap, true)
        }
        if (style.getImage(AtlasLayerIds.USER_PUCK_IMAGE) == null) {
            style.addImage(AtlasLayerIds.USER_PUCK_IMAGE, bitmap, true)
        }
    }
}

fun ensurePliMarker(style: Style, context: Context) {
    if (style.getImage("blue-force-marker") == null) {
        // ic_blue_force_marker is a VectorDrawable; BitmapFactory cannot decode it
        // and returns null, so the Drawable overload is required here.
        val pliDrawable = ContextCompat.getDrawable(
            context,
            R.drawable.ic_blue_force_marker,
        )
        if (pliDrawable == null) {
            Log.e("AtlasMap", "Failed to load PLI marker drawable")
            return
        }
        style.addImage("blue-force-marker", pliDrawable)
    }
}

fun markerColorHex(type: String): String {
    return when {
        type.startsWith("a-h-") -> "#ff0000"
        type.startsWith("a-n-") -> "#00ff00"
        type.startsWith("a-u-") -> "#ffff00"
        else -> "#ffffff"
    }
}

fun markersToFeatures(markers: Map<String, CotMarker>): RenderFeatureCollection =
    RenderFeatureCollection(
        markers.values.map { marker ->
            RenderFeature(
                geometry = RenderGeometry.Point(LngLat(marker.longitude, marker.latitude)),
                id = marker.uid,
                properties = mapOf(
                    "color" to RenderProperty.Text(markerColorHex(marker.type)),
                    "callsign" to RenderProperty.Text(marker.callsign),
                ),
            )
        },
    )

fun pushMarkers(sink: FeatureSink, markers: Map<String, CotMarker>) {
    sink.replace(AtlasLayer.Marker, markersToFeatures(markers))
}

fun pushOpsGraphics(sink: FeatureSink, graphics: List<OperationalGraphic>) {
    sink.replace(AtlasLayer.OpsGraphics, GraphicsGeoJsonMapper.toRenderFeatures(graphics))
}

fun ensureCotTrackIcons(style: Style, context: Context) {
    val icons = listOf(
        "friendly" to R.drawable.ic_friendly,
        "hostile" to R.drawable.ic_hostile,
        "neutral" to R.drawable.ic_neutral,
        "unknown" to R.drawable.ic_unknown,
    )
    for ((name, resId) in icons) {
        if (style.getImage(name) != null) continue
        val drawable = ContextCompat.getDrawable(context, resId) ?: continue
        style.addImage(name, drawable)
    }
}

fun pushMeshTracks(sink: FeatureSink, markers: Map<String, CotMarker>) {
    sink.replace(
        AtlasLayer.MeshTrack,
        CotGeoJsonMapper.toRenderFeatures(markers.values.toList()),
    )
}

fun pushPli(sink: FeatureSink, plis: Map<String, CotPli>) {
    sink.replace(
        AtlasLayer.Pli,
        RenderFeatureCollection(
            plis.values.map { pli ->
                RenderFeature(
                    geometry = RenderGeometry.Point(LngLat(pli.longitude, pli.latitude)),
                    id = pli.uid,
                    properties = mapOf("callsign" to RenderProperty.Text(pli.callsign)),
                )
            },
        ),
    )
}

private val scrubIconBitmap: Bitmap by lazy {
    val bitmap = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    paint.color = Color.parseColor("#10151C")
    paint.strokeWidth = 6.5f
    canvas.drawCircle(14f, 14f, 10.5f, paint)
    paint.color = Color.parseColor("#39C5F3")
    paint.strokeWidth = 3.2f
    canvas.drawCircle(14f, 14f, 10.5f, paint)
    paint.strokeWidth = 2.2f
    paint.color = Color.parseColor("#10151C")
    canvas.drawLine(14f, 1f, 14f, 6f, paint)
    canvas.drawLine(14f, 22f, 14f, 27f, paint)
    canvas.drawLine(1f, 14f, 6f, 14f, paint)
    canvas.drawLine(22f, 14f, 27f, 14f, paint)
    paint.strokeWidth = 1.2f
    paint.color = Color.parseColor("#F4F7FA")
    canvas.drawLine(14f, 1f, 14f, 6f, paint)
    canvas.drawLine(14f, 22f, 14f, 27f, paint)
    canvas.drawLine(1f, 14f, 6f, 14f, paint)
    canvas.drawLine(22f, 14f, 27f, 14f, paint)
    bitmap
}

fun ensureScrubIcon(style: Style) {
    if (style.getImage("scrub-icon") == null) {
        style.addImage("scrub-icon", scrubIconBitmap)
    }
}

fun pushScrubPoint(sink: FeatureSink, point: ProfilePoint?) {
    sink.replace(
        AtlasLayer.Scrub,
        if (point == null) {
            RenderFeatureCollection.EMPTY
        } else {
            RenderFeatureCollection(
                listOf(
                    RenderFeature(
                        geometry = RenderGeometry.Point(
                            LngLat(point.longitude, point.latitude),
                        ),
                        id = "scrub",
                    ),
                ),
            )
        },
    )
}

suspend fun refreshFootRoute(
    services: AtlasServices,
    context: Context,
    fromLat: Double,
    fromLng: Double,
) {
    val target = services.goTo.targetOrNull()
    if (target == null) {
        services.routing.setResult(null)
        return
    }
    val engine = services.routing.ensureEngine {
        AndroidRoutingLoader.load(context.applicationContext)
    } ?: return
    val startId = engine.nearestNodeId(fromLat, fromLng) ?: return
    val endId = engine.nearestNodeId(target.latitude, target.longitude) ?: return
    // PATROL is the default foot profile; a selector is follow-up UI.
    val result = engine.route(RoutingRequest(startId, endId, LoadProfile.PATROL))
    services.routing.setResult(result)
}

fun pushRouteResult(sink: FeatureSink, result: RoutingResult?) {
    sink.replace(
        AtlasLayer.Route,
        if (result == null || result.path.size < 2) {
            RenderFeatureCollection.EMPTY
        } else {
            RenderFeatureCollection(
                listOf(
                    RenderFeature(
                        geometry = RenderGeometry.LineString(
                            result.path.map { node ->
                                LngLat(node.longitude, node.latitude)
                            },
                        ),
                        id = "route",
                    ),
                ),
            )
        },
    )
}

fun pushRoadRouteResult(sink: FeatureSink, route: RoadRouteResult?) {
    sink.replace(
        AtlasLayer.Route,
        if (route == null || route.coordinates.size < 2) {
            RenderFeatureCollection.EMPTY
        } else {
            RenderFeatureCollection(
                listOf(
                    RenderFeature(
                        geometry = RenderGeometry.LineString(
                            route.coordinates.map { coord ->
                                LngLat(coord.longitude, coord.latitude)
                            },
                        ),
                        id = "road_route",
                    ),
                ),
            )
        },
    )
}

suspend fun runLosCalculation(services: AtlasServices) {
    val observer = services.losState.observer.value
    val target = services.losState.target.value
    if (observer == null || target == null) return
    // One engine for both the two-tap tool and the long-press analysis sheet, so
    // the map overlay and the profile chart cannot contradict each other. When no
    // DEM is active the engine reports NoTerrainData itself - a distinct verdict,
    // not a synthetic "blocked".
    services.losState.setProfile(
        services.lineOfSightEngine.calculateProfile(LoSRequest(observer, target)),
    )
}

fun pushLosState(
    sink: FeatureSink,
    observer: GeoPoint?,
    target: GeoPoint?,
    profile: TerrainProfile?,
) {
    val verdict = profile?.lineOfSight

    fun pointFeature(id: String, at: GeoPoint?) = if (at == null) {
        RenderFeatureCollection.EMPTY
    } else {
        RenderFeatureCollection(
            listOf(
                RenderFeature(
                    geometry = RenderGeometry.Point(LngLat(at.longitude, at.latitude)),
                    id = id,
                ),
            ),
        )
    }

    sink.replace(AtlasLayer.LosObserver, pointFeature("los-observer", observer))
    sink.replace(AtlasLayer.LosTarget, pointFeature("los-target", target))
    sink.replace(
        AtlasLayer.LosRay,
        if (observer == null || target == null) {
            RenderFeatureCollection.EMPTY
        } else {
            RenderFeatureCollection(
                listOf(
                    RenderFeature(
                        geometry = RenderGeometry.LineString(
                            listOf(
                                LngLat(observer.longitude, observer.latitude),
                                LngLat(target.longitude, target.latitude),
                            ),
                        ),
                        id = "los-ray",
                    ),
                ),
            )
        },
    )
    sink.replace(AtlasLayer.LosBlock, pointFeature("los-block", verdict?.blockingPoint))

    // Paint stays on the Style. Repainting a layer is a style concern with an
    // ordering dependency on the layer existing; the sink carries features, not
    // paint. This is the deliberate line in the interface's documentation.
    val ray = (sink as? MapLibreFeatureSink)?.currentStyle()
        ?.getLayerAs<LineLayer>(AtlasLayerIds.LOS_LAYER)
    // Amber, not red, for an unmeasured verdict. Red means terrain BLOCKED the ray;
    // painting unmeasured ground red asserts a terrain finding nobody made.
    //
    // The three-arm when this replaced collapsed IncompleteTerrain, NoTerrainData,
    // DegenerateGeometry and ProviderError into one amber, undoing the Phase 9 §9.5
    // split. LosRenderStyle now carries one entry per status, and the dash pattern is
    // the second channel: a dashed line is visibly "partly an assumption" where a
    // solid one reads as a finding.
    val render = LosRenderStyle.forStatus(verdict?.status)
    ray?.setProperties(
        PropertyFactory.lineColor(render.lineColor),
        PropertyFactory.lineWidth(render.lineWidth),
    )
    // A null dash array clears the pattern, which is why the solid states pass null
    // rather than "no dash set": a CLEAR verdict after an INCOMPLETE one must return
    // to a solid line, and skipping the call would leave the previous dashes up.
    ray?.setProperties(
        if (render.lineDashArray == null) {
            PropertyFactory.lineDasharray(null as Array<Float>?)
        } else {
            PropertyFactory.lineDasharray(render.lineDashArray)
        },
    )
}

fun mergedTrackFeatures(
    stored: List<Track>,
    recorder: TrackRecorder,
): RenderFeatureCollection {
    val features = ArrayList<RenderFeature>(tracksToFeatures(stored).features)
    val active = recorder.points()
    if (active.size >= 2) {
        features.add(
            RenderFeature(
                geometry = RenderGeometry.LineString(
                    active.map { fix ->
                        LngLat(fix.position.longitude, fix.position.latitude)
                    },
                ),
                id = "live-track",
            ),
        )
    }
    return RenderFeatureCollection(features)
}

fun pushPosition(sink: FeatureSink, services: AtlasServices) {
    val fix = services.location.latestFixOrNull() ?: return
    sink.replace(
        AtlasLayer.Position,
        RenderFeatureCollection(listOf(positionToFeature(fix.position, fix.headingDeg))),
    )
}

fun pushMeasure(sink: FeatureSink, services: AtlasServices) {
    val measure = services.measure
    val a = measure.pointAOrNull()
    val b = measure.pointBOrNull()
    sink.replace(
        AtlasLayer.Measure,
        if (a != null && b != null) measureToFeatures(a, b) else RenderFeatureCollection.EMPTY,
    )
    sink.replace(
        AtlasLayer.MeasureDots,
        RenderFeatureCollection(
            listOfNotNull(
                a?.let { RenderFeature(RenderGeometry.Point(LngLat(it.longitude, it.latitude)), "measure-a") },
                b?.let { RenderFeature(RenderGeometry.Point(LngLat(it.longitude, it.latitude)), "measure-b") },
            ),
        ),
    )
}

fun pushFence(sink: FeatureSink, fence: RadialFence?) {
    sink.replace(
        AtlasLayer.Fence,
        if (fence == null || !fence.armed) {
            RenderFeatureCollection.EMPTY
        } else {
            RenderFeatureCollection(
                listOf(
                    RenderFeature(
                        geometry = RenderGeometry.LineString(
                            fencePolygon(fence.center, fence.radiusMeters).map { point ->
                                LngLat(point.longitude, point.latitude)
                            },
                        ),
                        id = "fence",
                    ),
                ),
            )
        },
    )
}

fun pushGoTo(sink: FeatureSink, services: AtlasServices) {
    val target = services.goTo.targetOrNull()
    sink.replace(
        AtlasLayer.GoTo,
        if (target == null) {
            RenderFeatureCollection.EMPTY
        } else {
            RenderFeatureCollection(
                listOf(goToToFeature(target.latitude, target.longitude, target.label)),
            )
        },
    )
}

fun usableFixOf(services: AtlasServices): AtlasCoordinate? {
    return if (services.location.status() == AtlasLocationStatus.valid) {
        services.location.latestFixOrNull()?.position
    } else {
        null
    }
}

fun pushRings(sink: FeatureSink, services: AtlasServices) {
    sink.replace(
        AtlasLayer.RangeRings,
        ringsToFeatures(usableFixOf(services), RING_STEP_INDEX),
    )
}

fun pushGraticule(map: MapLibreMap, sink: FeatureSink) {
    val region = map.projection.visibleRegion.latLngBounds
    val box = AtlasBoundingBox(
        south = region.latitudeSouth,
        west = region.longitudeWest,
        north = region.latitudeNorth,
        east = region.longitudeEast,
    )
    if (!box.validate().isValid) {
        sink.replace(AtlasLayer.Graticule, RenderFeatureCollection.EMPTY)
        return
    }
    val grid = AtlasGrids.graticuleFor(
        box,
        AtlasGrids.intervalForZoom(map.cameraPosition.zoom.roundToInt()),
    )
    if (grid.meridians.size + grid.parallels.size > MAX_GRATICULE_LINES) {
        sink.replace(AtlasLayer.Graticule, RenderFeatureCollection.EMPTY)
        return
    }
    sink.replace(
        AtlasLayer.Graticule,
        graticuleToFeatures(box, AtlasGrids.intervalForZoom(map.cameraPosition.zoom.roundToInt())),
    )
}

fun pushHistoricalDossiers(
    sink: FeatureSink,
    index: HistoricalKnowledgeIndex,
    epochYear: Int? = null,
) {
    val allDossiers = if (epochYear != null) index.queryTemporal(epochYear) else index.getAll()
    val features = allDossiers.map { dossier ->
        RenderFeature(
            geometry = RenderGeometry.Point(LngLat(dossier.longitude, dossier.latitude)),
            id = dossier.id,
            properties = mapOf(
                "name" to RenderProperty.Text(dossier.name),
                "entityType" to RenderProperty.Text(dossier.entityType.name),
            ),
        )
    }
    (sink as? MapLibreFeatureSink)?.replaceSourceFeatures(
        AtlasLayerIds.HISTORICAL_DOSSIERS_SOURCE,
        RenderFeatureCollection(features),
    )
}
