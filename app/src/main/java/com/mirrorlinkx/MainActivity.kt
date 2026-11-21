package com.mirrorlinkx

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.net.wifi.WifiManager
import android.text.format.Formatter
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class MainActivity : Activity() {
    private val bg=Color.rgb(10,13,22); private val cyan=Color.rgb(70,220,255)
    private lateinit var status:TextView
    private val captureRequest=42
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);buildUi()}
    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(26,28,26,24);setBackgroundColor(bg)}
        val scroll=ScrollView(this);scroll.addView(root);setContentView(scroll)
        root.addView(label("MIRRORLINK",13,cyan,true));root.addView(label("X",42,Color.WHITE,true))
        root.addView(label("Android-to-Android • Local Wi-Fi",14,Color.LTGRAY,false));root.addView(space(18))
        root.addView(label("DEVICE IP: "+localIp(),15,Color.WHITE,true))
        status=label("● Ready",15,Color.WHITE,false);root.addView(status);root.addView(space(16))
        root.addView(label("RECEIVE SCREEN",12,cyan,true))
        root.addView(button("Start receiver",Color.rgb(0,145,170)){startActivity(Intent(this,ReceiverActivity::class.java))})
        root.addView(space(12));root.addView(label("SEND SCREEN",12,cyan,true))
        root.addView(label("Enter the receiver phone's IP address. Both devices must use the same Wi-Fi/LAN.",13,Color.LTGRAY,false))
        val ip=EditText(this).apply{hint="Receiver IP (192.168.x.x)";setTextColor(Color.WHITE);hintTextColor=Color.GRAY;setSingleLine(true)}
        root.addView(ip)
        root.addView(button("Share my screen",Color.rgb(35,90,130)){
            val host=ip.text.toString().trim()
            if(!host.matches(Regex("""[0-9]{1,3}(\.[0-9]{1,3}){3}"""))){Toast.makeText(this,"Enter a valid IPv4 address",Toast.LENGTH_SHORT).show();return@button}
            senderTarget=host
            val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(),captureRequest)
        })
        root.addView(space(18));root.addView(label("PAIRING QR",12,cyan,true))
        val qr=ImageView(this).apply{setBackgroundColor(Color.WHITE);setPadding(12,12,12,12)}
        try{val m=QRCodeWriter().encode("mirrorlinkx://receiver?host=${localIp()}&port=4747",BarcodeFormat.QR_CODE,320,320);val b=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888);for(x in 0 until 320)for(y in 0 until 320)b.setPixel(x,y,if(m[x,y])Color.BLACK else Color.WHITE);qr.setImageBitmap(b)}catch(_:Exception){}
        root.addView(qr,LinearLayout.LayoutParams(240,240).apply{gravity=Gravity.CENTER})
        root.addView(label("QR contains this device's IP address. Receiver listens on TCP 4747.",12,Color.LTGRAY,false))
        root.addView(space(18));root.addView(label("FEATURES",12,cyan,true))
        root.addView(label("✓ LAN JPEG video streaming\n✓ MediaProjection capture consent\n✓ Receiver display\n✓ Orientation-following capture\n✓ QR pairing payload\n• Audio and remote touch not included yet",14,Color.WHITE,false))
    }
    private fun localIp():String=try{val w=getSystemService(WIFI_SERVICE) as WifiManager;Formatter.formatIpAddress(w.connectionInfo.ipAddress).takeIf{it!="0.0.0.0"}?:"Unavailable"}catch(_:Exception){"Unavailable"}
    override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==captureRequest&&c==RESULT_OK&&d!=null){startForegroundService(Intent(this,CaptureService::class.java).apply{putExtra("resultCode",c);putExtra("data",d);putExtra("host",senderTarget)});status.text="● Sharing to ${senderTarget}"}else if(r==captureRequest)status.text="● Permission declined"}
    private fun label(t:String,s:Int,c:Int,b:Boolean)=TextView(this).apply{text=t;textSize=s.toFloat();setTextColor(c);if(b)setTypeface(null,android.graphics.Typeface.BOLD)}
    private fun space(h:Int)=android.view.View(this).apply{layoutParams=LinearLayout.LayoutParams(1,h)}
    private fun button(t:String,c:Int,a:()->Unit)=Button(this).apply{text=t;setTextColor(Color.WHITE);setBackgroundColor(c);setOnClickListener{a()}}
    companion object{var senderTarget="127.0.0.1"}
}
