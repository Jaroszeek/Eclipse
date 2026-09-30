package pl.eclipse.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import pl.eclipse.app.BuildConfig
import pl.eclipse.app.R
import pl.eclipse.app.data.ThemeMode
import pl.eclipse.app.notify.Notifier
import pl.eclipse.app.ui.calendar.CalendarScreen
import pl.eclipse.app.ui.components.CountBadge
import pl.eclipse.app.ui.components.EclipseBackground
import pl.eclipse.app.ui.components.EclipseDisc
import pl.eclipse.app.ui.components.EclipseMotion
import pl.eclipse.app.ui.components.GlassState
import pl.eclipse.app.ui.components.NavScreen
import pl.eclipse.app.ui.components.RevealScreen
import pl.eclipse.app.ui.components.Tag
import pl.eclipse.app.ui.components.glass
import pl.eclipse.app.ui.components.glassSource
import pl.eclipse.app.ui.components.rememberGlassState
import pl.eclipse.app.ui.diagnostics.DiagnosticsScreen
import pl.eclipse.app.ui.grades.GradesScreen
import pl.eclipse.app.ui.grades.SubjectScreen
import pl.eclipse.app.ui.home.HomeScreen
import pl.eclipse.app.ui.important.ImportantScreen
import pl.eclipse.app.ui.inbox.InboxScreen
import pl.eclipse.app.ui.inbox.NotificationsScreen
import pl.eclipse.app.ui.messages.ComposeScreen
import pl.eclipse.app.ui.login.FirstSyncScreen
import pl.eclipse.app.ui.login.LoginScreen
import pl.eclipse.app.ui.login.NotificationPermissionScreen
import pl.eclipse.app.ui.stats.StatsScreen
import pl.eclipse.app.ui.settings.SettingsScreen
import pl.eclipse.app.ui.style.StyleScreen
import pl.eclipse.app.ui.tests.TestsScreen
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Trasa z powiadomienia; klasa (nie data class), więc dwa kliknięcia tej samej trasy to dwa różne żądania. */
class RouteRequest(val route: String)

@Serializable data object HomeRoute
@Serializable data object CalendarRoute
@Serializable data object TestsRoute
@Serializable data object GradesRoute
@Serializable data class SubjectRoute(val key: String)
@Serializable data object StatsRoute
@Serializable data object ImportantRoute
@Serializable data object InboxRoute
@Serializable data object SettingsRoute
@Serializable data object NotificationsRoute
@Serializable data object StyleRoute
@Serializable data object ComposeRoute
@Serializable data object DiagnosticsRoute
@Serializable data object ReconRoute

enum class TopLevel(val route: Any, @param:StringRes val title: Int, @param:DrawableRes val icon: Int) {
    HOME(HomeRoute, R.string.nav_home, R.drawable.ic_space_dashboard),
    CALENDAR(CalendarRoute, R.string.nav_calendar, R.drawable.ic_calendar_month),
    TESTS(TestsRoute, R.string.nav_tests, R.drawable.ic_fact_check),
    GRADES(GradesRoute, R.string.nav_grades, R.drawable.ic_school),
    STATS(StatsRoute, R.string.nav_stats, R.drawable.ic_bar_chart),
    IMPORTANT(ImportantRoute, R.string.nav_important, R.drawable.ic_warning),
    INBOX(InboxRoute, R.string.nav_inbox, R.drawable.ic_inbox),
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(WARSAW)
private val DAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d.MM, HH:mm").withZone(WARSAW)

/** Cel nawigacji w ramce „Zaćmienia” ([NavScreen]); ekrany z menu rozmywają się przy zmianie. */
private inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T> { entry -> NavScreen(blur = entry.isTopLevel()) { content(entry) } }
}

private fun NavBackStackEntry.isTopLevel() = TopLevel.entries.any { destination.hasRoute(it.route::class) }

fun formatSyncTime(millis: Long): String {
    val instant = Instant.ofEpochMilli(millis)
    return if (instant.atZone(WARSAW).toLocalDate() == today()) TIME.format(instant) else DAY_TIME.format(instant)
}

/** Ekran najwyższego poziomu (SPEC 12.9): logowanie → pierwsza synchronizacja → zgoda na powiadomienia → aplikacja. */
private enum class Root { LOADING, LOGIN, FIRST_SYNC, PERMISSION, MAIN }

