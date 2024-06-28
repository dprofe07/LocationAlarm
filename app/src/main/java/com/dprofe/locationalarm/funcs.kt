package com.dprofe.locationalarm

import android.content.Context
import android.content.UriPermission
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.api.IGeoPoint
import org.osmdroid.util.GeoPoint

fun distanceBetween(a: GeoPoint, b: GeoPoint): Float {
    val q = floatArrayOf(0f)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, q)
    return q[0]
}

fun toGeoPoint(a: Any?): GeoPoint? {
    return when (a) {
        is GeoPoint -> a
        is IGeoPoint -> GeoPoint(a)
        is Location -> GeoPoint(a)
        else -> null
    }
}

fun distanceBetween(a: Any?, b: Any?): Float {
    return distanceBetween(
        toGeoPoint(a) ?: return Float.POSITIVE_INFINITY,
        toGeoPoint(b) ?: return Float.POSITIVE_INFINITY
    )
}

fun isGranted(context: Context, permission: String) : Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

fun Location(lat: Double, lon: Double): Location {
    val res = Location(LocationManager.GPS_PROVIDER)
    res.longitude = lon
    res.latitude = lat
    return res
}