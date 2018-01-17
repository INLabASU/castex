package info.jkjensen.castex.streamtransmitter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.WindowManager
import android.widget.Toast
import info.jkjensen.castex.R
import kotlinx.android.synthetic.main.activity_transmitter_2.*
import net.majorkernelpanic.streaming.Session
import net.majorkernelpanic.streaming.gl.SurfaceView
import net.majorkernelpanic.streaming.rtsp.RtspServer
import java.lang.Exception


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

    var session: Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transmitter_2)

//        togglebutton.onClick {
//            // Start stream.
//            session!!.start()
//        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_CODE)

        val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
        editor.putString(RtspServer.KEY_PORT, 1234.toString())
        editor.commit()

        sv = surfaceView

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
