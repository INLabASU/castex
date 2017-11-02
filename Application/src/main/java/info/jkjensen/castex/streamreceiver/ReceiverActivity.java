package info.jkjensen.castex.streamreceiver;


import android.Manifest;
import android.animation.TimeAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Objects;
import java.util.PriorityQueue;

import info.jkjensen.castex.Preferences;
import info.jkjensen.castex.R;


/**
 * This activity uses a {@link android.view.TextureView} to render the frames of a video decoded using
 * {@link android.media.MediaCodec} API.
 */
public class ReceiverActivity extends Activity implements TextureView.SurfaceTextureListener {
    private final boolean DEBUG_WRITE_TO_FILE = false;

    private final static int QUEUE_INITIAL_SIZE = 50;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private final int STREAMING_FRAME_RATE = 30;

    private TextureView mPlaybackView;
    private TimeAnimator mTimeAnimator = new TimeAnimator();

    // A utility that wraps up the underlying input and output buffer processing operations
    // into an east to use API.
    private MediaCodec mediaCodec;

    private NALParser nalParser;
    private Boolean textureReady = false;

    DecodeFrameTask frameTask;
    PacketReceiverTask packetReceiverTask;

    long startUs;

    MenuItem playButton;
    FileOutputStream fileOutputStream = null;
    MulticastSocket clientSocket;
    Socket tSock;
    private int SOCKET_PORT = 4446;

//    private int streamWidth = 1080;
//    private int streamHeight = 1794;
    private int streamWidth = 360;
    private int streamHeight = 640;
//    private int streamWidth = 750;
//    private int streamHeight = 1334;

    private final PriorityQueue<ByteBuffer> nalQueue = new PriorityQueue<>(QUEUE_INITIAL_SIZE);

    private Boolean multicastEnabled;
    private Boolean debugEnabled;
    private Boolean tcpEnabled;

