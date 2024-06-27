package com.dprofe.locationalarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.location.Location
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
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
            ).apply { if (Build.VERSION.SDK_INT > 33) add(Manifest.permission.POST_NOTIFICATIONS) }
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
                it.icon = ResourcesCompat.getDrawable(resources, R.drawable.alarm_point, null)
                    ?.toBitmap(50, 50)?.toDrawable(resources)
                targetPointCirclePolygon =
                    createCirclePolygon(currentRadius.toDouble(), it, Color.rgb(0, 100, 0))
                map.overlays.add(targetPointCirclePolygon)
                targetPoint = it
                presetPoint = null
            }
            map.invalidate()
        }

        btnRemovePoint = findViewById(R.id.map_btnRemovePoint)
        btnRemovePoint.setOnClickListener {
            presetPoint?.remove(map)
            map.overlays.remove(presetPoint)

            presetPoint = targetPoint
            presetPoint?.icon = ResourcesCompat.getDrawable(resources, R.drawable.alarm_point_preset, null)
                ?.toBitmap(50, 50)?.toDrawable(resources)
            targetPoint = null

            redraw()
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
                val p = Point()
                map.projection.rotateAndScalePoint(e.x.toInt(), e.y.toInt(), p)
                val x = p.x / mScale
                val y = p.y / mScale

                val dx = x - 30
                val dy = y - 60

                if (dx * dx + dy * dy <= 900) {
                    map.controller.animateTo(null, null, 500, 0f)
                    return true
                }
                return false
            }
        }
        northCompassOverlay.setCompassCenter(30f, 60f)
        map.overlays.add(northCompassOverlay)

        thread {
            while (true) {
                while (distanceBetween(
                        gpsLocProvider.lastKnownLocation,
                        targetPoint?.position
                    ) > currentRadius
                ) {
                    Thread.sleep(200)
                    Log.d(
                        "loc-alarm",
                        "waiting, ${
                            distanceBetween(
                                gpsLocProvider.lastKnownLocation,
                                targetPoint?.position
                            )
                        }"
                    )
                }
                playingRingtone?.let {
                    if (it.isPlaying)
                        it.stop()
                }

                Log.d("loc-alarm", "shown")

                presetPoint = targetPoint
                presetPoint?.icon = ResourcesCompat.getDrawable(resources, R.drawable.alarm_point_preset, null)
                    ?.toBitmap(50, 50)?.toDrawable(resources)

                targetPoint = null
                runOnUiThread {  redraw() }

                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {

                    val notificationBodyIntent =
                        Intent(applicationContext, MainActivity::class.java)
                    notificationBodyIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    val contentIntent = PendingIntent.getActivity(
                        applicationContext,
                        0,
                        notificationBodyIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val buttonStopIntent =
                        Intent(this, NotificationButtonPressedReciever::class.java)
                    val pButtonStopIntent = PendingIntent.getBroadcast(
                        this,
                        0,
                        buttonStopIntent,
                        PendingIntent.FLAG_IMMUTABLE
                    )


                    val notificationManager = NotificationManagerCompat.from(applicationContext)
                    val channel = NotificationChannel(
                        "location-alarm",
                        "Уведомление о прибытии",
                        NotificationManager.IMPORTANCE_HIGH
                    )

                    notificationManager.createNotificationChannel(channel)

                    val notification =
                        NotificationCompat.Builder(applicationContext, "location-alarm")
                            .setContentTitle("Вы приехали!")
                            .setContentText("Вы достигли указанной в приложении зоны")
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setSmallIcon(R.drawable.notification_icon)
                            .addAction(
                                NotificationCompat.Action(
                                    null,
                                    "Выключить оповещение",
                                    pButtonStopIntent
                                )
                            )
                            .setContentIntent(contentIntent)
                            .setOngoing(true)
                            .setSound(null)
                            .setDefaults(0)
                            .build()

                    notification.deleteIntent = pButtonStopIntent
                    notificationManager.notify(NOTIFICATION_ID, notification)
                    Thread.sleep(100)


                    val powermanager = (getSystemService(POWER_SERVICE) as PowerManager)
                    val wakeLock = powermanager.newWakeLock(
                        (PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP),
                        "locationalarm:tag"
                    )
                    wakeLock.acquire()
                    playingRingtone = RingtoneManager.getRingtone(
                        applicationContext,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ).apply {
                        isLooping = true
                        play()
                    }

                }
            }

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {
            currentRadius = data?.getIntExtra("radius", currentRadius) ?: currentRadius

            redraw()
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
        return distanceBetween(
            toGeoPoint(a) ?: return Float.POSITIVE_INFINITY,
            toGeoPoint(b) ?: return Float.POSITIVE_INFINITY
        )
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

    companion object {
        var playingRingtone: Ringtone? = null
        const val NOTIFICATION_ID = 1225
    }
}