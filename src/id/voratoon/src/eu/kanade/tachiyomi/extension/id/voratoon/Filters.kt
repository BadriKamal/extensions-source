package eu.kanade.tachiyomi.extension.id.voratoon

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    name: String,
    private val filterValues: Array<Pair<String, String>>,
) : Filter.Select<String>(name, filterValues.map { it.first }.toTypedArray()) {
    val selectedValue: String get() = filterValues[state].second
}

class SortFilter :
    UriPartFilter(
        "Sort",
        arrayOf(
            Pair("Latest Update", ""),
            Pair("New Series", "newseries"),
            Pair("Most Bookmark", "bookmark"),
            Pair("Popular", "popular"),
            Pair("Rating", "rating"),
            Pair("A-Z", "az"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("Any", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
            Pair("Cancelled", "cancelled"),
        ),
    )

class FormatFilter :
    UriPartFilter(
        "Format",
        arrayOf(
            Pair("Any", ""),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Manga", "manga"),
            Pair("Mangatoon", "mangatoon"),
            Pair("Webtoon", "webtoon"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("Any", ""),
            Pair("Project", "project"),
            Pair("Mirror", "mirror"),
        ),
    )

class AdaptationFilter :
    UriPartFilter(
        "Adaptation",
        arrayOf(
            Pair("Any", ""),
            Pair("Animated", "yes"),
            Pair("Yet Animated", "no"),
        ),
    )

class GenreFilter(private val genres: Array<Pair<String, String>>) :
    Filter.Group<GenreOption>(
        "Genres",
        genres.map { GenreOption(it.first, it.second) },
    ) {
    val selectedValues: List<String> get() = state.filter { it.state }.map { it.value }
}

class GenreOption(name: String, val value: String) : Filter.CheckBox(name)

class MinimumChaptersFilter : Filter.Text("Minimum Chapters") {
    val selectedValue: Int get() = state.toIntOrNull()?.takeIf { it > 0 } ?: 0
}

internal val genres = arrayOf(
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
).map { it to it.lowercase() }.toTypedArray()
