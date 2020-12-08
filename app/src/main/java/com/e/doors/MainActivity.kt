package com.e.doors

import android.content.Context
import android.graphics.Color
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Vibrator
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetAddress.getByAddress
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.TimeUnit
import android.os.Handler as Handler1

//import kotlinx.coroutines.*


const val  targetNetName="theflat"
//val  targetNetName="AndroidWifi"
const val wifiTimerInterval = 1000.toLong()
const val udpReplaySocketTimeout=100
const val udpRequestPort:Int = 54545
const val udpReplayPort:Int = 54546
var receiveBuf = ByteArray(256)
var udpReceivePacket = DatagramPacket(receiveBuf, receiveBuf.size)
var udpRequest = "IsSomebodyHere".toByteArray()
var udpRequestPacket:DatagramPacket = DatagramPacket(udpRequest, udpRequest.size, getBroadcastAddress(), udpRequestPort)
var deviceName = "doors"

fun getBroadcastAddress(): InetAddress {
    val quads = ByteArray(4)
    for (k in 0..3)
        quads[k] = 255.toByte()
    quads[0] = 192.toByte()
    quads[1] = 168.toByte()
    quads[2] = 1.toByte()
//    quads[3] = 112.toByte()
    return InetAddress.getByAddress(quads)
}

class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.M)

    public lateinit var   startBtn :Button
    public lateinit var txt1 :TextView
    public lateinit var txt2 :TextView
    public lateinit var txt3 :TextView
    public lateinit var txt4 :TextView
    public lateinit var wifiTimerTask : WifiTimerTask

    override fun onStop() {
        super.onStop()
        // остановить  подзадачу таймера
        wifiTimerTask.close()
        //
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            finishAffinity();
        } else {
            finish();
        }
        System.exit(0);
    }

    //
    class WifiTimerTask (val context: Context ):  Runnable{

        var udpRequestSocket = DatagramSocket(udpRequestPort)
        var udpReplaySocket  = DatagramSocket(udpReplayPort)
        var deviceIp = getBroadcastAddress()
        var handler = android.os.Handler()
        var stopWork = false
        var n:Long = 0
 
        private val vsp = View(context) // ради одного метода
        val btn = vsp.findViewById<Button>(R.id.startBtn)
        val t1  = vsp.findViewById<TextView>(R.id.textView1)
        val t2  = vsp.findViewById<TextView>(R.id.textView2)
        val t3  = vsp.findViewById<TextView>(R.id.textView3)
        val t4  = vsp.findViewById<TextView>(R.id.textView4)

        init {
            n=0
            udpReplaySocket.soTimeout = udpReplaySocketTimeout
            udpRequestSocket.setBroadcast(true)
        }

        public fun getNetName():String {
            var wifiManager = context.getApplicationContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
            var wifiInfo = wifiManager.getConnectionInfo() as WifiInfo
            var netName = wifiInfo.getSSID().replace("\"", "")
            return netName
        }

        public fun stringToUdp(s:String, Addr:InetAddress){
            var udpRequest = s.toByteArray()
            var udpRequestPacket = DatagramPacket(udpRequest, udpRequest.size, Addr, udpRequestPort)
            udpRequestSocket.send(udpRequestPacket)
        }

        public fun close(){
            udpRequestSocket.close()
            udpReplaySocket.close()
            stopWork=true
        }

        override fun run() {
            if(stopWork)  return ;
            handler.postDelayed(this, wifiTimerInterval ) // запланировал сл.запуск
            n++
            var ssid=getNetName()
            t2.text = ssid
            t3.text = n.toString()
            if (ssid != targetNetName) {
                    btn.isEnabled = false
                    btn.setBackgroundColor(Color.GRAY)
                    btn.text = "No Net"
            } else {
                stringToUdp("IsSomebodyHere", getBroadcastAddress() )
                //ждем ответ ограниченное время !!!!!!!!!!
                try {
                    // пока обслуживаем только одно устройство
                    udpReplaySocket.receive(udpReceivePacket)
                    deviceIp = udpReceivePacket.getAddress()
                    deviceName = udpReceivePacket.getData().toString()
                        t4.text = deviceIp.toString()
                        btn.isEnabled = true
                        btn.setBackgroundColor(Color.GREEN)
                        btn.text = "Connected"
                }
                catch (e: SocketTimeoutException) {
                        btn.text = "No device"
                        btn.setBackgroundColor(Color.GRAY)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startBtn = findViewById(R.id.startBtn) as Button
        startBtn.text = "No connection"
        startBtn.isEnabled = false
        startBtn.setBackgroundColor(Color.GRAY)

        txt1 = findViewById(R.id.textView1) as TextView
        txt2 = findViewById(R.id.textView2) as TextView
        txt3 = findViewById(R.id.textView3) as TextView
        txt4 = findViewById(R.id.textView4) as TextView

        wifiTimerTask = MainActivity.WifiTimerTask(this )
        val handler = android.os.Handler() // Handler вроде depricated
        wifiTimerTask.handler=handler

        var netName = wifiTimerTask.getNetName()
        txt1.text = netName
        // запускаем мониторинг сети по таймеру
        handler.post(wifiTimerTask)
        // wifiTimer.schedule(wifiTimerTask, 10.toLong(), wifiTimerInterval)

        val vibrator = getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val canVibrate: Boolean = vibrator.hasVibrator()
        val milliseconds = 1000L

        startBtn.setOnClickListener {
            // послать команду
            wifiTimerTask.stringToUdp("press", wifiTimerTask.deviceIp)
            try{
                wifiTimerTask.udpReplaySocket.receive(udpReceivePacket)
            } catch (e: SocketTimeoutException){
                // no replay from device
                startBtn.text = "No Replay"
                startBtn.isEnabled = false
                startBtn.setBackgroundColor(Color.GRAY)
                // разрешение произойдет при сл. опросе состояния сети
            }

            startBtn.isEnabled = false
            startBtn.setBackgroundColor(Color.RED)
            if (canVibrate) vibrator.vibrate(milliseconds)
            // пробуем запретить на 500мс
            //
            TimeUnit.MILLISECONDS.sleep(500)
            startBtn.isEnabled = true
            startBtn.setBackgroundColor(Color.GREEN)
        }
    }

    override fun onDestroy(){
        super.onDestroy()
    }
}