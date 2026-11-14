package eu.kanade.tachiyomi.extension.id.voratoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Source
abstract class VoraToon : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3) { it.host == baseUrl.toHttpUrl().host }
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        set("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
    }

    private val rscHeaders: Headers
        get() = headers.newBuilder().set("RSC", "1").build()

    override suspend fun getPopularManga(page: Int): MangasPage = parseSeriesList(
        browseUrl(baseUrl, page, filters = FilterList(SortFilter("popular"))),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseSeriesList(
        browseUrl(baseUrl, page, filters = FilterList(SortFilter())),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = parseSeriesList(browseUrl(baseUrl, page, query, filters))

    private suspend fun parseSeriesList(url: HttpUrl): MangasPage {
        val dto = client.get(url, rscHeaders).extractNextJs<BrowseDto> {
            it is JsonObject && "series" in it && "seriesMeta" in it
        } ?: return MangasPage(emptyList(), false)

        return MangasPage(
            mangas = dto.series.map { it.toSManga() },
            hasNextPage = dto.seriesMeta?.let { (it.page ?: 0) < (it.lastPage ?: 0) } ?: false,
        )
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchDetails && !fetchChapters) {
            return SMangaUpdate(manga, chapters)
        }

        val slug = manga.getSlug(baseUrl)
        val dto = client.get("$baseUrl/series/$slug", rscHeaders).extractNextJs<SeriesDetailsDto> {
            it is JsonObject && "series" in it && "chapters" in it
        } ?: return SMangaUpdate(manga, chapters)

        return SMangaUpdate(
            manga = if (fetchDetails) dto.series.firstOrNull()?.toSManga() ?: manga else manga,
            chapters = if (fetchChapters) dto.chapters.map { it.toSChapter(slug) } else chapters,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "series") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            this.url = "/series/$slug"
        }
        return getMangaUpdate(
            manga,
            emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, chapterIndex) = chapter.getSlugAndIndex(baseUrl)
        val chapterUrl = "$baseUrl/series/$slug/chapter/$chapterIndex"
        val rsc = client.get(chapterUrl, rscHeaders).use { it.body.string() }
        val dto = rsc.extractNextJsRsc<ChapterPageDto> {
            it is JsonObject && "chapter" in it
        } ?: error("Failed to extract chapter pages")

        val images = dto.chapter.imageUrls()
        if (images.isNotEmpty()) return dto.chapter.toPageList(chapterUrl, images)

        val ticket = dto.chapter.pagesTicket ?: error("Failed to extract chapter pages")
        val chunkPath = parseReaderChunkPath(rsc) ?: error("Failed to extract reader page script")
        val unlocked = unlockChapterPages(chapterUrl, ticket, chunkPath)

        check(unlocked.ok) { unlocked.message ?: "Failed to unlock chapter pages" }
        if (dto.chapter.id != null && unlocked.chapterId != null) {
            check(dto.chapter.id == unlocked.chapterId) { "Chapter page mismatch. Reload the chapter." }
        }
        val unlockedImages = unlocked.images.orEmpty()
        check(unlockedImages.isNotEmpty()) { "No pages found" }
        return dto.chapter.toPageList(chapterUrl, unlockedImages)
    }

    private val unlockMutex = Mutex()

    @Volatile
    private var cachedAction: Pair<String, String>? = null

    private val actionMutex = Mutex()

    private suspend fun unlockChapterPages(
        chapterUrl: String,
        ticket: String,
        chunkPath: String,
    ): UnlockChapterPagesDto = unlockMutex.withLock {
        val actionId = getUnlockActionId(chunkPath)
        val token = getTurnstileToken(
            url = chapterUrl,
            siteKey = TURNSTILE_SITE_KEY,
            action = "reader",
        )
        val body = listOf(ticket, token).toJsonString()
            .toRequestBody("text/plain; charset=UTF-8".toMediaType())
        val actionHeaders = headers.newBuilder()
            .set("Accept", "text/x-component")
            .set("Next-Action", actionId)
            .set("Referer", chapterUrl)
            .build()
        val response = client.post(chapterUrl, actionHeaders, body, ensureSuccess = false)
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            error("Failed to unlock chapter pages: HTTP $code")
        }
        response.extractNextJs<UnlockChapterPagesDto> {
            it is JsonObject && "ok" in it
        } ?: error("Failed to unlock chapter pages")
    }

    private suspend fun getUnlockActionId(chunkPath: String): String {
        cachedAction?.takeIf { it.first == chunkPath }?.let { return it.second }
        return actionMutex.withLock {
            cachedAction?.takeIf { it.first == chunkPath }?.second ?: run {
                val chunk = client.get("$baseUrl/_next/$chunkPath").use { it.body.string() }
                val actionId = parseUnlockActionId(chunk) ?: error("Failed to extract reader action")
                cachedAction = chunkPath to actionId
                actionId
            }
        }
    }

    override fun imageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .headers(
            headers.newBuilder()
                .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .set("Referer", page.url)
                .build(),
        )
        .get()
        .build()

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val dto = client.get("$baseUrl/browse", rscHeaders).extractNextJs<FilterPayloadDto> {
            it is JsonObject && "genres" in it && "seriesMeta" in it
        } ?: error("Failed to fetch filter data")
        return dto.genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val fetchedGenres = (data as? JsonArray)
            ?.parseAs<List<GenreItem>>()
            ?.map { it.toPair() }
            ?.takeIf { it.isNotEmpty() }
        val genres = fetchedGenres ?: staticGenres

        return FilterList(
            SortFilter(),
            StatusFilter(),
            FormatFilter(),
            TypeFilter(),
            AdaptationFilter(),
            GenreFilter(genres),
            MinimumChaptersFilter(),
        )
    }

    companion object {
        private const val TURNSTILE_SITE_KEY = "0x4AAAAAAEOmIHqZQLQ6GPRc"
    }
}

private val readerChunkRegex = Regex("""static/chunks/app/series/[^"]+/page-[^"]+\.js\?dpl=[^"]+""")
private val unlockActionRegex = Regex("""createServerReference\)\("([0-9a-f]+)"[^)]*"unlockChapterPages"""")

internal fun parseReaderChunkPath(rsc: String): String? = readerChunkRegex.find(rsc)?.value

internal fun parseUnlockActionId(chunk: String): String? = unlockActionRegex.find(chunk)?.groupValues?.get(1)
