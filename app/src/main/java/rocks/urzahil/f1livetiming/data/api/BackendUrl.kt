package rocks.urzahil.f1livetiming.data.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Completes a backend address typed without a scheme. Cleartext HTTP is only assumed for hosts that stay on
 * the local network (localhost, `.local`, single-label names and private IPv4 ranges), because that is where
 * a self-hosted Docker backend normally runs. Anything else is a public host and defaults to HTTPS, so
 * typing `f1api.example.com` never silently sends traffic unencrypted over the internet.
 * An explicit `http://` or `https://` prefix is always respected.
 */
object BackendUrl {

    private val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

    fun withDefaultScheme(input: String): String {
        val trimmed = input.trim()
        if (SCHEME_PREFIX.containsMatchIn(trimmed)) return trimmed
        // Classify the host exactly as the HTTP client will parse it (backslashes, userinfo, ports...). An
        // address that does not parse stays on the safe side.
        val host = "http://$trimmed".toHttpUrlOrNull()?.host ?: return "https://$trimmed"
        val scheme = if (isLocalHost(host.lowercase())) "http" else "https"
        return "$scheme://$trimmed"
    }

    internal fun isLocalHost(host: String): Boolean {
        if (host.isEmpty()) return true
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
            host.endsWith(".lan") || host.endsWith(".home.arpa")
        ) return true
        if (host == "::1" || host.startsWith("fe80:") || host.startsWith("fc") && host.contains(':') ||
            host.startsWith("fd") && host.contains(':')
        ) return true
        val octets = host.split('.').map { it.toIntOrNull() }
        if (octets.size == 4 && octets.all { it != null && it in 0..255 }) {
            val (a, b) = octets[0]!! to octets[1]!!
            return a == 10 || a == 127 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254) ||
                (a == 100 && b in 64..127) // CGNAT, used by Tailscale
        }
        // A bare name such as "nas" or a Docker service name cannot be a public domain. A bare number or a
        // 0x... value is not a name: the resolver reads it as a (public) IPv4 address.
        return !host.contains('.') && !host.contains(':') && !host.all { it.isDigit() } && !host.startsWith("0x")
    }
}
