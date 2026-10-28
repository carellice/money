package com.money.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.money.app.AppContainer
import com.money.app.MainActivity
import com.money.app.MoneyApp
import com.money.app.R
import com.money.app.data.RecurringEntity
import com.money.app.domain.Dates
import com.money.app.domain.Money
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/** Registers due fixed expenses and posts renewal reminders. */
class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MoneyApp
        runNow(app, app.container)
        return Result.success()
    }

    companion object {
        private const val NAME = "recurring-daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        suspend fun runNow(context: Context, container: AppContainer) {
            val reminders = container.repository.processRecurring()
            if (container.settings.current().remindersEnabled) {
                reminders.forEach { Reminders.notify(context, it) }
            }
            runCatching { MonthlyNotice.check(context, container) }
        }
    }
}

/** In the first days of a month, sums up the month just ended (with the AI comment when configured). */
object MonthlyNotice {
    private const val CHANNEL = "monthly"
    private const val ID = 9_000

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Resoconto del mese", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A inizio mese, com'è andato il mese appena finito"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    suspend fun check(context: Context, container: AppContainer) {
        val ai = container.ai
        val today = LocalDate.now()
        val month = YearMonth.from(today).minusMonths(1)
        if (!ai.store.monthlyNotice.value || today.dayOfMonth > 7 || ai.store.lastNoticeMonth == month.toString()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val report = ai.report(month)
        ai.store.lastNoticeMonth = month.toString()
        if (report.isEmpty) return

        val comment = if (ai.store.config.value != null) {
            runCatching { ai.monthCommentary(month) }.getOrNull()?.replace("**", "")
        } else null
        val grade = report.pagella?.let { "Pagella ${it.grade.letter} · " } ?: ""
        val title = "$grade${Dates.monthName(month)}: saldo ${Money.format(report.balance)}"
        val text = comment ?: report.pagella?.summary ?: "Entrate ${Money.format(report.income)}, uscite ${Money.format(report.expense)}."
        val intent = PendingIntent.getActivity(
            context, ID,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ROUTE, "report")
                .putExtra(MainActivity.EXTRA_MONTH, month.toString()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.lineSequence().firstOrNull { it.isNotBlank() } ?: text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(report.pagella?.grade?.color?.toInt() ?: 0)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID, notification)
    }
}

object Reminders {
    private const val CHANNEL = "renewals"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Rinnovi e spese fisse", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avvisi prima del rinnovo di abbonamenti e spese fisse"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        MonthlyNotice.createChannel(context)
    }

    fun notify(context: Context, item: RecurringEntity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val date = LocalDate.ofEpochDay(item.nextDate)
        val intent = PendingIntent.getActivity(
            context, item.id.toInt(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "recurring"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (item.autoRegister) "Verrà registrato in automatico." else "Ricordati di pagarlo."
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${item.name}: ${Money.format(item.amountCents)} ${Dates.relative(date)}")
            .setContentText("Rinnovo il ${Dates.long(date)}. $text")
            .setColor(item.color.toInt())
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(item.id.toInt(), notification)
    }
}
