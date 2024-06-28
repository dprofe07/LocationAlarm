package com.dprofe.locationalarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.media.RingtoneManager
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable

class LocationTrackerListener(private val activity: MainActivity) : LocationListener {
    @SuppressLint("MissingPermission")
    override fun onLocationChanged(location: Location) {
        val dist = distanceBetween(location, activity.targetPoint?.position)
        if (dist > MainActivity.currentRadius) {
            Log.d("loc-alarm", "waiting, $dist")
            return
        }
        MainActivity.playingRingtone?.let {
            if (it.isPlaying)
                it.stop()
        }

        Log.d("loc-alarm", "shown")

        activity.presetPoint = activity.targetPoint
        activity.presetPoint?.icon =
            ResourcesCompat.getDrawable(activity.resources, R.drawable.alarm_point_preset, null)
                ?.toBitmap(50, 50)?.toDrawable(activity.resources)

        activity.targetPoint = null
        activity.runOnUiThread { activity.redraw() }

        if (isGranted(activity, Manifest.permission.POST_NOTIFICATIONS)) {
            val notificationBodyIntent = Intent(activity.applicationContext, MainActivity::class.java)
            notificationBodyIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val contentIntent = PendingIntent.getActivity(
                activity.applicationContext,
                0,
                notificationBodyIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val buttonStopIntent =
                Intent(activity, NotificationButtonPressedReciever::class.java)
            val pButtonStopIntent = PendingIntent.getBroadcast(
                activity,
                0,
                buttonStopIntent,
                PendingIntent.FLAG_IMMUTABLE
            )


            val notificationManager = NotificationManagerCompat.from(activity.applicationContext)
            val channel = NotificationChannel(
                "location-alarm",
                "Уведомление о прибытии",
                NotificationManager.IMPORTANCE_HIGH
            )

            notificationManager.createNotificationChannel(channel)

            val notification =
                NotificationCompat.Builder(activity.applicationContext, "location-alarm")
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
            notificationManager.notify(MainActivity.NOTIFICATION_ID, notification)
            Thread.sleep(100)


            val powermanager = (activity.getSystemService(Service.POWER_SERVICE) as PowerManager)
            val wakeLock = powermanager.newWakeLock(
                (PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP),
                "locationalarm:tag"
            )
            wakeLock.acquire()
            MainActivity.playingRingtone = RingtoneManager.getRingtone(
                activity.applicationContext,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ).apply {
                isLooping = true
                play()
            }
        }
    }
}