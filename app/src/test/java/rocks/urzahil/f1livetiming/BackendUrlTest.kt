package rocks.urzahil.f1livetiming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rocks.urzahil.f1livetiming.data.api.BackendUrl
import rocks.urzahil.f1livetiming.data.repository.isHttpsUrl

class BackendUrlTest {

    @Test
    fun localAddressesDefaultToCleartextHttp() {
        listOf(
            "localhost:3000", "192.168.1.20:3000", "10.0.2.2:3000", "172.16.0.5", "172.31.255.1", "127.0.0.1",
            "100.64.1.2:3000", "nas:3000", "f1.local:3000", "[::1]:3000", "pi.home.arpa"
        ).forEach { assertEquals(it, "http://$it", BackendUrl.withDefaultScheme(it)) }
    }

    @Test
    fun publicHostsDefaultToHttps() {
        listOf("f1api.example.com", "f1api.example.com:8443/api", "8.8.8.8:3000", "172.32.0.1", "192.169.0.1", "user@host.example.org",
            "evil.com\\@10.0.0.1", "134744072", "0x08080808", "x.example.com:3000/x?next=http://y")
            .forEach { assertEquals(it, "https://$it", BackendUrl.withDefaultScheme(it)) }
    }

    @Test
    fun anExplicitSchemeIsAlwaysKept() {
        assertEquals("http://f1api.example.com", BackendUrl.withDefaultScheme("http://f1api.example.com"))
        assertEquals("https://192.168.1.20:3000", BackendUrl.withDefaultScheme("  https://192.168.1.20:3000 "))
    }

    @Test
    fun onlyWebAddressesAreAcceptedAsDocumentLinks() {
        assertTrue(isHttpsUrl("https://www.fia.com/doc.pdf"))
        assertFalse(isHttpsUrl("http://www.fia.com/doc.pdf"))
        assertFalse(isHttpsUrl(null))
        assertFalse(isHttpsUrl(""))
        assertFalse(isHttpsUrl("file:///data/data/app/secret"))
        assertFalse(isHttpsUrl("content://media/external/1"))
        assertFalse(isHttpsUrl("intent:#Intent;end"))
        assertFalse(isHttpsUrl("javascript:alert(1)"))
        assertFalse(isHttpsUrl("/relative/doc.pdf"))
    }
}
