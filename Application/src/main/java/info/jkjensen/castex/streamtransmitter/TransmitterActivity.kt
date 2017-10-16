package info.jkjensen.castex.streamtransmitter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.icu.text.SimpleDateFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
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
import info.jkjensen.castex.R
import kotlinx.android.synthetic.main.activity_transmitter.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.util.*

class TransmitterActivity : AppCompatActivity(), View.OnClickListener {

    private val TAG = "ScreenCaptureFragment"

    private val REQUEST_EXTERNAL_STORAGE = 1
    private val PERMISSIONS_STORAGE = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private val STATE_RESULT_CODE = "result_code"
    private val STATE_RESULT_DATA = "result_data"

    private val REQUEST_MEDIA_PROJECTION = 1

    private var mScreenDensity: Int = 0

    private var mResultCode: Int = 0
    private var mResultData: Intent? = null

    private var mSurface: Surface? = null
    private var mMediaProjection: MediaProjection? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mMediaProjectionManager: MediaProjectionManager? = null
    private var toggleButton: Button? = null
    private var playPauseButton: Button? = null
    private val mSurfaceView: SurfaceView? = null
    private var videoView: VideoView? = null

    private var encoder: MediaCodec? = null
    private var fileOutputStream: FileOutputStream? = null

    private var sock: DatagramSocket? = null
    private var group1: InetAddress? = null
    private var group2: InetAddress? = null
    private val currentPacket: DatagramPacket? = null
    private var configSent = false

    private var type:String? = null
    var mHolder:SurfaceHolder? = null
    var camera:Camera? = null

    private val PORT_OUT = 1900
    //    private final int PORT_OUT = 1900;
//        private val streamWidth = 1080
//        private val streamHeight = 1794
//        private val streamWidth = 750
//        private val streamHeight = 1334
    private val streamWidth = 360
    private val streamHeight = 640

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transmitter)
        if (savedInstanceState != null) {
            mResultCode = savedInstanceState.getInt(STATE_RESULT_CODE)
            mResultData = savedInstanceState.getParcelable<Intent>(STATE_RESULT_DATA)
        }

        type = intent.getStringExtra("TYPE")
        Log.d("TransmitterActivity", "type is " + type)

        webView.settings.javaScriptEnabled = true
        when(type){
            "WEB" ->{
                webView.visibility = View.VISIBLE
                webView.loadUrl("http://www.jkjensen.info")
            }
            "FILE" ->{
                webView.visibility = View.VISIBLE
                webView.loadUrl("http://drive.google.com/viewerng/viewer?embedded=true&url=http://www.pdf995.com/samples/pdf.pdf")
            }
            "VIDEO" ->{
                vid.visibility = View.VISIBLE
            }
            "CAMERA" ->{
                cameraView.visibility = View.VISIBLE
//                ActivityCompat.requestPermissions(this,
//                        arrayOf(Manifest.permission.CAMERA),
//                        42)
            }
        }


        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        mScreenDensity = metrics.densityDpi
        mMediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?

        videoView = findViewById(R.id.vid)
        val path = "android.resource://" + packageName + "/" + R.raw.trailer
        videoView?.setVideoURI(Uri.parse(path))
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

