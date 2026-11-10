package eu.kanade.tachiyomi.extension.id.voratoon

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoraToonTest {
    @Test
    fun latestUrlOmitsDefaultParameters() {
        val url = browseUrl("https://example.com", page = 1)

        assertEquals("https://example.com/browse", url.toString())
    }

    @Test
    fun popularUrlUsesPopularSortAndPage() {
        val url = browseUrl("https://example.com", page = 2, sort = "popular")

        assertEquals("popular", url.queryParameter("sort"))
        assertEquals("2", url.queryParameter("page"))
    }

    @Test
    fun searchUrlContainsQueryAndFilters() {
        val url = browseUrl(
            baseUrl = "https://example.com",
            page = 3,
            query = "slime",
            sort = "rating",
            status = "ongoing",
            format = "manhua",
            type = "mirror",
            anime = "yes",
            genres = listOf("action", "sci-fi"),
            minimumChapters = 10,
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
            status = "",
            format = "",
            type = "",
            anime = "",
            genres = emptyList(),
            minimumChapters = 0,
        )

        assertNull(url.queryParameter("genre"))
        assertNull(url.queryParameter("status"))
        assertNull(url.queryParameter("format"))
        assertNull(url.queryParameter("type"))
        assertNull(url.queryParameter("anime"))
        assertNull(url.queryParameter("min"))
    }

    @Test
    fun parsesMangaCards() {
        val document = Jsoup.parse(
            """
            <article class="card card-project">
              <a class="card-image-wrapper" href="/series/alpha">
                <img src="https://cdn.example/alpha.webp"/>
                <img src="https://flagcdn.com/w40/jp.png"/>
              </a>
              <div class="card-content"><a class="card-title" href="/series/alpha">Alpha</a></div>
            </article>
            <article class="card card-project">
              <a class="card-image-wrapper" href="/series/beta">
                <img src="https://cdn.example/beta.webp"/>
              </a>
              <div class="card-content"><a class="card-title" href="/series/beta">Beta</a></div>
            </article>
            <a aria-label="Halaman berikutnya" href="/browse?page=2">Next</a>
            """.trimIndent(),
        )

        val page = document.parseMangaPage()

        assertEquals(2, page.mangas.size)
        assertEquals("/series/alpha", page.mangas[0].url)
        assertEquals("Alpha", page.mangas[0].title)
        assertEquals("https://cdn.example/alpha.webp", page.mangas[0].thumbnailUrl)
        assertEquals("/series/beta", page.mangas[1].url)
        assertTrue(page.hasNextPage)
    }

    @Test
    fun parsesMangaDetails() {
        val document = Jsoup.parse(
            """
            <h1 class="series-title">Alpha</h1>
            <img alt="Cover Alpha" src="https://cdn.example/alpha.webp"/>
            <p class="synopsis synopsis-desktop">Synopsis here</p>
            <div class="info-item">
              <span class="info-label">Status</span>
              <strong class="info-val"><span>ONGOING</span></strong>
            </div>
            <div class="info-list-item"><span>Author</span><strong>Writer</strong></div>
            <div class="tags-container">
              <a class="tag" href="/browse?genre=Action">Action</a>
              <a class="tag" href="/browse?genre=Fantasy">Fantasy</a>
            </div>
            """.trimIndent(),
        )

        val details = document.parseMangaDetails("alpha")

        assertEquals("Alpha", details.title)
        assertEquals("https://cdn.example/alpha.webp", details.thumbnailUrl)
        assertEquals("Synopsis here", details.description)
        assertEquals("Writer", details.author)
        assertEquals("Action, Fantasy", details.genre)
        assertEquals("ONGOING", details.status)
    }

    @Test
    fun parsesChapterList() {
        val document = Jsoup.parse(
            """
            <a class="chapter-item" href="/series/alpha/chapter/12">
              <span class="chapter-title"><span class="chapter-number">Chapter 12</span></span>
              <span class="chapter-meta"><span class="chapter-date">9 jam lalu</span></span>
            </a>
            <a class="chapter-item" href="/series/alpha/chapter/11">
              <span class="chapter-title"><span class="chapter-number">Chapter 11</span></span>
              <span class="chapter-meta"><span class="chapter-date">2 bulan lalu</span></span>
            </a>
            """.trimIndent(),
        )

        val chapters = document.parseChapters()

        assertEquals(2, chapters.size)
        assertEquals("/series/alpha/chapter/12", chapters[0].url)
        assertEquals("Chapter 12", chapters[0].name)
        assertEquals(12f, chapters[0].number)
        assertTrue(chapters[0].dateUpload > 0)
        assertEquals(11f, chapters[1].number)
        assertTrue(chapters[1].dateUpload < chapters[0].dateUpload)
    }

    @Test
    fun parsesChapterPages() {
        val document = Jsoup.parse(
            """
            <img data-chapter-page="https://cdn.example/001.jpg" src="https://cdn.example/001.jpg"/>
            <img data-chapter-page="https://cdn.example/002.jpg" src="https://cdn.example/002.jpg"/>
            """.trimIndent(),
        )

        assertEquals(
            listOf("https://cdn.example/001.jpg", "https://cdn.example/002.jpg"),
            document.parsePages(),
        )
    }

    @Test
    fun parsesIndonesianRelativeDates() {
        val now = 1_700_000_000_000L

        assertEquals(now - 9 * 60 * 60 * 1000, parseRelativeDate("9 jam lalu", now))
        assertEquals(now - 14 * 24 * 60 * 60 * 1000, parseRelativeDate("14 hari lalu", now))
        assertEquals(now - 2L * 30 * 24 * 60 * 60 * 1000, parseRelativeDate("2 bulan lalu", now))
        assertEquals(now - 1L * 365 * 24 * 60 * 60 * 1000, parseRelativeDate("1 tahun lalu", now))
    }

    @Test
    fun unknownRelativeDatesAreZero() {
        val now = 1_700_000_000_000L

        assertEquals(0L, parseRelativeDate(null, now))
        assertEquals(0L, parseRelativeDate("", now))
        assertEquals(0L, parseRelativeDate("kemarin", now))
    }
}
