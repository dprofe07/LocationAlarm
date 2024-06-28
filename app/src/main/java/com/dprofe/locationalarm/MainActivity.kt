package com.dprofe.locationalarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.preference.PreferenceManager
import android.util.Log
import android.view.MotionEvent
import android.view.View
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
    lateinit var locationListener: LocationListener
    var targetPoint: Marker? = null
    var presetPoint: Marker? = null
    var targetPointCirclePolygon: Polygon? = null
    var presetPointCirclePolygon: Polygon? = null


    @SuppressLint("MissingPermission")
    // to remove displaying error, all permission are checked
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        requestPermissionsIfNecessary(
            arrayListOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
                .apply { if (Build.VERSION.SDK_INT > 29) add(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
                .apply { if (Build.VERSION.SDK_INT > 33) add(Manifest.permission.POST_NOTIFICATIONS) }
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
        map.overlays.add(MapEventsOverlay(MyMapEventListener(this)))


        if (isGranted(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            locationListener = LocationTrackerListener(this)
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1,
                0f, locationListener
            )
        }

        gpsLocProvider = GpsMyLocationProvider(applicationContext)

        val locationOverlay = MyLocationNewOverlay(gpsLocProvider, map)
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

        configureButtons()
        configureCompass()

        thread {
            while (gpsLocProvider.lastKnownLocation == null) {
                Thread.sleep(100)
            }
            runOnUiThread {
                map.controller.animateTo(GeoPoint(gpsLocProvider.lastKnownLocation), 18.0, 1000)
            }
        }

    }

    private fun configureButtons() {
        btnSettings = findViewById(R.id.map_btnSettings)
        btnSettings.setBackgroundResource(R.drawable.settings)
        btnSettings.setOnClickListener(this::btnSettingsClick)

        btnConfirmPoint = findViewById(R.id.map_btnConfirmPoint)
        btnConfirmPoint.setOnClickListener(this::btnConfirmPointClick)

        btnRemovePoint = findViewById(R.id.map_btnRemovePoint)
        btnRemovePoint.setOnClickListener(this::btnRemovePointClick)

        btnMe = findViewById(R.id.map_btnMe)
        btnMe.setBackgroundResource(R.drawable.my_location)
        btnMe.setOnClickListener(this::btnMeClick)

        btnPoint = findViewById(R.id.map_btnPoint)
        btnPoint.setBackgroundResource(R.drawable.point_location)
        btnPoint.setOnClickListener {
            map.controller.animateTo(targetPoint?.position, 18.0, 1000)
        }
    }

    private fun configureCompass() {
        val northCompassOverlay = object : CompassOverlay(applicationContext, map) {
            override fun draw(c: Canvas?, pProjection: Projection?) {
                drawCompass(c, -map.mapOrientation, pProjection?.screenRect)
            }

            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView?): Boolean {
                val p = Point()
                map.projection.rotateAndScalePoint(e.x.toInt(), e.y.toInt(), p)

                val dx = (p.x / mScale) - 30
                val dy = (p.y / mScale) - 60

                if (dx * dx + dy * dy <= 900) {
                    map.controller.animateTo(null, null, 500, 0f)
                    return true
                }
                return false
            }
        }
        northCompassOverlay.setCompassCenter(30f, 60f)
        map.overlays.add(northCompassOverlay)
    }

    private fun btnSettingsClick(v: View) {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivityForResult(intent, 100)
    }

    private fun btnConfirmPointClick(v: View) {
        if (presetPoint == null) {
            Toast.makeText(this, "Сначала выберите точку на карте", Toast.LENGTH_SHORT).show()
            return
        }
        targetPoint?.remove(map)
        map.overlays.remove(targetPoint)
        presetPoint?.let {
            it.icon = ResourcesCompat.getDrawable(resources, R.drawable.alarm_point, null)
                ?.toBitmap(50, 50)?.toDrawable(resources)
            targetPoint = it
            presetPoint = null
            locationListener.onLocationChanged(
                Location(
                    gpsLocProvider.lastKnownLocation.latitude,
                    gpsLocProvider.lastKnownLocation.longitude
                )
            )
        }
        redraw()

    }

    private fun btnRemovePointClick(v: View) {
        presetPoint?.remove(map)
        map.overlays.remove(presetPoint)

        presetPoint = targetPoint
        presetPoint?.icon =
            ResourcesCompat.getDrawable(resources, R.drawable.alarm_point_preset, null)
                ?.toBitmap(50, 50)?.toDrawable(resources)
        targetPoint = null

        redraw()
    }

    private fun btnMeClick(v: View) {
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

    fun redraw() {
        map.overlays.remove(targetPointCirclePolygon)
        map.overlays.remove(presetPointCirclePolygon)

        targetPointCirclePolygon = null
        targetPoint?.let {
            targetPointCirclePolygon =
                createCirclePolygon(currentRadius.toDouble(), it, Color.rgb(0, 100, 0))
            map.overlays.add(targetPointCirclePolygon)
        }
        presetPointCirclePolygon = null
        presetPoint?.let {
            presetPointCirclePolygon =
                createCirclePolygon(currentRadius.toDouble(), it, Color.rgb(100, 100, 0))
            map.overlays.add(presetPointCirclePolygon)
        }
        map.invalidate()
    }

    override fun onStop() {
        super.onStop()

        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putInt("radius", currentRadius)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100) {
            redraw()
            targetPoint?.let {
                locationListener.onLocationChanged(
                    Location(
                        gpsLocProvider.lastKnownLocation.latitude,
                        gpsLocProvider.lastKnownLocation.longitude
                    )
                )
            }
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
        q.outlinePaint.color =
            Color.argb(50, Color.red(color), Color.green(color), Color.blue(color))
        q.setOnClickListener { _, _, _ ->
            return@setOnClickListener false
        }
        return q
    }

    private fun requestPermissionsIfNecessary(permissions: ArrayList<String>): Boolean {
        val permissionsToRequest = ArrayList<String>()
        permissions.forEach { permission ->
            if (!isGranted(this, permission)) {
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.size > 0) {
            Log.d("perm", permissionsToRequest.size.toString())

            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toArray(Array(0) { _ -> "" }),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
        return permissions.all { isGranted(this, it) }
    }

    companion object {
        var playingRingtone: Ringtone? = null
        const val NOTIFICATION_ID = 1225
        var currentRadius = 100
    }
}