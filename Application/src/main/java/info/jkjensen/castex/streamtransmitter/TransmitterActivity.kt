package info.jkjensen.castex.streamtransmitter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.icu.text.SimpleDateFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.AsyncTask
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.Toast
import android.widget.VideoView
import info.jkjensen.castex.Preferences
import info.jkjensen.castex.R
import kotlinx.android.synthetic.main.activity_transmitter.*
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.util.*

class TransmitterActivity : AppCompatActivity(), View.OnClickListener {
    private val TAG = "ScreenCaptureFragment"
    // Flag for whether to write encoder output to a file on device for debugging.
    private val DEBUG_WRITE_TO_FILE = false

    private val REQUEST_EXTERNAL_STORAGE = 1
    private val PERMISSIONS_STORAGE = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private val STATE_RESULT_CODE = "result_code"
    private val STATE_RESULT_DATA = "result_data"

    private val REQUEST_MEDIA_PROJECTION = 1

    private var screenDensity: Int = 0

    private var mResultCode: Int = 0
    private var mResultData: Intent? = null

    private var inputSurface: Surface? = null
    private var mMediaProjection: MediaProjection? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var toggleButton: Button? = null
    private var playPauseButton: Button? = null
    private val mSurfaceView: SurfaceView? = null
    private var videoView: VideoView? = null

    private var encoder: MediaCodec? = null
    private var fileOutputStream: FileOutputStream? = null

    private var sock: MulticastSocket? = null
    private var tSock: Socket? = null
    private var group1: InetAddress? = null
    private var group2: InetAddress? = null
    private var configSent = false
    private var metrics:DisplayMetrics = DisplayMetrics()

    private var type:String? = null

    private val STREAMING_BIT_RATE = 220000
    private val STREAMING_FRAME_RATE = 30
    // ms to wait before repeating the same frame again if no change
    // see https://developer.android.com/reference/android/media/MediaFormat.html#KEY_REPEAT_PREVIOUS_FRAME_AFTER
    private val REPEAT_PREVIOUS_FRAME_AFTER = 1000000
    // iFrame interval in seconds
    private val I_FRAME_INTERVAL = 1
    private var PORT_OUT = 1900
//        private val streamWidth = 1080
//        private val streamHeight = 1794
//        private val streamWidth = 720
//        private val streamHeight = 1280
//        private val streamWidt`h = 750
//        private val streamHeight = 1334
    private val streamWidth = 360
    private val streamHeight = 640

    private var multicastEnabled:Boolean = false
    private var debugEnabled:Boolean = false
    private var tcpEnabled:Boolean = false

