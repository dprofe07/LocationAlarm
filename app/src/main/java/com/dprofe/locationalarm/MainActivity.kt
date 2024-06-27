package com.dprofe.locationalarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.location.Location
import android.media.RingtoneManager
import android.os.Bundle
import android.os.PowerManager
import android.preference.PreferenceManager
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import org.osmdroid.api.IGeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    lateinit var map: MapView
    private lateinit var gpsLocProvider: GpsMyLocationProvider
    private lateinit var btnSettings: Button
    private lateinit var btnMe: Button
    private lateinit var btnPoint: Button
    private lateinit var btnConfirmPoint: Button
    private lateinit var btnRemovePoint: Button
    var targetPoint: Marker? = null
    var presetPoint: Marker? = null
    var targetPointCirclePolygon: Polygon? = null
    var presetPointCirclePolygon: Polygon? = null
    var currentRadius = 100


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

        currentRadius = PreferenceManager.getDefaultSharedPreferences(this).getInt("radius", 100)

        map = findViewById(R.id.map_map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.zoomTo(5.0)
        map.minZoomLevel = 5.0
        map.maxZoomLevel = 21.0

        map.overlays.add(RotationGestureOverlay(map))

        gpsLocProvider = GpsMyLocationProvider(applicationContext)

        map.overlays.add(MapEventsOverlay(MyMapEventListener(this)))

        val locationOverlay = MyLocationNewOverlay(gpsLocProvider, map)
        locationOverlay.enableFollowLocation()
        locationOverlay.setPersonIcon(
            ResourcesCompat.getDrawable(resources, R.drawable.location_pointer, null)
                ?.toBitmap(150, 150)
        )
        locationOverlay.setPersonAnchor(.5f, 1f)
        locationOverlay.setDirectionIcon(
            ResourcesCompat.getDrawable(resources, R.drawable.arrow, null)?.toBitmap(100, 100)
        )
        locationOverlay.setDirectionAnchor(.5f, .5f)
        locationOverlay.enableMyLocation()
        map.overlays.add(locationOverlay)

        btnSettings = findViewById(R.id.map_btnSettings)
        btnSettings.setBackgroundResource(R.drawable.settings)
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.putExtra("radius", currentRadius)
            startActivityForResult(intent, 100)
        }

        btnConfirmPoint = findViewById(R.id.map_btnConfirmPoint)
        btnConfirmPoint.setOnClickListener {
            if (presetPoint == null) {
                Toast.makeText(this, "Сначала выберите точку на карте", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            targetPoint?.remove(map)
            map.overlays.remove(targetPointCirclePolygon)
            map.overlays.remove(presetPointCirclePolygon)
            presetPoint?.let {
                it.icon = ResourcesCompat.getDrawable(resources, R.drawable.alarm_point, null)?.toBitmap(50, 50)?.toDrawable(resources)
                targetPointCirclePolygon = createCirclePolygon(currentRadius.toDouble(), it, Color.rgb(0, 100, 0))
                map.overlays.add(targetPointCirclePolygon)
                targetPoint = it
                presetPoint = null
            }
            map.invalidate()
        }

        btnRemovePoint = findViewById(R.id.map_btnRemovePoint)
        btnRemovePoint.setOnClickListener {
            targetPoint?.remove(map)
            map.overlays.remove(targetPoint)
            map.overlays.remove(targetPointCirclePolygon)

            presetPoint?.remove(map)
            map.overlays.remove(presetPoint)
            map.overlays.remove(presetPointCirclePolygon)

            targetPoint = null
            presetPoint = null

            map.invalidate()
        }

        btnMe = findViewById(R.id.map_btnMe)
        btnMe.setBackgroundResource(R.drawable.my_location)
        btnMe.setOnClickListener {
            if (gpsLocProvider.lastKnownLocation != null) {
                val q = (17 / (map.zoomLevelDouble - 4) - 1) * 110 + 10
                if (distanceBetween(map.mapCenter, gpsLocProvider.lastKnownLocation) < q) {
                    map.controller.animateTo(
                        GeoPoint(gpsLocProvider.lastKnownLocation),
                        18.0,
                        500,
                        0f
                    )
                } else {
                    map.controller.animateTo(
                        GeoPoint(gpsLocProvider.lastKnownLocation),
                        map.zoomLevelDouble,
                        500
                    )
                }
            }
        }

        btnPoint = findViewById(R.id.map_btnPoint)
        btnPoint.setBackgroundResource(R.drawable.point_location)
        btnPoint.setOnClickListener {
            map.controller.animateTo(targetPoint?.position, 18.0, 1000)
        }

        thread {
            while (gpsLocProvider.lastKnownLocation == null) {
                Thread.sleep(100)
            }
            runOnUiThread {
                map.controller.animateTo(GeoPoint(gpsLocProvider.lastKnownLocation), 18.0, 1000)
            }
        }

        val northCompassOverlay = object : CompassOverlay(applicationContext, map) {
            override fun draw(c: Canvas?, pProjection: Projection?) {
                drawCompass(c, -map.mapOrientation, pProjection?.screenRect)
            }

            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView?): Boolean {
                val reuse = Point()
                map.projection.rotateAndScalePoint(e.x.toInt(), e.y.toInt(), reuse)

                if (reuse.x < mCompassFrameCenterX * 2 && reuse.y < mCompassFrameCenterY * 2) {
                    map.controller.animateTo(null, null, 500, 0f)
                    return true
                }
                return false
            }
        }
        map.overlays.add(northCompassOverlay)

        thread {
            while (distanceBetween(gpsLocProvider.lastKnownLocation, targetPoint?.position) > currentRadius) {
                Thread.sleep(100)
                Log.d("loc-alarm", "waiting, ${distanceBetween(gpsLocProvider.lastKnownLocation, targetPoint?.position)}")
            }
            Thread.sleep(4000)
            Log.d("loc-alarm", "shown")
            val powermanager = (getSystemService(POWER_SERVICE) as PowerManager)
            val wakeLock = powermanager.newWakeLock(
                (PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP),
                "locationalarm:tag"
            )
            wakeLock.acquire()
            val r = RingtoneManager.getRingtone(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALL))
            r.isLooping = true
            r.play()
            r.volume = 1f


            runOnUiThread { Toast.makeText(this, "ahaahahaaha", Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {
            currentRadius = data?.getIntExtra("radius", currentRadius) ?: currentRadius

            // redrawing
            map.overlays.remove(targetPointCirclePolygon)
            map.overlays.remove(presetPointCirclePolygon)

            targetPoint?.let {
                targetPointCirclePolygon = createCirclePolygon(currentRadius.toDouble(), it, Color.rgb(0, 100, 0))
                map.overlays.add(targetPointCirclePolygon)
            }
            presetPoint?.let {
                presetPointCirclePolygon = createCirclePolygon(currentRadius.toDouble(), it, Color.rgb(100, 100, 0))
                map.overlays.add(presetPointCirclePolygon)
            }
            map.invalidate()
        }
    }

    override fun onStop() {
        super.onStop()

        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putInt("radius", currentRadius)
        }
    }

    fun createCirclePolygon(radius: Double, marker: Marker, color: Int): Polygon {
        val q = Polygon(map)
        q.points = (0..359).map { f ->
            marker.position.destinationPoint(
                radius,
                f.toDouble()
            )
        }
        q.fillPaint.color = Color.argb(50, Color.red(color), Color.green(color), Color.blue(color))
        q.outlinePaint.color = Color.argb(50, Color.red(color), Color.green(color), Color.blue(color))
        q.setOnClickListener { _, _, _ ->
            return@setOnClickListener false
        }
        return q
    }


    private fun distanceBetween(a: GeoPoint, b: GeoPoint): Float {
        val q = floatArrayOf(0f)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, q)
        return q[0]
    }

    private fun toGeoPoint(a: Any?): GeoPoint? {
        return when (a) {
            is GeoPoint -> a
            is IGeoPoint -> GeoPoint(a)
            is Location -> GeoPoint(a)
            else -> null
        }
    }

    private fun distanceBetween(a: Any?, b: Any?): Float {
        return distanceBetween(toGeoPoint(a) ?: return Float.POSITIVE_INFINITY, toGeoPoint(b) ?: return Float.POSITIVE_INFINITY)
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