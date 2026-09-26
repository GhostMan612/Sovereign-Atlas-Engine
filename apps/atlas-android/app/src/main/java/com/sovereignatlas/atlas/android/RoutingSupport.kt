// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import com.sovereignatlas.atlas.geo.routing.RoutingEdge
import com.sovereignatlas.atlas.geo.routing.RoutingEngine
import com.sovereignatlas.atlas.geo.routing.RoutingGraph
import com.sovereignatlas.atlas.geo.routing.RoutingGraphReader
import com.sovereignatlas.atlas.geo.routing.RoutingNode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqliteRoutingGraphReader(private val db: SQLiteDatabase) : RoutingGraphReader {
    override fun readNodes(): Sequence<RoutingNode> = sequence {
        db.rawQuery(
            "SELECT id, latitude, longitude, elevationMeters FROM RoutingNode",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                yield(
                    RoutingNode(
                        id = cursor.getInt(0),
                        latitude = cursor.getDouble(1),
                        longitude = cursor.getDouble(2),
                        elevationMeters = cursor.getDouble(3),
                    ),
                )
            }
        }
    }

    override fun readEdges(): Sequence<RoutingEdge> = sequence {
        db.rawQuery(
            "SELECT id, fromNodeId, toNodeId, distanceMeters, gainMeters, " +
                "descentMeters, slopeDegrees, trailType FROM RoutingEdge",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                yield(
                    RoutingEdge(
                        id = cursor.getInt(0),
                        fromNodeId = cursor.getInt(1),
                        toNodeId = cursor.getInt(2),
                        distanceMeters = cursor.getDouble(3),
                        gainMeters = cursor.getDouble(4),
                        descentMeters = cursor.getDouble(5),
                        slopeDegrees = cursor.getDouble(6),
                        trailType = cursor.getString(7) ?: "CROSS_COUNTRY",
                    ),
                )
            }
        }
    }
}

object AndroidRoutingLoader {
    const val ROUTING_DIR = "routing"
    const val ROUTING_FILE = "routing.db"

    suspend fun load(context: Context): RoutingEngine? = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(ROUTING_DIR) ?: return@withContext null
        val file = File(dir, ROUTING_FILE)
        if (!file.isFile) return@withContext null
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            val graph = RoutingGraph()
            graph.loadFrom(SqliteRoutingGraphReader(db))
            if (graph.nodes.isEmpty()) return@withContext null
            RoutingEngine(graph)
        } catch (error: SQLiteException) {
            Log.w("RoutingLoader", "Routing graph unreadable: ${file.name}", error)
            null
        } finally {
            try {
                db?.close()
            } catch (error: Exception) {
                Unit
            }
        }
    }
}
