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
class ScreenRecordNotification(context:Context) {

    companion object {
        val id = "net.majorkernelpanic.streaming.ScreenRecordNotification"
        val name = "Screen Recording"
        val description = "Some description"
    }

    val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val importance = NotificationManager.IMPORTANCE_DEFAULT
    var channel: NotificationChannel? = null


    fun buildChannel(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
}