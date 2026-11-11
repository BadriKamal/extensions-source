package eu.kanade.tachiyomi.extension.id.voratoon

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriQueryFilter {
    fun addToQuery(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val param: String,
    private val options: Array<Pair<String, String>>,
    default: String = "",
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == default }.takeIf { it != -1 } ?: 0,
),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        options[state].second
            .takeIf { it.isNotEmpty() }
            ?.let { builder.addQueryParameter(param, it) }
    }
}

class SortFilter(default: String = "new") :
    UriPartFilter(
        "Urutkan",
        "sort",
        arrayOf(
            Pair("Terbaru", "new"),
            Pair("Populer", "popular"),
            Pair("Rating", "rating"),
            Pair("A - Z", "az"),
            Pair("Seri Baru", "newseries"),
            Pair("Favorit", "bookmark"),
        ),
        default,
    )

class StatusFilter(default: String = "") :
    UriPartFilter(
        "Status",
        "status",
        arrayOf(
            Pair("Semua", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
            Pair("Cancelled", "cancelled"),
        ),
        default,
    )

class FormatFilter(default: String = "") :
    UriPartFilter(
        "Format",
        "format",
        arrayOf(
            Pair("Semua", ""),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Mangatoon", "mangatoon"),
            Pair("Webtoon", "webtoon"),
        ),
        default,
    )

class TypeFilter(default: String = "") :
    UriPartFilter(
        "Tipe",
        "type",
        arrayOf(
            Pair("Semua", ""),
            Pair("Project", "project"),
            Pair("Mirror", "mirror"),
        ),
        default,
    )

class AdaptationFilter(default: String = "") :
    UriPartFilter(
        "Adaptasi",
        "anime",
        arrayOf(
            Pair("Semua", ""),
            Pair("Sudah Adaptasi", "yes"),
            Pair("Belum Adaptasi", "no"),
        ),
        default,
    )

class GenreOption(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Pair<String, String>>) :
    Filter.Group<GenreOption>(
        "Genre",
        genres.map { GenreOption(it.first, it.second) },
    ),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        state.filter { it.state }
            .map { it.value }
            .takeIf { it.isNotEmpty() }
            ?.let { builder.addQueryParameter("genre", it.joinToString(",")) }
    }
}

class MinimumChaptersFilter :
    Filter.Text("Minimal Chapter"),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        state.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.let { builder.addQueryParameter("min", it.toString()) }
    }
}

internal val staticGenres = listOf(
    "Adult",
    "4-Koma",
    "Action",
    "Adventure",
    "Comedy",
    "Cooking",
    "Demons",
    "Drama",
    "Ecchi",
    "Fantasy",
    "Game",
    "Gender Bender",
    "Gore",
    "Harem",
    "Historical",
    "Horror",
    "Isekai",
    "Josei",
    "Magic",
    "Martial Arts",
    "Mature",
    "Mecha",
    "Medical",
    "Military",
    "Music",
    "Mystery",
    "One-Shot",
    "Police",
    "Psychological",
    "Reincarnation",
    "Romance",
    "School",
    "School Life",
    "Sci-Fi",
    "Seinen",
    "Shoujo",
    "Shoujo Ai",
    "Shounen",
    "Shounen Ai",
    "Slice of Life",
    "Sports",
    "Super Power",
    "Supernatural",
    "Thriller",
    "Tragedy",
    "Vampire",
    "Webtoons",
    "Yuri",
).map { it to it.lowercase() }
