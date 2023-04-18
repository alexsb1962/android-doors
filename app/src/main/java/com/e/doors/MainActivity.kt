package com.e.doors

import android.content.*
import android.graphics.Color
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.*
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.lang.Runnable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.system.exitProcess

fun getBroadcastAddress(): InetAddress {
    val quads =ByteArray(4)
    quads[0] = 192.toByte(); quads[1] = 168.toByte(); quads[2] = 1.toByte(); quads[3] = 255.toByte()
    return InetAddress.getByAddress(quads)
}

data class SomePref( var deviceName: String,
                     var homeNet:String,
                     var targetIp: InetAddress
)


class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.M)

    //val  targetNetName="AndroidWifi"
    var wifiTimerIntervalShort = 1000L
    val udpReplaySocketTimeout=500  // время на отклик устройства
    val udpRequestPort:Int = 54545
    val udpReplayPort:Int = 54546
    var receiveBuf = ByteArray(256)
    var udpReceivePacket = DatagramPacket(receiveBuf, receiveBuf.size)
    var udpRequest = "IsSomeDoorsHere".toByteArray()
    var deviceName = "doors"
    var prefData = SomePref("doors", "theflat",
                            InetAddress.getByAddress(byteArrayOf(192.toByte(),168.toByte(),100.toByte(),100.toByte()) ) )

    var udpRequestSocket = DatagramSocket(udpRequestPort).also { it.broadcast = true }
    var udpReplaySocket  = DatagramSocket(udpReplayPort).also{it.soTimeout = udpReplaySocketTimeout}


    lateinit var   startBtn :Button
    lateinit var txt1 :TextView
    lateinit var txt2 :TextView
    lateinit var txt3 :TextView
    lateinit var txt4 :TextView



    fun getNetName(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo as WifiInfo
        return wifiInfo.ssid.replace("\"", "")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /*
        val savedValues = getSharedPreferences("app_values", Context.MODE_PRIVATE)
        ssid = savedValues.getString("homeNet","theflat") ?: ""
        targetIP = savedValues.getString("targetIP","0.0.0.0") ?: "0.0.0.0"
        */

        startBtn = findViewById(R.id.startBtn) as Button
        startBtn.text = getString(R.string.Noconnection )
        startBtn.isEnabled = false
        startBtn.setBackgroundColor(Color.GRAY)

        txt1 = findViewById(R.id.txt1) as TextView
        txt2 = findViewById(R.id.txt2) as TextView
        txt3 = findViewById(R.id.txt3) as TextView
        txt4 = findViewById(R.id.txt4) as TextView

       // val handler = android.os.Handler() // Handler вроде depricated

       // wifiTimerTask = WifiTimerTask(this.applicationContext).also { it.handler=handler }

        //wifiTimerTask.stringOverUdp( "IsSomeBodyHere", getBroadcastAddress()  )

        txt1.text = "--------"

        // запускаем мониторинг сети
        // handler.post(wifiTimerTask)
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        var  wifiInfo = wifiManager.connectionInfo as WifiInfo
        var ssid = wifiInfo.ssid.replace("\"", "")

        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                ssid = getNetName()
                if(ssid == prefData.homeNet){
                    // широковещательный запрос на наличие именно нашего контроллера
                    udpRequestSocket.broadcast = true
                    udpRequestSocket.send( DatagramPacket(udpRequest, udpRequest.size,getBroadcastAddress(), udpRequestPort)   )

                    try {
                        // пока обслуживаем только одно устройствo
                        // пофиг на возможную блокировку потока ui
                        udpReplaySocket.receive(udpReceivePacket)
                        prefData.targetIp = udpReceivePacket.getAddress()
                        deviceName = udpReceivePacket.getData().toString()
                        txt4.text = prefData.targetIp.toString()
                        startBtn.isEnabled = true
                        startBtn.setBackgroundColor(Color.GREEN)
                        startBtn.text =  getString(R.string.Connected)
                    }
                    catch (e: SocketTimeoutException) {
                        startBtn.text =  getString(R.string.NoDevice)
                        startBtn.isEnabled = false
                        startBtn.setBackgroundColor(Color.GRAY)
                        prefData.targetIp = getBroadcastAddress()
                    }
                } else{
                    startBtn.text =  "NoWifiNet"
                    startBtn.isEnabled = false
                    startBtn.setBackgroundColor(Color.GRAY)
                    prefData.targetIp = getBroadcastAddress()
                }
            }
        }

        val intentFilter = IntentFilter()
//        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        intentFilter.addAction(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)
        applicationContext.registerReceiver(wifiScanReceiver, intentFilter)




        val vibrator = getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val canVibrate: Boolean = vibrator.hasVibrator()
        val milliseconds = 1000L

        startBtn.setOnClickListener {
            // послать команду
            startBtn.isEnabled = false // во избежание повторного нажатия
            startBtn.setBackgroundColor(Color.RED)
            udpRequestSocket.broadcast = false
            udpRequestSocket.send( DatagramPacket( "press","press".length, prefData.targetIp, udpRequestPort))
            try {
                udpReplaySocket.receive(udpReceivePacket)
                if (udpReceivePacket.data.toString() == "ok") {
                    startBtn.isEnabled = true
                    startBtn.text = ssid
                    startBtn.setBackgroundColor(Color.GREEN)
                }
            } catch (e: SocketTimeoutException) {
                // no replay from device
                startBtn.text = getString(R.string.NoReplay)
                startBtn.isEnabled = false
                startBtn.setBackgroundColor(Color.GRAY)
            }
            if (canVibrate) {
                //var vibrationEffect=VibrationEffect.createOneShot(milliseconds,255)
                vibrator.vibrate(milliseconds)
            }


            startBtn.isEnabled = true
            startBtn.setBackgroundColor(Color.GREEN)

        }
    }

    override fun onStop() {
        // буду убивать приложение с выгрузкой из памяти при малейшем чихе

        // сохранение кой чего
        var ed = savedValues.edit()
        ed.putString("homeNet",homeNet)
        ed.putString("targetIP",targetIP)
        ed.apply()

        // остановить  подзадачу таймера
        wifiTimerTask.close()
        super.onStop()
        //
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            finishAffinity()
        } else {
            finish()
        }
        exitProcess(0) // не выполняется в текущем варианте
        super.onStop()
    }

    override fun onDestroy(){
        // todo при выходе - сначала custom затем супер
        //cancel()
        super.onDestroy()
    }
}