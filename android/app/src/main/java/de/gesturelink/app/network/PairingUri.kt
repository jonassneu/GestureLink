package de.gesturelink.app.network

import android.net.Uri
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

data class PairingUri(
    val host: String,
    val port: Int,
    val serverId: String,
    val fingerprint: String,
    val token: String,
) {
    companion object {
        fun parse(raw: String): PairingUri {
            val uri = Uri.parse(raw)
            require(uri.scheme == "gesturelink" && uri.host == "pair") { "Kein GestureLink-Pairing-Code" }
            require(uri.getQueryParameter("v") == "1") { "Nicht unterstützte Protokollversion" }
            val host = requireNotNull(uri.getQueryParameter("host"))
            val address = InetAddress.getByName(host)
            require(isPrivateOrLocal(address)) { "Nur lokale/private Adressen sind erlaubt" }
            val port = requireNotNull(uri.getQueryParameter("port")).toInt()
            require(port in 1..65_535)
            val fingerprint = requireNotNull(uri.getQueryParameter("fingerprint")).lowercase()
            require(fingerprint.matches(Regex("[0-9a-f]{64}"))) { "Ungültiger Zertifikat-Fingerprint" }
            val serverId = requireNotNull(uri.getQueryParameter("serverId"))
            require(serverId.length in 8..80)
            val token = requireNotNull(uri.getQueryParameter("token"))
            require(token.length in 32..100)
            return PairingUri(host, port, serverId, fingerprint, token)
        }

        fun isPrivateOrLocal(address: InetAddress): Boolean {
            if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) return true
            if (address is Inet4Address) {
                val b = address.address.map { it.toInt() and 0xff }
                return b[0] == 10 || b[0] == 127 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) || (b[0] == 169 && b[1] == 254)
            }
            return address is Inet6Address && (address.isLinkLocalAddress || address.isSiteLocalAddress)
        }
    }
}

