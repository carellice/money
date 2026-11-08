package com.money.app.ui.components

import com.money.app.ui.theme.linen
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.money.app.ui.theme.Ui

/** Inside a [FullScreenPage]: plays the exit animation, then runs the action (e.g. save and close). */
val LocalClosePage = staticCompositionLocalOf<(() -> Unit) -> Unit> { { it() } }

/**
 * A form or picker opened over the current screen at full size, with a back arrow on top:
 * it looks like the pages of the app and keeps the state of the screen that opened it.
 */
@Composable
fun FullScreenPage(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ui.colors
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    // Closing first plays the exit, then really closes: removing the dialog at once made it jump away.
    var after by remember { mutableStateOf<(() -> Unit)?>(null) }
    val closeThen: (() -> Unit) -> Unit = { if (shown.targetState) { after = it; shown.targetState = false } }
    val close = { closeThen(onDismiss) }
    LaunchedEffect(shown.isIdle, shown.currentState) {
        if (shown.isIdle && !shown.currentState && !shown.targetState) after?.invoke()
    }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val view = LocalView.current
        SideEffect {
            window ?: return@SideEffect
            // Edge to edge like the app: no dim, the keyboard resizes the page, bar icons follow the theme.
            window.setDimAmount(0f)
            window.setWindowAnimations(0)
            @Suppress("DEPRECATION")
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !c.isDark
                isAppearanceLightNavigationBars = !c.isDark
            }
        }
        AnimatedVisibility(
            shown,
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 320f)) { it / 2 } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it / 2 } + fadeOut(tween(200)),
        ) {
            Column(Modifier.fillMaxSize().linen(c.isDark).imePadding()) {
                TopBar(title, onBack = close, actions = actions)
                CompositionLocalProvider(LocalClosePage provides closeThen) { content() }
            }
        }
    }
}
