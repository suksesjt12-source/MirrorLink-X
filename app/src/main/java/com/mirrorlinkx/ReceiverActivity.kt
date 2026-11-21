package com.mirrorlinkx

import android.app.Activity
import android.os.Bundle
import android.graphics.BitmapFactory
import android.graphics.Color
import android.widget.*
import java.net.ServerSocket
import java.net.Socket
import java.io.DataInputStream
import java.util.concurrent.Executors

class ReceiverActivity:Activity(){
 private lateinit var preview:ImageView;private lateinit var status:TextView;private var server:ServerSocket?=null;private val pool=Executors.newCachedThreadPool()
 override fun onCreate(b:Bundle?){super.onCreate(b);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,18,12,12);setBackgroundColor(Color.BLACK)}
 status=TextView(this).apply{text="Waiting on TCP 4747";textSize=16f;setTextColor(Color.WHITE)};preview=ImageView(this).apply{adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER}
 root.addView(status);root.addView(preview,LinearLayout.LayoutParams(-1,0,1f));root.addView(Button(this).apply{text="Stop receiver";setOnClickListener{finish()}});setContentView(root);pool.execute{listen()}}
 private fun listen(){try{server=ServerSocket(4747);while(!isFinishing){val client=server!!.accept();runOnUiThread{status.text="Connected: ${client.inetAddress.hostAddress}"};pool.execute{readFrames(client)}}}catch(e:Exception){if(!isFinishing)runOnUiThread{status.text="Receiver error: ${e.message}"}}}
 private fun readFrames(s:Socket){try{val input=DataInputStream(s.getInputStream());while(true){val size=try{input.readInt()}catch(_:Exception){break};if(size<=0||size>6_000_000)break;val bytes=ByteArray(size);input.readFully(bytes);val bmp=BitmapFactory.decodeByteArray(bytes,0,size)?:continue;runOnUiThread{preview.setImageBitmap(bmp)}}}catch(_:Exception){}finally{try{s.close()}catch(_:Exception){};runOnUiThread{status.text="Disconnected • waiting"}}}
 override fun onDestroy(){try{server?.close()}catch(_:Exception){};pool.shutdownNow();super.onDestroy()}
}
