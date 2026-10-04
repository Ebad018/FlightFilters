package com.ebad.flightfilters

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DepartureBoard
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ebad.flightfilters.data.ThemeMode
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.AutoAwesome
import com.ebad.flightfilters.ui.AppViewModel
import com.ebad.flightfilters.ui.AskScreen
import com.ebad.flightfilters.ui.LoopScreen
import androidx.compose.material.icons.outlined.Loop
import com.ebad.flightfilters.ui.DealsScreen
import com.ebad.flightfilters.ui.DeparturesScreen
import com.ebad.flightfilters.ui.LinesScreen
import com.ebad.flightfilters.ui.NetworkScreen
import com.ebad.flightfilters.ui.PlanScreen
import com.ebad.flightfilters.ui.SettingsScreen
import com.ebad.flightfilters.ui.theme.FlightFiltersTheme

/** Five destinations in the bar (Material's maximum); Lines open from Plan, last-minute Departures from Deals. */
private enum class Dest(val route: String, val label: String, val icon: ImageVector, val inBar: Boolean = true) {
    DEALS("deals", "Deals", Icons.Outlined.LocalOffer),
    LOOP("loop", "Loop", Icons.Outlined.Loop),
    ASK("ask", "Ask", Icons.Outlined.AutoAwesome),
    NETWORK("network", "Network", Icons.Outlined.Hub),
    DEPARTURES("departures", "Departures", Icons.Outlined.DepartureBoard, inBar = false),
    PLAN("plan", "Plan", Icons.Outlined.Tune),
    LINES("lines", "Lines", Icons.Outlined.Timeline, inBar = false),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
            val settings by vm.settings.collectAsState()
            val s = settings
            val mode = s?.themeMode ?: ThemeMode.MIDNIGHT
            val dark = mode == ThemeMode.MIDNIGHT || (mode == ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme())
            androidx.compose.runtime.LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }
            FlightFiltersTheme(mode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (s != null) AppShell(vm, s)
                }
            }
        }
    }
}

@Composable
private fun AppShell(vm: AppViewModel, settings: com.ebad.flightfilters.data.AppSettings) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val showNav = route != "settings"

    fun go(r: String) = nav.navigateTop(r)

    val host: @Composable (Modifier) -> Unit = { mod ->
        NavHost(nav, startDestination = Dest.DEALS.route, modifier = mod) {
            composable(Dest.DEALS.route) {
                DealsScreen(vm, settings, onPlanned = { go(Dest.NETWORK.route) }, onOpenSettings = { nav.navigate("settings") }, onOpenDepartures = { nav.navigate(Dest.DEPARTURES.route) })
            }
            composable(Dest.LOOP.route) {
                LoopScreen(vm, settings, onOpenSettings = { nav.navigate("settings") })
            }
            composable(Dest.ASK.route) {
                AskScreen(
                    vm, settings,
                    onOpenNetwork = { go(Dest.NETWORK.route) },
                    onOpenDeals = { go(Dest.DEALS.route) },
                    onOpenSettings = { nav.navigate("settings") },
                )
            }
            composable(Dest.NETWORK.route) {
                NetworkScreen(vm, settings, onOpenPlan = { go(Dest.PLAN.route) }, onOpenSettings = { nav.navigate("settings") })
            }
            composable(Dest.DEPARTURES.route) { DeparturesScreen(vm, settings, onOpenSettings = { nav.navigate("settings") }) }
            composable(Dest.PLAN.route) {
                PlanScreen(vm, settings, onScan = { go(Dest.NETWORK.route) }, onOpenSettings = { nav.navigate("settings") }, onOpenLines = { nav.navigate(Dest.LINES.route) })
            }
            composable(Dest.LINES.route) {
                LinesScreen(vm, onRun = { go(Dest.NETWORK.route) }, onOpenPlan = { go(Dest.PLAN.route) }, onOpenSettings = { nav.navigate("settings") })
            }
            composable("settings") { SettingsScreen(vm, settings, onBack = { nav.popBackStack() }) }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            if (showNav) {
                NavigationRail(Modifier.fillMaxHeight()) {
                    Dest.entries.filter { it.inBar }.forEach { d ->
                        NavigationRailItem(
                            selected = route == d.route,
                            onClick = { go(d.route) },
                            icon = { Icon(d.icon, null) },
                            label = { Text(d.label) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f)) { host(Modifier.fillMaxSize()) }
        }
    } else {
        Scaffold(
            bottomBar = {
                if (showNav) NavigationBar {
                    Dest.entries.filter { it.inBar }.forEach { d ->
                        NavigationBarItem(
                            selected = route == d.route,
                            onClick = { go(d.route) },
                            icon = { Icon(d.icon, null) },
                            label = { Text(d.label) },
                        )
                    }
                }
            },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        ) { pad ->
            host(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad))
        }
    }
}

private fun NavHostController.navigateTop(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

