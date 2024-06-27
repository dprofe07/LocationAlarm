package com.dprofe.locationalarm

import android.graphics.Color
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class MyMapEventListener(val activity: MainActivity) : MapEventsReceiver {

    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
        activity.run {
            if (p == null) {
                presetPoint = null
                return@run
            }

            if (presetPoint == null && presetPointCirclePolygon == null) {
                presetPoint = Marker(map).apply {
                    this.icon = ResourcesCompat.getDrawable(resources, R.drawable.alarm_point_preset, null)
                        ?.toBitmap(50, 50)?.toDrawable(resources)
                    this.position = p
                    this.isDraggable = false
                    setOnMarkerClickListener { _, _ ->
                        return@setOnMarkerClickListener true
                    }
                }
                map.overlays.add(presetPoint)
                activity.presetPointCirclePolygon = activity.createCirclePolygon(activity.currentRadius.toDouble(), presetPoint!!, Color.rgb(100, 100, 0))
                map.overlays.add(activity.presetPointCirclePolygon)
                map.invalidate()
            } else {
                presetPoint?.remove(map)
                map.overlays.remove(presetPoint)
                presetPoint = null
                map.overlays.remove(activity.presetPointCirclePolygon)
                activity.presetPointCirclePolygon = null
                this@MyMapEventListener.singleTapConfirmedHelper(p)
            }
        }

        return false
    }

    override fun longPressHelper(p: GeoPoint?): Boolean {
        return false
    }

}