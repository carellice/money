package com.money.app.ui.components

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.money.app.ui.theme.Ui

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
    Dialog(
        onDismissRequest = onDismiss,
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
        val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(
            shown,
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 320f)) { it / 2 } + fadeIn(),
        ) {
            Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
                TopBar(title, onBack = onDismiss, actions = actions)
                content()
            }
        }
    }
}
