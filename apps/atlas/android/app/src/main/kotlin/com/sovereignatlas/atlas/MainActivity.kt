package com.sovereignatlas.atlas

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    private var locationChannel: LocationChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val channel = LocationChannel(this)
        channel.attach(flutterEngine.dartExecutor)
        locationChannel = channel
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationChannel?.onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onDestroy() {
        locationChannel?.detach()
        locationChannel = null
        super.onDestroy()
    }
}
