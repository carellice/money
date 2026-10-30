package com.money.app.ui.add

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.money.app.data.CategoryEntity
import com.money.app.data.PaymentMethod
import com.money.app.data.TxType
import com.money.app.domain.Dates
import com.money.app.domain.Money
import com.money.app.ui.ImageStore
import com.money.app.ui.ai.ThinkingDots
import com.money.app.ui.appViewModel
import com.money.app.ui.components.AppIcons
import com.money.app.ui.components.CategoryBadge
import com.money.app.ui.components.Pill
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.components.RoundIconButton
import com.money.app.ui.components.Segmented
import com.money.app.ui.components.TopBar
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.components.staggered
import com.money.app.ui.settings.MemberAvatar
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.blob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AddTransactionScreen(onClose: () -> Unit) {
    val vm = appViewModel { c, h -> AddViewModel(c, h) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val c = Ui.colors
    val accent by animateColorAsState(if (vm.type == TxType.EXPENSE) c.expense else c.income, label = "accent")

    BackHandler(enabled = vm.step > 0 && !vm.isEdit) { vm.step-- }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(
                title = when {
                    vm.isEdit -> "Modifica"
                    vm.type == TxType.EXPENSE -> "Nuova spesa"
                    else -> "Nuova entrata"
                },
                onBack = onClose,
            ) { StepDots(vm.step, accent) }

            AnimatedVisibility(vm.step > 0, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Summary(vm, categories, accent)
            }

            AnimatedContent(
                targetState = vm.step,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 300f)) { it * dir } + fadeIn()) togetherWith
                        (slideOutHorizontally(tween(250)) { -it * dir / 3 } + fadeOut(tween(200)))
                },
                label = "step",
                modifier = Modifier.weight(1f),
            ) { step ->
                when (step) {
                    0 -> AmountStep(vm, accent)
                    1 -> CategoryStep(vm, categories)
                    else -> DetailsStep(vm, accent, onDeleted = onClose)
                }
            }
        }
        if (vm.saved) SuccessBurst(accent, onDone = onClose)
    }
}

@Composable
private fun StepDots(step: Int, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 4.dp)) {
        repeat(3) { i ->
            val width by animateDpAsState(if (i == step) 22.dp else 8.dp, spring(dampingRatio = 0.6f), label = "dot")
            Box(
                Modifier
                    .size(width, 8.dp)
                    .clip(CircleShape)
                    .background(if (i <= step) accent else Ui.colors.surfaceHigh)
            )
        }
    }
}

@Composable
private fun Summary(vm: AddViewModel, categories: List<CategoryEntity>, accent: Color) {
    val category = categories.firstOrNull { it.id == vm.categoryId }
    val sub = categories.firstOrNull { it.id == vm.subcategoryId }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(Money.format(vm.amountCents), selected = true, onClick = { vm.step = 0 }, color = accent)
        if (category != null && vm.step > 1) {
            Pill(
                listOfNotNull(category.name, sub?.name).joinToString(" · "),
                selected = true,
                onClick = { vm.step = 1 },
                color = Color(category.color),
                icon = AppIcons[category.icon],
            )
        }
    }
}

// --- Step 1: amount ----------------------------------------------------------------------------

@Composable
private fun AmountStep(vm: AddViewModel, accent: Color) {
    val c = Ui.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!vm.isEdit) {
            Segmented(
                listOf("Spesa", "Entrata"),
                selected = if (vm.type == TxType.EXPENSE) 0 else 1,
                onSelect = { vm.switchType(if (it == 0) TxType.EXPENSE else TxType.INCOME) },
                colors = listOf(c.expense, c.income),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (vm.type == TxType.EXPENSE) "Quanto hai speso?" else "Quanto hai ricevuto?",
            style = Ui.type.bodyStrong,
            color = c.inkSoft,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .blob(accent.copy(alpha = 0.22f), seed = 5, amplitude = 0.1f),
            contentAlignment = Alignment.Center,
        ) {
            AmountDisplay(vm.amountText)
        }
        Spacer(Modifier.weight(1f))
        Keypad(onKey = vm::press, onBackspace = vm::backspace, onClear = vm::clearAmount)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Avanti", { vm.step = 1 }, enabled = vm.amountCents > 0, color = accent)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AmountDisplay(text: String) {
    val c = Ui.colors
    val shown = text.ifEmpty { "0" }
    val size = when {
        shown.length > 8 -> 44.sp
        shown.length > 6 -> 52.sp
        else -> 64.sp
    }
    val style = Ui.type.hero.copy(fontSize = size, lineHeight = size)
    Row(verticalAlignment = Alignment.Bottom) {
        shown.forEachIndexed { i, ch ->
            key(i, ch) {
                val pop = remember { Animatable(if (text.isEmpty()) 1f else 0f) }
                LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 600f)) }
                Text(
                    ch.toString(),
                    style = style,
                    color = if (text.isEmpty()) c.inkFaint else c.ink,
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.4f + 0.6f * pop.value
                        scaleY = 0.4f + 0.6f * pop.value
                        translationY = (1f - pop.value) * 40f
                        alpha = pop.value.coerceIn(0f, 1f)
                    },
                )
            }
        }
        Text(" €", style = style.copy(fontSize = size * 0.5f), color = c.inkSoft, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun Keypad(onKey: (Char) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val c = Ui.colors
    val keys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', ',', '0', '<')
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(62.dp)
                            .bouncyClick(pressedScale = 0.88f) { if (k == '<') onBackspace() else onKey(k) }
                            .clip(RoundedCornerShape(24.dp))
                            .background(c.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == '<') {
                            Icon(Icons.AutoMirrored.Rounded.Backspace, "Cancella", tint = c.ink)
                        } else {
                            Text(k.toString(), style = Ui.type.title, color = c.ink)
                        }
                    }
                }
            }
        }
        Text(
            "Azzera",
            style = Ui.type.caption,
            color = c.inkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClear).padding(4.dp),
        )
    }
}

