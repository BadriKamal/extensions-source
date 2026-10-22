package eu.kanade.tachiyomi.extension.id.voratoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

@Source
abstract class VoraToon : KeiSource() {

    @Volatile
    private var proxyToken: String? = null

    @Volatile
    private var proxyTokenFetchedAt = 0L

    // vt_proxy is bound to the User-Agent that requested the homepage.
    @Volatile
    private var proxyUserAgent: String? = null

    private val sessionMutex = Mutex()

    private val noStore = CacheControl.Builder().noStore().build()

    // Homepage bootstrap needs the app's Cloudflare/DoH stack; proxy calls must not run the
    // CloudflareInterceptor because it treats the proxy's JSON 403 as a challenge.
    private val proxyClient by lazy {
        network.client.newBuilder().apply {
            interceptors().clear()
            networkInterceptors().clear()
            cookieJar(CookieJar.NO_COOKIES)
            cache(null)
            rateLimit(3) { it.host == baseUrl.toHttpUrl().host }
            addCookie({ baseUrl.toHttpUrl().host }) {
                proxyToken?.let { listOf("vt_proxy" to it) } ?: emptyList()
            }
        }.build()
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("Accept", "application/json")
        set("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
    }

    override suspend fun getPopularManga(page: Int): MangasPage = parseSeriesListResponse(
        apiGet(
            "$baseUrl/api/proxy/series".toHttpUrl().newBuilder()
                .addQueryParameter("includeMeta", "true")
                .addQueryParameter("take", "30")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", "popularity")
                .addQueryParameter("sortOrder", "desc")
                .build(),
        ),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseSeriesListResponse(
        apiGet(
            "$baseUrl/api/proxy/series".toHttpUrl().newBuilder()
                .addQueryParameter("includeMeta", "true")
                .addQueryParameter("take", "30")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", "latest")
                .addQueryParameter("sortOrder", "desc")
                .build(),
        ),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = parseSeriesListResponse(
        apiGet(
            "$baseUrl/api/proxy/series".toHttpUrl().newBuilder()
                .addQueryParameter("includeMeta", "true")
                .addQueryParameter("take", "30")
                .addQueryParameter("page", page.toString())
                .apply {
                    if (query.isNotEmpty()) {
                        addQueryParameter("title", query)
                    }
                    val filterBuilder = StringBuilder()
                    filters.filterIsInstance<UriFilter>().forEach {
                        it.addToFilter(filterBuilder)
                    }
                    if (filterBuilder.isNotEmpty()) {
                        addQueryParameter("filter", filterBuilder.toString())
                    }
                    filters.filterIsInstance<UriQueryFilter>().forEach {
                        it.addToQuery(this)
                    }
                }
                .build(),
        ),
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.getSlug(baseUrl)
        val details = if (fetchDetails) async { apiGet("$baseUrl/api/proxy/series/$slug") } else null
        val chapterList = if (fetchChapters) async { apiGet("$baseUrl/api/proxy/series/$slug/chapters") } else null

        SMangaUpdate(
            manga = details?.await()?.parseAs<SeriesDetailResponse>()?.data?.toSManga() ?: manga,
            chapters = chapterList?.await()?.parseAs<ChapterListResponse>()?.data?.map { it.toSChapter(slug) } ?: chapters,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "series") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            this.url = "/series/$slug"
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, chapterIndex) = chapter.getSlugAndIndex(baseUrl)
        return apiGet("$baseUrl/api/proxy/series/$slug/chapters/$chapterIndex").parseAs<ChapterDetailResponse>().data.toPageList()
    }

    private fun parseSeriesListResponse(response: Response): MangasPage {
        val result = response.parseAs<SeriesListResponse>()
        val mangas = result.data.map { it.toSManga() }
        val hasNextPage = result.meta?.let { (it.page ?: 0) < (it.lastPage ?: 0) } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = apiGet("$baseUrl/api/proxy/genres").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<GenreResponse>()?.data?.map { it.toPair() }
        return FilterList(
            buildList {
                add(SortFilter())
                add(SortOrderFilter())
                add(StatusFilter())
                add(FormatFilter())
                add(TypeFilter())
                if (!genres.isNullOrEmpty()) {
                    add(GenreFilter(genres.toTypedArray()))
                }
            },
        )
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Referer", "$baseUrl/")
            .build()

        return Request.Builder()
            .url(page.imageUrl!!)
            .headers(newHeaders)
            .get()
            .build()
    }

    private suspend fun apiGet(url: String): Response = apiGet(url.toHttpUrl())

    private suspend fun apiGet(url: HttpUrl): Response {
        ensureSession()
        val token = proxyToken

        var response = proxyClient.get(url, sessionHeaders(), noStore, ensureSuccess = false)
        if (response.code != 403) {
            response.requireSuccess()
            return response
        }

        val error = response.use {
            runCatching { it.parseAs<ProxyErrorResponse>() }.getOrNull()
        }
        if (error?.code != "proxy_session_required") {
            throw IOException("VoraToon proxy error: ${error?.code ?: "HTTP 403"}")
        }

        ensureSession(force = true, staleToken = token)

        response = proxyClient.get(url, sessionHeaders(), noStore, ensureSuccess = false)
        if (response.code == 403) {
            val retryError = response.use {
                runCatching { it.parseAs<ProxyErrorResponse>() }.getOrNull()
            }
            throw IOException("VoraToon proxy error: ${retryError?.code ?: "HTTP 403"}")
        }
        response.requireSuccess()
        return response
    }

    private suspend fun ensureSession(force: Boolean = false, staleToken: String? = null) {
        if (!force && isSessionFresh()) return

        sessionMutex.withLock {
            if (!force && isSessionFresh()) return
            if (force && staleToken != null && proxyToken != staleToken) return
            if (proxyToken != null && refreshSession()) return
            bootstrapSession()
        }
    }

    private fun isSessionFresh(): Boolean = proxyToken != null && System.currentTimeMillis() - proxyTokenFetchedAt < SESSION_REFRESH_MS

    private suspend fun bootstrapSession() {
        proxyUserAgent = headers["User-Agent"]?.takeIf { it.isNotBlank() }

        val response = network.client.get(
            "$baseUrl/",
            sessionHeaders().newBuilder()
                .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .build(),
            noStore,
        )

        response.use {
            // The homepage doesn't reissue the cookie while the existing one is still valid.
            proxyToken = it.vtProxyCookie()
                ?: network.client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
                    .filter { cookie -> cookie.name == "vt_proxy" }
                    .maxByOrNull { cookie -> cookie.expiresAt }
                    ?.value
                ?: throw IOException("VoraToon proxy session cookie missing")
            proxyTokenFetchedAt = System.currentTimeMillis()
        }
    }

    private suspend fun refreshSession(): Boolean {
        val response = try {
            proxyClient.post(
                "$baseUrl/api/proxy-session",
                sessionHeaders(),
                ByteArray(0).toRequestBody(),
                ensureSuccess = false,
            )
        } catch (_: IOException) {
            return false
        }

        return response.use {
            if (!it.isSuccessful) return@use false
            val token = it.vtProxyCookie() ?: return@use false
            proxyToken = token
            proxyTokenFetchedAt = System.currentTimeMillis()
            true
        }
    }

    private fun sessionHeaders(): Headers {
        val userAgent = proxyUserAgent ?: headers["User-Agent"]?.takeIf { it.isNotBlank() }
            ?: return headers

        return headers.newBuilder()
            .set("User-Agent", userAgent)
            .build()
    }

    private fun Response.requireSuccess() {
        if (!isSuccessful) {
            use { throw IOException("VoraToon API returned HTTP ${it.code}") }
        }
    }

    private fun Response.vtProxyCookie(): String? = this.headers.values("Set-Cookie")
        .mapNotNull { Cookie.parse(baseUrl.toHttpUrl(), it) }
        .firstOrNull { it.name == "vt_proxy" }
        ?.value

    private companion object {
        const val SESSION_REFRESH_MS = 10 * 60 * 1000L
    }
}
