package com.money.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.money.app.data.RecurringKind
import com.money.app.data.TxType
import com.money.app.ui.add.AddTransactionScreen
import com.money.app.ui.ai.AiSettingsScreen
import com.money.app.ui.ai.ChatScreen
import com.money.app.ui.categories.CategoriesScreen
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.home.HomeScreen
import com.money.app.ui.recurring.RecurringEditScreen
import com.money.app.ui.recurring.RecurringScreen
import com.money.app.ui.report.ReportScreen
import com.money.app.ui.settings.SettingsScreen
import com.money.app.ui.transactions.TransactionsScreen
import com.money.app.ui.components.LocalMembers
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.LocalBlobTime
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.blob
import kotlin.math.sin

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val RECURRING = "recurring"
    const val REPORT = "report"
    const val CATEGORIES = "categories"
    const val SETTINGS = "settings"
    const val CHAT = "chat"
    const val AI_SETTINGS = "ai_settings"
    private const val ADD = "add"
    private const val RECURRING_EDIT = "recurring_edit"

    const val ADD_PATTERN = "$ADD?type={type}&id={id}"
    const val RECURRING_EDIT_PATTERN = "$RECURRING_EDIT?id={id}&kind={kind}"

    fun add(type: TxType = TxType.EXPENSE, id: Long = 0) = "$ADD?type=${type.name}&id=$id"
    fun recurringEdit(id: Long = 0, kind: RecurringKind = RecurringKind.SUBSCRIPTION) = "$RECURRING_EDIT?id=$id&kind=${kind.name}"

    val topLevel = setOf(HOME, TRANSACTIONS, RECURRING, REPORT)
}

/** Pops only if [pattern] is still on top, so double close requests are harmless. */
private fun NavController.closeScreen(pattern: String) {
    if (currentBackStackEntry?.destination?.route == pattern) popBackStack()
}

