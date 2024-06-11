package com.dprofe.locationalarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import org.osmdroid.api.IGeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private lateinit var map: MapView
    private lateinit var gpsLocProvider: GpsMyLocationProvider
    private lateinit var btnSettings: Button
    private lateinit var btnMe: Button
    private lateinit var btnPoint: Button
    private var targetPoint: GeoPoint? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        requestPermissionsIfNecessary(
            arrayListOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map_map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        map.overlays.add(RotationGestureOverlay(map))

        val mapNorthCompassOverlay = object : CompassOverlay(applicationContext, map) {
            override fun draw(c: Canvas?, pProjection: Projection?) {
                drawCompass(c, -map.mapOrientation, pProjection?.screenRect)
            }

            override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
                map.controller.animateTo(map.mapCenter, map.zoomLevelDouble, 500, 0f)
                return super.onSingleTapConfirmed(e, mapView)
            }
        }
        map.overlays.add(mapNorthCompassOverlay)

        gpsLocProvider = GpsMyLocationProvider(applicationContext)


        val mLocationOverlay = MyLocationNewOverlay(gpsLocProvider, map)
        mLocationOverlay.enableFollowLocation()
        mLocationOverlay.setPersonIcon(
            ResourcesCompat.getDrawable(resources, R.drawable.location_pointer, null)
                ?.toBitmap(150, 150)
        )
        mLocationOverlay.setPersonAnchor(.5f, 1f);
        mLocationOverlay.setDirectionIcon(
            ResourcesCompat.getDrawable(resources, R.drawable.arrow, null)?.toBitmap(100, 100)
        )
        mLocationOverlay.setDirectionAnchor(.5f, .5f);
        mLocationOverlay.enableMyLocation()
        map.overlays.add(mLocationOverlay)

        btnSettings = findViewById(R.id.map_btnSettings)
        btnSettings.setBackgroundResource(R.drawable.settings)
        btnSettings.setOnClickListener {
            PreferenceManager.getDefaultSharedPreferences(this).edit().let {
                it.putString("user-token", null)
                it.apply()
            }
            startActivity(Intent(this, SettingsActivity::class.java))
        }


        btnMe = findViewById(R.id.map_btnMe)
        btnMe.setBackgroundResource(R.drawable.my_location)
        btnMe.setOnClickListener {
            if (gpsLocProvider.lastKnownLocation != null) {
                if (distanceBetween(map.mapCenter, gpsLocProvider.lastKnownLocation) < 10) {
                    map.controller.animateTo(GeoPoint(gpsLocProvider.lastKnownLocation), 18.0,500, 0f)
                } else {
                    map.controller.animateTo(GeoPoint(gpsLocProvider.lastKnownLocation), map.zoomLevelDouble, 500)
                }
            }
        }

        btnPoint = findViewById(R.id.map_btnPoint)
        btnPoint.setBackgroundResource(R.drawable.point_location)
        btnPoint.setOnClickListener {
            if (targetPoint != null)
                map.controller.animateTo(targetPoint, 18.0, 1000)
        }

        thread {
            while (gpsLocProvider.lastKnownLocation == null) {
                Thread.sleep(100)
            }
            runOnUiThread {
                map.controller.animateTo(GeoPoint(gpsLocProvider.lastKnownLocation), 18.0, 1000)
            }
        }

        map.overlays.add(MapEventsOverlay(object: MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                TODO("Not yet implemented")
            }

        }))
    }
    private fun distanceBetween(a: GeoPoint, b: GeoPoint) : Float {
        val q = floatArrayOf(0f)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, q)
        return q[0]
    }

    private fun toGeoPoint(a: Any): GeoPoint? {
        return when(a) {
            is GeoPoint -> a
            is IGeoPoint -> GeoPoint(a)
            is Location -> GeoPoint(a)
            else -> null
        }
    }

    private fun distanceBetween(a: Any, b: Any) : Float {
        return distanceBetween(toGeoPoint(a) ?: return 0f, toGeoPoint(b) ?: return 0f)
    }

    private fun requestPermissionsIfNecessary(permissions: ArrayList<String>) {
        val permissionsToRequest = ArrayList<String>()
        permissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is not granted
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.size > 0) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toArray(Array(0) { _ -> "" }),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }
}