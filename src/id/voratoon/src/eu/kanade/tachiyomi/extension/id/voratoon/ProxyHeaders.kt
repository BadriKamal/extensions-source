package eu.kanade.tachiyomi.extension.id.voratoon

import okhttp3.Headers
import okhttp3.HttpUrl

internal fun Headers.proxyHeaders(url: HttpUrl, userAgent: String?): Headers {
    val origin = HttpUrl.Builder()
        .scheme(url.scheme)
        .host(url.host)
        .port(url.port)
        .build()
        .toString()
        .removeSuffix("/")

    return newBuilder()
        .set("Origin", origin)
        .set("Referer", "$origin/")
        .set("Sec-Fetch-Site", "same-origin")
        .apply { if (userAgent != null) set("User-Agent", userAgent) }
        .build()
}