/** Wejście do interfejsu. Każdy nowy ekran odsłania koło rosnące od tarczy poprzedniego („Zaćmienie”). */
@Composable
fun EclipseRoot(initialRoute: RouteRequest?, viewModel: ShellViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val root = when {
        !state.ready -> Root.LOADING
        !state.loggedIn -> Root.LOGIN
        state.firstSyncPending -> Root.FIRST_SYNC
        state.askPermission -> Root.PERMISSION
        else -> Root.MAIN
    }
    val reduceMotion = Eclipse.reduceMotion
    // Środek tarczy na ekranach, które ją mają, i skąd rośnie koło przy wejściu na ekran. Zwykłe mapy — czytane dopiero przy rysowaniu.
    val discs = remember { mutableMapOf<Root, Offset>() }
    val revealFrom = remember { mutableMapOf<Root, Offset?>() }
    // Stary ekran cofa się i blednie nad tłem aplikacji, a nie nad jasnym tłem okna.
    EclipseBackground {
        AnimatedContent(
            root,
            transitionSpec = {
                revealFrom[targetState] = discs[initialState]
                EclipseMotion.reveal(reduceMotion)
            },
            label = "root",
        ) { screen ->
            val onDiscPlaced: (Offset) -> Unit = { discs[screen] = it }
            RevealScreen(enabled = !reduceMotion, origin = { revealFrom[screen] }) {
                when (screen) {
                    Root.LOADING -> Unit
                    Root.LOGIN -> LoginScreen(onDiscPlaced)
                    Root.FIRST_SYNC -> FirstSyncScreen(
                        state.syncRunning, state.syncWaiting, state.lastSyncError,
                        onStart = viewModel::syncNow, onRetry = viewModel::retrySync, onLogout = viewModel::logout,
                        onDiscPlaced = onDiscPlaced,
                    )
                    Root.PERMISSION -> NotificationPermissionScreen(onDone = viewModel::permissionAsked)
                    Root.MAIN -> MainShell(state, viewModel, initialRoute)
                }
            }
        }
    }
}

