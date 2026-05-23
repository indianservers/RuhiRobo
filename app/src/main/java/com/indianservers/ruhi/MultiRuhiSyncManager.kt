package com.indianservers.ruhi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

enum class RuhiSyncMode { MIRROR, CONVERSATION, DANCE }
data class NearbyRuhi(val name: String, val mood: String, val address: String, val lastSeen: Long)

class MultiRuhiSyncManager(private val deviceName: String = "RuhiRobo") {
    private val _nearby = MutableStateFlow<List<NearbyRuhi>>(emptyList())
    val nearby: StateFlow<List<NearbyRuhi>> = _nearby
    private var beaconJob: Job? = null
    private var listenJob: Job? = null

    fun start(scope: CoroutineScope, moodProvider: () -> String) {
        beaconJob = scope.launch(Dispatchers.IO) {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                while (isActive) {
                    val payload = "$deviceName|${moodProvider()}".toByteArray()
                    socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), PORT))
                    delay(5_000)
                }
            }
        }
        listenJob = scope.launch(Dispatchers.IO) {
            DatagramSocket(PORT).use { socket ->
                val buffer = ByteArray(256)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length)
                    val parts = text.split("|")
                    if (parts.firstOrNull() != deviceName) {
                        val next = NearbyRuhi(parts.getOrNull(0).orEmpty(), parts.getOrNull(1).orEmpty(), packet.address.hostAddress.orEmpty(), System.currentTimeMillis())
                        _nearby.value = (_nearby.value.filterNot { it.address == next.address } + next).takeLast(8)
                    }
                }
            }
        }
    }

    fun stop() {
        beaconJob?.cancel()
        listenJob?.cancel()
    }

    companion object {
        const val PORT = 47474
    }
}