//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
//        when(requestCode){
//            42->{
//                if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    mHolder = cameraSurfaceView.holder
//                    mHolder?.addCallback(this)
//                    mHolder?.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
//                    camera = Camera.open()
//                    camera?.setPreviewDisplay(mHolder)
//                    camera?.startPreview()
//                }
//            }
//        }
//    }

    override fun onResume() {
        super.onResume()
        if(type == "CAMERA") cameraView.start()
    }

    private fun startBroadcast() {

        try {
            sock = DatagramSocket(4445)
            // Connect to the transmitting device IP.
            // PIXEL HOST DEVICE
            //            group1 = InetAddress.getByName("224.0.113.0"); // For multicast
            //            group1 = InetAddress.getByName("192.168.43.6"); // OnePlus 5
            group1 = InetAddress.getByName("192.168.43.110") // Samsung Galaxy S7
            //            group2 = InetAddress.getByName("192.168.43.37"); // Jk iPhone
            //            group2 = InetAddress.getByName("192.168.43.137"); // Moto E4
            //            group1 = InetAddress.getByName("192.168.43.81"); // Lab iPhone
            group2 = InetAddress.getByName("192.168.43.13")

            // E4 HOST DEVICE
            //            group1 = InetAddress.getByName("192.168.43.7"); // Pixel
            //            group1 = InetAddress.getByName("192.168.43.37"); // Jk iPhone

        } catch (e: SocketException) {
            e.printStackTrace()
        } catch (e: UnknownHostException) {
            e.printStackTrace()
        }

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels

        ActivityCompat.requestPermissions(
                this,
                PERMISSIONS_STORAGE,
                REQUEST_EXTERNAL_STORAGE
        )
        // Set up file writing for debugging.
        val s = SimpleDateFormat("ddMMyyyyhhmmss")
        val fileOut = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                s.format(Date()) + "testFrameOutput.h264")
        try {
            fileOutputStream = FileOutputStream(fileOut, true)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }

        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            //            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
            //                    width, height);
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                    streamWidth, streamHeight)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 350000)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0)
            format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                format.setInteger(MediaFormat.KEY_LATENCY, 0)
            }
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
            format.setInteger(MediaFormat.KEY_PRIORITY, 0x00)
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mSurface = MediaCodec.createPersistentInputSurface()
            encoder?.setInputSurface(mSurface)
            encoder?.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {

                }

                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    // Wait for SPS and PPS frames to be sent first.
                    if (!configSent) {
                        codec.releaseOutputBuffer(index, false)
                        return
                    }

                    val outputBuffer = codec.getOutputBuffer(index)
                    //                    try {
                    val `val`: Int
                    val buf: ByteBuffer

                    if (outputBuffer != null) {
                        buf = ByteBuffer.allocate(outputBuffer.limit())
                        //                            while(outputBuffer.position() < outputBuffer.limit()){
                        //                                byte cur = outputBuffer.get();
                        //                                fileOutputStream.write(cur);
                        //                                buf.put(cur);
                        //                            }
                        buf.put(outputBuffer)
                        buf.flip()
                        Log.d(TAG, "Wrote " + outputBuffer.limit() + " bytes.")

                        val broadcastTask = BroadcastTask(DatagramPacket(buf.array(), outputBuffer.limit(), group1, PORT_OUT))
                        val broadcastTask2 = BroadcastTask(DatagramPacket(buf.array(), outputBuffer.limit(), group2, PORT_OUT))
                        broadcastTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)
                        broadcastTask2.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)
                    } else {
                        return
                    }

                    codec.releaseOutputBuffer(index, false)
                    //                    } catch (IOException e) {
                    //                        e.printStackTrace();
                    //                    }

                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {

                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    Log.d(TAG, "Updated output format! New height:"
                            + format.getInteger(MediaFormat.KEY_HEIGHT) + " new width: " +
                            format.getInteger(MediaFormat.KEY_WIDTH))

                    val sps = format.getByteBuffer("csd-0")
                    val pps = format.getByteBuffer("csd-1")
                    val spsTask = BroadcastTask(DatagramPacket(sps.array(), sps.limit(), group1, PORT_OUT))
                    val spsTask2 = BroadcastTask(DatagramPacket(sps.array(), sps.limit(), group2, PORT_OUT))
                    val ppsTask = BroadcastTask(DatagramPacket(pps.array(), pps.limit(), group1, PORT_OUT))
                    val ppsTask2 = BroadcastTask(DatagramPacket(pps.array(), pps.limit(), group2, PORT_OUT))
                    spsTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)
                    ppsTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)
                    spsTask2.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)
                    ppsTask2.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR)
                    configSent = true
                }
            })
            encoder?.start()
        }// Set the encoder priority to realtime.
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

    private fun setUpMediaProjection() {
        mMediaProjection = mMediaProjectionManager?.getMediaProjection(mResultCode, mResultData)
    }

    private fun tearDownMediaProjection() {
//        if (mMediaProjection != null) {
            mMediaProjection?.stop()
            mMediaProjection = null
//        }
    }

    private fun startScreenCapture() {
        if (mSurface == null) {
            return
        }
        if (mMediaProjection != null) {
            setUpVirtualDisplay()
        } else if (mResultCode != 0 && mResultData != null) {
            setUpMediaProjection()
            setUpVirtualDisplay()
        } else {
            Log.i(TAG, "Requesting confirmation")
            // This initiates a prompt dialog for the user to confirm screen projection.
            startActivityForResult(
                    mMediaProjectionManager?.createScreenCaptureIntent(),
                    REQUEST_MEDIA_PROJECTION)
        }
    }

    private fun setUpVirtualDisplay() {
        //        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
        //                mSurfaceView.getWidth(), mSurfaceView.getHeight(), mScreenDensity,
        //                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        //                mSurface, null, null);
        mVirtualDisplay = mMediaProjection?.createVirtualDisplay("ScreenCapture",
                streamWidth, streamHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSurface, null, null)

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
                sock?.send(packetOut)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return ""
        }

        override fun onPostExecute(result: String) {}
    }
}
