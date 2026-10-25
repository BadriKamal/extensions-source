package eu.kanade.tachiyomi.extension.id.westmanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class WestManga : KeiSource() {
    private val visitTokenMutex = Mutex()
    private var visitToken: String? = null
    private var visitTokenExpiresAt = 0L
    private var visitTokenBaseUrl: String? = null

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", SortFilter.popular)

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", SortFilter.latest)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/contents".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "20")
            addQueryParameter("type", "Comic")
            filters.filterIsInstance<UrlFilter>().forEach {
                it.addToUrl(this)
            }
        }.build()

        val data = client.get(url, apiHeaders()).parseAs<PaginatedData<BrowseManga>>()
        val entries = data.data.map { it.toSManga(baseUrl) }
        return MangasPage(entries, data.paginator.hasNextPage())
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$baseUrl/api/contents/genres".toHttpUrl()
        return client.get(url, apiHeaders()).parseAs()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<Data<List<ApiGenre>>>()?.data
            ?.map { it.name to it.id.toString() }

        return FilterList(
            listOfNotNull(
                SortFilter(),
                StatusFilter(),
                CountryFilter(),
                ColorFilter(),
                genres?.takeIf { it.isNotEmpty() }?.let { GenreFilter(it) },
            ),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = when (url.pathSegments.firstOrNull()) {
            "comic", "manga" -> url.pathSegments.getOrNull(1)
            else -> null
        } ?: return null
        val api = "$baseUrl/api/comic/$slug".toHttpUrl()

        return client.get(api, apiHeaders()).parseAs<Data<Manga>>().data
            .toSManga(baseUrl)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val path = "$baseUrl${manga.url}".toHttpUrl().pathSegments
        require(path.size == 3) { "Migrate from $name to $name" }
        val slug = path[1]
        val api = "$baseUrl/api/comic/$slug".toHttpUrl()

        val data = client.get(api, apiHeaders()).parseAs<Data<Manga>>().data
        return SMangaUpdate(
            data.toSManga(baseUrl),
            data.chapters.map { it.toSChapter() },
        )
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = "$baseUrl${manga.url}".toHttpUrl().pathSegments[1]
        return "$baseUrl/comic/$slug"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val path = "$baseUrl${chapter.url}".toHttpUrl().pathSegments
        require(path.isNotEmpty()) { "Refresh Chapter List" }
        val slug = path.first()

        val url = "$baseUrl/api/v/$slug".toHttpUrl()
        val data = client.get(url, apiHeaders()).parseAs<Data<ImageList>>().data
        val base = baseUrl.toHttpUrl()
        return data.images.mapIndexed { idx, img ->
            Page(idx, imageUrl = base.resolve(img)?.toString() ?: img)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = "$baseUrl${chapter.url}".toHttpUrl().pathSegments.first()
        return "$baseUrl/view/$slug"
    }

    private suspend fun apiHeaders(): Headers = headersBuilder()
        .set("X-WM-Visit", getVisitToken())
        .build()

    private suspend fun getVisitToken(): String = visitTokenMutex.withLock {
        val currentBaseUrl = baseUrl
        val now = System.currentTimeMillis() / 1000
        val cachedToken = visitToken
        if (cachedToken != null &&
            visitTokenBaseUrl == currentBaseUrl &&
            visitTokenExpiresAt - now > TOKEN_REFRESH_MARGIN
        ) {
            return@withLock cachedToken
        }

        val url = "$currentBaseUrl/api/visit-token".toHttpUrl()
        val token = client.get(url, headers, cacheControl = CacheControl.FORCE_NETWORK).parseAs<VisitToken>()
        visitToken = token.token
        visitTokenExpiresAt = token.expiresAt
        visitTokenBaseUrl = currentBaseUrl
        token.token
    }
}

private const val TOKEN_REFRESH_MARGIN = 300L
