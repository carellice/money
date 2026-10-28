package com.money.app.ui.categories

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.money.app.AppContainer
import com.money.app.data.CategoryEntity
import com.money.app.data.Palette
import com.money.app.data.TxType
import com.money.app.domain.Money
import com.money.app.ui.add.NoteField
import com.money.app.ui.appViewModel
import com.money.app.ui.components.AmountField
import com.money.app.ui.components.CategoryBadge
import com.money.app.ui.components.ColorSwatches
import com.money.app.ui.components.FormLabel
import com.money.app.ui.components.IconPicker
import com.money.app.ui.components.Pill
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.components.RoundIconButton
import com.money.app.ui.components.Segmented
import com.money.app.ui.components.TopBar
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.components.staggered
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val categories: StateFlow<List<CategoryEntity>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(category: CategoryEntity, subs: List<CategoryEntity>) = viewModelScope.launch { repo.saveCategory(category, subs) }

    fun delete(category: CategoryEntity, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(repo.deleteCategory(category))
    }
}

private data class Editing(val category: CategoryEntity, val subs: List<CategoryEntity>)

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> CategoriesViewModel(c) }
    val all by vm.categories.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    val context = LocalContext.current
    val c = Ui.colors
    val type = if (tab == 0) TxType.EXPENSE else TxType.INCOME
    val tops = all.filter { it.parentId == null && it.type == type }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Categorie e budget", onBack) {
            RoundIconButton(Icons.Rounded.Add, {
                editing = Editing(
                    CategoryEntity(name = "", icon = "more", color = Palette.all.random(), type = type, sortOrder = tops.size),
                    emptyList(),
                )
            }, background = c.accent, tint = Ink, contentDescription = "Nuova categoria")
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item {
                Segmented(listOf("Spese", "Entrate"), tab, { tab = it }, colors = listOf(c.expense, c.income))
                Spacer(Modifier.height(4.dp))
                if (tab == 0) {
                    Text(
                        "Tocca una categoria per rinominarla, cambiare icona, aggiungere sottocategorie o fissare un budget mensile.",
                        style = Ui.type.caption,
                        color = c.inkSoft,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(tops, key = { it.id }) { cat ->
                val subs = all.filter { it.parentId == cat.id }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .staggered(tops.indexOf(cat), step = 30L)
                        .bouncyClick(pressedScale = 0.97f) { editing = Editing(cat, subs) }
                        .clip(RoundedCornerShape(24.dp))
                        .background(c.surface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(cat.icon, Color(cat.color), size = 46.dp, seed = cat.id.toInt())
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat.name, style = Ui.type.bodyStrong, color = c.ink)
                        Text(
                            if (subs.isEmpty()) "Nessuna sottocategoria" else subs.joinToString(", ") { it.name },
                            style = Ui.type.caption,
                            color = c.inkSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    cat.budgetCents?.let {
                        Text(
                            "${Money.short(it)}/mese",
                            style = Ui.type.caption,
                            color = Ink,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(cat.color)).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    editing?.let { e ->
        CategoryEditor(
            e,
            onDismiss = { editing = null },
            onSave = { cat, subs ->
                vm.save(cat, subs)
                editing = null
            },
            onDelete = { cat ->
                vm.delete(cat) { ok ->
                    if (ok) editing = null
                    else Toast.makeText(context, "Categoria in uso da movimenti o spese fisse: non si può eliminare.", Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

@Composable
private fun CategoryEditor(
    editing: Editing,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity, List<CategoryEntity>) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
) {
    val c = Ui.colors
    val original = editing.category
    var name by remember { mutableStateOf(original.name) }
    var icon by remember { mutableStateOf(original.icon) }
    var color by remember { mutableLongStateOf(original.color) }
    var budget by remember { mutableStateOf(original.budgetCents?.let { Money.toInput(it) } ?: "") }
    val subs = remember { mutableStateListOf<CategoryEntity>().apply { addAll(editing.subs) } }
    var newSub by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .imePadding()
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(icon, Color(color), size = 64.dp, animated = true)
                Spacer(Modifier.size(14.dp))
                Text(
                    if (original.id == 0L) "Nuova categoria" else "Modifica categoria",
                    style = Ui.type.title, color = c.ink,
                )
            }
            FormLabel("Nome")
            NoteField(name, { name = it }, "Es. Palestra")
            FormLabel("Icona")
            IconPicker(icon, Color(color)) { icon = it }
            FormLabel("Colore")
            ColorSwatches(Palette.all, color) { color = it }
            if (original.type == TxType.EXPENSE) {
                FormLabel("Budget mensile (facoltativo)")
                AmountField(budget, { budget = it }, placeholder = "Nessun limite")
            }
            FormLabel("Sottocategorie")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subs.forEach { sub ->
                    Pill(sub.name, true, { subs.remove(sub) }, color = Color(color), icon = Icons.Rounded.Close)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoteField(newSub, { newSub = it }, "Aggiungi sottocategoria", Modifier.weight(1f))
                Spacer(Modifier.size(8.dp))
                RoundIconButton(Icons.Rounded.Add, {
                    val trimmed = newSub.trim()
                    if (trimmed.isNotEmpty() && subs.none { it.name.equals(trimmed, ignoreCase = true) }) {
                        subs += CategoryEntity(name = trimmed, icon = icon, color = color, type = original.type, parentId = original.id.takeIf { it != 0L })
                    }
                    newSub = ""
                }, background = c.accent, tint = Ink)
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                "Salva",
                {
                    onSave(
                        original.copy(
                            name = name.trim(),
                            icon = icon,
                            color = color,
                            budgetCents = Money.parse(budget)?.takeIf { it > 0 },
                        ),
                        subs.toList(),
                    )
                },
                enabled = name.isNotBlank(),
                color = Color(color),
                icon = Icons.Rounded.Check,
            )
            if (original.id != 0L) {
                Text(
                    "Elimina categoria",
                    style = Ui.type.bodyStrong,
                    color = c.expenseText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .bouncyClick { onDelete(original) }
                        .padding(14.dp),
                )
            }
        }
    }
}
