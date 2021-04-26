package com.e.doors

import android.graphics.Color
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class UdpServ(udpRequestPort:Int=54545, udpReplayPort:Int=54546,udpReplaySocketTimeout:Int=500):{

    val udpRequestSocket = DatagramSocket(udpRequestPort).also { it.broadcast = true }
    val udpReplaySocket  = DatagramSocket(udpReplayPort).also{it.soTimeout = udpReplaySocketTimeout}
    var receiveBuf = ByteArray(256)
    var udpReceivePacket = DatagramPacket(receiveBuf, receiveBuf.size)
    var udpRequest = "IsSomebodyHere".toByteArray()
    var udpRequestPacket:DatagramPacket = DatagramPacket(udpRequest, udpRequest.size, getBroadcastAddress(), udpRequestPort)
    var deviceIp= getBroadcastAddress()
    var stopWork=false

    init{
        stringToUdp("IsSomeBodyHere",deviceIp)
        try{
            udpReplaySocket.receive(udpReceivePacket)
            deviceIp = udpReceivePacket.getAddress()
            deviceName = udpReceivePacket.getData().toString()
        }
        catch (e: SocketTimeoutException) {

        }
    }

    private fun stringOverUdp(s:String, Addr: InetAddress){
        val udpRequest = s.toByteArray()
        val udpRequestPacket = DatagramPacket(udpRequest, udpRequest.size, Addr, udpRequestPort)
        udpRequestSocket.send(udpRequestPacket)
    }

    fun close(){
        udpRequestSocket.close()
        udpReplaySocket.close()
        stopWork=true
    }


}