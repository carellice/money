package com.money.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import com.money.app.ui.theme.BlobSpec
import androidx.compose.ui.unit.Dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
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
import com.money.app.ui.settings.ServerSettingsScreen
import com.money.app.ui.settings.SettingsScreen
import com.money.app.ui.transactions.TransactionsScreen
import com.money.app.ui.components.LocalMembers
import com.money.app.ui.components.LocalPayments
import com.money.app.data.Payments
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
    const val SERVER_SETTINGS = "server_settings"
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
    val customPayments by appContainer().repository.customPayments.collectAsStateWithLifecycle(emptyList())
    val payments = remember(customPayments) { Payments(customPayments) }

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
        CompositionLocalProvider(LocalMembers provides membersById, LocalPayments provides payments) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                // Between tabs the page slides the way the bar's capsule goes.
                enterTransition = {
                    val dir = tabDirection(initialState.destination.route, targetState.destination.route)
                    if (dir != 0) tabEnter(dir) else fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.97f)
                },
                exitTransition = {
                    val dir = tabDirection(initialState.destination.route, targetState.destination.route)
                    if (dir != 0) tabExit(dir) else fadeOut(tween(180))
                },
                popEnterTransition = {
                    val dir = tabDirection(initialState.destination.route, targetState.destination.route)
                    if (dir != 0) tabEnter(dir) else fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 1.03f)
                },
                popExitTransition = {
                    val dir = tabDirection(initialState.destination.route, targetState.destination.route)
                    if (dir != 0) tabExit(dir) else fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.97f)
                },
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
                        onOpenServer = { nav.navigate(Routes.SERVER_SETTINGS) },
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
                composable(Routes.SERVER_SETTINGS) {
                    ServerSettingsScreen(onBack = { nav.closeScreen(Routes.SERVER_SETTINGS) })
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

/** +1 when moving to a tab on the right, -1 to one on the left, 0 when either side is not a tab. */
private fun tabDirection(from: String?, to: String?): Int {
    val a = tabs.indexOfFirst { it.route == from }
    val b = tabs.indexOfFirst { it.route == to }
    return if (a < 0 || b < 0) 0 else (b - a).sign
}

private fun tabEnter(dir: Int): EnterTransition =
    slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { dir * it / 4 } + fadeIn(tween(200))

private fun tabExit(dir: Int): ExitTransition =
    slideOutHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { -dir * it / 4 } + fadeOut(tween(160))

/** The capsule wraps the whole selected tab, icon and label, with a little air around it. */
private val CapsuleInset = 2.dp
private val CapsuleHeight = 64.dp

@Composable
private fun BottomBar(route: String?, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    val c = Ui.colors
    val density = LocalDensity.current
    val selected = tabs.indexOfFirst { it.route == route }
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .shadow(24.dp, CircleShape, ambientColor = Ink, spotColor = Ink)
                .clip(CircleShape)
                .background(c.surface),
        ) {
            // Tabs share what the side padding and the room for the add button leave.
            val slot = (maxWidth - 20.dp - 80.dp) / 4
            val centers = with(density) {
                tabs.indices.map { i -> (10.dp + slot * (i + 0.5f) + if (i >= 2) 80.dp else 0.dp).toPx() }
            }
            LiquidCapsule(selected, centers, slot - CapsuleInset * 2, c.accent)
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                // Every tab owns an equal slice of the full bar height, so the touch target is large.
                tabs.forEachIndexed { i, tab ->
                    if (i == 2) Spacer(Modifier.width(80.dp))
                    TabItem(tab, i == selected, Modifier.weight(1f)) { onSelect(tab.route) }
                }
            }
        }
        AddButton(onAdd)
    }
}

/**
 * The capsule behind the selected tab. Its two ends move on their own springs: the one in front
 * runs ahead and the one behind catches up later, so it stretches like a drop on the way and
 * settles back with a small bounce. Once it stops it slowly turns into a living blob, like the
 * add button, and goes back to a smooth capsule as soon as it moves again.
 */
