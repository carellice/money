package com.money.app.ai

import com.money.app.data.CategoryEntity
import com.money.app.data.MoneyRepository
import com.money.app.data.RecurringEntity
import com.money.app.data.TransactionEntity
import com.money.app.data.TxType
import com.money.app.domain.Dates
import com.money.app.domain.Money
import com.money.app.domain.MonthReport
import com.money.app.domain.ReportBuilder
import com.money.app.domain.percent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

data class Commentary(val text: String, val upToDate: Boolean)

/** Everything the app asks the model: month commentary, chat answers, category guesses. */
class AiAssistant(
    private val repo: MoneyRepository,
    val store: AiConfigStore,
    private val cacheDir: File,
    private val client: WorkersAiClient = WorkersAiClient(),
) {
    private val system = """
        Sei Money, l'assistente dentro l'app di finanza personale "Money". L'utente è italiano.
        Rispondi sempre in italiano, con tono amichevole, concreto e frasi brevi.
        Usa solo i dati forniti: se un'informazione manca dillo, non inventare numeri.
        Scrivi gli importi in euro nel formato italiano (es. 1.240,50 €).
        Parla di budget, abitudini di spesa e risparmio; non consigliare investimenti specifici
        (azioni, criptovalute, fondi): in quel caso suggerisci di sentire un consulente abilitato.
        Formattazione: testo semplice, al massimo **grassetto** ed elenchi con "- ".
    """.trimIndent()

    private fun config(): AiConfig = store.config.value ?: throw AiException("Configura Cloudflare Workers AI nelle impostazioni")

    suspend fun testConnection(): String =
        client.complete(config(), listOf(ChatMessage("user", "Rispondi solo con: Ciao, sono pronto!")), maxTokens = 30)

    suspend fun transcribe(wav: ByteArray): String = client.transcribe(config(), wav)

    // --- month commentary ---------------------------------------------------------------------

    private fun cacheFile(month: YearMonth) = File(File(cacheDir, "ai").apply { mkdirs() }, "commentary-$month.txt")

    private fun signature(r: MonthReport) =
        "${r.transactions.size}|${r.income}|${r.expense}|${r.transactions.maxOfOrNull { it.createdAt } ?: 0}"

    /** Last saved commentary, flagged stale when the month's movements changed since. */
    suspend fun cachedCommentary(month: YearMonth): Commentary? = withContext(Dispatchers.IO) {
        val file = cacheFile(month)
        if (!file.exists()) return@withContext null
        val (sig, text) = file.readText().split("\n", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        Commentary(text, sig == signature(report(month)))
    }

    suspend fun monthCommentary(month: YearMonth): String {
        val r = report(month)
        if (r.isEmpty) throw AiException("Nessun movimento in questo mese")
        val prompt = """
            Ecco il resoconto di ${Dates.monthYear(month)}.

            ${describe(r)}

            Scrivi un commento sul mese per l'utente:
            1. un paragrafo di 2-3 frasi su com'è andata, citando i numeri più importanti;
            2. "**Da tenere d'occhio**" con 1-2 punti;
            3. "**Consigli**" con 2-3 consigli concreti e misurabili per il mese prossimo (es. "tieni il delivery sotto 60 €").
            Massimo 170 parole.
        """.trimIndent()
        val text = client.complete(config(), listOf(ChatMessage("system", system), ChatMessage("user", prompt)))
        withContext(Dispatchers.IO) { cacheFile(month).writeText(signature(r) + "\n" + text) }
        return text
    }

    // --- chat ---------------------------------------------------------------------------------

    suspend fun ask(history: List<ChatMessage>, question: String): String {
        val data = chatContext()
        val messages = buildList {
            add(ChatMessage("system", "$system\n\nDATI DELL'UTENTE (oggi è ${Dates.numeric(LocalDate.now())}):\n$data"))
            addAll(history.takeLast(10))
            add(ChatMessage("user", question))
        }
        return client.complete(config(), messages, maxTokens = 900)
    }

    private suspend fun chatContext(): String {
        val today = LocalDate.now()
        val from = YearMonth.from(today).minusMonths(11).atDay(1)
        val txs = repo.transactionsBetweenOnce(from, today.plusYears(1))
        val cats = repo.allCategories().associateBy { it.id }
        val recurring = repo.recurring.first().filter { it.active }
        val sb = StringBuilder()

        sb.appendLine("## Totali per mese (ultimi 12 mesi)")
        txs.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.date)) }.toSortedMap().forEach { (m, list) ->
            val inc = list.filter { it.type == TxType.INCOME }.sumOf { it.amountCents }
            val exp = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }
            sb.appendLine("$m: entrate ${Money.format(inc)}, uscite ${Money.format(exp)}")
            list.groupBy { it.type to it.categoryId }.forEach { (key, l) ->
                val subs = l.groupBy { it.subcategoryId }.mapNotNull { (sub, sl) ->
                    sub?.let { "${cats[it]?.name} ${Money.short(sl.sumOf { t -> t.amountCents })}" }
                }
                sb.append("  - ${if (key.first == TxType.INCOME) "entrata" else "spesa"} ${cats[key.second]?.name}: ${Money.format(l.sumOf { it.amountCents })}")
                if (subs.isNotEmpty()) sb.append(" (${subs.joinToString(", ")})")
                sb.appendLine()
            }
        }

        val recentFrom = YearMonth.from(today).minusMonths(1).atDay(1).toEpochDay()
        sb.appendLine().appendLine("## Movimenti del mese corrente e precedente")
        txs.filter { it.date >= recentFrom }.sortedBy { it.date }.takeLast(300).forEach { sb.appendLine(line(it, cats)) }

        if (recurring.isNotEmpty()) {
            sb.appendLine().appendLine("## Spese fisse e abbonamenti attivi")
            recurring.forEach { sb.appendLine(recurringLine(it, cats)) }
        }
        val budgets = cats.values.filter { it.budgetCents != null }
        if (budgets.isNotEmpty()) {
            sb.appendLine().appendLine("## Budget mensili")
            budgets.forEach { sb.appendLine("- ${it.name}: ${Money.format(it.budgetCents!!)}") }
        }
        return sb.toString()
    }

    // --- category suggestion ------------------------------------------------------------------

    /** Category for a free text description: past movements with the same note first, then the model. */
    suspend fun suggestCategory(note: String, type: TxType): Pair<Long, Long?>? {
        val needle = note.trim().lowercase()
        if (needle.isEmpty()) return null
        val cats = repo.allCategories()
        val valid = cats.filter { it.type == type }.map { it.id }.toSet()
        val past = repo.transactionsBetweenOnce(LocalDate.now().minusYears(2), LocalDate.now().plusYears(1))
            .filter { it.type == type && it.note.trim().lowercase() == needle && it.categoryId in valid }
        past.groupBy { it.categoryId to it.subcategoryId }.maxByOrNull { it.value.size }?.let { return it.key }

        val config = store.config.value ?: return null
        val tops = cats.filter { it.type == type && it.parentId == null }
        val list = tops.joinToString("\n") { top ->
            val subs = cats.filter { it.parentId == top.id }
            "${top.id}: ${top.name}" + if (subs.isEmpty()) "" else subs.joinToString("") { "\n   ${it.id}: ${top.name} > ${it.name}" }
        }
        val prompt = """
            Classifica questa ${if (type == TxType.EXPENSE) "spesa" else "entrata"}: "$note".
            Categorie disponibili (id: nome):
            $list
            Rispondi SOLO con JSON: {"categoryId": <id categoria principale>, "subcategoryId": <id sottocategoria o null>}
        """.trimIndent()
        val answer = client.complete(config, listOf(ChatMessage("user", prompt)), maxTokens = 60, temperature = 0.0)
        val json = Regex("\\{[^{}]*\\}").find(answer)?.value ?: return null
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val category = obj.optLong("categoryId", -1).takeIf { id -> tops.any { it.id == id } } ?: return null
        val sub = if (obj.isNull("subcategoryId")) null
        else obj.optLong("subcategoryId", -1).takeIf { id -> cats.any { it.id == id && it.parentId == category } }
        return category to sub
    }

    // --- helpers ------------------------------------------------------------------------------

    suspend fun report(month: YearMonth): MonthReport {
        val txs = repo.transactionsBetweenOnce(month.minusMonths(5).atDay(1), month.atEndOfMonth())
        return ReportBuilder.build(month, txs, repo.allCategories())
    }

    private fun describe(r: MonthReport): String = buildString {
        appendLine("Entrate: ${Money.format(r.income)} (mese prima ${Money.format(r.prevIncome)})")
        appendLine("Uscite: ${Money.format(r.expense)} (mese prima ${Money.format(r.prevExpense)})")
        appendLine("Saldo: ${Money.format(r.balance)}" + (r.savingsRate?.let { ", tasso di risparmio ${percent(it)}" } ?: ""))
        appendLine("Spese fisse: ${Money.format(r.fixedExpense)}, media giornaliera ${Money.format(r.avgDailyExpense)}")
        r.pagella?.let { appendLine("Pagella dell'app: ${it.grade.letter} (${it.score}/100)") }
        appendLine("Spese per categoria:")
        r.expenseSlices.forEach { s ->
            append("- ${s.category.name}: ${Money.format(s.cents)} (${percent(s.share)}), mese prima ${Money.format(s.prevCents)}")
            if (s.subs.size > 1) append("; dettaglio: " + s.subs.joinToString(", ") { "${it.name} ${Money.short(it.cents)}" })
            appendLine()
        }
        if (r.incomeSlices.isNotEmpty()) appendLine("Entrate per fonte: " + r.incomeSlices.joinToString(", ") { "${it.category.name} ${Money.short(it.cents)}" })
        if (r.budgets.isNotEmpty()) appendLine("Budget: " + r.budgets.joinToString(", ") { "${it.category.name} ${Money.short(it.spent)} su ${Money.short(it.budget)}" })
        appendLine("Spese più grandi:")
        r.top.forEach { appendLine(line(it, r.categories)) }
    }

    private fun line(tx: TransactionEntity, cats: Map<Long, CategoryEntity>): String {
        val category = listOfNotNull(cats[tx.categoryId]?.name, tx.subcategoryId?.let { cats[it]?.name }).joinToString(" > ")
        val sign = if (tx.type == TxType.INCOME) "+" else "-"
        val note = tx.note.takeIf { it.isNotBlank() }?.let { " \"$it\"" } ?: ""
        val fixed = if (tx.recurringId != null) " [fissa]" else ""
        return "- ${LocalDate.ofEpochDay(tx.date)} $sign${Money.format(tx.amountCents)} $category$note (${tx.payment.label})$fixed"
    }

    private fun recurringLine(item: RecurringEntity, cats: Map<Long, CategoryEntity>) =
        "- ${item.name}: ${Money.format(item.amountCents)} ${item.frequency.label.lowercase()}, categoria ${cats[item.categoryId]?.name}, " +
            "prossimo addebito ${LocalDate.ofEpochDay(item.nextDate)}"
}
