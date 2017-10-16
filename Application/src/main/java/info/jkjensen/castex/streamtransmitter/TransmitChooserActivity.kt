package info.jkjensen.castex.streamtransmitter

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import info.jkjensen.castex.R
import kotlinx.android.synthetic.main.activity_transmit_chooser.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import android.app.Activity
import android.media.MediaPlayer
import android.net.Uri
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.view.SurfaceHolder
import org.jetbrains.anko.startActivity
import java.io.IOException


class TransmitChooserActivity:AppCompatActivity(), SurfaceHolder.Callback {
 private val READ_REQUEST_CODE = 42


 private var holder: SurfaceHolder? = null
 private val mediaPlayer: MediaPlayer = MediaPlayer()
 var fileUri: Uri? = null

 protected override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  setContentView(R.layout.activity_transmit_chooser)
  holder = videoView.holder
  holder?.addCallback(this)

  chooseFileButton.onClick { openContent("FILE") }
  cameraButton.onClick { openContent("CAMERA") }
  videoButton.onClick { openContent("VIDEO") }
  webButton.onClick { openContent("WEB") }
 }

 override fun surfaceCreated(p0: SurfaceHolder?) {
  mediaPlayer.setDisplay(holder)
  if(mediaPlayer.isPlaying) return
  // Loop the video
  mediaPlayer.setOnPreparedListener({
   mediaPlayer -> mediaPlayer.isLooping = true
  })
  val path = "android.resource://" + packageName + "/" + R.raw.waves
  try{
   mediaPlayer.setDataSource(this, Uri.parse(path))
   mediaPlayer.prepare()
   mediaPlayer.start()
  } catch (e: IOException){
   e.printStackTrace()
  }
 }

 override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
 }

 override fun surfaceDestroyed(p0: SurfaceHolder?) {
 }

 override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
  if (requestCode === READ_REQUEST_CODE && resultCode === Activity.RESULT_OK) {
   // The document selected by the user won't be returned in the intent.
   // Instead, a URI to that document will be contained in the return intent
   // provided to this method as a parameter.
   // Pull that URI using resultData.getData().
   if (data != null) {
    fileUri = data.data
    Log.i("TransmitChooserActivity", "Uri: " + fileUri!!.toString())
    showFile(fileUri)
   }
  }
  super.onActivityResult(requestCode, resultCode, data)
 }

 private fun openContent(type:String){
  when(type){
   "FILE"-> {
    Log.d("TCA", "File chosen")
   }
   "CAMERA"-> Log.d("TCA", "Camera chosen")
   "VIDEO"->{
    Log.d("TCA", "Video chosen")
   }
   "WEB"-> Log.d("TCA", "Web chosen")
  }
  startActivity<TransmitterActivity>("TYPE" to type)
 }

 private fun getFile(){
  val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
  intent.addCategory(Intent.CATEGORY_OPENABLE)
  intent.type = "*/*"
  startActivityForResult(intent, READ_REQUEST_CODE)
 }

 private fun showFile(uri:Uri?){
  Log.d("TransmitChooserActivity", uri!!.toString())
 }
}