    private long expectedFrameNumber = 0;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver);

        // Configure receiver preferences
        SharedPreferences sharedPreferences = getSharedPreferences("appConfig", Context.MODE_PRIVATE);
        multicastEnabled = sharedPreferences.getBoolean(Preferences.Companion.getKEY_MULTICAST(), false);
        debugEnabled = sharedPreferences.getBoolean(Preferences.Companion.getKEY_DEBUG(), false);
        tcpEnabled = sharedPreferences.getBoolean(Preferences.Companion.getKEY_TCP(), false);

        mPlaybackView = findViewById(R.id.PlaybackView);
        mPlaybackView.setSurfaceTextureListener(this);
        frameTask = new DecodeFrameTask();

        if(!tcpEnabled && multicastEnabled) {
            // Configure OS for multicast
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiManager.MulticastLock multicastLock = wifiManager.createMulticastLock("multicastLock");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mTimeAnimator != null && mTimeAnimator.isRunning()) {
            mTimeAnimator.end();
        }

        if(frameTask != null) {
            frameTask.cancel(true);
        }
        if(packetReceiverTask != null) {
            packetReceiverTask.cancel(true);
        }
        if(mediaCodec != null) {
            mediaCodec.release();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        // Set up the UDP socket for receiving data.
        try {
//            if(clientSocket == null || !clientSocket.isConnected()) {
                if(tcpEnabled){
                    tSock = new Socket(InetAddress.getByName("192.168.43.110"),1900);
                }else if(multicastEnabled) {
                    // Get the network interface for proper configuration.
                    NetworkInterface networkInterface = null;
                    Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
                    for (NetworkInterface netint : Collections.list(nets))
                        if (Objects.equals(netint.getName(), "wlan0")) networkInterface = netint;
                    clientSocket = new MulticastSocket(SOCKET_PORT);
                    clientSocket.joinGroup(new InetSocketAddress(InetAddress.getByName("224.0.113.0"), SOCKET_PORT), networkInterface);
                } else {
                    // We still use a MulticastSocket because it is a subclass of DatagramSocket
                    // Adjust the port for unicast.
                    SOCKET_PORT = 1900;
                    clientSocket = new MulticastSocket(null);

                    clientSocket.setReuseAddress(true);
                    if(!clientSocket.isBound()) {
                        clientSocket.bind(new InetSocketAddress(SOCKET_PORT));
                    }
                }
//            }
            nalParser = new NALParser();
        } catch (IOException e) {
            e.printStackTrace();
        }

//        if(debugEnabled && DEBUG_WRITE_TO_FILE) {
            // Request to write to storage.
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
//        }

        // Start receiving and decoding.
        packetReceiverTask = new PacketReceiverTask();
        packetReceiverTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        frameTask = new DecodeFrameTask();
        frameTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_menu, menu);
        playButton = menu.getItem(0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_play) {
//            mAttribView.setVisibility(View.VISIBLE);
//            ActivityCompat.requestPermissions(
//                    this,
//                    PERMISSIONS_STORAGE,
//                    REQUEST_EXTERNAL_STORAGE
//            );
//            if(textureReady){
//                Boolean canRun = true;
//                if(frameTask.getStatus() == AsyncTask.Status.RUNNING){
//                    frameTask.cancel(true);
//                    Log.w("Main", "Can run: " + canRun);
//                }
//                packetReceiverTask = new PacketReceiverTask();
//                packetReceiverTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//                frameTask = new DecodeFrameTask();
//                frameTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//                playButton.setEnabled(false);
//            }
        }
        return true;
    }

    public void startPlayback() {
        startUs = System.nanoTime() / 1000;

        if(debugEnabled && DEBUG_WRITE_TO_FILE) {
            SimpleDateFormat s = new SimpleDateFormat("ddMMyyyyhhmmss");
            File fileOut = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "testReceiverOutput" + s.format(new Date()) + ".txt");
            try {
                Log.d("Main", "Writing to " + fileOut.toString());
                fileOutputStream = new FileOutputStream(fileOut, true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                streamWidth, streamHeight);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, STREAMING_FRAME_RATE);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        NALBuffer sps;
        // Get NAL Units until an SPS unit is found.
        while(true){
            while(peekQueue() == null);
            sps = nalParser.getNext(pollQueue());
            Log.d("Main", "searching for an SPS frame...");
            if((sps.buffer.get(4) & 0x1f) != 0x07){
                Log.d("Main", "Not an SPS Frame");
                continue;
            }
            Log.d("Main", "Found an SPS Frame");

            while(peekQueue() == null);
            NALBuffer pps = nalParser.getNext(pollQueue());
            if((pps.buffer.get(4) & 0x1f) != 0x08){
                Log.d("Main", "Not a PPS Frame");
                continue;
            }
            Log.d("Main", "Found a PPS Frame");

            format.setByteBuffer("csd-0", sps.buffer);
            format.setByteBuffer("csd-1", pps.buffer);
//            try {
//                if(debugEnabled && DEBUG_WRITE_TO_FILE){
//                    fileOutputStream.write(sps.buffer.array());
//                    fileOutputStream.write(pps.buffer.array());
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

            try {
                mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                mediaCodec.configure(format, new Surface(mPlaybackView.getSurfaceTexture()), null, 0);
                mediaCodec.start();
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Log.e("Main", "------------------- CONFIG FRAMES ABOVE THIS POINT ---------------------");
        long totaliFrameBytes = 0;
        long totaliFrames = 0;

        Boolean isIFrame = false;
        while(true) {

            while(peekQueue() == null);
            NALBuffer nb = nalParser.getNext(pollQueue());

            int type = nb.buffer.get(4);
            if(debugEnabled && DEBUG_WRITE_TO_FILE) try {
                fileOutputStream.write((nb.buffer.limit() + "\n").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }

            if((type & 0x1f) == 0x05){
                totaliFrameBytes += nb.buffer.limit();
                totaliFrames++;
                isIFrame = true;
                Log.d("iFrame", "Current totals - iframes: " + totaliFrames + ", bytes: " + totaliFrameBytes);
            }
            nb.buffer.rewind();

            if (nb == null) {
                Log.e("Main", "Couldn't get NAL");
                return;
            }

            int inputIndex;
            while ((inputIndex = mediaCodec.dequeueInputBuffer(-1)) < 0) {
                Log.d("Main", "Input index: " + inputIndex);
            }
//            Log.d("Main", "Final Input index: " + inputIndex);

            ByteBuffer codecBuffer = mediaCodec.getInputBuffer(inputIndex);
            codecBuffer.put(nb.buffer);

//            try {
//                if(debugEnabled && DEBUG_WRITE_TO_FILE)fileOutputStream.write(nb.buffer.array());
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

            long presentationTimeMS = (System.nanoTime()/1000) - startUs; // Don't think this is necessary.

            int flags = 0;
            // If this is the final NAL Unit we can signify the end of the stream.
//            if(nb.lastNAL){
//                flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
//            }
            if((type & 0x1f) == 0x07 || (type & 0x1f) == 0x08){
                Log.e("SPSPPS", "Found new SPS, PPS");
                mediaCodec.queueInputBuffer(inputIndex, 0, nb.size, /*presentationTimeMS*/0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
            } else {
                mediaCodec.queueInputBuffer(inputIndex, 0, nb.size, /*presentationTimeMS*/0, isIFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : flags);
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int outputIndex = mediaCodec.dequeueOutputBuffer(info, 0);
            if (outputIndex >= 0) {
                mediaCodec.releaseOutputBuffer(outputIndex, true);
            }
            isIFrame = false;
        }
    }

    private class DecodeFrameTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... strings) {
            startPlayback();
            return null;
        }

        @Override
        protected void onPostExecute(String result){
            try{
                mediaCodec.stop();
                mediaCodec.release();
            } catch (Exception e){
                e.printStackTrace();
            }
//            nalParser.release();
        }
    }

    private class PacketReceiverTask extends AsyncTask<String, String, String> {
        private DatagramPacket dPacket;

        public PacketReceiverTask(){
        }

        @Override
        protected String doInBackground(String... strings) {
            // Continuously receive packets and put them on a priorityqueue
            while(true){
                try {
                    byte[] buff = new byte[100535];
                    dPacket = new DatagramPacket(buff, 100535);
                    // Some Android devices require you to manually reset data every time or the
                    // previous data size will be used.
                    dPacket.setData(buff);
                    if(tcpEnabled){
                        ByteBuffer buf = ByteBuffer.wrap(buff);
                        SocketChannel input = tSock.getChannel();
                        int n = 0;
                        while(n >= 0){
                            n = input.read(buf);
                        }
                        dPacket.setData(buf.array());
                    }else if(multicastEnabled) {
                        clientSocket.receive(dPacket);
                    }else{
                        clientSocket.receive(dPacket);
                    }
                    ByteBuffer buf = ByteBuffer.wrap(dPacket.getData());
                    int frameNumber = buf.getInt();
                    if(frameNumber == 1) expectedFrameNumber = 0;
                    expectedFrameNumber++;
//                      Log.d("FrameCountTest", "Expected Frame Number: " + expectedFrameNumber + "Frame Number: " + frameNumber);
                    Log.d("FrameCountTest", "Discrepancy:" + (frameNumber - expectedFrameNumber));
                    buf.compact();
                    dPacket.setData(buf.array(), buf.arrayOffset(), buf.limit());
                    addToQueue(ByteBuffer.wrap(dPacket.getData(), dPacket.getOffset(), dPacket.getLength()).duplicate());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private ByteBuffer pollQueue(){
        synchronized (nalQueue){
            return nalQueue.poll();
        }
    }

    private ByteBuffer peekQueue(){
        synchronized (nalQueue){
            return nalQueue.peek();
        }
    }

    private boolean addToQueue(ByteBuffer b){
        synchronized (nalQueue){
//            Log.d("Main", "Queue size: " + nalQueue.size());
            return nalQueue.add(b);
        }
    }
}
