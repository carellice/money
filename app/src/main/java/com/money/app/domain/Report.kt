package com.money.app.domain

import com.money.app.data.CategoryEntity
import com.money.app.data.Palette
import com.money.app.data.PaymentMethod
import com.money.app.data.TransactionEntity
import com.money.app.data.TxType
import com.money.app.domain.Dates.cap
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

data class SubSlice(val name: String, val cents: Long)

data class Slice(
    val category: CategoryEntity,
    val cents: Long,
    val share: Float,
    val prevCents: Long,
    val count: Int,
    val subs: List<SubSlice>,
)

data class MonthPoint(val month: YearMonth, val income: Long, val expense: Long)

data class BudgetLine(val category: CategoryEntity, val spent: Long, val budget: Long) {
    val ratio: Float get() = if (budget == 0L) 0f else spent.toFloat() / budget
}

enum class Grade(val letter: String, val title: String, val color: Long, val min: Int) {
    A("A", "Da manuale", Palette.MINT, 85),
    B("B", "Ottimo lavoro", Palette.SKY, 70),
    C("C", "Nella media", Palette.BUTTER, 55),
    D("D", "Si può fare meglio", Palette.ORANGE, 40),
    E("E", "Mese in salita", Palette.CORAL, 25),
    F("F", "SOS portafoglio", Palette.ROSE, 0);

    companion object {
        fun of(score: Int) = entries.first { score >= it.min }
    }
}

data class Pagella(val grade: Grade, val score: Int, val summary: String)

/** [positive]: true good news, false warning, null neutral. */
data class Insight(val text: String, val positive: Boolean?)

data class MonthReport(
    val month: YearMonth,
    val income: Long,
    val expense: Long,
    val savingsRate: Float?,
    val avgDailyExpense: Long,
    val expenseSlices: List<Slice>,
    val incomeSlices: List<Slice>,
    /** Expense per day of month, index 0 = day 1. */
    val daily: List<Long>,
    val fixedExpense: Long,
    val payments: List<Pair<PaymentMethod, Long>>,
    val top: List<TransactionEntity>,
    val prevIncome: Long,
    val prevExpense: Long,
    val trend: List<MonthPoint>,
    val budgets: List<BudgetLine>,
    val pagella: Pagella?,
    val insights: List<Insight>,
    val transactions: List<TransactionEntity>,
    val categories: Map<Long, CategoryEntity>,
) {
    val balance: Long get() = income - expense
    val variableExpense: Long get() = expense - fixedExpense
    val isEmpty: Boolean get() = transactions.isEmpty()
}

object ReportBuilder {

