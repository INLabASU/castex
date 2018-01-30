package net.majorkernelpanic.streaming

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build

@TargetApi(Build.VERSION_CODES.O)
/**
 * Created by jk on 1/9/18.
 */
class CastexNotification(context:Context) {

    companion object {
        val id = "info.jkjensen.castex.streamtransmitter"
        val name = "castex"
        val description = "Some description"
    }

    val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val importance = NotificationManager.IMPORTANCE_DEFAULT
    var channel: NotificationChannel? = null


    fun buildChannel(){
        channel = NotificationChannel(id, name, importance)
        channel!!.description = description
        channel!!.enableLights(true)

        channel!!.lightColor = Color.RED
        channel!!.enableVibration(false)
        channel!!.setSound(null, null)
//        channel!!.vibrationPattern = LongArray(0)
        notificationManager.createNotificationChannel(channel)
    }
}