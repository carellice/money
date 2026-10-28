package com.money.app.ui.report

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidPath
import androidx.core.content.res.ResourcesCompat
import com.money.app.R
import com.money.app.data.Palette
import com.money.app.data.TxType
import com.money.app.domain.Dates
import com.money.app.domain.Money
import com.money.app.domain.MonthReport
import com.money.app.domain.percent
import com.money.app.ui.ImageStore
import com.money.app.ui.theme.BlobSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.LocalDate

private const val INK = 0xFF15121F.toInt()
private const val INK_SOFT = 0xFF5E5870.toInt()
private const val PAPER = 0xFFF6F1EA.toInt()
private const val LINE = 0x1A000000

/** Draws the monthly report as a multi page A4 PDF and as a social card image. */
class ReportExport(private val context: Context) {
    private val typeface: Typeface = ResourcesCompat.getFont(context, R.font.bricolage) ?: Typeface.DEFAULT

    private fun paint(size: Float, weight: Int = 400, color: Int = INK, opsz: Int = 14) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = this@ReportExport.typeface
        textSize = size
        this.color = color
        fontVariationSettings = "'wght' $weight, 'opsz' $opsz"
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun blob(canvas: Canvas, seed: Int, cx: Float, cy: Float, w: Float, h: Float, color: Int) {
        val path = BlobSpec(seed, amplitude = 0.08f).path(Size(w, h), 1.3f).asAndroidPath()
        path.offset(cx - w / 2, cy - h / 2)
        canvas.drawPath(path, fill(color))
    }

    private fun centered(canvas: Canvas, text: String, cx: Float, baseline: Float, paint: Paint) {
        canvas.drawText(text, cx - paint.measureText(text) / 2, baseline, paint)
    }

    // --- PDF ----------------------------------------------------------------------------------

    suspend fun writePdf(report: MonthReport, out: OutputStream) = withContext(Dispatchers.Default) {
        val doc = PdfDocument()
        Pdf(doc, report).render()
        doc.writeTo(out)
        doc.close()
    }

