package com.money.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.money.app.MainActivity
import com.money.app.MoneyApp
import com.money.app.domain.Dates
import com.money.app.domain.Money
import com.money.app.domain.Recurrence
import kotlinx.coroutines.flow.first
import java.time.LocalDate

internal data class RenewalRow(
    val id: Long,
    val name: String,
    val amount: String,
    val relative: String,
    val date: String,
    val soon: Boolean,
    val logo: Bitmap,
)

internal data class RenewalsData(val rows: List<RenewalRow>, val next30Days: String, val accent: Bitmap, val backdrop: Bitmap)

/** Upcoming subscription and fixed expense renewals, resizable from one row to a full list. */
class RenewalsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, MEDIUM, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { Content(data) }
    }

    private suspend fun load(context: Context): RenewalsData {
        val container = (context.applicationContext as MoneyApp).container
        val today = LocalDate.now()
        val active = container.repository.recurring.first().filter { it.active }
        val logoPx = (40 * context.resources.displayMetrics.density).toInt()
        val rows = active.sortedBy { it.nextDate }.take(8).map { item ->
            val date = LocalDate.ofEpochDay(item.nextDate)
            RenewalRow(
                id = item.id,
                name = item.name,
                amount = Money.format(item.amountCents),
                relative = Dates.relative(date, today).replaceFirstChar { it.uppercase() },
                date = Dates.short(date),
                soon = item.nextDate - today.toEpochDay() <= 3,
                logo = WidgetBitmaps.logo(item, logoPx),
            )
        }
        val next30 = active.sumOf { item ->
            Recurrence.occurrencesBetween(item, today, today.plusDays(30)).size * item.amountCents
        }
        val accent = container.settings.current().accent.toInt()
        val density = context.resources.displayMetrics.density
        return RenewalsData(
            rows = rows,
            next30Days = Money.short(next30),
            accent = WidgetBitmaps.blob(7, accent, (14 * density).toInt()),
            backdrop = WidgetBitmaps.backdrop(accent, (300 * density).toInt(), (200 * density).toInt()),
        )
    }

    @Composable
    private fun Content(data: RenewalsData) {
        val size = LocalSize.current
        val open = actionStartActivity(
            Intent().setClassName("com.money.app", MainActivity::class.java.name)
                .putExtra(MainActivity.EXTRA_ROUTE, "recurring")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        val list = data.rows.isNotEmpty() && size.height >= MEDIUM.height
        Box(GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(Background).clickable(open)) {
            Image(
                ImageProvider(data.backdrop),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = if (list) Alignment.Vertical.Top else Alignment.Vertical.CenterVertically,
            ) {
                when {
                    data.rows.isEmpty() -> Empty(data)
                    size.height < MEDIUM.height -> RenewalLine(data.rows.first(), showDate = size.width >= WIDE.width)
                    else -> {
                        Header(data, size.width >= WIDE.width)
                        Spacer(GlanceModifier.height(6.dp))
                        LazyColumn(GlanceModifier.fillMaxWidth()) {
                            items(data.rows, itemId = { it.id }) { row ->
                                Column {
                                    RenewalLine(row, showDate = size.width >= WIDE.width)
                                    Spacer(GlanceModifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(data: RenewalsData, wide: Boolean) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Image(ImageProvider(data.accent), contentDescription = null, modifier = GlanceModifier.size(12.dp))
            Spacer(GlanceModifier.width(8.dp))
            Text(
                "Prossimi rinnovi",
                style = TextStyle(color = InkColor, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                if (wide) "30 giorni: ${data.next30Days}" else data.next30Days,
                style = TextStyle(color = SoftColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun RenewalLine(row: RenewalRow, showDate: Boolean) {
        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Image(ImageProvider(row.logo), contentDescription = row.name, modifier = GlanceModifier.size(36.dp))
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    row.name,
                    style = TextStyle(color = InkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    if (showDate) "${row.relative} · ${row.date}" else row.relative,
                    style = TextStyle(color = if (row.soon) SoonColor else SoftColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Text(row.amount, style = TextStyle(color = InkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }

    @Composable
    private fun Empty(data: RenewalsData) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Image(ImageProvider(data.accent), contentDescription = null, modifier = GlanceModifier.size(14.dp))
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Text("Nessun rinnovo in arrivo", style = TextStyle(color = InkColor, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text("Aggiungi abbonamenti in Money", style = TextStyle(color = SoftColor, fontSize = 12.sp), maxLines = 1)
            }
        }
    }

    companion object {
        private val COMPACT = DpSize(110.dp, 50.dp)
        private val MEDIUM = DpSize(180.dp, 110.dp)
        private val WIDE = DpSize(260.dp, 110.dp)
        private val LARGE = DpSize(260.dp, 200.dp)

        // Same palette as the app: warm paper by day, deep plum by night.
        private val Background = ColorProvider(day = Color(0xFFF6F1EA), night = Color(0xFF1E1A2B))
        private val InkColor = ColorProvider(day = Color(0xFF1B1726), night = Color(0xFFF4EFFA))
        private val SoftColor = ColorProvider(day = Color(0xFF5E5870), night = Color(0xFFB9B2CC))
        private val SoonColor = ColorProvider(day = Color(0xFFD14B3A), night = Color(0xFFFF9C8E))

        /** Redraws every placed widget; cheap no-op when none is on the home screen. */
        suspend fun refresh(context: Context) {
            runCatching { RenewalsWidget().updateAll(context) }
        }
    }
}

class RenewalsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RenewalsWidget()
}
