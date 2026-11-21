package com.mirrorlinkx

import android.app.*
import android.content.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.ImageReader
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import java.net.Socket
import java.io.DataOutputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService:Service(){
 private var projection:MediaProjection?=null;private var display:VirtualDisplay?=null;private var reader:ImageReader?=null;private var socket:Socket?=null;private var output:DataOutputStream?=null
 private val worker=Executors.newSingleThreadExecutor();private val busy=AtomicBoolean(false)
 override fun onStartCommand(i:Intent?,f:Int,id:Int):Int{
  createChannel();startForeground(1,notification());val code=i?.getIntExtra("resultCode",0)?:0;val data=i?.getParcelableExtra<Intent>("data");val host=i?.getStringExtra("host")?:return START_NOT_STICKY
  if(data!=null)worker.execute{stream(host,code,data)};return START_NOT_STICKY
 }
 private fun stream(host:String,code:Int,data:Intent){try{
  socket=Socket(host,4747);socket!!.tcpNoDelay=true;output=DataOutputStream(BufferedOutputStream(socket!!.getOutputStream()))
  projection=(getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code,data)
  val metrics=resources.displayMetrics
  val scale=minOf(1f,900f/metrics.widthPixels);val w=(metrics.widthPixels*scale).toInt().coerceAtLeast(2);val h=(metrics.heightPixels*scale).toInt().coerceAtLeast(2)
  reader=ImageReader.newInstance(w,h,ImageFormat.RGBA_8888,2)
  reader!!.setOnImageAvailableListener({r->val image=try{r.acquireLatestImage()}catch(_:Exception){null}?:return@setOnImageAvailableListener
   if(!busy.compareAndSet(false,true)){image.close();return@setOnImageAvailableListener}
   worker.execute{try{val p=image.planes[0];val padded=Bitmap.createBitmap(w+(p.rowStride-p.pixelStride*w)/p.pixelStride,h,Bitmap.Config.ARGB_8888);padded.copyPixelsFromBuffer(p.buffer);val bitmap=Bitmap.createBitmap(padded,0,0,w,h);val baos=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,50,baos);val bytes=baos.toByteArray();synchronized(output!!){output!!.writeInt(bytes.size);output!!.write(bytes);output!!.flush()};bitmap.recycle();padded.recycle()}catch(_:Exception){stopSelf()}finally{image.close();busy.set(false)}}
  },Handler(Looper.getMainLooper()))
  display=projection!!.createVirtualDisplay("MirrorLinkX",w,h,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,null)
 }catch(_:Exception){stopSelf()}}
 override fun onDestroy(){try{output?.writeInt(0);output?.flush()}catch(_:Exception){};try{socket?.close()}catch(_:Exception){};display?.release();reader?.close();projection?.stop();worker.shutdownNow();super.onDestroy()}
 override fun onBind(i:Intent?):IBinder?=null
 private fun createChannel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("mirrorlink_capture","MirrorLink X screen share",NotificationManager.IMPORTANCE_LOW))}
 private fun notification():Notification=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,"mirrorlink_capture").setContentTitle("MirrorLink X").setContentText("Screen sharing active").setSmallIcon(android.R.drawable.ic_menu_view).build()else @Suppress("DEPRECATION") Notification.Builder(this).setContentTitle("MirrorLink X").setContentText("Screen sharing active").setSmallIcon(android.R.drawable.ic_menu_view).build()
}
