package com.money.app.ui.components

import com.money.app.ui.theme.LocalSkeuo
import com.money.app.ui.theme.LocalMetro
import com.money.app.ui.theme.tile
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.money.app.ui.theme.CircleShape
import com.money.app.ui.theme.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import com.money.app.domain.AmountOp
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.gel
import com.money.app.ui.theme.skin
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.money.app.ui.theme.paper
import com.money.app.ui.theme.well
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight

@Composable
fun FormLabel(text: String, modifier: Modifier = Modifier) {
    // Nothing: instrument-panel label in ALL CAPS.
    val caps = com.money.app.ui.theme.LocalNothing.current
    Text(if (caps) text.uppercase() else text, style = Ui.type.label, color = Ui.colors.inkSoft, modifier = modifier.padding(top = 14.dp, bottom = 8.dp))
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (com.money.app.ui.theme.LocalNothing.current) return SwitchRowNothing(title, subtitle, checked, onChange, modifier)
    if (LocalMetro.current) return SwitchRowMetro(title, subtitle, checked, onChange, modifier)
    if (LocalSkeuo.current) return SwitchRowSkeuo(title = title, subtitle = subtitle, checked = checked, onChange = onChange, modifier = modifier)
    val c = Ui.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .bouncyClick(pressedScale = 0.98f) { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Ui.type.bodyStrong, color = c.ink)
            subtitle?.let { Text(it, style = Ui.type.caption, color = c.inkSoft) }
        }
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = c.accent,
                checkedBorderColor = c.accent,
                uncheckedThumbColor = c.inkFaint,
                uncheckedTrackColor = c.surfaceHigh,
                uncheckedBorderColor = c.surfaceHigh,
            ),
        )
    }
}

@Composable
private fun SwitchRowSkeuo(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ui.colors
    Row(
        modifier
            .fillMaxWidth()
            .paper(c.surface, RoundedCornerShape(16.dp), elevation = 3.dp, dark = c.isDark)
            .bouncyClick(pressedScale = 0.98f) { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Ui.type.bodyStrong, color = c.ink)
            subtitle?.let { Text(it, style = Ui.type.caption, color = c.inkSoft) }
        }
        Spacer(Modifier.size(12.dp))
        ToggleSwitch(checked, onChange)
    }
}

/** Old-school slide toggle: a recessed track that fills with gel, and a domed knob that slides on a spring. */
@Composable
fun ToggleSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val t by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "toggle")
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .size(width = 64.dp, height = 32.dp)
            .well(c.surfaceHigh, shape, c.isDark)
            .clickable { onChange(!checked) },
    ) {
        // The fill grows from the left as the knob travels.
        Box(Modifier.fillMaxHeight().fillMaxWidth(0.3f + 0.7f * t).gel(c.accent, shape, drop = false).graphicsLayer { alpha = t })
        Text(
            "I", style = Ui.type.label.onGel(), color = Ink.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp).graphicsLayer { alpha = t },
        )
        Text(
            "O", style = Ui.type.label, color = c.inkFaint,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 11.dp).graphicsLayer { alpha = 1f - t },
        )
        Box(
            Modifier
                .padding(2.dp)
                .size(28.dp)
                .graphicsLayer { translationX = t * 32.dp.toPx() }
                .knob(c.isDark),
        )
    }
}

/** Euro amount input with decimal keyboard; with [operations] it also takes + − × ÷ (see AmountMath). */
@Composable
fun AmountField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "0,00",
    operations: Boolean = false,
) {
    val c = Ui.colors
    BasicTextField(
        value = value,
        onValueChange = { text ->
            onChange(
                if (operations) text.filter { it.isDigit() || it == ',' || it == '.' || AmountOp.of(it) != null }.take(24)
                else text.filter { it.isDigit() || it == ',' || it == '.' }.take(10)
            )
        },
        singleLine = true,
        textStyle = Ui.type.display.copy(color = c.ink),
        cursorBrush = SolidColor(c.accentText),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .skin({ well(c.surfaceHigh, RoundedCornerShape(16.dp), c.isDark) }) { clip(RoundedCornerShape(24.dp)).background(c.surface) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = Ui.type.display, color = c.inkFaint)
                    inner()
                }
                Text("€", style = Ui.type.title, color = c.inkSoft)
            }
        },
    )
}

/** The four operations under an [AmountField]: each one goes at the end, replacing one already there. */
@Composable
fun OperationKeys(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    Row(modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(AmountOp.PLUS, AmountOp.MINUS, AmountOp.TIMES, AmountOp.DIVIDE).forEach { op ->
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .bouncyClick(pressedScale = 0.9f, enabled = value.isNotEmpty()) {
                        onChange(value.trimEnd { AmountOp.of(it) != null } + op.sign)
                    }
                    .skin({ paper(c.surface, RoundedCornerShape(12.dp), elevation = 3.dp, dark = c.isDark) }) { clip(RoundedCornerShape(18.dp)).background(c.surfaceHigh) },
                contentAlignment = Alignment.Center,
            ) {
                Text(op.sign, style = Ui.type.title, color = if (value.isNotEmpty()) c.ink else c.inkFaint)
            }
        }
    }
}

@Composable
/** [shown] maps a stored colour to the one painted, for the accent whose default looks different in this theme. */
fun ColorSwatches(colors: List<Long>, selected: Long, shown: (Long) -> Long = { it }, onSelect: (Long) -> Unit) {
    // Padding leaves room for the enlarged selected swatch, which LazyRow would clip.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
    ) {
        items(colors) { value ->
            val isSelected = value == selected
            val scale by animateFloatAsState(if (isSelected) 1.15f else 1f, spring(dampingRatio = 0.4f), label = "swatch")
            Box(
                Modifier
                    .size(40.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .bouncyClick { onSelect(value) }
                    .skin({ gel(Color(shown(value)), CircleShape) }) { clip(CircleShape).background(Color(shown(value))) }
                    .then(if (isSelected) Modifier.border(3.dp, Ui.colors.ink, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Rounded.Check, null, tint = Ink, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun IconPicker(selected: String, color: Color, onSelect: (String) -> Unit) {
    val c = Ui.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(AppIcons.all.keys.toList()) { key ->
            val isSelected = key == selected
            Box(
                Modifier
                    .size(48.dp)
                    .bouncyClick { onSelect(key) }
                    .skin({
                        if (isSelected) gel(color, RoundedCornerShape(12.dp))
                        else paper(c.surface, RoundedCornerShape(12.dp), elevation = 2.dp, dark = c.isDark)
                    }) { clip(RoundedCornerShape(16.dp)).background(if (isSelected) color.tile() else c.surface) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons[key], null, tint = if (isSelected) Ink else c.inkSoft, modifier = Modifier.size(22.dp))
            }
        }
    }
}
