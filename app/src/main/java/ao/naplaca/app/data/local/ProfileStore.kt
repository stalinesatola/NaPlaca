package ao.naplaca.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ao.naplaca.app.domain.Leagues
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AlertPrefs(
    val goals: Boolean = true,
    val redCards: Boolean = true,
    val kickoff: Boolean = true,
    val halftime: Boolean = false,
    val fullTime: Boolean = true,
    val reminder: Boolean = true,
    val reminderMinutes: Int = 30,
    val quietHours: Boolean = false,
    /** Horas inteiras (0–23). Silêncio de quietStart até quietEnd. */
    val quietStart: Int = 23,
    val quietEnd: Int = 7,
) {
    fun isQuiet(hour: Int): Boolean {
        if (!quietHours || quietStart == quietEnd) return false
        return if (quietStart < quietEnd) hour in quietStart until quietEnd else hour >= quietStart || hour < quietEnd
    }
}

@Serializable
data class Profile(
    val name: String = "",
    val leagues: Set<String> = Leagues.defaultSelection,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val alerts: AlertPrefs = AlertPrefs(),
    val onboarded: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "profile")

class ProfileStore(private val context: Context) {
    private object K {
        val name = stringPreferencesKey("name")
        val leagues = stringSetPreferencesKey("leagues")
        val theme = stringPreferencesKey("theme")
        val onboarded = booleanPreferencesKey("onboarded")
        val goals = booleanPreferencesKey("alert_goals")
        val red = booleanPreferencesKey("alert_red")
        val kickoff = booleanPreferencesKey("alert_kickoff")
        val halftime = booleanPreferencesKey("alert_halftime")
        val fullTime = booleanPreferencesKey("alert_fulltime")
        val reminder = booleanPreferencesKey("alert_reminder")
        val reminderMinutes = intPreferencesKey("alert_reminder_minutes")
        val quiet = booleanPreferencesKey("quiet")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
    }

    val profile: Flow<Profile> = context.dataStore.data.map { p ->
        val d = AlertPrefs()
        Profile(
            name = p[K.name].orEmpty(),
            leagues = p[K.leagues] ?: Leagues.defaultSelection,
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            onboarded = p[K.onboarded] ?: false,
            alerts = AlertPrefs(
                goals = p[K.goals] ?: d.goals,
                redCards = p[K.red] ?: d.redCards,
                kickoff = p[K.kickoff] ?: d.kickoff,
                halftime = p[K.halftime] ?: d.halftime,
                fullTime = p[K.fullTime] ?: d.fullTime,
                reminder = p[K.reminder] ?: d.reminder,
                reminderMinutes = p[K.reminderMinutes] ?: d.reminderMinutes,
                quietHours = p[K.quiet] ?: d.quietHours,
                quietStart = p[K.quietStart] ?: d.quietStart,
                quietEnd = p[K.quietEnd] ?: d.quietEnd,
            ),
        )
    }

    suspend fun current(): Profile = profile.first()

    suspend fun save(profile: Profile) {
        context.dataStore.edit { p ->
            p[K.name] = profile.name
            p[K.leagues] = profile.leagues
            p[K.theme] = profile.theme.name
            p[K.onboarded] = profile.onboarded
            with(profile.alerts) {
                p[K.goals] = goals
                p[K.red] = redCards
                p[K.kickoff] = kickoff
                p[K.halftime] = halftime
                p[K.fullTime] = fullTime
                p[K.reminder] = reminder
                p[K.reminderMinutes] = reminderMinutes
                p[K.quiet] = quietHours
                p[K.quietStart] = quietStart
                p[K.quietEnd] = quietEnd
            }
        }
    }

    suspend fun update(transform: (Profile) -> Profile) = save(transform(current()))
}