    suspend fun pdfFile(report: MonthReport): File = withContext(Dispatchers.IO) {
        val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, "Money-${report.month}.pdf")
        file.outputStream().use { writePdf(report, it) }
        file
    }

    private inner class Pdf(private val doc: PdfDocument, private val r: MonthReport) {
        private val width = 595
        private val height = 842
        private val margin = 40f
        private var pageNo = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = 0f

        fun render() {
            newPage()
            header()
            tiles()
            pagella()
            insights()
            categories()
            budgets()
            trend()
            movements()
            finishPage()
        }

        private fun newPage() {
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
            canvas = page.canvas
            canvas.drawColor(PAPER)
            y = margin
        }

        private fun finishPage() {
            val footer = paint(8f, color = INK_SOFT)
            canvas.drawText("Money · ${Dates.monthYear(r.month)}", margin, height - 22f, footer)
            val num = "$pageNo"
            canvas.drawText(num, width - margin - footer.measureText(num), height - 22f, footer)
            doc.finishPage(page)
        }

        private fun ensure(space: Float) {
            if (y + space > height - 50f) {
                finishPage()
                newPage()
            }
        }

        private fun sectionTitle(text: String) {
            ensure(40f)
            y += 14f
            canvas.drawText(text, margin, y + 14f, paint(15f, 700, opsz = 36))
            y += 26f
        }

        private fun paragraph(text: String, size: Float = 10f, color: Int = INK, x: Float = margin, w: Float = width - 2 * margin): Float {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint(size, color = color), w.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
            ensure(layout.height.toFloat())
            canvas.save()
            canvas.translate(x, y)
            layout.draw(canvas)
            canvas.restore()
            return layout.height.toFloat()
        }

        private fun header() {
            val h = 150f
            canvas.drawRoundRect(RectF(margin, y, width - margin, y + h), 28f, 28f, fill(INK))
            canvas.save()
            canvas.clipRect(margin, y, width - margin, y + h)
            blob(canvas, 11, width - 120f, y + 30f, 220f, 200f, Palette.LILAC.toInt())
            blob(canvas, 21, width - 230f, y + h + 10f, 140f, 120f, Palette.ROSE.toInt())
            canvas.restore()
            canvas.drawText("REPORT MENSILE", margin + 24f, y + 36f, paint(9f, 600, 0xFFB9B2CC.toInt()))
            canvas.drawText(Dates.monthYear(r.month), margin + 24f, y + 76f, paint(32f, 800, 0xFFF4EFFA.toInt(), opsz = 72))
            canvas.drawText(
                "Generato il ${Dates.numeric(LocalDate.now())} · ${r.transactions.size} movimenti",
                margin + 24f, y + 100f, paint(9f, 500, 0xFFB9B2CC.toInt()),
            )
            r.pagella?.let { p ->
                val cx = width - margin - 70f
                val cy = y + h / 2
                blob(canvas, 5, cx, cy, 104f, 104f, p.grade.color.toInt())
                centered(canvas, p.grade.letter, cx, cy + 20f, paint(54f, 800, INK, 72))
            }
            y += h + 18f
        }

        private fun tiles() {
            val items = listOf(
                Triple("Entrate", Money.format(r.income), Palette.MINT),
                Triple("Uscite", Money.format(r.expense), Palette.CORAL),
                Triple("Saldo", Money.format(r.balance), Palette.LILAC),
                Triple("Risparmio", r.savingsRate?.let { percent(it) } ?: "—", Palette.BUTTER),
            )
            val gap = 10f
            val w = (width - 2 * margin - gap * 3) / 4
            items.forEachIndexed { i, (label, value, color) ->
                val x = margin + i * (w + gap)
                canvas.drawRoundRect(RectF(x, y, x + w, y + 64f), 18f, 18f, fill(color.toInt()))
                canvas.drawText(label, x + 12f, y + 22f, paint(9f, 600, INK))
                val vp = paint(14f, 700, INK, 36)
                canvas.drawText(TextUtils.ellipsize(value, vp, w - 20f, TextUtils.TruncateAt.END).toString(), x + 12f, y + 46f, vp)
            }
            y += 80f
        }

        private fun pagella() {
            val p = r.pagella ?: return
            sectionTitle("Pagella: ${p.grade.letter} · ${p.grade.title} (${p.score}/100)")
            y += paragraph(p.summary, 11f) + 4f
        }

        private fun insights() {
            if (r.insights.isEmpty()) return
            sectionTitle("Cosa è successo")
            r.insights.forEach { insight ->
                val color = when (insight.positive) {
                    true -> Palette.MINT
                    false -> Palette.CORAL
                    null -> Palette.LILAC
                }.toInt()
                ensure(18f)
                canvas.drawCircle(margin + 5f, y + 7f, 4.5f, fill(color))
                y += paragraph(insight.text, 10f, x = margin + 16f, w = width - 2 * margin - 16f) + 5f
            }
        }

        private fun categories() {
            if (r.expenseSlices.isEmpty()) return
            sectionTitle("Spese per categoria")
            val rows = r.expenseSlices
            val donut = 150f
            ensure(donut + 10f)
            // Donut
            val rect = RectF(margin + 10f, y, margin + 10f + donut, y + donut)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 26f }
            rect.inset(13f, 13f)
            var start = -90f
            rows.forEach { s ->
                stroke.color = s.category.color.toInt()
                val sweep = s.share * 360f
                canvas.drawArc(rect, start, sweep, false, stroke)
                start += sweep
            }
            centered(canvas, Money.short(r.expense), rect.centerX(), rect.centerY() + 5f, paint(13f, 700, INK, 36))
            // Table
            val tx = margin + donut + 40f
            val right = width - margin
            var ty = y + 4f
            val head = paint(8f, 600, INK_SOFT)
            canvas.drawText("CATEGORIA", tx, ty + 8f, head)
            drawRight("IMPORTO", right - 110f, ty + 8f, head)
            drawRight("%", right - 60f, ty + 8f, head)
            drawRight("VS PREC.", right, ty + 8f, head)
            ty += 16f
            val tableRows = rows.take(9)
            tableRows.forEach { s ->
                canvas.drawCircle(tx + 4f, ty + 6f, 4f, fill(s.category.color.toInt()))
                canvas.drawText(s.category.name, tx + 14f, ty + 10f, paint(10f, 500))
                drawRight(Money.format(s.cents), right - 110f, ty + 10f, paint(10f, 600))
                drawRight(percent(s.share), right - 60f, ty + 10f, paint(10f))
                val delta = if (s.prevCents > 0) s.cents - s.prevCents else null
                drawRight(
                    delta?.let { (if (it > 0) "+" else "") + Money.short(it) } ?: "nuova",
                    right, ty + 10f,
                    paint(9f, 500, if ((delta ?: 0) > 0) 0xFFD14B3A.toInt() else 0xFF1E8A63.toInt()),
                )
                ty += 16f
            }
            y = maxOf(y + donut, ty) + 6f
            // Subcategory detail
            rows.forEach { s ->
                if (s.subs.size <= 1) return@forEach
                ensure(16f)
                val text = "${s.category.name}: " + s.subs.joinToString(" · ") { "${it.name} ${Money.short(it.cents)}" }
                y += paragraph(text, 8.5f, INK_SOFT) + 3f
            }
        }

        private fun budgets() {
            if (r.budgets.isEmpty()) return
            sectionTitle("Budget")
            r.budgets.forEach { b ->
                ensure(26f)
                canvas.drawText(b.category.name, margin, y + 10f, paint(10f, 600))
                drawRight("${Money.short(b.spent)} / ${Money.short(b.budget)}", width - margin, y + 10f, paint(10f))
                val barY = y + 16f
                canvas.drawRoundRect(RectF(margin, barY, width - margin, barY + 6f), 3f, 3f, fill(LINE))
                val color = if (b.ratio > 1f) Palette.CORAL else if (b.ratio > 0.8f) Palette.BUTTER else Palette.MINT
                val w = (width - 2 * margin) * b.ratio.coerceIn(0f, 1f)
                canvas.drawRoundRect(RectF(margin, barY, margin + w, barY + 6f), 3f, 3f, fill(color.toInt()))
                y += 30f
            }
        }

        private fun trend() {
            sectionTitle("Ultimi ${r.trend.size} mesi")
            val h = 110f
            ensure(h + 30f)
            val max = (r.trend.maxOfOrNull { maxOf(it.income, it.expense) } ?: 0L).coerceAtLeast(1L)
            val slot = (width - 2 * margin) / r.trend.size
            r.trend.forEach { point ->
                val i = r.trend.indexOf(point)
                val x = margin + i * slot + slot / 2
                val ih = h * point.income / max
                val eh = h * point.expense / max
                canvas.drawRoundRect(RectF(x - 14f, y + h - ih, x - 2f, y + h), 4f, 4f, fill(Palette.MINT.toInt()))
                canvas.drawRoundRect(RectF(x + 2f, y + h - eh, x + 14f, y + h), 4f, 4f, fill(Palette.CORAL.toInt()))
                centered(canvas, Dates.monthShort(point.month), x, y + h + 14f, paint(8.5f, if (point.month == r.month) 700 else 400))
            }
            y += h + 26f
            canvas.drawCircle(margin + 4f, y, 4f, fill(Palette.MINT.toInt()))
            canvas.drawText("Entrate", margin + 12f, y + 3f, paint(8.5f))
            canvas.drawCircle(margin + 64f, y, 4f, fill(Palette.CORAL.toInt()))
            canvas.drawText("Uscite", margin + 72f, y + 3f, paint(8.5f))
            y += 12f
        }

        private fun movements() {
            if (r.transactions.isEmpty()) return
            sectionTitle("Tutti i movimenti")
            val cols = floatArrayOf(margin, margin + 62f, margin + 180f, margin + 380f)
            val right = width - margin
            fun headerRow() {
                val head = paint(8f, 600, INK_SOFT)
                canvas.drawText("DATA", cols[0], y + 8f, head)
                canvas.drawText("CATEGORIA", cols[1], y + 8f, head)
                canvas.drawText("NOTA", cols[2], y + 8f, head)
                canvas.drawText("METODO", cols[3], y + 8f, head)
                drawRight("IMPORTO", right, y + 8f, head)
                y += 16f
            }
            headerRow()
            r.transactions.sortedWith(compareBy({ it.date }, { it.createdAt })).forEach { tx ->
                if (y + 16f > height - 50f) {
                    finishPage()
                    newPage()
                    headerRow()
                }
                val cat = r.categories[tx.categoryId]
                val sub = tx.subcategoryId?.let { r.categories[it] }
                val body = paint(9f)
                canvas.drawText(Dates.numeric(LocalDate.ofEpochDay(tx.date)), cols[0], y + 9f, body)
                canvas.drawText(ellipsize(listOfNotNull(cat?.name, sub?.name).joinToString(" · "), body, cols[2] - cols[1] - 8f), cols[1], y + 9f, body)
                canvas.drawText(ellipsize(tx.note, body, cols[3] - cols[2] - 8f), cols[2], y + 9f, body)
                canvas.drawText(tx.payment.label, cols[3], y + 9f, paint(9f, color = INK_SOFT))
                val income = tx.type == TxType.INCOME
                drawRight(
                    Money.format(if (income) tx.amountCents else -tx.amountCents, signed = true),
                    right, y + 9f, paint(9f, 600, if (income) 0xFF1E8A63.toInt() else INK),
                )
                canvas.drawLine(margin, y + 14f, right, y + 14f, Paint().apply { color = LINE })
                y += 16f
            }
        }

        private fun ellipsize(text: String, paint: TextPaint, w: Float) =
            TextUtils.ellipsize(text, paint, w, TextUtils.TruncateAt.END).toString()

        private fun drawRight(text: String, right: Float, baseline: Float, paint: Paint) {
            canvas.drawText(text, right - paint.measureText(text), baseline, paint)
        }
    }

    // --- Share card -----------------------------------------------------------------------------

    suspend fun cardFile(report: MonthReport): File = withContext(Dispatchers.IO) {
        val w = 1080
        val h = 1350
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(INK)
        blob(canvas, 11, w * 0.95f, h * 0.05f, 760f, 700f, Palette.LILAC.toInt())
        blob(canvas, 21, w * 0.0f, h * 0.62f, 520f, 520f, Palette.MINT.toInt() and 0x55FFFFFF)
        blob(canvas, 33, w * 1.0f, h * 0.98f, 600f, 560f, Palette.ROSE.toInt() and 0x66FFFFFF)

        val light = 0xFFF4EFFA.toInt()
        val soft = 0xFFB9B2CC.toInt()
        canvas.drawText("MONEY", 80f, 130f, paint(34f, 700, soft))
        canvas.drawText(Dates.monthName(report.month), 80f, 250f, paint(120f, 800, light, 72))
        canvas.drawText("${report.month.year}", 84f, 320f, paint(52f, 600, soft, 72))

        report.pagella?.let { p ->
            blob(canvas, 5, 820f, 300f, 300f, 300f, p.grade.color.toInt())
            centered(canvas, p.grade.letter, 820f, 360f, paint(180f, 800, INK, 72))
            centered(canvas, p.grade.title, 820f, 500f, paint(38f, 700, light, 36))
        }

        canvas.drawText("saldo del mese", 80f, 560f, paint(36f, 500, soft))
        canvas.drawText(Money.format(report.balance), 80f, 680f, paint(110f, 800, light, 72))

        val stat = paint(40f, 700, INK, 36)
        canvas.drawRoundRect(RectF(80f, 740f, 520f, 880f), 70f, 70f, fill(Palette.MINT.toInt()))
        canvas.drawText("entrate", 120f, 795f, paint(28f, 500, INK))
        canvas.drawText(Money.short(report.income), 120f, 852f, stat)
        canvas.drawRoundRect(RectF(560f, 740f, 1000f, 880f), 70f, 70f, fill(Palette.CORAL.toInt()))
        canvas.drawText("uscite", 600f, 795f, paint(28f, 500, INK))
        canvas.drawText(Money.short(report.expense), 600f, 852f, stat)

        var y = 960f
        report.expenseSlices.take(3).forEach { s ->
            canvas.drawText(s.category.name, 80f, y, paint(36f, 600, light))
            val pct = percent(s.share)
            val pp = paint(36f, 600, soft)
            canvas.drawText(pct, 1000f - pp.measureText(pct), y, pp)
            canvas.drawRoundRect(RectF(80f, y + 20f, 1000f, y + 44f), 12f, 12f, fill(0x22FFFFFF))
            canvas.drawRoundRect(RectF(80f, y + 20f, 80f + 920f * s.share, y + 44f), 12f, 12f, fill(s.category.color.toInt()))
            y += 110f
        }

        canvas.drawText("Il mio mese con Money", 80f, h - 70f, paint(30f, 500, soft))

        val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, "Money-${report.month}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        file
    }

    fun share(file: File, mime: String, title: String) {
        val uri = ImageStore.uriFor(context, file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
