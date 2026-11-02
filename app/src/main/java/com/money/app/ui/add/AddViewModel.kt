package com.money.app.ui.add

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.money.app.AppContainer
import com.money.app.data.CategoryEntity
import com.money.app.data.MemberEntity
import com.money.app.data.PaymentChoice
import com.money.app.data.PaymentMethod
import com.money.app.data.TransactionEntity
import com.money.app.data.TxType
import com.money.app.domain.Money
import com.money.app.ui.ImageStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

class AddViewModel(container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val repo = container.repository
    private val ai = container.ai

    val editId: Long = handle.get<Long>("id") ?: 0L
    val isEdit: Boolean get() = editId != 0L

    var type by mutableStateOf(
        runCatching { TxType.valueOf(handle.get<String>("type") ?: "") }.getOrDefault(TxType.EXPENSE)
    )
        private set
    /** Digits typed on the keypad, POS style: the last two are always the cents ("1250" is 12,50 €). */
    private var digits by mutableStateOf("")

    /** The amount as shown above the keypad ("12,50"), empty before the first digit. */
    val amountText: String
        get() = digits.toLongOrNull()?.let { "%d,%02d".format(it / 100, it % 100) }.orEmpty()
    var categoryId by mutableStateOf<Long?>(null)
        private set
    var subcategoryId by mutableStateOf<Long?>(null)
    var date by mutableStateOf(LocalDate.now())
    var payment by mutableStateOf(PaymentChoice(if (type == TxType.INCOME) PaymentMethod.TRANSFER else PaymentMethod.CARD))
    var note by mutableStateOf("")
    var receiptPath by mutableStateOf<String?>(null)
        private set
    var memberId by mutableStateOf<Long?>(null)
    var step by mutableIntStateOf(if (editId != 0L) 2 else 0)
    var saved by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(editId == 0L)
        private set

    private var original: TransactionEntity? = null
    /** Pictures taken in this session, deleted if the movement is not saved. */
    private val createdReceipts = mutableListOf<String>()

    val categories: StateFlow<List<CategoryEntity>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val members: StateFlow<List<MemberEntity>> =
        repo.members.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val amountCents: Long get() = digits.toLongOrNull() ?: 0L

    /** With people set up, every movement must say whose it is. */
    val memberChosen: Boolean get() = members.value.isEmpty() || members.value.any { it.id == memberId }

    init {
        if (!isEdit) viewModelScope.launch { memberId = repo.members.first().firstOrNull()?.id }
        if (isEdit) viewModelScope.launch {
            repo.transaction(editId)?.let { tx ->
                original = tx
                type = tx.type
                digits = tx.amountCents.toString()
                categoryId = tx.categoryId
                subcategoryId = tx.subcategoryId
                date = LocalDate.ofEpochDay(tx.date)
                payment = PaymentChoice(tx.payment, tx.paymentId)
                note = tx.note
                receiptPath = tx.receiptPath
                memberId = tx.memberId
            }
            loaded = true
        }
    }

    /** A digit (or "00") enters from the right, as on a card terminal. */
    fun press(key: String) {
        val next = (digits + key).trimStart('0')
        if (next.length <= MAX_DIGITS) digits = next
    }

    fun backspace() {
        digits = digits.dropLast(1)
    }

    fun clearAmount() {
        digits = ""
    }

    fun switchType(newType: TxType) {
        if (newType == type) return
        type = newType
        categoryId = null
        subcategoryId = null
        payment = PaymentChoice(if (newType == TxType.INCOME) PaymentMethod.TRANSFER else PaymentMethod.CARD)
    }

    fun selectCategory(id: Long) {
        if (categoryId != id) subcategoryId = null
        categoryId = id
    }

    var suggesting by mutableStateOf(false)
        private set
    var suggestion by mutableStateOf<String?>(null)
        private set

    /** Picks category and subcategory from the note: past movements first, then the AI. */
    fun suggestCategory(onPicked: () -> Unit) = viewModelScope.launch {
        if (note.isBlank() || suggesting) return@launch
        suggesting = true
        suggestion = null
        runCatching { ai.suggestCategory(note, type) }
            .onSuccess { result ->
                if (result == null) {
                    suggestion = if (ai.store.config.value == null) "Nessun movimento simile. Collega l'AI nelle impostazioni per avere suggerimenti."
                    else "Non sono sicuro: scegli tu la categoria."
                } else {
                    selectCategory(result.first)
                    subcategoryId = result.second
                    onPicked()
                }
            }
            .onFailure { suggestion = it.message }
        suggesting = false
    }

    fun newReceiptFile(): File = repo.newImageFile("receipt")

    fun removeReceipt() = setReceipt(null)

    private fun setReceipt(path: String?) {
        receiptPath?.let {
            if (it in createdReceipts) {
                repo.deleteImage(it)
                createdReceipts.remove(it)
            }
        }
        receiptPath = path
        path?.let { createdReceipts += it }
    }

    fun importReceipt(context: Context, uri: Uri) = viewModelScope.launch {
        val file = newReceiptFile()
        if (ImageStore.import(context, uri, file, 1800)) setReceipt(file.path)
    }

    fun captured(file: File, ok: Boolean) = viewModelScope.launch {
        if (ok && ImageStore.shrink(file, 1800)) setReceipt(file.path) else file.delete()
    }

    fun save() = viewModelScope.launch {
        val category = categoryId ?: return@launch
        if (amountCents <= 0 || !memberChosen) return@launch
        val savedId = repo.saveTransaction(
            TransactionEntity(
                id = editId,
                type = type,
                amountCents = amountCents,
                date = date.toEpochDay(),
                categoryId = category,
                subcategoryId = subcategoryId,
                payment = payment.method,
                paymentId = payment.customId,
                note = note.trim(),
                receiptPath = receiptPath,
                recurringId = original?.recurringId,
                createdAt = original?.createdAt ?: System.currentTimeMillis(),
                memberId = memberId?.takeIf { id -> members.value.any { it.id == id } },
            )
        )
        // Not saved (server unreachable): the form stays open with everything filled in.
        if (savedId < 0) return@launch
        original?.receiptPath?.takeIf { it != receiptPath }?.let(repo::deleteImage)
        createdReceipts.clear()
        saved = true
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        original?.let {
            repo.deleteTransaction(it)
            repo.discardReceipt(it)
        }
        createdReceipts.forEach(repo::deleteImage)
        createdReceipts.clear()
        onDone()
    }

    override fun onCleared() {
        if (!saved) createdReceipts.forEach(repo::deleteImage)
    }

    private companion object {
        /** Up to 9.999.999,99 €. */
        const val MAX_DIGITS = 9
    }
}