    /**
     * @param all movements covering at least [month] and the months before it used for the trend.
     */
    fun build(
        month: YearMonth,
        all: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        trendMonths: Int = 6,
        today: LocalDate = LocalDate.now(),
    ): MonthReport {
        val byId = categories.associateBy { it.id }
        val byMonth = all.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.date)) }
        val current = byMonth[month].orEmpty()
        val previous = byMonth[month.minusMonths(1)].orEmpty()

        val expenses = current.filter { it.type == TxType.EXPENSE }
        val incomes = current.filter { it.type == TxType.INCOME }
        val income = incomes.sumOf { it.amountCents }
        val expense = expenses.sumOf { it.amountCents }
        val prevIncome = previous.filter { it.type == TxType.INCOME }.sumOf { it.amountCents }
        val prevExpense = previous.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }

        val expenseSlices = slices(expenses, previous.filter { it.type == TxType.EXPENSE }, byId)
        val incomeSlices = slices(incomes, previous.filter { it.type == TxType.INCOME }, byId)

        val daily = MutableList(month.lengthOfMonth()) { 0L }
        expenses.forEach { daily[LocalDate.ofEpochDay(it.date).dayOfMonth - 1] += it.amountCents }

        val elapsedDays = when {
            month == YearMonth.from(today) -> today.dayOfMonth
            month.isAfter(YearMonth.from(today)) -> 1
            else -> month.lengthOfMonth()
        }

        val budgets = categories
            .filter { it.parentId == null && it.type == TxType.EXPENSE && (it.budgetCents ?: 0) > 0 }
            .map { cat -> BudgetLine(cat, expenses.filter { it.categoryId == cat.id }.sumOf { it.amountCents }, cat.budgetCents!!) }
            .sortedByDescending { it.ratio }

        val trend = (trendMonths - 1 downTo 0).map { back ->
            val m = month.minusMonths(back.toLong())
            val list = byMonth[m].orEmpty()
            MonthPoint(
                m,
                list.filter { it.type == TxType.INCOME }.sumOf { it.amountCents },
                list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents },
            )
        }

        val savingsRate = if (income > 0) (income - expense).toFloat() / income else null
        val payments = expenses.groupBy { it.payment }
            .map { (method, list) -> method to list.sumOf { it.amountCents } }
            .sortedByDescending { it.second }

        val report = MonthReport(
            month = month,
            income = income,
            expense = expense,
            savingsRate = savingsRate,
            avgDailyExpense = expense / elapsedDays.coerceAtLeast(1),
            expenseSlices = expenseSlices,
            incomeSlices = incomeSlices,
            daily = daily,
            fixedExpense = expenses.filter { it.recurringId != null }.sumOf { it.amountCents },
            payments = payments,
            top = expenses.sortedByDescending { it.amountCents }.take(5),
            prevIncome = prevIncome,
            prevExpense = prevExpense,
            trend = trend,
            budgets = budgets,
            pagella = null,
            insights = emptyList(),
            transactions = current,
            categories = byId,
        )
        return report.copy(pagella = pagella(report), insights = insights(report))
    }

    private fun slices(
        list: List<TransactionEntity>,
        previous: List<TransactionEntity>,
        byId: Map<Long, CategoryEntity>,
    ): List<Slice> {
        val total = list.sumOf { it.amountCents }.coerceAtLeast(1)
        val prevByCat = previous.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountCents } }
        return list.groupBy { it.categoryId }.mapNotNull { (catId, items) ->
            val category = byId[catId] ?: return@mapNotNull null
            val cents = items.sumOf { it.amountCents }
            val subs = items.groupBy { it.subcategoryId }
                .map { (subId, l) -> SubSlice(subId?.let { byId[it]?.name } ?: "Generico", l.sumOf { it.amountCents }) }
                .sortedByDescending { it.cents }
            Slice(category, cents, cents.toFloat() / total, prevByCat[catId] ?: 0, items.size, subs)
        }.sortedByDescending { it.cents }
    }

    private fun pagella(r: MonthReport): Pagella? {
        if (r.isEmpty) return null
        var points = 0f
        var weight = 0f

        // Savings: 0% or less -> 0, 30% or more -> full marks.
        weight += 50f
        points += when {
            r.income == 0L -> 0f
            else -> 50f * ((r.savingsRate ?: 0f) / 0.30f).coerceIn(0f, 1f)
        }

        if (r.budgets.isNotEmpty()) {
            weight += 30f
            val respected = r.budgets.sumOf { line ->
                // Full marks under budget, linearly down to zero at +50%.
                (1.0 - ((line.ratio - 1.0) / 0.5).coerceIn(0.0, 1.0))
            }
            points += 30f * (respected / r.budgets.size).toFloat()
        }

        if (r.prevExpense > 0) {
            weight += 20f
            val change = (r.expense - r.prevExpense).toFloat() / r.prevExpense
            // -10% or better -> full marks, +20% or worse -> zero.
            points += 20f * (1f - ((change + 0.10f) / 0.30f)).coerceIn(0f, 1f)
        }

        val score = (points / weight * 100).roundToInt().coerceIn(0, 100)
        val grade = Grade.of(score)
        val summary = when (grade) {
            Grade.A -> "Mese esemplare: hai messo da parte tanto e tenuto le spese sotto controllo."
            Grade.B -> "Bel mese: risparmi solidi, qualche margine di miglioramento."
            Grade.C -> "Mese equilibrato, ma i risparmi potrebbero crescere."
            Grade.D -> "Le spese hanno corso un po' troppo: occhio alle categorie in rosso."
            Grade.E -> "Hai speso quasi tutto quello che è entrato. Il prossimo mese si recupera."
            Grade.F -> "Le uscite hanno superato le entrate. Serve una stretta."
        }
        return Pagella(grade, score, summary)
    }

    private fun insights(r: MonthReport): List<Insight> {
        if (r.isEmpty) return emptyList()
        val out = mutableListOf<Insight>()
        val prevName = Dates.monthName(r.month.minusMonths(1)).lowercase(ItalianLocale)

        when {
            r.income == 0L && r.expense > 0 -> out += Insight("Nessuna entrata registrata: le spese pesano tutte sui risparmi.", false)
            r.balance >= 0 && r.income > 0 ->
                out += Insight("Hai messo da parte ${Money.format(r.balance)}, il ${percent(r.savingsRate ?: 0f)} di quello che hai guadagnato.", true)
            r.balance < 0 -> out += Insight("Hai speso ${Money.format(-r.balance)} più di quanto è entrato.", false)
        }

        r.expenseSlices.firstOrNull()?.let { top ->
            out += Insight("La voce più pesante è ${top.category.name}: ${percent(top.share)} delle uscite.", null)
        }

        val changes = r.expenseSlices.filter { it.prevCents > 0 }
            .map { it to (it.cents - it.prevCents) }
            .filter { (s, delta) -> abs(delta) >= 2000 && abs(delta).toFloat() / s.prevCents >= 0.10f }
        changes.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.let { (s, delta) ->
            out += Insight("${s.category.name} è salita del ${percent(delta.toFloat() / s.prevCents)} rispetto a $prevName (+${Money.short(delta)}).", false)
        }
        changes.minByOrNull { it.second }?.takeIf { it.second < 0 }?.let { (s, delta) ->
            out += Insight("Hai ridotto ${s.category.name} del ${percent(-delta.toFloat() / s.prevCents)} rispetto a $prevName (${Money.short(delta)}).", true)
        }

        if (r.budgets.isNotEmpty()) {
            val over = r.budgets.filter { it.spent > it.budget }
            if (over.isEmpty()) out += Insight("Tutti i budget rispettati. Grande!", true)
            over.take(2).forEach {
                out += Insight("Budget di ${it.category.name} sforato di ${Money.short(it.spent - it.budget)}.", false)
            }
        }

        if (r.expense > 0 && r.fixedExpense > 0) {
            out += Insight("Le spese fisse valgono il ${percent(r.fixedExpense.toFloat() / r.expense)} delle uscite.", null)
        }

        r.daily.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.let { (index, cents) ->
            val day = r.month.atDay(index + 1)
            out += Insight("Il giorno più caro è stato ${Dates.weekday(day)} ${day.dayOfMonth} (${Money.short(cents)}).", null)
        }

        val weekend = r.transactions.filter {
            it.type == TxType.EXPENSE && LocalDate.ofEpochDay(it.date).dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }.sumOf { it.amountCents }
        if (r.expense > 0 && weekend.toFloat() / r.expense > 0.4f) {
            out += Insight("Il ${percent(weekend.toFloat() / r.expense)} delle spese è nel weekend.", null)
        }
        return out
    }

    fun headline(month: YearMonth): String = Dates.monthYear(month).cap()
}
