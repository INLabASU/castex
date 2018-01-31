package info.jkjensen.castex.streamtransmitter

import android.annotation.TargetApi
import android.app.*
import android.content.Intent
import android.content.Context
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.net.wifi.WifiManager
import android.os.Build
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.SurfaceHolder
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
import android.widget.RelativeLayout
import android.widget.Toast
import info.jkjensen.castex.R
import net.majorkernelpanic.streaming.ScreenRecordNotification
import net.majorkernelpanic.streaming.MediaStream
import net.majorkernelpanic.streaming.Session
import net.majorkernelpanic.streaming.SessionBuilder
import net.majorkernelpanic.streaming.gl.SurfaceView
import net.majorkernelpanic.streaming.rtsp.RtspServer
import net.majorkernelpanic.streaming.video.VideoQuality
import org.jetbrains.anko.layoutInflater
import org.jetbrains.anko.mediaProjectionManager


/**
 * Created by jk on 1/3/18.
 */
class ScreenCapturerService: IntentService("ScreenCaptureService") {

    companion object {
        val MEDIA_PROJECTION_RESULT_CODE = "mediaprojectionresultcode"
        val MEDIA_PROJECTION_RESULT_DATA = "mediaprojectionresultdata"
        val STOP_ACTION = "Castex.StopAction"
    }

    private val TAG = "ScreenCaptureService"
    private val ONGOING_NOTIFICATION_IDENTIFIER = 1

    private val REQUEST_MEDIA_PROJECTION_CODE = 1
    private val REQUEST_CAMERA_CODE = 200
    private var sessionBuilder:SessionBuilder = SessionBuilder.getInstance()

    private var resultCode: Int = 0
    private var resultData: Intent? = null
    private var mediaProjection: MediaProjection? = null
    private val broadcastReceiver = CastexNotificationBroadcastReceiver()

    var session: Session? = null

    @TargetApi(Build.VERSION_CODES.O)
    override fun onCreate() {

        // Create a notification channel for the recording process
        ScreenRecordNotification(this).buildChannel()

        Log.d("ScreenCaptureService", "Service started.")
        val filter = IntentFilter()
        filter.addAction(ScreenCapturerService.STOP_ACTION)
        registerReceiver(broadcastReceiver, filter)

        Toast.makeText(this, "Sharing screen", Toast.LENGTH_LONG).show()
        super.onCreate()
    }

    override fun onDestroy() {
        unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    override fun onHandleIntent(intent: Intent?) {

        val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
        editor.putString(RtspServer.KEY_PORT, 1234.toString())
        editor.commit()

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("multicastLock")
        multicastLock.setReferenceCounted(false)
        multicastLock.acquire()


        val layout:RelativeLayout = layoutInflater.inflate(R.layout.bg_surface_view, null) as RelativeLayout
        val params = WindowManager.LayoutParams(1,1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                FLAG_WATCH_OUTSIDE_TOUCH or FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSPARENT)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.addView(layout, params)

        val svf:SurfaceView = layout.findViewById(R.id.surface_view_fake)
        val sh:SurfaceHolder = svf.holder
        svf.setZOrderOnTop(true)
        sh.setFormat(PixelFormat.TRANSPARENT)

        sessionBuilder = sessionBuilder
                .setContext(applicationContext)
//                .setSurfaceView(TransmitterActivity2.sv)
                .setSurfaceView(svf)
                .setCamera(1)
                .setPreviewOrientation(90)
                .setContext(applicationContext)
                .setAudioEncoder(SessionBuilder.AUDIO_NONE)
                //Supposedly supported resolutions: 1920x1080, 1600x1200, 1440x1080, 1280x960, 1280x768, 1280x720, 1024x768, 800x600, 800x480, 720x480, 640x480, 640x360, 480x640, 480x360, 480x320, 352x288, 320x240, 240x320, 176x144, 160x120, 144x176

//                .setVideoQuality(VideoQuality(320,240,30,2000000)) // Supported
//                .setVideoQuality(VideoQuality(640,480,30,2000000)) // Supported
//                .setVideoQuality(VideoQuality(720,480,30,2000000)) // Supported
//                .setVideoQuality(VideoQuality(800,600,30,2000000)) // Supported
                .setVideoQuality(VideoQuality(TransmitterActivity2.STREAM_WIDTH,
                        TransmitterActivity2.STREAM_HEIGHT,
                        TransmitterActivity2.STREAM_FRAMERATE,
                        TransmitterActivity2.STREAM_BITRATE)) // Supported
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

        val resultCode = intent?.getIntExtra(MEDIA_PROJECTION_RESULT_CODE, 0)
        val resultData:Intent? = intent?.getParcelableExtra<Intent>(MEDIA_PROJECTION_RESULT_DATA)
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode!!, resultData)

        sessionBuilder.setMediaProjection(mediaProjection)

        val metrics: DisplayMetrics = applicationContext.resources.displayMetrics
        sessionBuilder.setDisplayMetrics(metrics)

        session = sessionBuilder.build()
        session!!.videoTrack.streamingMethod = MediaStream.MODE_MEDIACODEC_API
        session!!.configure()
        startService(Intent(applicationContext, RtspServer::class.java))
        Log.d("ScreenCaptureService", "Starting session preview")
        session!!.startPreview()

        while(true){
            Thread.sleep(1000000)
        }
//        stopSelf()
    }
}