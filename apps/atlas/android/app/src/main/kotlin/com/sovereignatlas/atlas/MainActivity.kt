package com.sovereignatlas.atlas

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    private var locationChannel: LocationChannel? = null
    private var headingChannel: HeadingChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val location = LocationChannel(this)
        location.attach(flutterEngine.dartExecutor)
        locationChannel = location
        val heading = HeadingChannel(this)
        heading.attach(flutterEngine.dartExecutor)
        headingChannel = heading
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
        headingChannel?.detach()
        headingChannel = null
        super.onDestroy()
    }
}
