package info.jkjensen.castex.streamtransmitter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import net.majorkernelpanic.streaming.rtsp.RtspServer

/**
 * Created by jk on 1/18/18.
 */
class CastexNotificationBroadcastReceiver: BroadcastReceiver() {
    companion object {
        val TAG = "CastexNotificationBroadcastReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when(intent?.action){
            ScreenCapturerService.STOP_ACTION ->{
                Log.d(TAG, "Stopping ScreenCaptureService")
                context?.stopService(Intent(context, ScreenCapturerService::class.java))
                context?.stopService(Intent(context, RtspServer::class.java))
            }
        }
    }

}