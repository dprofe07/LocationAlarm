package com.dprofe.locationalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationManagerCompat

class NotificationButtonPressedReciever : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (MainActivity.playingRingtone != null)
            MainActivity.playingRingtone?.stop()

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(MainActivity.NOTIFICATION_ID)

    }
}