// --- Step 2: category --------------------------------------------------------------------------

@Composable
private fun CategoryStep(vm: AddViewModel, categories: List<CategoryEntity>) {
    val c = Ui.colors
    val scope = rememberCoroutineScope()
    val tops = categories.filter { it.type == vm.type && it.parentId == null }
    val subs = categories.filter { it.parentId != null && it.parentId == vm.categoryId }
    val selected = tops.firstOrNull { it.id == vm.categoryId }
    Column(Modifier.fillMaxSize()) {
        Text(
            "In che categoria?",
            style = Ui.type.title,
            color = c.ink,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            NoteField(
                vm.note,
                { vm.note = it },
                if (vm.type == TxType.EXPENSE) "Cosa hai comprato? Es. Esselunga" else "Da dove arriva? Es. stipendio",
                Modifier.weight(1f),
            )
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .size(54.dp)
                    .bouncyClick(enabled = vm.note.isNotBlank()) {
                        vm.suggestCategory {
                            scope.launch {
                                delay(450)
                                vm.step = 2
                            }
                        }
                    }
                    .blob(if (vm.note.isNotBlank()) c.accent else c.surfaceHigh, seed = 61, amplitude = 0.09f, animated = vm.suggesting),
                contentAlignment = Alignment.Center,
            ) {
                if (vm.suggesting) ThinkingDots(Ink, Modifier.padding(top = 6.dp))
                else Icon(Icons.Rounded.AutoAwesome, "Suggerisci categoria", tint = if (vm.note.isNotBlank()) Ink else c.inkFaint)
            }
        }
        vm.suggestion?.let {
            Text(it, style = Ui.type.caption, color = c.inkSoft, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(tops, key = { _, it -> it.id }) { index, cat ->
                CategoryCell(cat, cat.id == vm.categoryId, Modifier.staggered(index, step = 25L)) {
                    vm.selectCategory(cat.id)
                    if (categories.none { it.parentId == cat.id }) scope.launch {
                        delay(260)
                        vm.step = 2
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = subs.isNotEmpty(),
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(c.surface)
                    .padding(20.dp)
                    .navigationBarsPadding()
            ) {
                Text("Più nel dettaglio?", style = Ui.type.label, color = c.inkSoft)
                Spacer(Modifier.height(10.dp))
                val color = selected?.let { Color(it.color) } ?: c.accent
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Generico", vm.subcategoryId == null, { vm.subcategoryId = null }, color = color)
                    subs.forEach { sub ->
                        Pill(sub.name, vm.subcategoryId == sub.id, {
                            vm.subcategoryId = sub.id
                            scope.launch {
                                delay(220)
                                vm.step = 2
                            }
                        }, color = color)
                    }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Avanti", { vm.step = 2 }, color = color)
            }
        }
    }
}

@Composable
private fun CategoryCell(cat: CategoryEntity, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ui.colors
    val scale by animateFloatAsState(if (selected) 1.15f else 1f, spring(dampingRatio = 0.4f, stiffness = 400f), label = "cellScale")
    Column(
        modifier.bouncyClick(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryBadge(
            cat.icon,
            Color(cat.color),
            size = 60.dp,
            seed = cat.id.toInt(),
            animated = selected,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            cat.name,
            style = Ui.type.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            color = if (selected) c.ink else c.inkSoft,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// --- Step 3: details ---------------------------------------------------------------------------

@Composable
private fun DetailsStep(vm: AddViewModel, accent: Color, onDeleted: () -> Unit) {
    val c = Ui.colors
    val context = LocalContext.current
    val members by vm.members.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingPhoto?.let { vm.captured(File(it), ok) }
        pendingPhoto = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.importReceipt(context, it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        if (members.isNotEmpty()) {
            Label(if (vm.type == TxType.EXPENSE) "Di chi è la spesa?" else "Di chi è l'entrata?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                members.forEach { member ->
                    Pill(
                        member.name,
                        vm.memberId == member.id,
                        { vm.memberId = member.id },
                        color = Color(member.color),
                        leading = { MemberAvatar(member, size = 24.dp) },
                    )
                }
            }
        }

        Label("Quando?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Oggi", vm.date == today, { vm.date = today }, color = accent)
            Pill("Ieri", vm.date == today.minusDays(1), { vm.date = today.minusDays(1) }, color = accent)
            val other = vm.date != today && vm.date != today.minusDays(1)
            Pill(
                if (other) Dates.long(vm.date) else "Altra data",
                other,
                { showPicker = true },
                color = accent,
                icon = Icons.Rounded.CalendarMonth,
            )
        }

        Label(if (vm.type == TxType.EXPENSE) "Come hai pagato?" else "Come l'hai ricevuto?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentMethod.entries.forEach { method ->
                Pill(method.label, vm.payment == method, { vm.payment = method }, color = accent, icon = AppIcons[method.icon])
            }
        }

        Label("Nota")
        NoteField(
            vm.note,
            { vm.note = it },
            if (vm.type == TxType.EXPENSE) "Es. pizza con gli amici" else "Es. stipendio di ottobre",
        )

        Label(if (vm.type == TxType.EXPENSE) "Scontrino" else "Ricevuta")
        ReceiptPicker(
            path = vm.receiptPath,
            onCamera = {
                val file = vm.newReceiptFile()
                pendingPhoto = file.path
                camera.launch(ImageStore.uriFor(context, file))
            },
            onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemove = vm::removeReceipt,
        )

        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            if (vm.isEdit) "Salva modifiche" else "Salva",
            { vm.save() },
            enabled = vm.amountCents > 0 && vm.categoryId != null && (members.isEmpty() || members.any { it.id == vm.memberId }),
            color = accent,
            icon = Icons.Rounded.Check,
        )
        if (vm.isEdit) {
            Text(
                "Elimina movimento",
                style = Ui.type.bodyStrong,
                color = c.expenseText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .clickable { confirmDelete = true }
                    .padding(14.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = vm.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { vm.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ showPicker = false }) { Text("Annulla") } },
        ) { DatePicker(state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare il movimento?") },
            text = { Text("L'operazione non si può annullare.") },
            confirmButton = { TextButton({ confirmDelete = false; vm.delete(onDeleted) }) { Text("Elimina", color = c.expenseText) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = Ui.type.label, color = Ui.colors.inkSoft, modifier = Modifier.padding(top = 10.dp))
}

@Composable
fun NoteField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = Ui.colors
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = Ui.type.body.copy(color = c.ink),
        cursorBrush = SolidColor(c.accentText),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surface)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                if (value.isEmpty()) Text(placeholder, style = Ui.type.body, color = c.inkFaint)
                inner()
            }
        },
    )
}

@Composable
private fun ReceiptPicker(path: String?, onCamera: () -> Unit, onGallery: () -> Unit, onRemove: () -> Unit) {
    val c = Ui.colors
    var preview by remember { mutableStateOf(false) }
    if (path != null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable { preview = true }
        ) {
            AsyncImage(File(path), "Scontrino", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            RoundIconButton(
                Icons.Rounded.Close, onRemove,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                background = Ink.copy(alpha = 0.6f), tint = Color.White, size = 36.dp,
                contentDescription = "Rimuovi",
            )
        }
        if (preview) {
            Dialog(onDismissRequest = { preview = false }) {
                AsyncImage(
                    File(path), "Scontrino",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { preview = false },
                )
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple("Scatta foto", Icons.Rounded.PhotoCamera, onCamera), Triple("Galleria", Icons.Rounded.Image, onGallery))
                .forEach { (label, icon, action) ->
                    Row(
                        Modifier
                            .weight(1f)
                            .bouncyClick(onClick = action)
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.surface)
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, null, tint = c.ink, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(label, style = Ui.type.label, color = c.ink)
                    }
                }
        }
    }
}

/** Blob that pops with a ring of particles, then closes the screen. */
@Composable
private fun SuccessBurst(color: Color, onDone: () -> Unit) {
    val c = Ui.colors
    val pop = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
        burst.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        delay(150)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg.copy(alpha = 0.85f * pop.value.coerceIn(0f, 1f)))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(320.dp)) {
            val count = 14
            val radius = size.minDimension / 2f * (0.35f + 0.65f * burst.value)
            for (i in 0 until count) {
                val angle = 2 * PI * i / count
                val center = Offset(
                    size.width / 2 + (cos(angle) * radius).toFloat(),
                    size.height / 2 + (sin(angle) * radius).toFloat(),
                )
                drawCircle(color.copy(alpha = 1f - burst.value), radius = (if (i % 2 == 0) 9f else 6f) * (1.2f - burst.value) * density, center = center)
            }
        }
        Box(
            Modifier
                .size(150.dp)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .blob(color, seed = 77, amplitude = 0.1f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, null, tint = Ink, modifier = Modifier.size(64.dp))
        }
    }
}
