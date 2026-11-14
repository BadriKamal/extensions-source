package eu.kanade.tachiyomi.extension.id.voratoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoraToonTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun latestUrlUsesNewSort() {
        val url = browseUrl("https://example.com", page = 1, filters = FilterList(SortFilter()))

        assertEquals("https://example.com/browse?page=1&sort=new", url.toString())
    }

    @Test
    fun popularUrlUsesPopularSortAndPage() {
        val url = browseUrl("https://example.com", page = 2, filters = FilterList(SortFilter("popular")))

        assertEquals("popular", url.queryParameter("sort"))
        assertEquals("2", url.queryParameter("page"))
    }

    @Test
    fun searchUrlContainsQueryAndFilters() {
        val url = browseUrl(
            baseUrl = "https://example.com",
            page = 3,
            query = "slime",
            filters = FilterList(
                SortFilter("rating"),
                StatusFilter("ongoing"),
                FormatFilter("manhua"),
                TypeFilter("mirror"),
                AdaptationFilter("yes"),
                genreFilter("action", "sci-fi"),
                MinimumChaptersFilter().apply { state = "10" },
            ),
        )

        assertEquals("slime", url.queryParameter("q"))
        assertEquals("action,sci-fi", url.queryParameter("genre"))
        assertEquals("ongoing", url.queryParameter("status"))
        assertEquals("manhua", url.queryParameter("format"))
        assertEquals("mirror", url.queryParameter("type"))
        assertEquals("yes", url.queryParameter("anime"))
        assertEquals("rating", url.queryParameter("sort"))
        assertEquals("10", url.queryParameter("min"))
        assertEquals("3", url.queryParameter("page"))
    }

    @Test
    fun emptyFiltersAreOmitted() {
        val url = browseUrl(
            baseUrl = "https://example.com",
            page = 1,
            filters = FilterList(
                StatusFilter(),
                FormatFilter(),
                TypeFilter(),
                AdaptationFilter(),
                GenreFilter(staticGenres),
                MinimumChaptersFilter(),
            ),
        )

        assertNull(url.queryParameter("q"))
        assertNull(url.queryParameter("genre"))
        assertNull(url.queryParameter("status"))
        assertNull(url.queryParameter("format"))
        assertNull(url.queryParameter("type"))
        assertNull(url.queryParameter("anime"))
        assertNull(url.queryParameter("sort"))
        assertNull(url.queryParameter("min"))
        assertEquals("1", url.queryParameter("page"))
    }

    @Test
    fun mapsBrowseDto() {
        val dto = json.decodeFromString<BrowseDto>(
            """
            {
              "series": [
                {
                  "id": 42,
                  "data": {
                    "slug": "alpha",
                    "title": "Alpha",
                    "author": "Writer",
                    "status": "Ongoing",
                    "synopsis": "Synopsis here",
                    "coverImage": "https://cdn.example/alpha.webp",
                    "genres": [
                      {"data": {"name": "Action"}},
                      {"data": {"name": "Fantasy"}}
                    ]
                  }
                }
              ],
              "seriesMeta": {"page": 1, "lastPage": 4}
            }
            """.trimIndent(),
        )

        val item = dto.series.single()

        assertEquals("/series/alpha", item.seriesUrl())
        assertEquals(SManga.ONGOING, item.statusCode())
        assertEquals(1, dto.seriesMeta?.page)
        assertEquals(4, dto.seriesMeta?.lastPage)
    }

    @Test
    fun seriesDtoFallsBackToIdWhenSlugIsMissing() {
        val dto = json.decodeFromString<BrowseDto>(
            """{"series":[{"id":7,"data":{"slug":null,"title":"Fallback"}}]}""",
        )

        assertEquals("/series/7", dto.series.single().seriesUrl())
    }

    @Test
    fun mapsSeriesStatus() {
        fun statusOf(value: String?): Int = json.decodeFromString<BrowseDto>(
            """{"series":[{"id":1,"data":{"slug":"alpha","title":"Alpha","status":$value}}]}""",
        ).series.single().statusCode()

        assertEquals(SManga.COMPLETED, statusOf("\"Completed\""))
        assertEquals(SManga.ON_HIATUS, statusOf("\"Hiatus\""))
        assertEquals(SManga.CANCELLED, statusOf("\"Cancelled\""))
        assertEquals(SManga.UNKNOWN, statusOf("null"))
    }

    @Test
    fun mapsSeriesDetailsAndChapters() {
        val dto = json.decodeFromString<SeriesDetailsDto>(
            """
            {
              "series": [
                {"id": 1, "data": {"slug": "alpha", "title": "Alpha", "status": "Completed"}}
              ],
              "chapters": [
                {
                  "chapterIndex": 12,
                  "createdAt": "2026-11-11T06:42:55.155Z",
                  "updatedAt": "2026-11-11T07:00:00.000Z",
                  "data": {"title": "Prologue", "images": []}
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(SManga.COMPLETED, dto.series.single().statusCode())

        val chapter = dto.chapters.single()

        assertEquals("/series/alpha/chapter/12", chapter.chapterUrl("alpha"))
        assertEquals("Chapter 12: Prologue", chapter.chapterName())
        assertEquals(12f, chapter.chapterNumber())
        assertEquals(
            java.time.Instant.parse("2026-11-11T06:42:55.155Z").toEpochMilli(),
            chapter.dateUpload(),
        )
    }

    @Test
    fun mapsChapterPagesWithChapterUrlForReferer() {
        val chapterUrl = "https://example.com/series/alpha/chapter/1"
        val dto = json.decodeFromString<ChapterPageDto>(
            """
            {
              "chapter": {
                "chapterIndex": 1,
                "data": {
                  "images": [
                    "https://cdn.example/001.jpg",
                    "https://cdn.example/002.jpg"
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf("https://cdn.example/001.jpg", "https://cdn.example/002.jpg"),
            dto.chapter.imageUrls(),
        )

        val pages = dto.chapter.toPageList(chapterUrl)

        assertEquals(2, pages.size)
        assertEquals(chapterUrl, pages[0].url)
        assertEquals("https://cdn.example/001.jpg", pages[0].imageUrl)
        assertEquals(chapterUrl, pages[1].url)
        assertEquals("https://cdn.example/002.jpg", pages[1].imageUrl)
    }

    @Test
    fun mapsGenreItems() {
        val item = json.decodeFromString<GenreItem>("""{"data":{"name":"Sci-Fi"}}""")

        assertEquals("Sci-Fi" to "sci-fi", item.toPair())
    }

    @Test
    fun extractsSlugFromRelativeAndAbsoluteMangaUrls() {
        assertEquals("alpha", seriesSlug("/series/alpha", "https://example.com"))
        assertEquals("alpha", seriesSlug("https://example.com/series/alpha", "https://example.com"))
    }

    @Test
    fun extractsSlugAndIndexFromChapterUrls() {
        assertEquals(
            "alpha" to "12.5",
            chapterSlugAndIndex("/series/alpha/chapter/12.5", "https://example.com"),
        )
        assertEquals(
            "alpha" to "12",
            chapterSlugAndIndex("https://example.com/series/alpha/chapter/12", "https://example.com"),
        )
        assertEquals(
            "alpha" to "7",
            chapterSlugAndIndex("/chapter/alpha-chapter-7-bahasa-indonesia", "https://example.com"),
        )
    }

    @Test
    fun parsesReaderChunkPathFromRsc() {
        val rsc = """
            "274","static/chunks/app/series/%5Bslug%5D/chapter/%5Bchapter%5D/page-b2bb5a726c433035.js?dpl=1794553167210-0f9c04d5-d2fd-4712-bbd7-b4d0a27d55a8"],"ReaderScreen"
        """.trimIndent()

        assertEquals(
            "static/chunks/app/series/%5Bslug%5D/chapter/%5Bchapter%5D/page-b2bb5a726c433035.js?dpl=1794553167210-0f9c04d5-d2fd-4712-bbd7-b4d0a27d55a8",
            parseReaderChunkPath(rsc),
        )
    }

    @Test
    fun parsesUnlockActionIdFromReaderChunk() {
        val chunk = """(0,y.createServerReference)("60d1bbcd5ae753b54be8a4ecf238a7ed44beb27311",y.callServer,void 0,y.findSourceMapURL,"unlockChapterPages")"""

        assertEquals("60d1bbcd5ae753b54be8a4ecf238a7ed44beb27311", parseUnlockActionId(chunk))
    }

    @Test
    fun mapsUnlockResponse() {
        val dto = json.decodeFromString<UnlockChapterPagesDto>(
            """{"ok":true,"chapterId":430923,"images":["https://cdn.example/001.jpg","https://cdn.example/002.jpg"]}""",
        )

        assertTrue(dto.ok)
        assertEquals(430923L, dto.chapterId)
        assertEquals(2, dto.images?.size)
    }

    @Test
    fun mapsUnlockedPagesWithChapterUrlForReferer() {
        val chapterUrl = "https://example.com/series/alpha/chapter/1"
        val item = json.decodeFromString<ChapterItem>(
            """{"id":430923,"data":{"images":[]},"pagesTicket":"p1.ticket"}""",
        )

        assertEquals(430923L, item.id)
        assertEquals("p1.ticket", item.pagesTicket)

        val pages = item.toPageList(
            chapterUrl,
            listOf("https://cdn.example/001.jpg", "https://cdn.example/002.jpg"),
        )

        assertEquals(2, pages.size)
        assertEquals(chapterUrl, pages[0].url)
        assertEquals("https://cdn.example/001.jpg", pages[0].imageUrl)
        assertEquals(chapterUrl, pages[1].url)
        assertEquals("https://cdn.example/002.jpg", pages[1].imageUrl)
    }

    private fun genreFilter(vararg selected: String) = GenreFilter(staticGenres).apply {
        state.filter { it.value in selected }.forEach { it.state = true }
    }
}
