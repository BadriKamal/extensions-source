package eu.kanade.tachiyomi.extension.id.voratoon

import org.jsoup.nodes.Document

internal class ParsedMangaPage(
    val mangas: List<ParsedManga>,
    val hasNextPage: Boolean,
)

internal class ParsedManga(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
)

internal class ParsedMangaDetails(
    val title: String,
    val thumbnailUrl: String?,
    val description: String?,
    val author: String?,
    val genre: String?,
    val status: String?,
)

internal class ParsedChapter(
    val url: String,
    val name: String,
    val number: Float,
    val dateUpload: Long,
)

internal fun Document.parseMangaPage(): ParsedMangaPage {
    val mangas = select("article.card").mapNotNull { card ->
        val link = card.selectFirst("a.card-title") ?: return@mapNotNull null
        val href = link.attr("href")
        if (!href.startsWith("/series/")) return@mapNotNull null

        ParsedManga(
            url = href,
            title = link.text(),
            thumbnailUrl = card.selectFirst("a.card-image-wrapper img")?.absUrl("src"),
        )
    }

    return ParsedMangaPage(mangas, selectFirst("a[aria-label=\"Halaman berikutnya\"]") != null)
}

internal fun Document.parseMangaDetails(slug: String): ParsedMangaDetails = ParsedMangaDetails(
    title = selectFirst("h1.series-title")?.text()?.ifBlank { null } ?: slug,
    thumbnailUrl = selectFirst("img[alt^=Cover]")?.absUrl("src"),
    description = selectFirst("p.synopsis.synopsis-desktop")?.text(),
    author = select("div.info-list-item")
        .firstOrNull { it.selectFirst("span")?.text()?.contains("Author") == true }
        ?.selectFirst("strong")?.text(),
    genre = select("div.tags-container a.tag").joinToString(", ") { it.text() },
    status = select("div.info-item")
        .firstOrNull { it.selectFirst("span.info-label")?.text() == "Status" }
        ?.selectFirst("strong.info-val")?.text(),
)

internal fun Document.parseChapters(): List<ParsedChapter> = select("a.chapter-item").mapNotNull { item ->
    val href = item.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
    val number = item.selectFirst(".chapter-number")?.text() ?: return@mapNotNull null

    ParsedChapter(
        url = href,
        name = item.selectFirst(".chapter-title")?.text()?.ifBlank { number } ?: number,
        number = number.substringAfterLast(' ').replace(',', '.').toFloatOrNull() ?: -1f,
        dateUpload = parseRelativeDate(item.selectFirst(".chapter-date")?.text()),
    )
}

internal fun Document.parsePages(): List<String> = select("img[data-chapter-page]")
    .map { it.attr("data-chapter-page") }