    private var frameCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transmitter)

        // Configure transmission preferences
        val sharedPreferences = getSharedPreferences("appConfig", Context.MODE_PRIVATE)
        multicastEnabled = sharedPreferences.getBoolean(Preferences.KEY_MULTICAST, false)
        debugEnabled = sharedPreferences.getBoolean(Preferences.KEY_DEBUG, false)
        tcpEnabled = sharedPreferences.getBoolean(Preferences.KEY_TCP, false)

        // Prefer IPv4 over IPv6 so we can do normal network things.
        System.setProperty("java.net.preferIPv4Stack" , "true")

        if (savedInstanceState != null) {
            mResultCode = savedInstanceState.getInt(STATE_RESULT_CODE)
            mResultData = savedInstanceState.getParcelable<Intent>(STATE_RESULT_DATA)
        }

        if(!tcpEnabled && multicastEnabled) {
            // Configure the OS to allow multicast
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG)
            wifiLock.acquire()
            val multicastLock = wifiManager.createMulticastLock("multicastLock")
            multicastLock.acquire()

            // Adjust the port for multicast
            PORT_OUT = 4446
        }

        // Grab the type of transmission that is occurring
        type = intent.getStringExtra("TYPE")

        when(type){
            "WEB" ->{
                webView.visibility = View.VISIBLE
                webView.loadUrl("http://www.jkjensen.info")
            }
            "FILE" ->{
                webView.visibility = View.VISIBLE
//                webView.loadUrl("android.resource://" + packageName + "/" + R.raw.pdf)
            }
            "VIDEO" ->{
                vid.visibility = View.VISIBLE
            }
            "CAMERA" ->{
                cameraView.visibility = View.VISIBLE
            }
        }

        windowManager.defaultDisplay.getMetrics(metrics)
        screenDensity = metrics.densityDpi
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?

        videoView = findViewById(R.id.vid)
        val videoPath = "android.resource://" + packageName + "/" + R.raw.trailer
        videoView?.setVideoURI(Uri.parse(videoPath))
        videoView?.start()
        toggleButton = findViewById(R.id.toggleStream)
        toggleButton?.setOnClickListener(this)
        playPauseButton = findViewById(R.id.playPause)
        playPauseButton?.setOnClickListener(this)

        startBroadcast()
        val started = Toast.makeText(this, "Started broadcast", Toast.LENGTH_SHORT)
        started.show()
        startScreenCapture()
    }

    override fun onResume() {
        super.onResume()
        if(type == "CAMERA") cameraView.start()
    }

    public override fun onPause() {
        super.onPause()
        stopScreenCapture()
        if(type == "CAMERA") cameraView.stop()
    }

    public override fun onDestroy() {
        super.onDestroy()
        tearDownMediaProjection()
        encoder?.release()
        sock?.close()
        if(type == "CAMERA") cameraView.destroy()
    }

    private fun startBroadcast() {

        try {

            if(tcpEnabled){
                group1 = InetAddress.getByName("192.168.43.110") // Samsung Galaxy S7
                tSock = Socket(group1, 1900)
            }else if(multicastEnabled) {
                // MULTICAST
                var networkInterface: NetworkInterface? = null
                val nets = NetworkInterface.getNetworkInterfaces()
                for (netint in Collections.list(nets))
                    if (netint.name == "wlan0") networkInterface = netint
                sock = MulticastSocket(4445)
                sock?.`interface` = networkInterface?.inetAddresses?.nextElement()
                group1 = InetAddress.getByName("224.0.113.0")
            } else{
                // We use MulticastSocket still because it is a subclass of DatagramSocket.
                sock = MulticastSocket(null)
                sock?.reuseAddress = true
                sock?.bind(InetSocketAddress(1900))

                // Connect to the transmitting device IP.
                // PIXEL HOST DEVICE
//                group1 = InetAddress.getByName("224.0.113.0"); // For multicast
//                group1 = InetAddress.getByName("192.168.43.6"); // OnePlus 5
                group1 = InetAddress.getByName("192.168.43.110") // Samsung Galaxy S7
//                group2 = InetAddress.getByName("192.168.43.37"); // Jk iPhone
//                group2 = InetAddress.getByName("192.168.43.137"); // Moto E4
//                group1 = InetAddress.getByName("192.168.43.81"); // Lab iPhone
                group2 = InetAddress.getByName("192.168.43.13")

                // E4 HOST DEVICE
//                group1 = InetAddress.getByName("192.168.43.7"); // Pixel
//                group1 = InetAddress.getByName("192.168.43.37"); // Jk iPhone

                // Oneplus 5 HOST DEVICE
//              group1 = InetAddress.getByName("192.168.43.110") // Galaxy S7
            }
        } catch (e: SocketException) {
            e.printStackTrace()
        } catch (e: UnknownHostException) {
            e.printStackTrace()
        }

        if(debugEnabled && DEBUG_WRITE_TO_FILE) {
            // Request to write to storage.
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            )
            // Set up file writing for debugging.
            val s = SimpleDateFormat("ddMMyyyyhhmmss")
            val fileOut = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    s.format(Date()) + "testTransmitterOutput.h264")
            try {
                fileOutputStream = FileOutputStream(fileOut, true)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }

        try {
            // Create and configure the encoder
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                    streamWidth, streamHeight)
            format.setInteger(MediaFormat.KEY_BIT_RATE, STREAMING_BIT_RATE)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, STREAMING_FRAME_RATE)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0)
            format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_PREVIOUS_FRAME_AFTER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                format.setInteger(MediaFormat.KEY_LATENCY, 0)
            }
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            format.setInteger(MediaFormat.KEY_PRIORITY, 0x00)
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            inputSurface = MediaCodec.createPersistentInputSurface()
            encoder?.setInputSurface(inputSurface)
            encoder?.setCallback(CastexEncoderCallback())
            encoder?.start()
        }
        catch (e: IOException) {
            e.printStackTrace()
        }
    }

    public override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        if (mResultData != null) {
            outState!!.putInt(STATE_RESULT_CODE, mResultCode)
            outState.putParcelable(STATE_RESULT_DATA, mResultData)
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.toggleStream -> if (mVirtualDisplay == null) {
                startScreenCapture()
            } else {
                stopScreenCapture()
            }
            R.id.playPause -> if (videoView!!.isPlaying()) {
                videoView?.pause()
                playPauseButton?.setText(R.string.play)
            } else {
                videoView?.start()
                playPauseButton?.setText(R.string.pause)
            }
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "User cancelled")
                Toast.makeText(this, R.string.user_cancelled, Toast.LENGTH_SHORT).show()
                return
            }
            val activity = this
            Log.i(TAG, "Starting screen capture")
            mResultCode = resultCode
            mResultData = data
            setUpMediaProjection()
            setUpVirtualDisplay()
        }
    }

    private fun setUpMediaProjection() {
        mMediaProjection = mediaProjectionManager?.getMediaProjection(mResultCode, mResultData)
    }

    private fun tearDownMediaProjection() {
            mMediaProjection?.stop()
            mMediaProjection = null
    }

    private fun startScreenCapture() {
        if (inputSurface == null) {
            return
        }
        if (mMediaProjection != null) {
            setUpVirtualDisplay()
        } else if (mResultCode != 0 && mResultData != null) {
            setUpMediaProjection()
            setUpVirtualDisplay()
        } else {
            // This initiates a prompt dialog for the user to confirm screen projection.
            startActivityForResult(
                    mediaProjectionManager?.createScreenCaptureIntent(),
                    REQUEST_MEDIA_PROJECTION)
        }
    }

    private fun setUpVirtualDisplay() {
        mVirtualDisplay = mMediaProjection?.createVirtualDisplay("ScreenCapture",
                streamWidth, streamHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface, null, null)

        toggleButton?.setText(R.string.stop)
    }

    private fun stopScreenCapture() {
        if (mVirtualDisplay == null) {
            return
        }
        mVirtualDisplay?.release()
        mVirtualDisplay = null
        toggleButton?.setText(R.string.start)
    }

    private inner class BroadcastTask internal constructor(internal var packetOut: DatagramPacket) : AsyncTask<String, String, String>() {

        override fun doInBackground(vararg strings: String): String? {
            try {
                when {
                    tcpEnabled -> {
                        val out = DataOutputStream(tSock?.getOutputStream())
                        out.write(packetOut.data)
                    }
                    multicastEnabled -> sock?.send(packetOut)
                    else -> sock?.send(packetOut)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return ""
        }

        override fun onPostExecute(result: String) {}
    }

    private inner class CastexEncoderCallback: MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            // Wait for SPS and PPS frames to be sent first.
            if (!configSent) {
                codec.releaseOutputBuffer(index, false)
                return
            }

            val outputBuffer = codec.getOutputBuffer(index)
            val buf: ByteBuffer

            if (outputBuffer != null) {
                buf = ByteBuffer.allocate(outputBuffer.limit() + 4)
                buf.putInt(++frameCount)
                if(debugEnabled && DEBUG_WRITE_TO_FILE) {
                    fileOutputStream!!.write((outputBuffer.limit().toString() + "\n").toByteArray())
                }
                while (outputBuffer.position() < outputBuffer.limit()) {
                    val cur:Byte = outputBuffer.get()
//                        fileOutputStream!!.write(cur as Int)
                    buf.put(cur)
                }
                buf.put(outputBuffer)
                buf.flip()

                Log.d(TAG, "Sending packet of size: " + outputBuffer.limit().toString() )
                val broadcastTask = BroadcastTask(DatagramPacket(buf.array(), outputBuffer.limit(), group1, PORT_OUT))
                broadcastTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)

                if(!multicastEnabled && !tcpEnabled) {
                    // Pseudo-multicast iterative approach
                        val broadcastTask2 = BroadcastTask(DatagramPacket(buf.array(), outputBuffer.limit(), group2, PORT_OUT))
                        broadcastTask2.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)
                }
            } else {
                return
            }

            codec.releaseOutputBuffer(index, false)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            Log.d(TAG, "Updated output format! New height:"
                    + format.getInteger(MediaFormat.KEY_HEIGHT) + " new width: " +
                    format.getInteger(MediaFormat.KEY_WIDTH))

            val sps = format.getByteBuffer("csd-0")
            val pps = format.getByteBuffer("csd-1")
            // Add 4 additional bytes for debugging
            val spsOut = ByteBuffer.allocate(sps.limit() + 4)
            val ppsOut = ByteBuffer.allocate(pps.limit() + 4)
            // Place 4-byte frame count before the actual payload
            spsOut.putInt(++frameCount)
            ppsOut.putInt(++frameCount)
            spsOut.put(sps)
            ppsOut.put(pps)
            val spsTask = BroadcastTask(DatagramPacket(spsOut.array(), spsOut.limit(), group1, PORT_OUT))
            val ppsTask = BroadcastTask(DatagramPacket(ppsOut.array(), ppsOut.limit(), group1, PORT_OUT))
            spsTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)
            ppsTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)
            if(!multicastEnabled && !tcpEnabled) {
                val spsTask2 = BroadcastTask(DatagramPacket(spsOut.array(), spsOut.limit(), group2, PORT_OUT))
                val ppsTask2 = BroadcastTask(DatagramPacket(ppsOut.array(), ppsOut.limit(), group2, PORT_OUT))
                spsTask2.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)
                ppsTask2.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)
            }
            configSent = true
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {}
    }
}