@Composable
private fun MainShell(state: ShellState, viewModel: ShellViewModel, initialRoute: RouteRequest?) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val glass = rememberGlassState()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val top = TopLevel.entries.firstOrNull { destination?.hasRoute(it.route::class) == true }
    var toolboxOpen by rememberSaveable { mutableStateOf(false) }
    var subjectTitle by remember { mutableStateOf("") }

    LaunchedEffect(initialRoute) { initialRoute?.let { routeFor(it.route) }?.let { nav.navigateTop(it) } }

    val title = when {
        top != null -> stringResource(top.title)
        destination?.hasRoute(SubjectRoute::class) == true -> subjectTitle
        destination?.hasRoute(SettingsRoute::class) == true -> stringResource(R.string.nav_settings)
        destination?.hasRoute(ComposeRoute::class) == true -> stringResource(R.string.compose_title)
        destination?.hasRoute(NotificationsRoute::class) == true -> stringResource(R.string.nav_notifications)
        destination?.hasRoute(StyleRoute::class) == true -> stringResource(R.string.nav_style)
        destination?.hasRoute(DiagnosticsRoute::class) == true -> stringResource(R.string.diag_title)
        else -> stringResource(R.string.recon_title)
    }

    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topBarHeight = 64.dp
    val bannerHeight = if (state.syncFailing) 56.dp else 0.dp
    val contentPadding = PaddingValues(top = statusBar + topBarHeight + bannerHeight + 8.dp, bottom = navBar + 24.dp)

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen, // otwieranie ikoną menu — gest od krawędzi koliduje z systemowym „wstecz” (SPEC 12.0)
        scrimColor = Palette.Night1.copy(alpha = 0.45f),
        drawerContent = {
            Drawer(
                state = state,
                glass = glass,
                current = top,
                onSelect = { dest ->
                    scope.launch { drawer.close() }
                    nav.navigateTop(dest)
                },
                onThemeClick = viewModel::cycleTheme,
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassSource(glass)) {
                EclipseBackground()
                val reduceMotion = Eclipse.reduceMotion
                NavHost(
                    nav,
                    startDestination = HomeRoute,
                    enterTransition = {
                        if (reduceMotion) EnterTransition.None else if (targetState.isTopLevel()) EclipseMotion.throughEnter else EclipseMotion.forwardEnter
                    },
                    exitTransition = {
                        if (reduceMotion) ExitTransition.None else if (targetState.isTopLevel()) EclipseMotion.throughExit else EclipseMotion.forwardExit
                    },
                    popEnterTransition = { if (reduceMotion) EnterTransition.None else EclipseMotion.backEnter },
                    popExitTransition = { if (reduceMotion) ExitTransition.None else EclipseMotion.backExit() },
                    predictivePopEnterTransition = { if (reduceMotion) EnterTransition.None else EclipseMotion.backEnter },
                    predictivePopExitTransition = { edge -> if (reduceMotion) ExitTransition.None else EclipseMotion.backExit(edge) },
                ) {
                    screen<HomeRoute> {
                        HomeScreen(
                            contentPadding,
                            onOpenTests = { nav.navigateTop(TestsRoute) },
                            onOpenImportant = { nav.navigateTop(ImportantRoute) },
                            onOpenGrades = { nav.navigateTop(GradesRoute) },
                        )
                    }
                    screen<CalendarRoute> {
                        CalendarScreen(contentPadding, glass, toolboxOpen, { toolboxOpen = it }, state.syncRunning, viewModel::syncNow)
                    }
                    screen<TestsRoute> {
                        TestsScreen(contentPadding, state.syncRunning, viewModel::syncNow, onOpenCalculator = { nav.navigate(SubjectRoute(it)) })
                    }
                    screen<GradesRoute> {
                        GradesScreen(contentPadding, state.syncRunning, viewModel::syncNow, onOpenSubject = { nav.navigate(SubjectRoute(it)) })
                    }
                    screen<SubjectRoute> { back ->
                        val route = back.toRoute<SubjectRoute>()
                        SubjectScreen(route.key, contentPadding, onTitle = { subjectTitle = it })
                    }
                    screen<StatsRoute> { StatsScreen(contentPadding) }
                    screen<ImportantRoute> { ImportantScreen(contentPadding, onOpenSubject = { nav.navigate(SubjectRoute(it)) }) }
                    screen<InboxRoute> { InboxScreen(contentPadding, onCompose = { nav.navigate(ComposeRoute) }) }
                    screen<ComposeRoute> { ComposeScreen(contentPadding, onDone = { nav.popBackStack() }) }
                    screen<SettingsRoute> {
                        SettingsScreen(
                            contentPadding,
                            onOpenSubject = { nav.navigate(SubjectRoute(it)) },
                            onOpenStyle = { nav.navigate(StyleRoute) },
                            onOpenDiagnostics = { nav.navigate(DiagnosticsRoute) },
                        )
                    }
                    screen<NotificationsRoute> { NotificationsScreen(contentPadding, onOpenRoute = { r -> routeFor(r)?.let { nav.navigateTop(it) } }) }
                    screen<StyleRoute> { StyleScreen(contentPadding) }
                    screen<DiagnosticsRoute> {
                        DiagnosticsScreen(onOpenRecon = { nav.navigate(ReconRoute) }, modifier = Modifier.padding(contentPadding))
                    }
                    screen<ReconRoute> { ReconScreen(Modifier.padding(contentPadding)) }
                }
            }
            TopBar(
                title = title,
                glass = glass,
                state = state,
                isTopLevel = top != null,
                showToolbox = top == TopLevel.CALENDAR,
                onMenu = { scope.launch { drawer.open() } },
                onBack = { nav.popBackStack() },
                onRefresh = viewModel::syncNow,
                onBell = { nav.navigate(NotificationsRoute) { launchSingleTop = true } },
                onToolbox = { toolboxOpen = !toolboxOpen },
                onBannerDetails = { nav.navigate(SettingsRoute) { launchSingleTop = true } },
            )
        }
    }
}

