package eu.kanade.tachiyomi.extension.id.voratoon

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyHeadersTest {
    @Test
    fun replacesStaleOriginWithApiRequestOrigin() {
        val inherited = Headers.Builder()
            .add("Origin", "https://v10.voratoon.com")
            .add("Referer", "https://v10.voratoon.com/")
            .build()

        val result = inherited.proxyHeaders("https://13.voratoon.com/api/proxy/genres".toHttpUrl(), null)

        assertEquals("https://13.voratoon.com", result["Origin"])
        assertEquals("https://13.voratoon.com/", result["Referer"])
    }

    @Test
    fun originExcludesCredentialsPathQueryAndFragment() {
        val result = Headers.Builder().build().proxyHeaders(
            "https://user:password@13.voratoon.com:8443/api/proxy/series?page=1#fragment".toHttpUrl(),
            null,
        )

        assertEquals("https://13.voratoon.com:8443", result["Origin"])
        assertEquals("https://13.voratoon.com:8443/", result["Referer"])
    }

    @Test
    fun originPreservesIpv6AndNonDefaultHttpPort() {
        val result = Headers.Builder().build().proxyHeaders("http://[::1]:8080/api/proxy/genres".toHttpUrl(), null)

        assertEquals("http://[::1]:8080", result["Origin"])
        assertEquals("http://[::1]:8080/", result["Referer"])
    }

    @Test
    fun replacesDuplicateOriginSignalsAndCrossSiteMetadata() {
        val inherited = Headers.Builder()
            .add("Origin", "https://v10.voratoon.com")
            .add("origin", "https://voratoon.co")
            .add("Referer", "https://voratoon.co/")
            .add("referer", "https://v10.voratoon.com/")
            .add("Sec-Fetch-Site", "cross-site")
            .build()

        val result = inherited.proxyHeaders("https://13.voratoon.com/api/proxy/series".toHttpUrl(), null)

        assertEquals(listOf("https://13.voratoon.com"), result.values("Origin"))
        assertEquals(listOf("https://13.voratoon.com/"), result.values("Referer"))
        assertEquals(listOf("same-origin"), result.values("Sec-Fetch-Site"))
    }

    @Test
    fun preservesBootstrapUserAgentWhenAppDefaultChanges() {
        val inherited = Headers.Builder().add("User-Agent", "changed-app-agent").build()

        val result = inherited.proxyHeaders("https://13.voratoon.com/api/proxy-session".toHttpUrl(), "bootstrap-agent")

        assertEquals("bootstrap-agent", result["User-Agent"])
    }

    @Test
    fun preservesAppUserAgentWhenNoSessionAgentIsCaptured() {
        val inherited = Headers.Builder().add("User-Agent", "app-agent").build()

        val result = inherited.proxyHeaders("https://13.voratoon.com/".toHttpUrl(), null)

        assertEquals("app-agent", result["User-Agent"])
        assertEquals("https://13.voratoon.com", result["Origin"])
    }

    @Test
    fun preservesUnrelatedHeadersWithoutMutatingSourceHeaders() {
        val inherited = Headers.Builder()
            .add("Origin", "https://v10.voratoon.com")
            .add("Accept", "application/json")
            .add("Accept-Language", "id")
            .build()

        val result = inherited.proxyHeaders("https://13.voratoon.com/api/proxy/genres".toHttpUrl(), null)

        assertEquals("application/json", result["Accept"])
        assertEquals("id", result["Accept-Language"])
        assertEquals("https://v10.voratoon.com", inherited["Origin"])
        assertEquals("https://13.voratoon.com", result["Origin"])
    }
}
