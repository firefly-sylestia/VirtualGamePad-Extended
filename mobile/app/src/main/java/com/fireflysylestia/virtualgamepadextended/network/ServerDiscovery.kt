package com.fireflysylestia.virtualgamepadextended.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * A PC found on the local network by UDP discovery.
 *
 * @property host the address the reply arrived from, ready to dial
 * @property port the TCP port the PC's game server is listening on
 * @property name the app name the PC reported, for showing in a list
 */
data class DiscoveredServer(
    val host: String,
    val port: Int,
    val name: String,
)

/**
 * Finds PCs listening for this app by UDP broadcast, so a tap replaces a typed IP.
 *
 * The PC answers probes on a fixed UDP port with its name and game port. This
 * sends the probe and collects replies for [timeoutMillis], de-duplicated by
 * address so one PC is not listed once per reply. Discovery is best effort by
 * design: a network that blocks broadcast simply finds nothing, and the manual
 * IP entry and QR scan still work.
 */
object ServerDiscovery {
    private const val tag = "ServerDiscovery"

    /** Must match DISCOVERY_PORT on the PC. */
    private const val DISCOVERY_PORT = 47820
    private const val PROBE = "VGP-DISCOVER"
    private const val REPLY_MAGIC = "VGP-PC"

    /**
     * Probe the network and return every PC that answers, or an empty list on
     * any failure. Blocks the calling thread, so run it off the main thread.
     */
    fun discover(timeoutMillis: Int = 2000): List<DiscoveredServer> {
        val found = LinkedHashMap<String, DiscoveredServer>()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = timeoutMillis

                val probeBytes = PROBE.toByteArray()
                // 255.255.255.255 reaches every host on the local network; the OS
                // routes it out of the active interface.
                val broadcast = InetAddress.getByName("255.255.255.255")
                socket.send(DatagramPacket(probeBytes, probeBytes.size, broadcast, DISCOVERY_PORT))

                val buffer = ByteArray(512)
                val deadline = System.currentTimeMillis() + timeoutMillis
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: Exception) {
                        break // soTimeout elapsed: no more replies coming.
                    }
                    parseReply(packet)?.let { server ->
                        // The sender's address is authoritative, not anything in the
                        // payload, so one PC is keyed by where the reply came from.
                        if (!found.containsKey(server.host)) {
                            found[server.host] = server
                            Log.d(tag, "Found ${server.name} at ${server.host}:${server.port}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Discovery is a convenience; manual entry and QR still work.
            Log.w(tag, "Discovery failed: ${e.message}")
        }
        return found.values.toList()
    }

    /** Parse one reply into a server, or null if it is not ours. */
    private fun parseReply(packet: DatagramPacket): DiscoveredServer? {
        val text = String(packet.data, packet.offset, packet.length).trim { it <= ' ' }
        val parts = text.split('|')
        // Expected: VGP-PC|<name>|<port>. A short or foreign packet is ignored.
        if (parts.size < 3 || parts[0] != REPLY_MAGIC) return null
        val port = parts[2].toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        return DiscoveredServer(
            host = packet.address.hostAddress ?: return null,
            port = port,
            name = parts[1].ifBlank { "PC" },
        )
    }
}
