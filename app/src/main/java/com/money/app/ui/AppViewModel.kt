package com.money.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.money.app.AppContainer
import com.money.app.MoneyApp

/** ViewModel scoped to the current nav destination, built from the app container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as MoneyApp
    return viewModel { create(app.container, createSavedStateHandle()) }
}

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as MoneyApp).container
