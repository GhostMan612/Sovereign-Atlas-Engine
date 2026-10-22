// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.navigation

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import com.sovereignatlas.atlas.geo.navigation.RestrictionType
import com.sovereignatlas.atlas.geo.navigation.RoadClass
import com.sovereignatlas.atlas.geo.navigation.RoadGraphBundleSerializer
import com.sovereignatlas.atlas.geo.navigation.RoadNetworkGraph
import com.sovereignatlas.atlas.geo.navigation.SurfaceType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AndroidRoadGraphLoader {
    const val NAVIGATION_DIR = "navigation"
    const val ROADS_FILE = "roads.db"
    const val ROADS_PACK_FILE = "roads.pack"

    suspend fun load(
        context: Context,
        allowDemoSimulation: Boolean = false,
    ): RoadNetworkGraph = withContext(Dispatchers.IO) {
        loadSynchronousOrEmpty(context, allowDemoSimulation)
    }

    fun loadFromDirectory(dir: File?): RoadNetworkGraph? {
        if (dir == null || !dir.isDirectory) return null
        val packFile = File(dir, ROADS_PACK_FILE)
        if (packFile.isFile) {
            val bundle = RoadGraphBundleSerializer.loadVerifiedFile(packFile)
            if (bundle != null && bundle.graph.nodes.isNotEmpty()) {
                return bundle.graph
            }
        }
        val dbFile = File(dir, ROADS_FILE)
        if (dbFile.isFile && RoadGraphBundleSerializer.verifyFileWithSidecar(dbFile)) {
            val dbGraph = loadFromDatabase(dbFile)
            if (dbGraph != null && dbGraph.nodes.isNotEmpty()) {
                return dbGraph
            }
        }
        return null
    }

    fun isRoutableDirectoryAvailable(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val packFile = File(dir, ROADS_PACK_FILE)
        if (packFile.isFile && packFile.length() > 0 && RoadGraphBundleSerializer.verifyFileWithSidecar(packFile)) {
            return true
        }
        val dbFile = File(dir, ROADS_FILE)
        return dbFile.isFile && dbFile.length() > 0 && RoadGraphBundleSerializer.verifyFileWithSidecar(dbFile)
    }

    fun loadSynchronousOrEmpty(
        context: Context,
        allowDemoSimulation: Boolean = false,
    ): RoadNetworkGraph {
        loadFromDirectory(context.getExternalFilesDir(NAVIGATION_DIR))?.let { return it }
        loadFromDirectory(File(context.filesDir, NAVIGATION_DIR))?.let { return it }
        loadFromDirectory(context.filesDir)?.let { return it }
        return if (allowDemoSimulation) {
            buildDemoSimulationGraph()
        } else {
            RoadNetworkGraph.EMPTY
        }
    }

    fun isRoutableDataAvailable(context: Context): Boolean {
        if (isRoutableDirectoryAvailable(context.getExternalFilesDir(NAVIGATION_DIR))) return true
        if (isRoutableDirectoryAvailable(File(context.filesDir, NAVIGATION_DIR))) return true
        return isRoutableDirectoryAvailable(context.filesDir)
    }

    private fun loadFromDatabase(file: File): RoadNetworkGraph? {
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val builder = RoadNetworkGraph.Builder()
            db.rawQuery("SELECT id, latitude, longitude FROM RoadNode", null).use { cursor ->
                while (cursor.moveToNext()) {
                    builder.addNode(
                        id = cursor.getLong(0),
                        lat = cursor.getDouble(1),
                        lon = cursor.getDouble(2),
                    )
                }
            }
            db.rawQuery(
                "SELECT id, fromNodeId, toNodeId, lengthMeters, speedKmh, name, roadClass, isOneWay, surface FROM RoadEdge",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val roadClassStr = cursor.getString(6) ?: "PRIMARY"
                    val roadClass = runCatching { RoadClass.valueOf(roadClassStr) }.getOrDefault(RoadClass.PRIMARY)
                    val surfaceStr = cursor.getString(8) ?: "PAVED"
                    val surface = runCatching { SurfaceType.valueOf(surfaceStr) }.getOrDefault(SurfaceType.PAVED)
                    builder.addEdge(
                        id = cursor.getLong(0),
                        fromNodeId = cursor.getLong(1),
                        toNodeId = cursor.getLong(2),
                        lengthMeters = cursor.getDouble(3),
                        speedKmh = cursor.getDouble(4),
                        name = cursor.getString(5) ?: "Road",
                        roadClass = roadClass,
                        isOneWay = cursor.getInt(7) == 1,
                        surface = surface,
                    )
                }
            }
            runCatching {
                db.rawQuery(
                    "SELECT fromEdgeId, viaNodeId, toEdgeId, restrictionType FROM TurnRestriction",
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val typeStr = cursor.getString(3) ?: "NO_LEFT_TURN"
                        val restrictionType = runCatching { RestrictionType.valueOf(typeStr) }
                            .getOrDefault(RestrictionType.NO_LEFT_TURN)
                        builder.addTurnRestriction(
                            fromEdgeId = cursor.getLong(0),
                            viaNodeId = cursor.getLong(1),
                            toEdgeId = cursor.getLong(2),
                            type = restrictionType,
                        )
                    }
                }
            }
            builder.build()
        } catch (e: SQLiteException) {
            Log.w("AndroidRoadGraphLoader", "Could not load road db: ${file.name}", e)
            null
        } finally {
            try {
                db?.close()
            } catch (e: Exception) {
                Unit
            }
        }
    }

    fun buildDemoSimulationGraph(): RoadNetworkGraph {
        val builder = RoadNetworkGraph.Builder()
        builder.addNode(1L, 37.7749, -122.4194)
        builder.addNode(2L, 37.7755, -122.4180)
        builder.addNode(3L, 37.7765, -122.4165)
        builder.addNode(4L, 37.7780, -122.4150)
        builder.addNode(5L, 37.7795, -122.4135)

        builder.addEdge(
            id = 101L,
            fromNodeId = 1L,
            toNodeId = 2L,
            lengthMeters = 150.0,
            speedKmh = 45.0,
            name = "Market Street",
            roadClass = RoadClass.PRIMARY,
        )
        builder.addEdge(
            id = 102L,
            fromNodeId = 2L,
            toNodeId = 3L,
            lengthMeters = 180.0,
            speedKmh = 45.0,
            name = "Market Street",
            roadClass = RoadClass.PRIMARY,
        )
        builder.addEdge(
            id = 103L,
            fromNodeId = 3L,
            toNodeId = 4L,
            lengthMeters = 220.0,
            speedKmh = 50.0,
            name = "Market Street",
            roadClass = RoadClass.PRIMARY,
        )
        builder.addEdge(
            id = 104L,
            fromNodeId = 4L,
            toNodeId = 5L,
            lengthMeters = 210.0,
            speedKmh = 50.0,
            name = "Market Street",
            roadClass = RoadClass.PRIMARY,
        )

        builder.addNode(6L, 37.7740, -122.4170)
        builder.addNode(7L, 37.7770, -122.4200)

        builder.addEdge(
            id = 105L,
            fromNodeId = 2L,
            toNodeId = 6L,
            lengthMeters = 200.0,
            speedKmh = 35.0,
            name = "8th Street",
            roadClass = RoadClass.SECONDARY,
        )
        builder.addEdge(
            id = 106L,
            fromNodeId = 2L,
            toNodeId = 7L,
            lengthMeters = 230.0,
            speedKmh = 35.0,
            name = "Hyde Street",
            roadClass = RoadClass.SECONDARY,
        )

        return builder.build()
    }
}
