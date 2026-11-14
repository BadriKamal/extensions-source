package eu.kanade.tachiyomi.extension.id.voratoon

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.time.Instant

@Serializable
class BrowseDto(
    val series: List<SeriesItem>,
    val seriesMeta: Meta? = null,
)

@Serializable
class FilterPayloadDto(
    val genres: List<GenreItem>,
)

@Serializable
class SeriesDetailsDto(
    val series: List<SeriesItem>,
    val chapters: List<ChapterItem>,
)

@Serializable
class ChapterPageDto(
    val chapter: ChapterItem,
)

@Serializable
class SeriesItem(
    private val id: Int,
    private val data: SeriesData,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = seriesUrl()
        title = data.title
        thumbnail_url = data.coverImage
        author = data.author
        description = data.synopsis
        genre = data.genres?.joinToString { it.data.name } ?: ""
        status = statusCode()
        initialized = true
    }

    internal fun seriesUrl(): String = "/series/${data.slug ?: id}"

    internal fun statusCode(): Int = when (data.status?.lowercase()) {
        "ongoing", "on going" -> SManga.ONGOING
        "completed", "complete" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled", "canceled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class SeriesData(
    val slug: String?,
    val title: String,
    val author: String? = null,
    val status: String? = null,
    val synopsis: String? = null,
    val coverImage: String? = null,
    val genres: List<GenreData>? = null,
)

@Serializable
class GenreData(
    val data: GenreInfo,
)

@Serializable
class GenreInfo(
    val name: String,
)

@Serializable
class GenreItem(
    private val data: GenreInfo,
) {
    fun toPair(): Pair<String, String> = data.name to data.name.lowercase()
}

@Serializable
class Meta(
    val page: Int? = null,
    val lastPage: Int? = null,
)

@Serializable
class ChapterItem(
    internal val id: Long? = null,
    private val data: ChapterData,
    private val createdAt: String? = null,
    private val updatedAt: String? = null,
    private val chapterIndex: Float? = null,
    internal val pagesTicket: String? = null,
) {
    fun toSChapter(seriesSlug: String?): SChapter = SChapter.create().apply {
        url = chapterUrl(seriesSlug)
        name = chapterName()
        date_upload = dateUpload()
        chapter_number = chapterNumber()
    }

    fun toPageList(chapterUrl: String, images: List<String> = imageUrls()): List<Page> = images.mapIndexed { index, imageUrl ->
        Page(index, url = chapterUrl, imageUrl = imageUrl.trim())
    }

    internal fun chapterUrl(seriesSlug: String?): String = "/series/$seriesSlug/chapter/${formattedIndex()}"

    internal fun chapterName(): String {
        val index = formattedIndex()
        return if (data.title.isNullOrBlank()) {
            "Chapter $index"
        } else {
            "Chapter $index: ${data.title}"
        }
    }

    internal fun dateUpload(): Long = Instant.tryParse(createdAt ?: updatedAt)

    internal fun imageUrls(): List<String> = data.images.orEmpty()

    internal fun chapterNumber(): Float = data.index ?: chapterIndex ?: 0f

    private fun formattedIndex(): String = chapterNumber().toString().removeSuffix(".0")
}

@Serializable
class ChapterData(
    val index: Float? = null,
    val title: String? = null,
    val images: List<String>? = null,
)

@Serializable
class UnlockChapterPagesDto(
    val ok: Boolean,
    val reason: String? = null,
    val message: String? = null,
    val chapterId: Long? = null,
    val images: List<String>? = null,
)

fun SManga.getSlug(baseUrl: String): String = seriesSlug(url, baseUrl)

fun SChapter.getSlugAndIndex(baseUrl: String): Pair<String, String> = chapterSlugAndIndex(url, baseUrl)

internal fun seriesSlug(url: String, baseUrl: String): String = (url.toHttpUrlOrNull() ?: "$baseUrl$url".toHttpUrl()).pathSegments[1]

internal fun chapterSlugAndIndex(url: String, baseUrl: String): Pair<String, String> = if (url.startsWith("/chapter/")) {
    val slug = url.substringAfter("/chapter/").substringBefore("-chapter-")
    val chapterIndex = url.substringAfter("-chapter-").substringBefore("-bahasa-")
    slug to chapterIndex
} else {
    val httpUrl = url.toHttpUrlOrNull() ?: "$baseUrl$url".toHttpUrl()
    httpUrl.pathSegments[1] to httpUrl.pathSegments[3]
}
