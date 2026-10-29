package com.money.app

import android.app.Application
import com.money.app.ai.AiAssistant
import com.money.app.ai.AiConfigStore
import com.money.app.data.MoneyDatabase
import com.money.app.data.MoneyRepository
import com.money.app.data.SettingsStore
import com.money.app.update.UpdateManager
import com.money.app.widget.RenewalsWidget
import com.money.app.work.Reminders
import com.money.app.work.RecurringWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import kotlinx.coroutines.launch

class AppContainer(app: Application) {
    val database = MoneyDatabase.build(app)
    val repository = MoneyRepository(database, app.filesDir)
    val settings = SettingsStore(app)
    val ai = AiAssistant(repository, AiConfigStore(app), app.filesDir)
    val scope = CoroutineScope(SupervisorJob())
    val updates = UpdateManager(app, scope)

    /** Month shown by home, movements and report, kept in sync between screens. */
    val month = MutableStateFlow(YearMonth.now())
}

class MoneyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Reminders.createChannel(this)
        RecurringWorker.schedule(this)
        container.scope.launch {
            container.repository.seedIfEmpty()
            RecurringWorker.runNow(this@MoneyApp, container)
        }
        container.updates.checkIfDue()
        // Keep the home screen widget in step with fixed expenses and the accent color.
        container.scope.launch {
            combine(container.repository.recurring, container.settings.settings.map { it.accent }) { _, _ -> }
                .drop(1)
                .collectLatest { RenewalsWidget.refresh(this@MoneyApp) }
        }
    }
}
