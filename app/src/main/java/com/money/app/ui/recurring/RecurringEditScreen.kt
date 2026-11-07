package com.money.app.ui.recurring

import com.money.app.ui.components.FullScreenPage
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.layout.widthIn
import com.money.app.domain.Money
import com.money.app.domain.Recurrence
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.money.app.brand.Brand
import com.money.app.brand.Brands
import com.money.app.data.Frequency
import com.money.app.data.Palette
import com.money.app.ui.settings.PaymentPicker
import com.money.app.data.RecurringKind
import com.money.app.data.TxType
import com.money.app.domain.Dates
import com.money.app.ui.add.NoteField
import com.money.app.ui.appViewModel
import com.money.app.ui.components.AmountField
import com.money.app.ui.components.AppIcons
import com.money.app.ui.components.ColorSwatches
import com.money.app.ui.components.FormLabel
import com.money.app.ui.components.IconPicker
import com.money.app.ui.components.Pill
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.components.RecurringLogo
import com.money.app.ui.components.RoundIconButton
import com.money.app.ui.components.Segmented
import com.money.app.ui.components.SwitchRow
import com.money.app.ui.components.TopBar
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.theme.Ui
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val reminderOptions = listOf(-1 to "No", 0 to "Il giorno stesso", 1 to "1 giorno prima", 2 to "2 giorni prima", 3 to "3 giorni prima", 7 to "1 settimana prima")

