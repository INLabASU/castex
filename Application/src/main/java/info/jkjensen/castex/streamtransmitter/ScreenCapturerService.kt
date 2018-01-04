package info.jkjensen.castex.streamtransmitter

import android.app.IntentService
import android.app.Notification
import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat.IMPORTANCE_LOW
import android.util.Log
import android.widget.Toast
import info.jkjensen.castex.R


/**
 * Created by jk on 1/3/18.
 */
class ScreenCapturerService: IntentService("ScreenCaptureService") {
    private val ONGOING_NOTIFICATION_IDENTIFIER = 1

    override fun onCreate() {

        val notificationIntent = Intent(this, TransmitterActivity2::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "ScreenSharingNotification")
                    .setContentTitle(getText(R.string.notification_title))
                    .setContentText(getText(R.string.notification_message))
    //                .setSmallIcon(R.drawable.abc_ic_star_black_16dp)
                    .setContentIntent(pendingIntent)
                    .setTicker(getText(R.string.notification_message))
                    .build()
        } else {
            TODO("VERSION.SDK_INT < O")
        }

        startForeground(ONGOING_NOTIFICATION_IDENTIFIER, notification)
        Log.d("ScreenCaptureService", "Service started.")

        Toast.makeText(this, "woo", Toast.LENGTH_LONG).show()
        super.onCreate()
    }

    override fun onHandleIntent(intent: Intent?) {


        try{
            Thread.sleep(15000)
        }catch (e:InterruptedException){
            Thread.currentThread().interrupt()
        }

        Log.d("ScreenCaptureService", "Service complete.")
        stopSelf()
    }
}