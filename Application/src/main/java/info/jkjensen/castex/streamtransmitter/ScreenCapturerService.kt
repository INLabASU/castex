package info.jkjensen.castex.streamtransmitter

import android.annotation.TargetApi
import android.app.*
import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat.startActivityForResult
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat.IMPORTANCE_LOW
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import info.jkjensen.castex.R
import kotlinx.android.synthetic.main.activity_transmitter_2.*
import net.majorkernelpanic.streaming.SessionBuilder
import net.majorkernelpanic.streaming.rtsp.RtspServer
import net.majorkernelpanic.streaming.video.VideoQuality


/**
 * Created by jk on 1/3/18.
 */
class ScreenCapturerService: IntentService("ScreenCaptureService") {
    private val ONGOING_NOTIFICATION_IDENTIFIER = 1
    private val REQUEST_MEDIA_PROJECTION_CODE = 1
    private val REQUEST_CAMERA_CODE = 200
    private var sessionBuilder:SessionBuilder = SessionBuilder.getInstance()

    @TargetApi(Build.VERSION_CODES.O)
    override fun onCreate() {

        val notificationIntent = Intent(this, TransmitterActivity2::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CastexNotification.id)
                    .setContentTitle(getText(R.string.notification_title))
                    .setContentText(getText(R.string.notification_message))
                    .setSmallIcon(R.drawable.abc_ic_star_black_16dp)
                    .setContentIntent(pendingIntent)
                    .setTicker(getText(R.string.notification_message))
                    .build()
        } else {
            TODO("VERSION.SDK_INT < O")
        }

        startForeground(ONGOING_NOTIFICATION_IDENTIFIER, notification)
        Log.d("ScreenCaptureService", "Service started.")

//        Toast.makeText(this, "woo", Toast.LENGTH_LONG).show()
        super.onCreate()
    }

    override fun onHandleIntent(intent: Intent?) {

        val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
        editor.putString(RtspServer.KEY_PORT, 1234.toString())
        editor.commit()

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("multicastLock")
        multicastLock.setReferenceCounted(false)
        multicastLock.acquire()

        val displayMetrics: DisplayMetrics = applicationContext.resources.displayMetrics
        val height:Int = displayMetrics.heightPixels
        val width:Int = displayMetrics.widthPixels


        sessionBuilder = sessionBuilder
//                .setSurfaceView(SurfaceView())
                .setCamera(0)
                .setPreviewOrientation(90)
                .setContext(applicationContext)
                .setAudioEncoder(SessionBuilder.AUDIO_NONE)
                //Supposedly supported resolutions: 1920x1080, 1600x1200, 1440x1080, 1280x960, 1280x768, 1280x720, 1024x768, 800x600, 800x480, 720x480, 640x480, 640x360, 480x640, 480x360, 480x320, 352x288, 320x240, 240x320, 176x144, 160x120, 144x176

//                .setVideoQuality(VideoQuality(320,240,30,2000000)) // Supported
//                .setVideoQuality(VideoQuality(640,480,30,2000000)) // Supported
//                .setVideoQuality(VideoQuality(720,480,30,2000000)) // Supported
//                .setVideoQuality(VideoQuality(800,600,30,2000000)) // Supported
                .setVideoQuality(VideoQuality(768,1024,30,1000000)) // Supported
//                .setVideoQuality(VideoQuality(1280,960,4,8000000)) // Supported
//                .setVideoQuality(VideoQuality(1080,1920,30,8000000)) // Supported
//                .setDestination("192.168.43.19")// mbp
//                .setDestination("192.168.43.20")// iMac
//                .setDestination("192.168.43.19")// mbp
//                .setDestination("192.168.43.110")// Galaxy s7
//                .setDestination("192.168.43.6")// OnePlus 5
//                .setDestination("232.0.1.2") // multicast
//                .setCallback(this)
        sessionBuilder.videoEncoder = SessionBuilder.VIDEO_H264

        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // This initiates a prompt dialog for the user to confirm screen projection.
//        startActivityForResult(
//                mediaProjectionManager.createScreenCaptureIntent(),
//                REQUEST_MEDIA_PROJECTION_CODE)

//        try{
//            Thread.sleep(5000)
//        }catch (e:InterruptedException){
//            Thread.currentThread().interrupt()
//        }

        Log.d("ScreenCaptureService", "Service complete.")
        stopSelf()
    }
}