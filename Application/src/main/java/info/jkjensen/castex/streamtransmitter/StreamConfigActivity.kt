@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jkjensen.castex.streamtransmitter

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import info.jkjensen.castex.R
import kotlinx.android.synthetic.main.activity_stream_config.*
import org.jetbrains.anko.mediaProjectionManager
import org.jetbrains.anko.sdk25.coroutines.onClick

class StreamConfigActivity : AppCompatActivity() {
    private val TAG = "StreamConfigActivity"

    private val REQUEST_MEDIA_PROJECTION_CODE = 1
    private val REQUEST_OVERLAY_CODE = 201
    private val REQUEST_CAMERA_CODE = 200

    val localContext = this
    var networkManager: CastexNetworkManager? = null
    var receiver: NetworkBroadcastReceiver? = null

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
            val channel: WifiP2pManager.Channel = p2pManager.initialize(applicationContext, Looper.getMainLooper(), null)
            networkManager = CastexNetworkManager(channel)

            // This initiates a prompt dialog for the user to confirm screen projection.
            startActivityForResult(
                    mediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_MEDIA_PROJECTION_CODE)
        }
    }

    override fun onResume() {
        super.onResume()
        receiver = NetworkBroadcastReceiver()
//        registerReceiver(receiver, networkManager!!.intentFilter)
    }

    override fun onPause() {
        super.onPause()
//        unregisterReceiver(receiver)
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

    fun setIsWifiP2pEnabled(enabled:Boolean){
        // TODO: Handle this situation for the user to let them know.
        Log.d(TAG, "WIFI Direct enabled: " + enabled)
    }


    /**
     * Created by jk on 2/6/18.
     * initialize() must have been called on p2pManager before passing it in.
     */
    inner class CastexNetworkManager(val channel: WifiP2pManager.Channel) {
        val intentFilter = IntentFilter()

        init {

            // Indicates a change in the Wi-Fi P2P status.
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)

            // Indicates a change in the list of available peers.
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)

            // Indicates the state of Wi-Fi P2P connectivity has changed.
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)

            // Indicates this device's details have changed.
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
    }

    inner class NetworkBroadcastReceiver: BroadcastReceiver(){

        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getAction()
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION == action) {
                // Determine if Wifi P2P mode is enabled or not, alert
                // the Activity.
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    setIsWifiP2pEnabled(true)
                } else {
                    setIsWifiP2pEnabled(false)
                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == action) {

                // The peer list has changed! We should probably do something about
                // that.

            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION == action) {

                // Connection state changed! We should probably do something about
                // that.

            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION == action) {
//                val fragment = fragmentManager.findFragmentById(R.id.frag_list) as DeviceListFragment
//                fragment.updateThisDevice(intent.getParcelableExtra(
//                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE) as WifiP2pDevice)
                intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE) as WifiP2pDevice

            }
        }

    }
}