@Composable
fun MoneyRoot(deepLinkRoute: String?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val c = Ui.colors
    val members by appContainer().repository.members.collectAsStateWithLifecycle(emptyList())
    val membersById = remember(members) { members.associateBy { it.id } }

    LaunchedEffect(deepLinkRoute) {
        if (deepLinkRoute != null && deepLinkRoute in Routes.topLevel) {
            nav.navigate(deepLinkRoute) { launchSingleTop = true }
        }
        if (deepLinkRoute != null) onDeepLinkHandled()
    }

    // Tabs are not restored on purpose: every page opens scrolled to the top.
    // Home stays in the back stack, so it gets an explicit nudge instead.
    var homeScrollReset by remember { mutableIntStateOf(0) }
    val switchTab: (String) -> Unit = switch@{ target ->
        // Tapping the tab you are already on does nothing.
        if (nav.currentDestination?.route == target) return@switch
        if (target == Routes.HOME) homeScrollReset++
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id)
            launchSingleTop = true
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        CompositionLocalProvider(LocalMembers provides membersById) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                enterTransition = { fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.97f) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 1.03f) },
                popExitTransition = { fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.97f) },
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onAdd = { nav.navigate(Routes.add(it)) },
                        onOpenReport = { switchTab(Routes.REPORT) },
                        onOpenRecurring = { switchTab(Routes.RECURRING) },
                        onOpenRecurringItem = { nav.navigate(Routes.recurringEdit(it)) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onOpenChat = { nav.navigate(Routes.CHAT) },
                        scrollToTop = homeScrollReset,
                    )
                }
                composable(Routes.TRANSACTIONS) {
                    TransactionsScreen(onOpen = { nav.navigate(Routes.add(id = it)) })
                }
                composable(Routes.RECURRING) {
                    RecurringScreen(
                        onOpen = { nav.navigate(Routes.recurringEdit(it)) },
                        onAdd = { nav.navigate(Routes.recurringEdit()) },
                    )
                }
                composable(Routes.REPORT) {
                    ReportScreen(
                        onOpenTransaction = { nav.navigate(Routes.add(id = it)) },
                        onOpenChat = { nav.navigate(Routes.CHAT) },
                        onOpenAiSettings = { nav.navigate(Routes.AI_SETTINGS) },
                    )
                }
                composable(
                    Routes.ADD_PATTERN,
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType; defaultValue = TxType.EXPENSE.name },
                        navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    ),
                    enterTransition = { slideInVertically(spring(dampingRatio = 0.85f, stiffness = 320f)) { it / 2 } + fadeIn() },
                    popExitTransition = { slideOutVertically(tween(260)) { it / 2 } + fadeOut(tween(200)) },
                ) {
                    AddTransactionScreen(onClose = { nav.closeScreen(Routes.ADD_PATTERN) })
                }
                composable(
                    Routes.RECURRING_EDIT_PATTERN,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("kind") { type = NavType.StringType; defaultValue = RecurringKind.SUBSCRIPTION.name },
                    ),
                    enterTransition = { slideInVertically(spring(dampingRatio = 0.85f, stiffness = 320f)) { it / 2 } + fadeIn() },
                    popExitTransition = { slideOutVertically(tween(260)) { it / 2 } + fadeOut(tween(200)) },
                ) {
                    RecurringEditScreen(onClose = { nav.closeScreen(Routes.RECURRING_EDIT_PATTERN) })
                }
                composable(Routes.CATEGORIES) {
                    CategoriesScreen(onBack = { nav.closeScreen(Routes.CATEGORIES) })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { nav.closeScreen(Routes.SETTINGS) },
                        onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
                        onOpenAi = { nav.navigate(Routes.AI_SETTINGS) },
                    )
                }
                composable(Routes.CHAT) {
                    ChatScreen(
                        onBack = { nav.closeScreen(Routes.CHAT) },
                        onOpenAiSettings = { nav.navigate(Routes.AI_SETTINGS) },
                    )
                }
                composable(Routes.AI_SETTINGS) {
                    AiSettingsScreen(onBack = { nav.closeScreen(Routes.AI_SETTINGS) })
                }
            }
        }

        AnimatedVisibility(
            visible = route in Routes.topLevel,
            enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 300f)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomBar(route, switchTab) { nav.navigate(Routes.add()) }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.TRANSACTIONS, "Movimenti", Icons.AutoMirrored.Rounded.ReceiptLong),
    Tab(Routes.RECURRING, "Fisse", Icons.Rounded.Autorenew),
    Tab(Routes.REPORT, "Report", Icons.Rounded.DonutLarge),
)

@Composable
private fun BottomBar(route: String?, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    val c = Ui.colors
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .shadow(24.dp, CircleShape, ambientColor = Ink, spotColor = Ink)
                .clip(CircleShape)
                .background(c.surface)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Every tab owns an equal slice of the full bar height, so the touch target is large.
            tabs.take(2).forEach { TabItem(it, route == it.route, Modifier.weight(1f)) { onSelect(it.route) } }
            Spacer(Modifier.width(80.dp))
            tabs.drop(2).forEach { TabItem(it, route == it.route, Modifier.weight(1f)) { onSelect(it.route) } }
        }
        AddButton(onAdd)
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ui.colors
    val bg by animateColorAsState(if (selected) c.accent else c.surface, label = "tabBg")
    val fg by animateColorAsState(if (selected) Ink else c.inkSoft, label = "tabFg")
    val label by animateColorAsState(if (selected) c.ink else c.inkSoft, label = "tabLabel")
    val width by animateDpAsState(if (selected) 56.dp else 40.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "tabPill")
    Column(
        modifier
            .fillMaxHeight()
            .bouncyClick(pressedScale = 0.9f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(width = width, height = 34.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tab.icon, null, tint = fg, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(tab.label, style = Ui.type.caption, color = label, maxLines = 1)
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val c = Ui.colors
    val time = LocalBlobTime.current
    Box(
        Modifier
            .size(70.dp)
            .graphicsLayer {
                val breathe = 1f + 0.035f * sin(time.value * 2.2f)
                scaleX = breathe
                scaleY = breathe
            }
            .bouncyClick(pressedScale = 0.85f, onClick = onClick)
            .blob(c.accent, seed = 3, amplitude = 0.09f, speed = 1.4f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, "Aggiungi", tint = Ink, modifier = Modifier.size(32.dp))
    }
}
