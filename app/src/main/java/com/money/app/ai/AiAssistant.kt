package com.money.app.ai

import com.money.app.data.CategoryEntity
import com.money.app.data.MemberEntity
import com.money.app.data.MoneyRepository
import com.money.app.data.PaymentChoice
import com.money.app.data.PaymentMethod
import com.money.app.data.Payments
import com.money.app.data.RecurringKind
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

/** A movement the chat prepared from what the user said, with a one line [summary] for the confirm card. */
data class Draft(val tx: TransactionEntity, val summary: String)

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

    /** Answer to [question]; carries a [Draft] when the user asked to add a movement and nothing is missing. */
    suspend fun ask(history: List<ChatMessage>, question: String): ChatMessage {
        val data = chatContext()
        val cats = repo.allCategories()
        val payments = repo.payments()
        val members = repo.members.first()
        val messages = buildList {
            add(ChatMessage("system", "$system\n\n${addRules(cats, payments, members)}\n\nDATI DELL'UTENTE (oggi è ${Dates.numeric(LocalDate.now())}):\n$data"))
            addAll(history.takeLast(10))
            add(ChatMessage("user", question))
        }
        val answer = client.complete(config(), messages, maxTokens = 900)
        return ChatMessage("assistant", answer, draft = parseDraft(answer, cats, payments, members))
    }

    /** @return false when the server didn't save it. */
    suspend fun save(draft: Draft): Boolean = repo.saveTransaction(draft.tx) >= 0

    private fun addRules(cats: List<CategoryEntity>, payments: Payments, members: List<MemberEntity>): String {
        val methods = payments.builtIns.joinToString(", ") { "${it.name}: ${it.label}" } +
            payments.custom.values.joinToString("") { ", custom:${it.id}: ${it.name}" }
        return buildString {
            appendLine("## Aggiungere movimenti")
            appendLine("Se l'utente ti chiede di aggiungere o registrare una spesa o un'entrata (spesso a voce, es. \"aggiungi una spesa di 15 euro che ho comprato da mangiare\"):")
            appendLine("- capisci tipo, importo, categoria (e sottocategoria se adatta), data (oggi se non la dice), metodo di pagamento (se non lo dice: CARD per le spese, TRANSFER per le entrate) e una nota breve;")
            if (members.isNotEmpty()) appendLine("- la persona è obbligatoria: se non dice chi ha pagato la spesa o di chi è l'entrata, chiediglielo e non preparare ancora il movimento;")
            appendLine("- se manca l'importo o hai dubbi veri sulla categoria, chiedi prima di preparare il movimento;")
            appendLine("- quando hai tutto, scrivi una frase breve di riepilogo e in fondo, su una riga sola, il movimento in questo formato esatto:")
            val exampleCat = cats.firstOrNull { it.type == TxType.EXPENSE && it.parentId == null }?.id ?: 1
            val exampleMember = members.firstOrNull()?.id?.toString() ?: "null"
            appendLine("<movimento>{\"tipo\":\"spesa\",\"importo\":15.00,\"categoryId\":$exampleCat,\"subcategoryId\":null,\"data\":\"${LocalDate.now()}\",\"metodo\":\"CARD\",\"memberId\":$exampleMember,\"nota\":\"pranzo\"}</movimento>")
            appendLine("- \"tipo\" è \"spesa\" o \"entrata\"; \"importo\" è un numero col punto (15.50); usa solo gli id numerici elencati qui sotto;")
            if (members.isNotEmpty()) appendLine("- \"memberId\" è l'id della persona che ha pagato o che riceve l'entrata, mai null;")
            appendLine("- non dire mai che l'hai già salvato: l'utente lo conferma con un pulsante sotto il tuo messaggio.")
            appendLine("Categorie di spesa (id: nome):")
            appendLine(categoryList(cats, TxType.EXPENSE))
            appendLine("Categorie di entrata (id: nome):")
            appendLine(categoryList(cats, TxType.INCOME))
            appendLine("Metodi di pagamento (codice: nome): $methods")
            if (members.isNotEmpty()) appendLine("Persone (id: nome): " + members.joinToString(", ") { "${it.id}: ${it.name}" })
        }
    }

    private fun parseDraft(answer: String, cats: List<CategoryEntity>, payments: Payments, members: List<MemberEntity>): Draft? {
        // The model often bends the format: code fences inside the tag, "15,50", a subcategory id as categoryId, names instead of ids.
        val raw = DRAFT.find(answer)?.groupValues?.get(1) ?: return null
        val o = runCatching {
            JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1).replace(DECIMAL_COMMA, "$1.$2"))
        }.getOrNull() ?: return null
        val cents = Math.round(amount(o.opt("importo")) * 100)
        if (cents <= 0) return null
        val said = when (o.optString("tipo").lowercase().trim()) {
            "spesa", "uscita", "expense" -> TxType.EXPENSE
            "entrata", "income" -> TxType.INCOME
            else -> null
        }
        val picked = find(cats, o.opt("categoryId"), said) ?: find(cats, o.opt("categoria"), said) ?: return null
        val category = picked.parentId?.let { p -> cats.firstOrNull { it.id == p } } ?: picked
        val type = category.type
        val sub = picked.takeIf { it.parentId != null }
            ?: find(cats, o.opt("subcategoryId"), type)?.takeIf { it.parentId == category.id }
        val member = members.firstOrNull { it.id == idOf(o.opt("memberId")) }
            ?: members.firstOrNull { it.name.equals(o.optString("memberId").trim(), true) || it.name.equals(o.optString("persona").trim(), true) }
            ?: members.singleOrNull()
        if (members.isNotEmpty() && member == null) return null
        val date = runCatching { LocalDate.parse(o.optString("data")) }.getOrDefault(LocalDate.now())
        val code = if (o.isNull("metodo")) "" else o.optString("metodo")
        val choice = code.removePrefix("custom:").toLongOrNull()?.takeIf { code.startsWith("custom:") && it in payments.custom }?.let { PaymentChoice.custom(it) }
            ?: payments.builtIns.firstOrNull { it.name == code }?.let { PaymentChoice(it) }
            ?: PaymentChoice((if (type == TxType.INCOME) PaymentMethod.TRANSFER else PaymentMethod.CARD).takeIf { it !in payments.hidden } ?: payments.builtIns.first())
        val note = if (o.isNull("nota")) "" else o.optString("nota").trim()
        val tx = TransactionEntity(
            type = type, amountCents = cents, date = date.toEpochDay(), categoryId = category.id, subcategoryId = sub?.id,
            payment = choice.method, paymentId = choice.customId, note = note, memberId = member?.id,
        )
        val summary = listOfNotNull(
            "${if (type == TxType.INCOME) "Entrata" else "Spesa"} di ${Money.format(cents)}",
            listOfNotNull(category.name, sub?.name).joinToString(" > "),
            payments.label(choice),
            member?.name,
            if (kotlin.math.abs(date.toEpochDay() - LocalDate.now().toEpochDay()) <= 1) Dates.relative(date) else Dates.short(date),
            note.takeIf { it.isNotEmpty() }?.let { "\"$it\"" },
        ).joinToString(" · ")
        return Draft(tx, summary)
    }

    private suspend fun chatContext(): String {
        val today = LocalDate.now()
        val from = YearMonth.from(today).minusMonths(11).atDay(1)
        val txs = repo.transactionsBetweenOnce(from, today.plusYears(1))
        val cats = repo.allCategories().associateBy { it.id }
        val payments = repo.payments()
        val recurring = repo.recurring.first()
        val members = repo.members.first().associate { it.id to it.name }
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

        // Totals by payment method and by person, so questions about them work beyond the last two months too.
        val methods = payments.builtIns.map { PaymentChoice(it) } +
            payments.custom.values.sortedBy { it.sortOrder }.map { PaymentChoice.custom(it.id) }
        sb.appendLine().appendLine("## Metodi di pagamento dell'utente")
        sb.appendLine(methods.joinToString(", ") { payments.label(it) })
        sb.appendLine().appendLine("## Uscite per metodo di pagamento, per mese")
        txs.filter { it.type == TxType.EXPENSE }.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.date)) }.toSortedMap().forEach { (m, list) ->
            val byMethod = list.groupBy { PaymentChoice(it.payment, it.paymentId) }
                .map { (choice, l) -> "${payments.label(choice)} ${Money.format(l.sumOf { it.amountCents })}" }
            sb.appendLine("$m: ${byMethod.joinToString(", ")}")
        }
        if (members.isNotEmpty()) {
            sb.appendLine().appendLine("## Persone della famiglia: ${members.values.joinToString(", ")}")
            sb.appendLine("Uscite per persona, per mese:")
            txs.filter { it.type == TxType.EXPENSE }.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.date)) }.toSortedMap().forEach { (m, list) ->
                val byMember = list.groupBy { it.memberId }
                    .map { (id, l) -> "${id?.let { members[it] } ?: "nessuno"} ${Money.format(l.sumOf { it.amountCents })}" }
                sb.appendLine("$m: ${byMember.joinToString(", ")}")
            }
        }

        val recentFrom = YearMonth.from(today).minusMonths(1).atDay(1).toEpochDay()
        sb.appendLine().appendLine("## Movimenti del mese corrente e precedente (data, importo, categoria, nota, metodo di pagamento, persona)")
        txs.filter { it.date >= recentFrom }.sortedBy { it.date }.takeLast(300).forEach { sb.appendLine(line(it, cats, payments, members)) }

        if (recurring.isNotEmpty()) {
            sb.appendLine().appendLine("## Spese fisse e abbonamenti")
            recurring.sortedByDescending { it.active }.forEach { sb.appendLine(recurringLine(it, cats, payments)) }
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
        val prompt = """
            Classifica questa ${if (type == TxType.EXPENSE) "spesa" else "entrata"}: "$note".
            Categorie disponibili (id: nome):
            ${categoryList(cats, type)}
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

    private fun categoryList(cats: List<CategoryEntity>, type: TxType) =
        cats.filter { it.type == type && it.parentId == null }.joinToString("\n") { top ->
            val subs = cats.filter { it.parentId == top.id }
            "${top.id}: ${top.name}" + if (subs.isEmpty()) "" else subs.joinToString("") { "\n   ${it.id}: ${top.name} > ${it.name}" }
        }

    suspend fun report(month: YearMonth): MonthReport {
        val txs = repo.transactionsBetweenOnce(month.minusMonths(5).atDay(1), month.atEndOfMonth())
        return ReportBuilder.build(month, txs, repo.allCategories(), repo.payments().custom.values.toList())
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
        r.top.forEach { appendLine(line(it, r.categories, r.paymentNames)) }
    }

    private fun line(
        tx: TransactionEntity,
        cats: Map<Long, CategoryEntity>,
        payments: Payments,
        members: Map<Long, String> = emptyMap(),
    ): String {
        val category = listOfNotNull(cats[tx.categoryId]?.name, tx.subcategoryId?.let { cats[it]?.name }).joinToString(" > ")
        val sign = if (tx.type == TxType.INCOME) "+" else "-"
        val note = tx.note.takeIf { it.isNotBlank() }?.let { " \"$it\"" } ?: ""
        val fixed = if (tx.recurringId != null) " [fissa]" else ""
        val member = tx.memberId?.let { members[it] }?.let { ", $it" } ?: ""
        return "- ${LocalDate.ofEpochDay(tx.date)} $sign${Money.format(tx.amountCents)} $category$note (${payments.label(tx)}$member)$fixed"
    }

    private fun recurringLine(item: RecurringEntity, cats: Map<Long, CategoryEntity>, payments: Payments) = buildString {
        append("- ${item.name} (${when (item.kind) { RecurringKind.SUBSCRIPTION -> "abbonamento"; RecurringKind.FIXED -> "spesa fissa"; RecurringKind.FINANCING -> "finanziamento" }}")
        item.installments?.let { append(", rate pagate ${item.installmentsPaid} su $it") }
        if (!item.active) append(", in pausa")
        append("): ${Money.format(item.amountCents)} ${item.frequency.label.lowercase()}, categoria ${cats[item.categoryId]?.name}, ")
        append("pagato con ${payments.label(PaymentChoice(item.payment, item.paymentId))}, ")
        append(if (item.autoRegister) "registrato in automatico" else "da registrare a mano")
        if (item.active) append(", prossimo addebito ${LocalDate.ofEpochDay(item.nextDate)}")
        if (item.note.isNotBlank()) append(", nota \"${item.note}\"")
    }

    companion object {
        private val DRAFT = Regex("(?s)<movimento>(.*?)(?:</movimento>|$)")
        /** 15,50 written as a bare number breaks the JSON. */
        private val DECIMAL_COMMA = Regex("(?<=:)\\s*(\\d+),(\\d{1,2})(?=\\s*[,}])")

        private fun idOf(v: Any?): Long? = when (v) {
            is Number -> v.toLong()
            is String -> v.trim().toDoubleOrNull()?.toLong()
            else -> null
        }

        private fun amount(v: Any?): Double = when (v) {
            is Number -> v.toDouble()
            is String -> v.replace("€", "").replace("euro", "", true).trim().replace(',', '.').toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

        /** Category by id, or by name ("Spesa > Supermercato" or just "Supermercato"), preferring the said [type]. */
        private fun find(cats: List<CategoryEntity>, v: Any?, type: TxType?): CategoryEntity? {
            val pool = cats.sortedBy { if (it.type == type) 0 else 1 }
            idOf(v)?.let { id -> return pool.firstOrNull { it.id == id } }
            val name = (v as? String)?.substringAfterLast('>')?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return pool.firstOrNull { it.name.equals(name, true) }
        }
        private val TAG = Regex("(?s)<movimento>.*?(</movimento>|$)")

        /** Chat text without the machine readable movement. */
        fun visible(text: String) = TAG.replace(text, "").trim()

        fun hasDraft(text: String) = "<movimento>" in text
    }
}
