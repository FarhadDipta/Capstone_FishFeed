package com.fishfeed.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fishfeed.AlarmActivity
import com.fishfeed.data.FeedingRepository
import com.fishfeed.data.Pond
import com.fishfeed.ui.components.ROUTE_HISTORY
import com.fishfeed.ui.components.ROUTE_HOME
import com.fishfeed.ui.components.ROUTE_PROFILE
import com.fishfeed.ui.components.ROUTE_SCHEDULE
import com.fishfeed.ui.components.ROUTE_STATS
import com.fishfeed.ui.screens.DashboardScreen
import com.fishfeed.ui.screens.HistoryScreen
import com.fishfeed.ui.screens.PondFormScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.fishfeed.ui.screens.PondListScreen
import com.fishfeed.ui.screens.ProfileScreen
import com.fishfeed.ui.screens.ScheduleFormScreen
import com.fishfeed.ui.screens.ScheduleListScreen
import com.fishfeed.ui.screens.SetupScreen
import com.fishfeed.ui.screens.SplashScreen
import com.fishfeed.ui.screens.StatsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val R_SPLASH = "splash"
private const val R_SETUP = "setup"
private const val R_PONDS = "ponds"
private const val R_POND_FORM = "pond_form/{pondId}/{first}"
private const val R_SCHEDULE_FORM = "schedule_form/{pondId}/{scheduleId}"

/** Graf navigasi seluruh aplikasi. Layar alarm dan jeda ada di [AlarmActivity]. */
@Composable
fun AppNav(repo: FeedingRepository) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun goTab(target: String) {
        try {
            nav.navigate(target) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } catch (e: Exception) {
            try {
                nav.navigate(target) { launchSingleTop = true }
            } catch (e2: Exception) {
                // Abaikan kesalahan navigasi
            }
        }
    }

    NavHost(navController = nav, startDestination = R_SPLASH) {
        composable(R_SPLASH) {
            SplashScreen()
            LaunchedEffect(Unit) {
                delay(1300)
                try {
                    val name = repo.getUserName()
                    val destination = when {
                        name == null -> R_SETUP
                        repo.pondCount() == 0 -> "pond_form/0/true"
                        else -> ROUTE_HOME
                    }
                    nav.navigate(destination) { popUpTo(R_SPLASH) { inclusive = true } }
                } catch (e: Exception) {
                    try {
                        nav.navigate(ROUTE_HOME) { popUpTo(R_SPLASH) { inclusive = true } }
                    } catch (e2: Exception) {
                        // Abaikan kesalahan navigasi
                    }
                }
            }
        }
        composable(R_SETUP) {
            SetupScreen { name, pondName, pondSize, photoPath, onComplete ->
                scope.launch {
                    repo.saveUserName(name)

                    repo.savePondWithSchedules(
                        Pond(
                            name = pondName,
                            sizeText = pondSize,
                            photoPath = photoPath
                        ),
                        listOf(
                            6 to 0,
                            12 to 0,
                            18 to 0
                        )
                    )

                    onComplete()

                    nav.navigate(ROUTE_HOME) {
                        popUpTo(R_SETUP) {
                            inclusive = true
                        }
                    }
                }
            }
        }
        composable(
            R_POND_FORM,
            arguments = listOf(
                navArgument("pondId") { type = NavType.LongType },
                navArgument("first") { type = NavType.BoolType }
            )
        ) { entry ->
            val pondId = entry.arguments?.getLong("pondId") ?: 0L
            val first = entry.arguments?.getBoolean("first") ?: false
            PondFormScreen(
                repo = repo,
                pondId = pondId,
                first = first,
                onSaved = {
                    if (first) {
                        nav.navigate(ROUTE_HOME) { popUpTo(R_POND_FORM) { inclusive = true } }
                    } else {
                        nav.popBackStack()
                    }
                },
                onBack = { nav.popBackStack() },
                onOpenSchedules = { goTab(ROUTE_SCHEDULE) }
            )
        }
        composable(ROUTE_HOME) {
            DashboardScreen(
                repo = repo,
                onSelectTab = ::goTab,
                onManagePonds = { nav.navigate(R_PONDS) },
                onSnooze = { context.startActivity(AlarmActivity.intent(context, it, openSnooze = true)) }
            )
        }
        composable(ROUTE_SCHEDULE) {
            ScheduleListScreen(
                repo = repo,
                onSelectTab = ::goTab,
                onAdd = { nav.navigate("schedule_form/$it/0") },
                onEdit = { pondId, scheduleId -> nav.navigate("schedule_form/$pondId/$scheduleId") }
            )
        }
        composable(ROUTE_HISTORY) { HistoryScreen(repo, onSelectTab = ::goTab) }
        composable(ROUTE_STATS) { StatsScreen(repo, onSelectTab = ::goTab) }
        composable(ROUTE_PROFILE) {
            ProfileScreen(repo, onSelectTab = ::goTab, onManagePonds = { nav.navigate(R_PONDS) })
        }
        composable(R_PONDS) {
            PondListScreen(
                repo = repo,
                onBack = { nav.popBackStack() },
                onOpenPond = { goTab(ROUTE_SCHEDULE) },
                onAdd = { nav.navigate("pond_form/0/false") },
                onEdit = { nav.navigate("pond_form/$it/false") }
            )
        }
        composable(
            R_SCHEDULE_FORM,
            arguments = listOf(
                navArgument("pondId") { type = NavType.LongType },
                navArgument("scheduleId") { type = NavType.LongType }
            )
        ) { entry ->
            ScheduleFormScreen(
                repo = repo,
                pondId = entry.arguments?.getLong("pondId") ?: 0L,
                scheduleId = entry.arguments?.getLong("scheduleId") ?: 0L,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
