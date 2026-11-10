package eu.kanade.tachiyomi.extension.id.voratoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

@Source
abstract class VoraToon : KeiSource() {

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        set("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
    }

    override suspend fun getPopularManga(page: Int): MangasPage = client.get(
        browseUrl(baseUrl, page, sort = "popular"),
    ).asJsoup().parseMangaPage().toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get(
        browseUrl(baseUrl, page),
    ).asJsoup().parseMangaPage().toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = browseUrl(
            baseUrl = baseUrl,
            page = page,
            query = query,
            sort = filters.filterIsInstance<SortFilter>().firstOrNull()?.selectedValue.orEmpty(),
            status = filters.filterIsInstance<StatusFilter>().firstOrNull()?.selectedValue.orEmpty(),
            format = filters.filterIsInstance<FormatFilter>().firstOrNull()?.selectedValue.orEmpty(),
            type = filters.filterIsInstance<TypeFilter>().firstOrNull()?.selectedValue.orEmpty(),
            anime = filters.filterIsInstance<AdaptationFilter>().firstOrNull()?.selectedValue.orEmpty(),
            genres = filters.filterIsInstance<GenreFilter>().firstOrNull()?.selectedValues.orEmpty(),
            minimumChapters = filters.filterIsInstance<MinimumChaptersFilter>().firstOrNull()?.selectedValue ?: 0,
        )

        return client.get(url).asJsoup().parseMangaPage().toMangasPage()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.getSlug(baseUrl)
        val document = client.get("$baseUrl/series/$slug").asJsoup()

        return SMangaUpdate(
            manga = if (fetchDetails) document.parseMangaDetails(slug).toSManga(slug) else manga,
            chapters = if (fetchChapters) document.parseChapters().map { it.toSChapter() } else chapters,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "series") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        return client.get("$baseUrl/series/$slug").asJsoup().parseMangaDetails(slug).toSManga(slug)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(
        if (chapter.url.startsWith("http")) chapter.url else "$baseUrl${chapter.url}",
    ).asJsoup().parsePages().toPageList()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        FormatFilter(),
        TypeFilter(),
        AdaptationFilter(),
        GenreFilter(genres),
        MinimumChaptersFilter(),
    )

    override fun imageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .headers(
            headersBuilder()
                .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .build(),
        )
        .get()
        .build()

    private fun ParsedMangaPage.toMangasPage(): MangasPage = MangasPage(
        mangas = mangas.map { manga ->
            SManga.create().apply {
                url = manga.url
                title = manga.title
                thumbnail_url = manga.thumbnailUrl
            }
        },
        hasNextPage = hasNextPage,
    )

    private fun ParsedMangaDetails.toSManga(slug: String): SManga = SManga.create().apply {
        url = "/series/$slug"
        title = this@toSManga.title
        thumbnail_url = this@toSManga.thumbnailUrl
        description = this@toSManga.description
        author = this@toSManga.author
        genre = this@toSManga.genre
        status = parseStatus(this@toSManga.status)
        initialized = true
    }

    private fun ParsedChapter.toSChapter(): SChapter = SChapter.create().apply {
        url = this@toSChapter.url
        name = this@toSChapter.name
        chapter_number = this@toSChapter.number
        date_upload = this@toSChapter.dateUpload
    }

    private fun List<String>.toPageList(): List<Page> = mapIndexed { index, url ->
        Page(index, imageUrl = url)
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled", "canceled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

private fun SManga.getSlug(baseUrl: String): String = (url.toHttpUrlOrNull() ?: "$baseUrl$url".toHttpUrl()).pathSegments[1]
