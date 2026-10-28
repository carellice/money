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
import com.money.app.data.PaymentMethod
import com.money.app.data.TransactionEntity
import com.money.app.data.TxType
import com.money.app.domain.Money
import com.money.app.ui.ImageStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    var amountText by mutableStateOf("")
        private set
    var categoryId by mutableStateOf<Long?>(null)
        private set
    var subcategoryId by mutableStateOf<Long?>(null)
    var date by mutableStateOf(LocalDate.now())
    var payment by mutableStateOf(if (type == TxType.INCOME) PaymentMethod.TRANSFER else PaymentMethod.CARD)
    var note by mutableStateOf("")
    var receiptPath by mutableStateOf<String?>(null)
        private set
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

    val amountCents: Long get() = Money.parse(amountText) ?: 0L

    init {
        if (isEdit) viewModelScope.launch {
            repo.transaction(editId)?.let { tx ->
                original = tx
                type = tx.type
                amountText = Money.toInput(tx.amountCents)
                categoryId = tx.categoryId
                subcategoryId = tx.subcategoryId
                date = LocalDate.ofEpochDay(tx.date)
                payment = tx.payment
                note = tx.note
                receiptPath = tx.receiptPath
            }
            loaded = true
        }
    }

    fun press(key: Char) {
        val t = amountText
        if (key == ',') {
            if (!t.contains(',')) amountText = if (t.isEmpty()) "0," else "$t,"
            return
        }
        if (t.contains(',')) {
            if (t.substringAfter(',').length >= 2) return
        } else if (t.length >= 7) return
        amountText = if (t == "0") key.toString() else t + key
    }

    fun backspace() {
        amountText = amountText.dropLast(1)
    }

    fun clearAmount() {
        amountText = ""
    }

    fun switchType(newType: TxType) {
        if (newType == type) return
        type = newType
        categoryId = null
        subcategoryId = null
        payment = if (newType == TxType.INCOME) PaymentMethod.TRANSFER else PaymentMethod.CARD
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
        if (amountCents <= 0) return@launch
        repo.saveTransaction(
            TransactionEntity(
                id = editId,
                type = type,
                amountCents = amountCents,
                date = date.toEpochDay(),
                categoryId = category,
                subcategoryId = subcategoryId,
                payment = payment,
                note = note.trim(),
                receiptPath = receiptPath,
                recurringId = original?.recurringId,
                createdAt = original?.createdAt ?: System.currentTimeMillis(),
            )
        )
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
}
