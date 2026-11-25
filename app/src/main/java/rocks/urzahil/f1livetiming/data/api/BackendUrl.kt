package rocks.urzahil.f1livetiming.data.api

/**
 * Completes a backend address typed without a scheme. Cleartext HTTP is only assumed for hosts that stay on
 * the local network (localhost, `.local`, single-label names and private IPv4 ranges), because that is where
 * a self-hosted Docker backend normally runs. Anything else is a public host and defaults to HTTPS, so
 * typing `f1api.example.com` never silently sends traffic unencrypted over the internet.
 * An explicit `http://` or `https://` prefix is always respected.
 */
object BackendUrl {

    fun withDefaultScheme(input: String): String {
        val trimmed = input.trim()
        if (trimmed.contains("://")) return trimmed
        val scheme = if (isLocalHost(extractHost(trimmed))) "http" else "https"
        return "$scheme://$trimmed"
    }

    internal fun extractHost(address: String): String {
        var authority = address.substringBefore('/').substringBefore('?').substringBefore('#')
        authority = authority.substringAfterLast('@')
        return if (authority.startsWith("[")) {
            authority.substringAfter('[').substringBefore(']')
        } else {
            authority.substringBefore(':')
        }.lowercase()
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
        // A bare name such as "nas" or a Docker service name cannot be a public domain.
        return !host.contains('.') && !host.contains(':')
    }
}
