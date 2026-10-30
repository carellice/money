package com.money.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.money.app.data.MemberEntity
import com.money.app.data.Palette
import com.money.app.ui.add.NoteField
import com.money.app.ui.components.ColorSwatches
import com.money.app.ui.components.FormLabel
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui

/** Round badge with the person's initial. */
@Composable
fun MemberAvatar(member: MemberEntity, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(member.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(member.name.take(1).uppercase(), style = Ui.type.bodyStrong, color = Ink)
    }
}

@Composable
fun MembersSection(members: List<MemberEntity>, onSave: (MemberEntity) -> Unit, onDelete: (MemberEntity) -> Unit) {
    val c = Ui.colors
    var editing by remember { mutableStateOf<MemberEntity?>(null) }

    members.forEach { member ->
        Row(
            Modifier
                .fillMaxWidth()
                .bouncyClick(pressedScale = 0.98f) { editing = member }
                .clip(RoundedCornerShape(22.dp))
                .background(c.surface)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MemberAvatar(member)
            Spacer(Modifier.size(14.dp))
            Text(member.name, style = Ui.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkFaint)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.98f) {
                editing = MemberEntity(name = "", color = Palette.all[members.size % Palette.all.size])
            }
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = c.accentText, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Aggiungi persona", style = Ui.type.bodyStrong, color = c.ink)
            Text(
                if (members.isEmpty()) "Per indicare di chi è ogni spesa o entrata, ad esempio in famiglia"
                else "Ogni spesa o entrata andrà assegnata a una persona",
                style = Ui.type.caption,
                color = c.inkSoft,
            )
        }
    }

    editing?.let { member ->
        MemberEditor(
            member,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            },
            onDelete = {
                onDelete(it)
                editing = null
            },
        )
    }
}

@Composable
private fun MemberEditor(
    original: MemberEntity,
    onDismiss: () -> Unit,
    onSave: (MemberEntity) -> Unit,
    onDelete: (MemberEntity) -> Unit,
) {
    val c = Ui.colors
    var name by remember { mutableStateOf(original.name) }
    var color by remember { mutableLongStateOf(original.color) }
    var confirmDelete by remember { mutableStateOf(false) }
    val preview = original.copy(name = name.ifBlank { "?" }, color = color)

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
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(preview, size = 64.dp)
                Spacer(Modifier.size(14.dp))
                Text(
                    if (original.id == 0L) "Nuova persona" else "Modifica persona",
                    style = Ui.type.title, color = c.ink,
                )
            }
            FormLabel("Nome")
            NoteField(name, { name = it }, "Es. Giulia")
            FormLabel("Colore")
            ColorSwatches(Palette.all, color) { color = it }
            Spacer(Modifier.size(24.dp))
            PrimaryButton(
                "Salva",
                { onSave(original.copy(name = name.trim(), color = color)) },
                enabled = name.isNotBlank(),
                color = Color(color),
                icon = Icons.Rounded.Check,
            )
            if (original.id != 0L) {
                Text(
                    "Elimina persona",
                    style = Ui.type.bodyStrong,
                    color = c.expenseText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .bouncyClick { confirmDelete = true }
                        .padding(14.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare ${original.name}?") },
            text = { Text("I suoi movimenti restano, ma non saranno più assegnati a nessuno.") },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    onDelete(original)
                }) { Text("Elimina", color = c.expenseText) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Annulla") } },
        )
    }
}
