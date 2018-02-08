package info.jkjensen.castex.streamtransmitter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.net.wifi.p2p.WifiP2pManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.widget.Toast
import info.jkjensen.castex.CastexNetworkManager
import info.jkjensen.castex.R
import kotlinx.android.synthetic.main.activity_stream_config.*
import net.majorkernelpanic.streaming.rtsp.RtspServer
import org.jetbrains.anko.sdk25.coroutines.onClick

class StreamConfigActivity : AppCompatActivity() {
    private val TAG = "StreamConfigActivity"

    private val REQUEST_MEDIA_PROJECTION_CODE = 1
    private val REQUEST_OVERLAY_CODE = 201
    private val REQUEST_CAMERA_CODE = 200

    val localContext = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stream_config)

        startStreamButton.onClick {


            // Get camera permission (Still required for Libstreaming...)
            ActivityCompat.requestPermissions(localContext, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_CODE)

            // Allow the drawing of the virtualDisplay on a surface.
            // TODO: Let the user know why we are doing this.
            if(!Settings.canDrawOverlays(localContext)) {
                val overlayIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                overlayIntent.data = Uri.parse("package:" + packageName)
                startActivityForResult(overlayIntent, REQUEST_OVERLAY_CODE)
            }

            val p2pManager:WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
            p2pManager.initialize(applicationContext, Looper.getMainLooper(), null)
            val networkManager = CastexNetworkManager(p2pManager)

            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            // This initiates a prompt dialog for the user to confirm screen projection.
            startActivityForResult(
                    mediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_MEDIA_PROJECTION_CODE)
        }
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
}
