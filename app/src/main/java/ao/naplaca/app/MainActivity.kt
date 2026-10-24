package ao.naplaca.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.naplaca.app.data.local.Profile
import ao.naplaca.app.ui.DeepLink
import ao.naplaca.app.ui.NaPlacaRoot
import ao.naplaca.app.ui.screens.OnboardingScreen
import ao.naplaca.app.ui.theme.NaPlacaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    private val deepLinks = MutableStateFlow<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        val store = (application as NaPlacaApp).container.profileStore
        // null enquanto o perfil carrega, para não mostrar o onboarding por um instante.
        val profileFlow = store.profile.map<Profile, Profile?> { it }
        setContent {
            val profile by profileFlow.collectAsStateWithLifecycle(initialValue = null)
            val p = profile
            NaPlacaTheme(p?.theme ?: ao.naplaca.app.data.local.ThemeMode.SYSTEM) {
                when {
                    p == null -> Unit
                    !p.onboarded -> OnboardingScreen(requestNotifications = ::requestNotificationPermission)
                    else -> NaPlacaRoot(deepLinks)
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_MATCH_ID) ?: return
        val league = intent.getStringExtra(EXTRA_LEAGUE) ?: return
        if (id != "test") deepLinks.value = DeepLink(id, league)
    }

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
        const val EXTRA_LEAGUE = "league"
    }
}
