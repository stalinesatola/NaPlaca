package ao.naplaca.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ao.naplaca.app.domain.Match
import ao.naplaca.app.domain.Source
import ao.naplaca.app.ui.screens.AlertsScreen
import ao.naplaca.app.ui.screens.BantubetScreen
import ao.naplaca.app.ui.screens.ExploreScreen
import ao.naplaca.app.ui.screens.FavoritesScreen
import ao.naplaca.app.ui.screens.LeagueScreen
import ao.naplaca.app.ui.screens.LeaguesScreen
import ao.naplaca.app.ui.screens.MatchDetailScreen
import ao.naplaca.app.ui.screens.MatchesScreen
import ao.naplaca.app.ui.screens.ProfileScreen
import ao.naplaca.app.ui.screens.TeamScreen
import kotlinx.coroutines.flow.MutableStateFlow

data class DeepLink(val matchId: String, val league: String)

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    MATCHES("matches", "Jogos", Icons.Outlined.SportsSoccer),
    LEAGUES("leagues", "Ligas", Icons.Outlined.EmojiEvents),
    EXPLORE("explore", "Explorar", Icons.Outlined.Insights),
    BANTUBET("bantubet", "Bantubet", Icons.Outlined.BarChart),
    PROFILE("profile", "Perfil", Icons.Outlined.Person),
}

object Routes {
    fun match(id: String, league: String) = "match/${Uri.encode(id)}/${Uri.encode(league)}"
    fun league(slug: String) = "league/${Uri.encode(slug)}"
    fun team(source: Source, slug: String, id: String) = "team/${source.name}/${Uri.encode(slug)}/${Uri.encode(id)}"
}

@Composable
fun NaPlacaRoot(deepLinks: MutableStateFlow<DeepLink?>) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val pending by deepLinks.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let {
            nav.navigate(Routes.match(it.matchId, it.league))
            deepLinks.value = null
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val openMatch: (Match) -> Unit = { m -> nav.navigate(Routes.match(m.id, m.leagueSlug)) }
        val openTeam: (Source, String, String) -> Unit = { s, slug, id -> nav.navigate(Routes.team(s, slug, id)) }
        NavHost(nav, startDestination = Tab.MATCHES.route, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Tab.MATCHES.route) { MatchesScreen(onOpenMatch = openMatch, requestNotifications = requestNotifications) }
            composable(Tab.LEAGUES.route) { LeaguesScreen(onOpenLeague = { nav.navigate(Routes.league(it)) }) }
            composable(Tab.EXPLORE.route) { ExploreScreen() }
            composable(Tab.BANTUBET.route) { BantubetScreen(onBack = null) }
            composable(Tab.PROFILE.route) {
                ProfileScreen(onOpenFavorites = { nav.navigate("favorites") }, onOpenAlerts = { nav.navigate("alerts") })
            }
            composable(
                "match/{id}/{league}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("league") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val league = entry.arguments?.getString("league").orEmpty()
                MatchDetailScreen(
                    matchId = id,
                    league = league,
                    onBack = { nav.popBackStack() },
                    onOpenTeam = { m, team -> if (team.id.isNotBlank()) openTeam(m.source, m.leagueSlug, team.id) },
                    onOpenBantubet = { nav.navigate("bantubet-full") },
                    requestNotifications = requestNotifications,
                )
            }
            composable("league/{slug}") { entry ->
                LeagueScreen(
                    slug = entry.arguments?.getString("slug").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenMatch = openMatch,
                    onOpenTeam = openTeam,
                )
            }
            composable("team/{source}/{slug}/{id}") { entry ->
                val source = runCatching { Source.valueOf(entry.arguments?.getString("source").orEmpty()) }.getOrDefault(Source.ESPN)
                TeamScreen(
                    source = source,
                    slug = entry.arguments?.getString("slug").orEmpty(),
                    teamId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenMatch = openMatch,
                    requestNotifications = requestNotifications,
                )
            }
            composable("favorites") {
                FavoritesScreen(
                    onBack = { nav.popBackStack() },
                    onOpenTeam = openTeam,
                    onOpenMatch = { id, league -> nav.navigate(Routes.match(id, league)) },
                )
            }
            composable("alerts") { AlertsScreen(onBack = { nav.popBackStack() }, requestNotifications = requestNotifications) }
            composable("bantubet-full") { BantubetScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
