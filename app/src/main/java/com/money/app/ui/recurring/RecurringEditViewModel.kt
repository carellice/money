package com.money.app.ui.recurring

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.money.app.AppContainer
import com.money.app.brand.Brand
import com.money.app.brand.Brands
import com.money.app.data.CategoryEntity
import com.money.app.data.Frequency
import com.money.app.data.Palette
import com.money.app.data.PaymentMethod
import com.money.app.data.RecurringEntity
import com.money.app.data.RecurringKind
import com.money.app.data.TxType
import com.money.app.domain.Money
import com.money.app.ui.ImageStore
import com.money.app.work.RecurringWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class RecurringEditViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val repo = container.repository

    val editId: Long = handle.get<Long>("id") ?: 0L
    val isEdit: Boolean get() = editId != 0L

    var kind by mutableStateOf(
        runCatching { RecurringKind.valueOf(handle.get<String>("kind") ?: "") }.getOrDefault(RecurringKind.SUBSCRIPTION)
    )
        private set
    var name by mutableStateOf("")
    var amountText by mutableStateOf("")
    var frequency by mutableStateOf(Frequency.MONTHLY)
    var date by mutableStateOf(LocalDate.now())
    var categoryId by mutableStateOf<Long?>(null)
        private set
    var subcategoryId by mutableStateOf<Long?>(null)
    var payment by mutableStateOf(PaymentMethod.CARD)
    var brandKey by mutableStateOf<String?>(null)
        private set
    var imagePath by mutableStateOf<String?>(null)
        private set
    var icon by mutableStateOf("repeat")
    var color by mutableLongStateOf(Palette.LILAC)
    var autoRegister by mutableStateOf(true)
    var remindDays by mutableIntStateOf(2)
    var active by mutableStateOf(true)
    var loaded by mutableStateOf(false)
        private set

    private var original: RecurringEntity? = null
    private val createdImages = mutableListOf<String>()
    private var saved = false

    val categories: StateFlow<List<CategoryEntity>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val amountCents: Long get() = Money.parse(amountText) ?: 0L

    init {
        viewModelScope.launch {
            val item = if (isEdit) repo.recurringItem(editId) else null
            if (item != null) {
                original = item
                kind = item.kind
                name = item.name
                amountText = Money.toInput(item.amountCents)
                frequency = item.frequency
                date = LocalDate.ofEpochDay(item.nextDate)
                categoryId = item.categoryId
                subcategoryId = item.subcategoryId
                payment = item.payment
                brandKey = item.brandKey
                imagePath = item.imagePath
                icon = item.icon
                color = item.color
                autoRegister = item.autoRegister
                remindDays = item.remindDaysBefore
                active = item.active
            } else {
                applyKindDefaults(repo.allCategories())
            }
            loaded = true
        }
    }

    private fun applyKindDefaults(all: List<CategoryEntity>) {
        val (catName, subName) = if (kind == RecurringKind.SUBSCRIPTION) "Abbonamenti" to "Streaming" else "Casa" to "Affitto"
        val top = all.firstOrNull { it.parentId == null && it.type == TxType.EXPENSE && it.name == catName }
            ?: all.firstOrNull { it.parentId == null && it.type == TxType.EXPENSE }
        categoryId = top?.id
        subcategoryId = all.firstOrNull { it.parentId == top?.id && it.name == subName }?.id
        icon = if (kind == RecurringKind.SUBSCRIPTION) "repeat" else "home"
        color = if (kind == RecurringKind.SUBSCRIPTION) Palette.LILAC else Palette.PEACH
    }

    fun changeKind(newKind: RecurringKind) {
        if (newKind == kind) return
        kind = newKind
        if (!isEdit) applyKindDefaults(categories.value)
    }

    fun selectCategory(id: Long) {
        if (id != categoryId) subcategoryId = null
        categoryId = id
    }

    /** [replaceName]: always use the brand name, as when picked from the name suggestions. */
    fun pickBrand(brand: Brand?, replaceName: Boolean = false) {
        val previous = Brands.find(brandKey)
        if (replaceName || name.isBlank() || name == previous?.name) name = brand?.name.orEmpty()
        brandKey = brand?.key
        setImage(null)
        if (brand != null) suggestSubcategory(brand)
    }

    /** Points the subcategory at the brand family, when the category is still the default one. */
    private fun suggestSubcategory(brand: Brand) {
        val all = categories.value
        val current = all.firstOrNull { it.id == categoryId } ?: return
        if (current.name != "Abbonamenti") return
        val subName = when (Brands.groupOf(brand.key)) {
            "Streaming", "Video & social" -> "Streaming"
            "Musica & audio" -> "Musica"
            "Cloud & software", "AI", "Sicurezza" -> "Software"
            "Telefonia & casa" -> "Telefonia"
            else -> null
        } ?: return
        all.firstOrNull { it.parentId == current.id && it.name == subName }?.let { subcategoryId = it.id }
    }

    fun importImage(context: Context, uri: Uri) = viewModelScope.launch {
        val file = repo.newImageFile("logo")
        if (ImageStore.import(context, uri, file, 512)) {
            setImage(file.path)
            brandKey = null
        }
    }

    fun clearLogo() {
        brandKey = null
        setImage(null)
    }

    private fun setImage(path: String?) {
        imagePath?.let {
            if (it in createdImages) {
                repo.deleteImage(it)
                createdImages.remove(it)
            }
        }
        imagePath = path
        path?.let { createdImages += it }
    }

    fun save(context: Context, onDone: () -> Unit) = viewModelScope.launch {
        val cents = amountCents.takeIf { it > 0 } ?: return@launch
        val category = categoryId ?: return@launch
        val epoch = date.toEpochDay()
        val o = original
        val reschedule = o == null || o.nextDate != epoch || o.frequency != frequency
        repo.saveRecurring(
            RecurringEntity(
                id = editId,
                kind = kind,
                name = name.trim().ifBlank { if (kind == RecurringKind.SUBSCRIPTION) "Abbonamento" else "Spesa fissa" },
                amountCents = cents,
                categoryId = category,
                subcategoryId = subcategoryId,
                payment = payment,
                frequency = frequency,
                startDate = if (reschedule || o == null) epoch else o.startDate,
                nextDate = epoch,
                brandKey = brandKey,
                imagePath = imagePath,
                icon = icon,
                color = color,
                autoRegister = autoRegister,
                remindDaysBefore = remindDays,
                notifiedFor = if (reschedule) null else o?.notifiedFor,
                active = active,
                note = o?.note.orEmpty(),
            )
        )
        o?.imagePath?.takeIf { it != imagePath }?.let(repo::deleteImage)
        createdImages.clear()
        saved = true
        val app = context.applicationContext
        container.scope.launch { RecurringWorker.runNow(app, container) }
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        original?.let { repo.deleteRecurring(it) }
        createdImages.forEach(repo::deleteImage)
        createdImages.clear()
        onDone()
    }

    override fun onCleared() {
        if (!saved) createdImages.forEach(repo::deleteImage)
    }
}