@Composable
private fun LiquidCapsule(index: Int, centers: List<Float>, capsuleWidth: Dp, color: Color) {
    val density = LocalDensity.current
    val half = with(density) { capsuleWidth.toPx() / 2 }
    val left = remember { Animatable(Float.NaN) }
    val right = remember { Animatable(Float.NaN) }
    val placedHalf = remember { floatArrayOf(Float.NaN) }
    // 0 smooth capsule, 1 blob: the capsule shrinks inside the blob while the blob grows over it.
    val organic = remember { Animatable(0f) }
    val spec = remember(index) { BlobSpec(11 + max(index, 0), amplitude = 0.09f) }
    val blobPath = remember { Path() }
    val time = LocalBlobTime.current
    LaunchedEffect(index, centers, half) {
        if (index < 0) return@LaunchedEffect
        val center = centers[index]
        val targetLeft = center - half
        val targetRight = center + half
        // First time, or a new bar width: straight in place.
        if (half != placedHalf[0]) {
            placedHalf[0] = half
            left.snapTo(targetLeft)
            right.snapTo(targetRight)
            organic.snapTo(1f)
            return@LaunchedEffect
        }
        val front = spring<Float>(dampingRatio = 0.8f, stiffness = 900f)
        val back = spring<Float>(dampingRatio = 0.7f, stiffness = 240f)
        val toRight = targetLeft > left.value
        launch { organic.animateTo(0f, tween(120)) }
        val edges = listOf(
            launch { left.animateTo(targetLeft, if (toRight) back else front) },
            launch { right.animateTo(targetRight, if (toRight) front else back) },
        )
        edges.joinAll()
        organic.animateTo(1f, tween(700))
    }
    val restWidth = half * 2
    val restHeight = with(density) { CapsuleHeight.toPx() }
    Box(
        Modifier.fillMaxSize().drawBehind {
            if (left.value.isNaN()) return@drawBehind
            val width = max(right.value - left.value, restWidth * 0.78f)
            // Stretched it gets a little thinner, squeezed a little taller, like a drop of liquid.
            val height = (restHeight * (restWidth / width).pow(0.25f)).coerceIn(restHeight * 0.82f, restHeight * 1.08f)
            val center = (left.value + right.value) / 2
            val o = organic.value
            if (o < 0.99f) {
                val k = 1f - 0.4f * o
                val w = width * k
                val h = height * k
                drawRoundRect(color, Offset(center - w / 2, size.height / 2 - h / 2), Size(w, h), CornerRadius(min(w, h) / 2))
            }
            if (o > 0.01f) {
                val k = 0.75f + 0.25f * o
                spec.path(Size(restWidth, restHeight), time.value * 1.4f, blobPath)
                withTransform({
                    translate(center - restWidth / 2, size.height / 2 - restHeight / 2)
                    scale(k, k, Offset(restWidth / 2, restHeight / 2))
                }) { drawPath(blobPath, color) }
            }
        },
    )
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ui.colors
    val fg by animateColorAsState(if (selected) Ink else c.inkSoft, label = "tabFg")
    // On the capsule both stay dark, as on every accent fill.
    val label by animateColorAsState(if (selected) Ink else c.inkSoft, label = "tabLabel")
    val scale = remember { Animatable(1f) }
    val tilt = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    // Chosen: the icon shrinks at once, then springs back to size wobbling side to side.
    LaunchedEffect(selected) {
        if (armed && selected) {
            scale.snapTo(0.62f)
            launch { scale.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 420f)) }
            tilt.snapTo(0f)
            tilt.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = 320f), initialVelocity = 420f)
        }
        armed = true
    }
    Column(
        modifier
            .fillMaxHeight()
            .bouncyClick(pressedScale = 0.9f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            tab.icon, null, tint = fg,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationZ = tilt.value
                },
        )
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
