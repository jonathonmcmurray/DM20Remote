package com.dm20remote.net

import com.dm20remote.debug.WireLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

data class DiscoveredConsole(val address: String, val banner: String)

/**
 * UDP discovery recovered from `ConnectActivity`:
 *
 * ```
 * UDP -> 255.255.255.255:6399   payload 5A 5A 5A 5A
 * UDP <- console                payload starts with "OK"
 * ```
 */
object Discovery {
    const val DISCOVERY_PORT = 6399
    val PROBE = byteArrayOf(0x5A, 0x5A, 0x5A, 0x5A)
    const val DEFAULT_HOST = "192.168.43.1"

    /** Console's Wi-Fi AP fallback address. */
    const val CONTROL_PORT = 6400
    const val METER_PORT = 6401
    const val MUSIC_PORT = 6402

    const val DEFAULT_TIMEOUT_MS = 5000

    suspend fun discover(timeoutMs: Int = DEFAULT_TIMEOUT_MS): List<DiscoveredConsole> =
        withContext(Dispatchers.IO) {
            WireLog.note("discovery: searching for consoles (${timeoutMs} ms)")
            val found = LinkedHashMap<String, DiscoveredConsole>()
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 200
                val deadline = System.currentTimeMillis() + timeoutMs

                sendProbe(socket)
                // Re-probe a few times so a console that is still booting is caught.
                var nextProbe = System.currentTimeMillis() + 1000

                while (System.currentTimeMillis() < deadline) {
                    val buffer = ByteArray(512)
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        if (System.currentTimeMillis() >= nextProbe && System.currentTimeMillis() < deadline - 300) {
                            sendProbe(socket)
                            nextProbe = System.currentTimeMillis() + 1000
                        }
                        continue
                    } catch (_: Exception) {
                        break
                    }
                    val reply = String(packet.data, packet.offset, packet.length, Charsets.US_ASCII)
                    val host = packet.address?.hostAddress
                    WireLog.note("discovery: reply <- ${host ?: "?"} (${packet.length} B) '${reply.trim()}'")
                    if (reply.startsWith("OK") && host != null) {
                        found[host] = DiscoveredConsole(host, reply.trim())
                    }
                }
            }
            WireLog.note("discovery: ${found.size} console(s) found")
            found.values.toList()
        }

    private fun sendProbe(socket: DatagramSocket) {
        val targets = LinkedHashSet<InetAddress>()
        targets.add(InetAddress.getByName("255.255.255.255"))
        targets.addAll(broadcastAddresses())
        for (target in targets) {
            try {
                socket.send(DatagramPacket(PROBE, PROBE.size, target, DISCOVERY_PORT))
                WireLog.note("discovery: probe -> $target:$DISCOVERY_PORT (${PROBE.size} B)")
            } catch (e: Exception) {
                // Some interfaces reject broadcasts; ignore and keep going.
                WireLog.note("discovery: probe -> $target:$DISCOVERY_PORT failed: ${e.message}")
            }
        }
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (ia in nif.interfaceAddresses) {
                    ia.broadcast?.let { result.add(it) }
                }
            }
        } catch (_: Exception) {
            // Ignore interfaces we cannot enumerate.
        }
        return result
    }
}
