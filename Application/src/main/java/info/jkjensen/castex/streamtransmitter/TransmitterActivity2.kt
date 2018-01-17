package info.jkjensen.castex.streamtransmitter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_transmitter_2.*
import net.majorkernelpanic.streaming.SessionBuilder
import net.majorkernelpanic.streaming.rtsp.RtspServer
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.view.SurfaceHolder
import net.majorkernelpanic.streaming.Session
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.WindowManager
import net.majorkernelpanic.streaming.video.VideoQuality
import java.lang.Exception
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.widget.Toast
import info.jkjensen.castex.R
import net.majorkernelpanic.streaming.MediaStream
import net.majorkernelpanic.streaming.gl.SurfaceView
import org.jetbrains.anko.mediaProjectionManager
import org.jetbrains.anko.sdk25.coroutines.onClick


class TransmitterActivity2 : AppCompatActivity(), SurfaceHolder.Callback, Session.Callback {

    companion object {
        fun testing1(){
            Log.d("tag","somethign")
        }
        var sv:SurfaceView? = null
    }

    private val REQUEST_MEDIA_PROJECTION_CODE = 1
    private val REQUEST_CAMERA_CODE = 200
    private val REQUEST_OVERLAY_CODE = 201
    private val TAG = "TA2"

    private var sessionBuilder:SessionBuilder = SessionBuilder.getInstance()

    private val streamWidth = 1024
    private val streamHeight = 768

    private var resultCode: Int = 0
    private var resultData: Intent? = null

    private var mediaProjection:MediaProjection? = null
    private var virtualDisplay:VirtualDisplay? = null

    var session: Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transmitter_2)

        togglebutton.onClick {
            // Start stream.
            session!!.start()
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_CODE)

        val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
        editor.putString(RtspServer.KEY_PORT, 1234.toString())
        editor.commit()

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("multicastLock")
        multicastLock.setReferenceCounted(false)
        multicastLock.acquire()

        val displayMetrics:DisplayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height:Int = displayMetrics.heightPixels
        val width:Int = displayMetrics.widthPixels

        sv = surfaceView


//        sessionBuilder = sessionBuilder
//                .setSurfaceView(surfaceView)
//                .setCamera(0)
//                .setPreviewOrientation(90)
//                .setContext(applicationContext)
//                .setAudioEncoder(SessionBuilder.AUDIO_NONE)
//                //Supposedly supported resolutions: 1920x1080, 1600x1200, 1440x1080, 1280x960, 1280x768, 1280x720, 1024x768, 800x600, 800x480, 720x480, 640x480, 640x360, 480x640, 480x360, 480x320, 352x288, 320x240, 240x320, 176x144, 160x120, 144x176
//
////                .setVideoQuality(VideoQuality(320,240,30,2000000)) // Supported
////                .setVideoQuality(VideoQuality(640,480,30,2000000)) // Supported
////                .setVideoQuality(VideoQuality(720,480,30,2000000)) // Supported
////                .setVideoQuality(VideoQuality(800,600,30,2000000)) // Supported
//                .setVideoQuality(VideoQuality(768,1024,30,1000000)) // Supported
////                .setVideoQuality(VideoQuality(1280,960,4,8000000)) // Supported
////                .setVideoQuality(VideoQuality(1080,1920,30,8000000)) // Supported
////                .setDestination("192.168.43.19")// mbp
////                .setDestination("192.168.43.20")// iMac
////                .setDestination("192.168.43.19")// mbp
////                .setDestination("192.168.43.110")// Galaxy s7
////                .setDestination("192.168.43.6")// OnePlus 5
////                .setDestination("232.0.1.2") // multicast
//                .setCallback(this)
//        sessionBuilder.videoEncoder = SessionBuilder.VIDEO_H264

        if(!Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            overlayIntent.data = Uri.parse("package:" + packageName)
            startActivityForResult(overlayIntent, REQUEST_OVERLAY_CODE)
        }
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // This initiates a prompt dialog for the user to confirm screen projection.
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION_CODE)

    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_MEDIA_PROJECTION_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "User cancelled")
                Toast.makeText(this, R.string.user_cancelled, Toast.LENGTH_SHORT).show()
                return
            }

            val serviceIntent = Intent(this, ScreenCapturerService::class.java)
            serviceIntent.putExtra(ScreenCapturerService.MEDIA_PROJECTION_RESULT_CODE, resultCode)
            serviceIntent.putExtra(ScreenCapturerService.MEDIA_PROJECTION_RESULT_DATA, data)
            startService(serviceIntent)
        } else if (requestCode == REQUEST_OVERLAY_CODE){
            Log.i(TAG, "Got overlay permissions")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.e("ServerActivity", requestCode.toString())
    }

    override fun onStop() {
        super.onStop()
        // Stops the RTSP server
//        stopService(Intent(this, RtspServer::class.java))
//        stopService(Intent(this, ScreenCapturerService::class.java))
    }

    override fun surfaceCreated(p0: SurfaceHolder?) {
//        session!!.startPreview()
    }

    override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {}

    override fun surfaceDestroyed(p0: SurfaceHolder?) {}

    override fun onBitrateUpdate(bitrate: Long) {
        Log.d("ServerActivity", "Bitrate update: " + bitrate.toString())
    }

    override fun onSessionError(reason: Int, streamType: Int, e: Exception?) {
        Log.d("ServerActivity", "Error")
        e?.printStackTrace()
    }

    override fun onPreviewStarted() {
        Log.d("ServerActivity", "Preview started")
    }

    override fun onSessionConfigured() {
        Log.d("ServerActivity", "Sesh configured")
//        session!!.start()
    }

    override fun onSessionStarted() {
        Log.d("ServerActivity", "Sesh started")
    }

    override fun onSessionStopped() {
        Log.d("ServerActivity", "Sesh stopped")
    }

}
