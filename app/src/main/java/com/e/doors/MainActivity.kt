package com.e.doors

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.lang.Runnable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

//import kotlinx.coroutines.*

//4
var  targetNetName="theflat"
//var  targetNetName="AndroidWifi"
var wifiTimerIntervalLong = 5000L
var wifiTimerIntervalShort = 1000L
val udpReplaySocketTimeout=600
const val udpRequestPort:Int = 54545
const val udpReplayPort:Int = 54546
var receiveBuf = ByteArray(256)
var udpReceivePacket = DatagramPacket(receiveBuf, receiveBuf.size)
var udpRequest = "IsSomebodyHere".toByteArray()
var udpRequestPacket:DatagramPacket = DatagramPacket(udpRequest, udpRequest.size, getBroadcastAddress(), udpRequestPort)
var deviceName = "doors"


fun getBroadcastAddress(): InetAddress {
    val quads =ByteArray(4)
    quads[0] = 192.toByte(); quads[1] = 168.toByte(); quads[2] = 1.toByte(); quads[3] = 255.toByte()
    return InetAddress.getByAddress(quads)
}

class MainActivity : AppCompatActivity(), CoroutineScope {
    @RequiresApi(Build.VERSION_CODES.M)

    protected val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main.immediate+job
    val cScope= CoroutineScope(Dispatchers.Main);

    lateinit var prefs: SharedPreferences
    lateinit var valEditor = prefs.edit()

    public lateinit var   startBtn :Button
    public lateinit var txt1 :TextView
    public lateinit var txt2 :TextView
    public lateinit var txt3 :TextView
    public lateinit var txt4 :TextView
    public lateinit var wifiTimerTask : WifiTimerTask

    public fun getNetName():String {
        val wifiManager = getApplicationContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.getConnectionInfo() as WifiInfo
        val netName = wifiInfo.getSSID().replace("\"", "")
        return netName
    }

    inner class WifiTimerTask (val context: Context ):  Runnable{
        var udpRequestSocket = DatagramSocket(udpRequestPort).also { it.broadcast = true }
        var udpReplaySocket  = DatagramSocket(udpReplayPort).also{it.soTimeout = udpReplaySocketTimeout}
        var deviceIp = getBroadcastAddress()
        var handler = android.os.Handler()
        var ssid =""
        var stopWork = false
        var n:Long = 0


        public fun stringOverUdp(s:String, Addr:InetAddress){
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
            n++
            // запланирую сл.запуск
            if ( startBtn.isEnabled() ) handler.postDelayed(this, wifiTimerIntervalLong) else handler.postDelayed(this, wifiTimerIntervalShort)
            ssid=getNetName()
            txt2.text = ssid
            txt3.text = n.toString()
            if (ssid != targetNetName) {
                startBtn.isEnabled = false
                startBtn.setBackgroundColor(Color.GRAY)
                startBtn.text = "No Net"
            } else {
                stringOverUdp("IsSomebodyHere", deviceIp )
                //ждем ответ ограниченное время !!!!!!!!!!
                try {
                    // пока обслуживаем только одно устройствo
                    // пофиг на возможную блокировку потока ui  
                    udpReplaySocket.receive(udpReceivePacket)
                    deviceIp = udpReceivePacket.getAddress()
                    deviceName = udpReceivePacket.getData().toString()
                    txt4.text = deviceIp.toString()
                    startBtn.isEnabled = true
                    startBtn.setBackgroundColor(Color.GREEN)
                    startBtn.text = "Connected"
                }
                catch (e: SocketTimeoutException) {
                    startBtn.text = "No device"
                    startBtn.isEnabled = false
                    startBtn.setBackgroundColor(Color.GRAY)
                    deviceIp = getBroadcastAddress()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        valEditor = prefs.edit()
        targetNetName = prefs.getString("TARGET_NET_NAME","theflat")?:"theflat"
        valEditor.putString("TARGET_NET_NAME", targetNetName)
        wifiTimerIntervalLong=prefs.getLong("INTERVAL_LONG",wifiTimerIntervalLong)?:wifiTimerIntervalLong
        valEditor.putLong("INTERVAL_LONG", wifiTimerIntervalLong)
        wifiTimerIntervalShort=prefs.getLong("INTERVAL_SHORT", wifiTimerIntervalShort)?: wifiTimerIntervalShort
        valEditor.putLong("INTERVAL_SHORT", wifiTimerIntervalShort)
        valEditor.apply()
        valEditor.commit()

        startBtn = findViewById(R.id.startBtn) as Button
        startBtn.text = "No connection"
        startBtn.isEnabled = false
        startBtn.setBackgroundColor(Color.GRAY)

        txt1 = findViewById(R.id.txt1) as TextView
        txt2 = findViewById(R.id.txt2) as TextView
        txt3 = findViewById(R.id.txt3) as TextView
        txt4 = findViewById(R.id.txt4) as TextView

        val handler = android.os.Handler() // Handler вроде depricated
        wifiTimerTask = WifiTimerTask(this).also { it.handler=handler }

        txt1.text = "--------"

        // запускаем мониторинг сети по таймеру
        handler.post(wifiTimerTask)

        val vibrator = getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val canVibrate: Boolean = vibrator.hasVibrator()
        val milliseconds = 1000L

        startBtn.setOnClickListener {
            // послать команду
            startBtn.isEnabled = false // во избежание повторного нажатия
            startBtn.setBackgroundColor(Color.RED)
            wifiTimerTask.stringOverUdp("press", wifiTimerTask.deviceIp)
            try {
                wifiTimerTask.udpReplaySocket.receive(udpReceivePacket)
                if (udpReceivePacket.data.toString() == "ok") {
                    startBtn.isEnabled = true
                    startBtn.text = wifiTimerTask.ssid
                    startBtn.setBackgroundColor(Color.GREEN)
                }
            } catch (e: SocketTimeoutException) {
                // no replay from device
                startBtn.text = "No Replay"
                startBtn.isEnabled = false
                startBtn.setBackgroundColor(Color.GRAY)
            }
            if (canVibrate) {
                //var vibrationEffect=VibrationEffect.createOneShot(milliseconds,255)
                vibrator.vibrate(milliseconds)
            }

            // пробуем запретить на 500мс без блокировки  потока
            cScope.launch{
                delay(500)
                startBtn.isEnabled = true
                startBtn.setBackgroundColor(Color.GREEN)
            }

        }
    }

    override fun onStop() {
        // буду убивать приложение с выгрузкой из памяти при малейшем чихе
        // остановить  подзадачу таймера
        wifiTimerTask.close()
        super.onStop()
        //
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            finishAffinity();
        } else {
            finish();
        }
        System.exit(0); // не выполняется в текущем варианте
        super.onStop()
    }

    override fun onDestroy(){
        // todo при выходе - сначала custom затем супер
        job.cancel()
        super.onDestroy()
    }
}