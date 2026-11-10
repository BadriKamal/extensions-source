package eu.kanade.tachiyomi.extension.id.voratoon

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun browseUrl(
    baseUrl: String,
    page: Int,
    query: String = "",
    sort: String = "",
    status: String = "",
    format: String = "",
    type: String = "",
    anime: String = "",
    genres: List<String> = emptyList(),
    minimumChapters: Int = 0,
): HttpUrl = "$baseUrl/browse".toHttpUrl().newBuilder().apply {
    if (query.isNotBlank()) addQueryParameter("q", query)
    if (genres.isNotEmpty()) addQueryParameter("genre", genres.joinToString(","))
    if (status.isNotEmpty()) addQueryParameter("status", status)
    if (format.isNotEmpty()) addQueryParameter("format", format)
    if (type.isNotEmpty()) addQueryParameter("type", type)
    if (anime.isNotEmpty()) addQueryParameter("anime", anime)
    if (sort.isNotEmpty()) addQueryParameter("sort", sort)
    if (minimumChapters > 0) addQueryParameter("min", minimumChapters.toString())
    if (page > 1) addQueryParameter("page", page.toString())
}.build()

internal fun parseRelativeDate(text: String?, now: Long = System.currentTimeMillis()): Long {
    if (text.isNullOrBlank()) return 0L

    val match = RELATIVE_DATE.find(text) ?: return 0L
    val (amount, unit) = match.destructured
    val seconds = when (unit) {
        "detik" -> 1L
        "menit" -> 60L
        "jam" -> 60L * 60
        "hari" -> 24L * 60 * 60
        "minggu" -> 7L * 24 * 60 * 60
        "bulan" -> 30L * 24 * 60 * 60
        "tahun" -> 365L * 24 * 60 * 60
        else -> return 0L
    }

    return now - amount.toLong() * seconds * 1000
}

private val RELATIVE_DATE = Regex("""(\d+)\s+(detik|menit|jam|hari|minggu|bulan|tahun)\s+lalu""")