@Composable
fun RecurringEditScreen(onClose: () -> Unit) {
    val vm = appViewModel { c, h -> RecurringEditViewModel(c, h) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Ui.colors
    var showBrands by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var nameFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.importImage(context, it) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.save(context, onClose)
    }
    val save = {
        val needsPermission = vm.remindDays >= 0 && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.save(context, onClose)
    }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        TopBar(
            title = when {
                vm.isEdit -> "Modifica"
                vm.kind == RecurringKind.SUBSCRIPTION -> "Nuovo abbonamento"
                vm.kind == RecurringKind.FINANCING -> "Nuovo finanziamento"
                else -> "Nuova spesa fissa"
            },
            onBack = onClose,
        ) {
            if (vm.isEdit) RoundIconButton(Icons.Rounded.Delete, { confirmDelete = true }, contentDescription = "Elimina")
        }
        if (!vm.loaded) return@Column

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Segmented(
                listOf("Abbonamento", "Spesa fissa", "Rate"),
                RecurringKind.entries.indexOf(vm.kind),
                { vm.changeKind(RecurringKind.entries[it]) },
            )

            // Logo
            Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    targetState = Triple(vm.brandKey, vm.imagePath, vm.icon to vm.color),
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = 0.5f, stiffness = 300f)) + fadeIn()) togetherWith (scaleOut() + fadeOut())
                    },
                    label = "logo",
                ) { (brand, image, look) ->
                    RecurringLogo(brand, image, look.first, Color(look.second), size = 112.dp, animated = true)
                }
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Logo preimpostato", vm.brandKey != null, { showBrands = true }, icon = Icons.Rounded.Stars)
                    Pill("Carica immagine", vm.imagePath != null, {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, icon = Icons.Rounded.Image)
                    if (vm.brandKey != null || vm.imagePath != null) {
                        Pill("Usa un'icona", false, vm::clearLogo, icon = Icons.Rounded.Block)
                    }
                }
            }
            AnimatedVisibility(vm.brandKey == null && vm.imagePath == null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    FormLabel("Icona")
                    IconPicker(vm.icon, Color(vm.color)) { vm.icon = it }
                    FormLabel("Colore")
                    ColorSwatches(Palette.all, vm.color) { vm.color = it }
                }
            }

            FormLabel("Nome")
            NoteField(
                vm.name,
                { vm.name = it },
                when (vm.kind) {
                    RecurringKind.SUBSCRIPTION -> "Es. Netflix"
                    RecurringKind.FINANCING -> "Es. Klarna, telefono"
                    RecurringKind.FIXED -> "Es. Affitto"
                },
                Modifier.onFocusChanged { nameFocused = it.isFocused },
            )
            val suggestions = remember(vm.name) {
                if (vm.name.isBlank()) emptyList()
                else Brands.search(vm.name).filterNot { it.name.equals(vm.name.trim(), ignoreCase = true) }.take(8)
            }
            AnimatedVisibility(
                visible = nameFocused && vm.kind != RecurringKind.FIXED && suggestions.isNotEmpty(),
                enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                LazyRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    lazyItems(suggestions, key = { it.key }) { brand ->
                        Pill(
                            brand.name,
                            selected = false,
                            onClick = {
                                vm.pickBrand(brand, replaceName = true)
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.animateItem(),
                            leading = { RecurringLogo(brand.key, null, "repeat", brand.color, size = 24.dp) },
                        )
                    }
                }
            }

            val financing = vm.kind == RecurringKind.FINANCING
            FormLabel(if (financing) "Importo della rata" else "Importo")
            AmountField(vm.amountText, { vm.amountText = it })
            if (financing) {
                FormLabel("Numero di rate")
                Stepper(vm.installments, min = 1) {
                    vm.installments = it
                    vm.installmentsPaid = vm.installmentsPaid.coerceAtMost(it)
                }
                if (vm.isEdit) {
                    FormLabel("Rate già pagate")
                    Stepper(vm.installmentsPaid, min = 0, max = vm.installments) { vm.installmentsPaid = it }
                }
            }

            FormLabel("Frequenza")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Frequency.entries.forEach { f -> Pill(f.label, vm.frequency == f, { vm.frequency = f }) }
            }

            FormLabel(
                when {
                    !financing -> "Prossimo addebito"
                    vm.isEdit -> "Prossima rata"
                    else -> "Prima rata"
                }
            )
            Pill(Dates.long(vm.date).replaceFirstChar { it.uppercase() }, true, { showDate = true }, icon = Icons.Rounded.CalendarMonth)
            val left = vm.installments - vm.installmentsPaid
            if (financing && vm.amountCents > 0 && left > 0) {
                Text(
                    (if (left == 1) "Manca 1 rata" else "Mancano $left rate") + " da ${Money.format(vm.amountCents)}: " +
                        "${Money.format(vm.amountCents * left)} in tutto, l'ultima il " +
                        "${Dates.long(Recurrence.occurrence(vm.date, vm.frequency, (left - 1).toLong()))}.",
                    style = Ui.type.caption,
                    color = c.inkSoft,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (vm.date.isBefore(LocalDate.now()) && vm.autoRegister) {
                Text(
                    "Data passata: gli addebiti già trascorsi verranno registrati nei movimenti.",
                    style = Ui.type.caption,
                    color = c.inkSoft,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            FormLabel("Categoria")
            val tops = categories.filter { it.parentId == null && it.type == TxType.EXPENSE }
            val subs = categories.filter { it.parentId != null && it.parentId == vm.categoryId }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tops.forEach { cat ->
                    Pill(cat.name, vm.categoryId == cat.id, { vm.selectCategory(cat.id) }, color = Color(cat.color), icon = AppIcons[cat.icon])
                }
            }
            AnimatedVisibility(subs.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                val color = tops.firstOrNull { it.id == vm.categoryId }?.let { Color(it.color) } ?: c.accent
                FlowRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill("Generico", vm.subcategoryId == null, { vm.subcategoryId = null }, color = color)
                    subs.forEach { sub -> Pill(sub.name, vm.subcategoryId == sub.id, { vm.subcategoryId = sub.id }, color = color) }
                }
            }

            FormLabel("Metodo di pagamento")
            PaymentPicker(vm.payment, { vm.payment = it })

            FormLabel("Promemoria")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reminderOptions.forEach { (days, label) -> Pill(label, vm.remindDays == days, { vm.remindDays = days }) }
            }

            Spacer(Modifier.height(18.dp))
            SwitchRow(
                "Registra in automatico",
                "Il giorno dell'addebito la spesa finisce da sola nei movimenti. Se è spento, resta in attesa finché non la aggiungi tu tenendola premuta.",
                vm.autoRegister,
                { vm.autoRegister = it },
            )
            if (vm.isEdit) {
                Spacer(Modifier.height(10.dp))
                SwitchRow("Attiva", "Metti in pausa senza perdere i dati.", vm.active, { vm.active = it })
            }

            Spacer(Modifier.height(24.dp))
            PrimaryButton("Salva", { save() }, enabled = vm.amountCents > 0 && vm.categoryId != null, icon = Icons.Rounded.Check)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showBrands) {
        BrandPicker(onDismiss = { showBrands = false }) {
            vm.pickBrand(it)
            showBrands = false
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(vm.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { vm.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ showDate = false }) { Text("Annulla") } },
        ) { DatePicker(state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare ${vm.name.ifBlank { "questa spesa fissa" }}?") },
            text = { Text("I movimenti già registrati restano nello storico.") },
            confirmButton = { TextButton({ confirmDelete = false; vm.delete(onClose) }) { Text("Elimina", color = c.expenseText) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Annulla") } },
        )
    }
}

/** A number picked with - and + buttons, such as the installments of a financing. */
@Composable
private fun Stepper(value: Int, min: Int, max: Int = 99, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RoundIconButton(Icons.Rounded.Remove, { onChange((value - 1).coerceAtLeast(min)) }, contentDescription = "Meno")
        Text(
            value.toString(),
            style = Ui.type.title,
            color = Ui.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp),
        )
        RoundIconButton(Icons.Rounded.Add, { onChange((value + 1).coerceAtMost(max)) }, contentDescription = "Più")
    }
}

@Composable
private fun BrandPicker(onDismiss: () -> Unit, onPick: (Brand) -> Unit) {
    val c = Ui.colors
    var query by remember { mutableStateOf("") }
    FullScreenPage("Scegli un logo", onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            NoteField(query, { query = it }, "Cerca Netflix, Spotify, iCloud…")
            Spacer(Modifier.height(8.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(84.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
            modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
        ) {
            if (query.isBlank()) {
                Brands.groups.forEach { group ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "g_${group.name}") {
                        Text(group.name, style = Ui.type.label, color = c.inkSoft, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp))
                    }
                    items(group.brands, key = { it.key }) { BrandCell(it, onPick) }
                }
            } else {
                items(Brands.search(query), key = { it.key }) { BrandCell(it, onPick) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Loghi da Simple Icons (CC0). I marchi appartengono ai rispettivi proprietari.",
                    style = Ui.type.caption,
                    color = c.inkFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun BrandCell(brand: Brand, onPick: (Brand) -> Unit) {
    Column(
        Modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(20.dp))
            .bouncyClick { onPick(brand) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            RecurringLogo(brand.key, null, "repeat", brand.color, size = 56.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            brand.name,
            style = Ui.type.caption,
            color = Ui.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.size(width = 76.dp, height = 18.dp),
        )
    }
}