fun NavHostController.navigateTop(route: Any) = navigate(route) {
    popUpTo(HomeRoute) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Trasa z powiadomienia (deep link, SPEC 9). */
private fun routeFor(name: String): Any? = when (name) {
    Notifier.ROUTE_HOME -> HomeRoute
    Notifier.ROUTE_GRADES -> GradesRoute
    Notifier.ROUTE_TESTS -> TestsRoute
    Notifier.ROUTE_CALENDAR -> CalendarRoute
    Notifier.ROUTE_IMPORTANT -> ImportantRoute
    Notifier.ROUTE_INBOX -> InboxRoute
    Notifier.ROUTE_SETTINGS -> SettingsRoute
    Notifier.ROUTE_NOTIFICATIONS -> NotificationsRoute
    "style" -> StyleRoute
    "stats" -> StatsRoute
    "diagnostics" -> DiagnosticsRoute
    else -> null
}

@Composable
private fun TopBar(
    title: String,
    glass: GlassState,
    state: ShellState,
    isTopLevel: Boolean,
    showToolbox: Boolean,
    onMenu: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onBell: () -> Unit,
    onToolbox: () -> Unit,
    onBannerDetails: () -> Unit,
) {
    val c = Eclipse.colors
    Column(Modifier.fillMaxWidth().glass(glass, bordered = false)) {
        Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isTopLevel) {
                val description = stringResource(if (state.critical > 0) R.string.open_menu_alert else R.string.open_menu)
                IconButton(onClick = onMenu, modifier = Modifier.semantics { contentDescription = description }) {
                    Box {
                        Icon(painterResource(R.drawable.ic_menu), null, tint = c.text)
                        if (state.critical > 0) Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Critical).align(Alignment.TopEnd))
                    }
                }
            } else {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back), tint = c.text) }
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.settings.demoMode) Tag(stringResource(R.string.demo_tag), c.accent)
            }
            RefreshButton(state.syncRunning, onRefresh)
            IconButton(onClick = onBell) {
                Box {
                    Icon(painterResource(R.drawable.ic_notifications), stringResource(R.string.open_notifications), tint = c.text)
                    if (state.unreadNotifications > 0) Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent).align(Alignment.TopEnd))
                }
            }
            if (showToolbox) {
                IconButton(onClick = onToolbox) { Icon(painterResource(R.drawable.ic_widgets), stringResource(R.string.open_toolbox), tint = c.text) }
            }
        }
        HorizontalDivider(color = c.border)
        if (state.syncFailing) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(Palette.Critical.copy(alpha = 0.16f)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_sync_problem), null, Modifier.size(20.dp), tint = c.readable(Palette.Critical))
                Text(
                    state.lastSyncAt?.let { stringResource(R.string.sync_failing, formatSyncTime(it)) } ?: stringResource(R.string.sync_failing_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.text,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    maxLines = 2,
                )
                TextButton(onClick = onBannerDetails) { Text(stringResource(R.string.details)) }
            }
        }
    }
}

@Composable
private fun RefreshButton(running: Boolean, onRefresh: () -> Unit) {
    val c = Eclipse.colors
    val reduceMotion = Eclipse.reduceMotion
    val rotation = if (running && !reduceMotion) {
        rememberInfiniteTransition(label = "sync").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "rotation").value
    } else 0f
    IconButton(onClick = onRefresh, enabled = !running) {
        Icon(
            painterResource(R.drawable.ic_refresh),
            stringResource(if (running) R.string.refreshing else R.string.refresh),
            Modifier.rotate(rotation),
            tint = if (running) c.accentText else c.text,
        )
    }
}

@Composable
private fun Drawer(state: ShellState, glass: GlassState, current: TopLevel?, onSelect: (Any) -> Unit, onThemeClick: () -> Unit) {
    val c = Eclipse.colors
    Column(
        Modifier
            .fillMaxHeight()
            .width(300.dp)
            .glass(glass, RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            EclipseDisc(coverage = 0.35f, size = 44.dp, showProgress = false)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, color = c.text, modifier = Modifier.padding(start = 10.dp))
        }
        Spacer(Modifier.height(12.dp))
        TopLevel.entries.forEach { dest ->
            val badge = when (dest) {
                TopLevel.GRADES -> state.newGrades to c.accent
                TopLevel.INBOX -> state.unreadInbox to c.accent
                TopLevel.IMPORTANT -> state.critical to Palette.Critical
                else -> 0 to c.accent
            }
            DrawerItem(stringResource(dest.title), dest.icon, current == dest, badge.first, badge.second) { onSelect(dest.route) }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = c.border)
        DrawerItem(stringResource(R.string.nav_settings), R.drawable.ic_settings, false, 0, c.accent) { onSelect(SettingsRoute) }
        val themeName = stringResource(
            when (state.settings.themeMode) {
                ThemeMode.SYSTEM -> R.string.theme_system
                ThemeMode.LIGHT -> R.string.theme_light
                ThemeMode.DARK -> R.string.theme_dark
            },
        )
        DrawerItem(
            stringResource(R.string.theme_label, themeName),
            if (c.isDark) R.drawable.ic_dark_mode else R.drawable.ic_light_mode,
            false, 0, c.accent, onThemeClick,
        )
        if (BuildConfig.DEBUG) DrawerItem(stringResource(R.string.nav_style), R.drawable.ic_palette, false, 0, c.accent) { onSelect(StyleRoute) }
        Text(
            state.lastSyncAt?.let { stringResource(R.string.last_sync, formatSyncTime(it)) } ?: stringResource(R.string.last_sync_never),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun DrawerItem(label: String, @DrawableRes icon: Int, selected: Boolean, badge: Int, badgeColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val c = Eclipse.colors
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) c.accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(painterResource(icon), null, tint = if (selected) c.accentText else c.textSecondary)
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (selected) c.text else c.text, modifier = Modifier.weight(1f))
        CountBadge(badge, color = badgeColor)
    }
}

