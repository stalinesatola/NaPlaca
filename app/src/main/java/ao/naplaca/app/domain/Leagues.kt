package ao.naplaca.app.domain

data class League(
    val slug: String,
    val name: String,
    val country: String,
    val source: Source,
    /** Id na TheSportsDB (só para ligas dessa fonte). */
    val sportsDbId: String? = null,
    val sportsDbSeason: String? = null,
    val hasLeaders: Boolean = source == Source.ESPN,
)

object Leagues {
    const val GIRABOLA = "ang.girabola"

    val all: List<League> = listOf(
        League(GIRABOLA, "Girabola", "Angola", Source.SPORTSDB, sportsDbId = "5229", sportsDbSeason = "2026-2027"),
        League("caf.champions", "Liga dos Campeões CAF", "África", Source.ESPN),
        League("caf.nations", "CAN", "África", Source.ESPN),
        League("uefa.champions", "Liga dos Campeões", "Europa", Source.ESPN),
        League("uefa.europa", "Liga Europa", "Europa", Source.ESPN),
        League("eng.1", "Premier League", "Inglaterra", Source.ESPN),
        League("esp.1", "LaLiga", "Espanha", Source.ESPN),
        League("por.1", "Liga Portugal", "Portugal", Source.ESPN),
        League("ita.1", "Serie A", "Itália", Source.ESPN),
        League("ger.1", "Bundesliga", "Alemanha", Source.ESPN),
        League("fra.1", "Ligue 1", "França", Source.ESPN),
        League("bra.1", "Brasileirão", "Brasil", Source.ESPN),
        League("fifa.world", "Copa do Mundo", "Mundo", Source.ESPN),
    )

    val defaultSelection: Set<String> = setOf(
        GIRABOLA, "caf.champions", "uefa.champions", "eng.1", "esp.1", "por.1", "ita.1", "ger.1", "fra.1",
    )

    fun bySlug(slug: String): League? = all.firstOrNull { it.slug == slug }
    fun name(slug: String): String = bySlug(slug)?.name ?: slug
}
