package com.money.app.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui

@Composable
fun FormLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Ui.type.label, color = Ui.colors.inkSoft, modifier = modifier.padding(top = 14.dp, bottom = 8.dp))
}

@Composable
fun SwitchRow(
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

/** Euro amount input with decimal keyboard. */
@Composable
fun AmountField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "0,00") {
    val c = Ui.colors
    BasicTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() || it == ',' || it == '.' }.take(10)) },
        singleLine = true,
        textStyle = Ui.type.display.copy(color = c.ink),
        cursorBrush = SolidColor(c.accentText),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.surface)
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

@Composable
fun ColorSwatches(colors: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
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
                    .clip(CircleShape)
                    .background(Color(value))
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) color else c.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons[key], null, tint = if (isSelected) Ink else c.inkSoft, modifier = Modifier.size(22.dp))
            }
        }
    }
}
