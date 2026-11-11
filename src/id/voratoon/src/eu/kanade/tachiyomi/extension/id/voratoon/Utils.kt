package eu.kanade.tachiyomi.extension.id.voratoon

import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun browseUrl(
    baseUrl: String,
    page: Int,
    query: String = "",
    filters: FilterList = FilterList(),
): HttpUrl = "$baseUrl/browse".toHttpUrl().newBuilder().apply {
    addQueryParameter("page", page.toString())
    if (query.isNotBlank()) addQueryParameter("q", query)
    filters.filterIsInstance<UriQueryFilter>().forEach { it.addToQuery(this) }
}.build()
