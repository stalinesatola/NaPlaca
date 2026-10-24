package ao.naplaca.app.data.remote

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API pública (sem chave) usada pelos sites da ESPN.
 * Base: https://site.api.espn.com/
 * As respostas são lidas como JsonObject e convertidas em [EspnMapper], porque o formato
 * varia entre endpoints (ex.: "score" é texto no scoreboard e objeto no calendário da equipa).
 */
interface EspnApi {
    @GET("apis/site/v2/sports/soccer/{league}/scoreboard")
    suspend fun scoreboard(@Path("league") league: String, @Query("dates") dates: String? = null): JsonObject

    @GET("apis/site/v2/sports/soccer/{league}/summary")
    suspend fun summary(@Path("league") league: String, @Query("event") eventId: String): JsonObject

    @GET("apis/v2/sports/soccer/{league}/standings")
    suspend fun standings(@Path("league") league: String): JsonObject

    @GET("apis/site/v2/sports/soccer/{league}/statistics")
    suspend fun leaders(@Path("league") league: String): JsonObject

    @GET("apis/site/v2/sports/soccer/{league}/teams/{team}")
    suspend fun team(@Path("league") league: String, @Path("team") teamId: String): JsonObject

    @GET("apis/site/v2/sports/soccer/{league}/teams/{team}/schedule")
    suspend fun teamSchedule(
        @Path("league") league: String,
        @Path("team") teamId: String,
        @Query("fixture") fixture: Boolean? = null,
    ): JsonObject
}

/**
 * TheSportsDB com a chave de teste gratuita "123" — cobre o Girabola.
 * Base: https://www.thesportsdb.com/api/v1/json/123/
 */
interface SportsDbApi {
    @GET("eventsday.php")
    suspend fun eventsDay(@Query("d") date: String, @Query("l") leagueId: String): JsonObject

    @GET("eventsnextleague.php")
    suspend fun nextLeague(@Query("id") leagueId: String): JsonObject

    @GET("eventspastleague.php")
    suspend fun pastLeague(@Query("id") leagueId: String): JsonObject

    @GET("eventsseason.php")
    suspend fun season(@Query("id") leagueId: String, @Query("s") season: String): JsonObject

    @GET("lookupevent.php")
    suspend fun event(@Query("id") eventId: String): JsonObject

    @GET("lookuptable.php")
    suspend fun table(@Query("l") leagueId: String, @Query("s") season: String): JsonObject

    @GET("lookupteam.php")
    suspend fun team(@Query("id") teamId: String): JsonObject

    @GET("eventsnext.php")
    suspend fun teamNext(@Query("id") teamId: String): JsonObject

    @GET("eventslast.php")
    suspend fun teamLast(@Query("id") teamId: String): JsonObject
}
