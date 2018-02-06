package info.jkjensen.castex

import android.Manifest
import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import info.jkjensen.castex.streamreceiver.ReceiverActivity
import info.jkjensen.castex.streamtransmitter.ScreenCapturerService
import info.jkjensen.castex.streamtransmitter.StreamConfigActivity
import info.jkjensen.castex.streamtransmitter.TransmitChooserActivity

import kotlinx.android.synthetic.main.activity_splash.*
import java.io.IOException
import org.jetbrains.anko.startActivity
import java.net.NetworkInterface
import java.util.*


class SplashActivity : Activity(), SurfaceHolder.Callback {
    private val TAG = "SplashActivity"

    //    private var videoView = null
    private var holder:SurfaceHolder? = null
    private var mediaPlayer:MediaPlayer? = MediaPlayer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

//        val path = "android.resource://" + packageName + "/" + R.raw.waves
        //Set up app settings
        initAppPreferences()

        holder = videoView.holder
        holder?.addCallback(this)

        broadcastButton.setOnClickListener {
            startActivity<StreamConfigActivity>()
        }

        receiverButton.setOnClickListener {
            startActivity<ReceiverActivity>()
        }
        logNetworkInterfaces()
    }

    fun initAppPreferences(){
        val sharedPreferences: SharedPreferences = getSharedPreferences("appConfig", Context.MODE_PRIVATE)
        val editor:SharedPreferences.Editor = sharedPreferences.edit()
        editor.putBoolean(Preferences.KEY_DEBUG, Preferences.DEBUG)
        editor.putBoolean(Preferences.KEY_MULTICAST, Preferences.MULTICAST)
        editor.putBoolean(Preferences.KEY_TCP, Preferences.TCP)
        editor.apply()
    }

    fun logNetworkInterfaces() {
        Log.d("Splash", "Here are all network interfaces:")
        val nets = NetworkInterface.getNetworkInterfaces()
        for (netint in Collections.list(nets))
            displayInterfaceInformation(netint)
    }

    fun displayInterfaceInformation(netint: NetworkInterface) {
        Log.d("Splash", "Display name: " + netint.displayName)
        Log.d("Splash", "Name: " + netint.name)
        if(netint.name == "wlan0") Log.d("Splash", "Supports multicast? " + netint.supportsMulticast())
//        val inetAddresses = netint.inetAddresses
//        for (inetAddress in Collections.list(inetAddresses)) {
//            Log.d("Splash", "InetAddress: %s\n" + inetAddress)
//        }
//        Log.d("Splash", "\n")
    }

    override fun surfaceCreated(p0: SurfaceHolder?) {
        mediaPlayer?.setDisplay(holder)
        if(mediaPlayer!!.isPlaying) return
        // Loop the video
        mediaPlayer?.setOnPreparedListener({
            mediaPlayer -> mediaPlayer.isLooping = true
        })
        val path = "android.resource://" + packageName + "/" + R.raw.waves
        try{
            mediaPlayer?.setDataSource(this, Uri.parse(path))
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        } catch (e: IOException){
            e.printStackTrace()
        }
    }

    override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
    }

    override fun surfaceDestroyed(p0: SurfaceHolder?) {
    }

    override fun onStop() {
        super.onStop()
//        if(mediaP

//        mediaPlayer?.reset()
        mediaPlayer?.release()
//        mediaPlayer = null
    }

    override fun onResume() {
        mediaPlayer = MediaPlayer()
        super.onResume()
    }

}
