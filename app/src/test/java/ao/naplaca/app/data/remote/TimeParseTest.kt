package ao.naplaca.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeParseTest {
    @Test
    fun formatos() {
        val expected = 1792841400000L // 2026-10-24T11:30:00Z
        assertEquals(expected, TimeParse.epochMillis("2026-10-24T11:30Z"))
        assertEquals(expected, TimeParse.epochMillis("2026-10-24T11:30:00Z"))
        assertEquals(expected, TimeParse.epochMillis("2026-10-24T11:30:00+00:00"))
        assertEquals(expected, TimeParse.epochMillis("2026-10-24T12:30:00+01:00"))
        assertEquals(expected, TimeParse.epochMillis("2026-10-24T11:30:00"))
        assertEquals(expected, TimeParse.epochMillis("2026-10-24 11:30:00"))
        assertNull(TimeParse.epochMillis("ontem"))
        assertNull(TimeParse.epochMillis(null))
    }
}

class EspnDatesTest {
    private fun repo(zone: String) = ao.naplaca.app.data.repository.FootballRepository(
        espn = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(EspnApi::class.java)) { _, _, _ -> error("sem rede") } as EspnApi,
        sportsDb = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SportsDbApi::class.java)) { _, _, _ -> error("sem rede") } as SportsDbApi,
        cache = object : ao.naplaca.app.data.local.CacheDao {
            override suspend fun get(key: String) = null
            override suspend fun put(entry: ao.naplaca.app.data.local.CacheEntity) {}
            override suspend fun deleteOlderThan(before: Long) {}
        },
        zone = { java.time.ZoneId.of(zone) },
    )

    @Test
    fun luandaPrecisaDeDoisDiasDeNovaIorque() {
        val d = java.time.LocalDate.of(2026, 10, 24)
        assertEquals(setOf(d.minusDays(1), d), repo("Africa/Luanda").espnDatesFor(d))
    }

    @Test
    fun novaIorqueUsaOProprioDia() {
        val d = java.time.LocalDate.of(2026, 10, 24)
        assertEquals(setOf(d), repo("America/New_York").espnDatesFor(d))
    }
}
