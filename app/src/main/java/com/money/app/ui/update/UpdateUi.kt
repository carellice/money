package com.money.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.money.app.domain.percent
import com.money.app.ui.ai.aiText
import com.money.app.ui.components.Pill
import com.money.app.ui.components.SoftCard
import com.money.app.ui.components.SoftProgress
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.blob
import com.money.app.update.UpdateManager
import com.money.app.update.UpdateState

/** Card shown when a newer release exists; drives download and install. */
@Composable
fun UpdateBanner(state: UpdateState, updates: UpdateManager, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Ready -> state.release
        is UpdateState.Failed -> state.release
        else -> null
    } ?: return

    SoftCard(modifier, color = c.surfaceHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).blob(c.accent, seed = 17, amplitude = 0.1f), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Money ${release.version} è disponibile", style = Ui.type.titleSmall, color = c.ink)
        }
        if (release.notes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(aiText(release.notes.lines().take(6).joinToString("\n")), style = Ui.type.caption, color = c.inkSoft)
        }
        Spacer(Modifier.height(12.dp))
        when (state) {
            is UpdateState.Downloading -> {
                SoftProgress(state.progress, c.accent, height = 10.dp)
                Spacer(Modifier.height(6.dp))
                Text("Scarico… ${percent(state.progress)}", style = Ui.type.caption, color = c.inkSoft)
            }
            is UpdateState.Ready -> Pill("Installa", true, { updates.install(state.file) }, icon = Icons.Rounded.InstallMobile)
            else -> {
                if (state is UpdateState.Failed) {
                    Text(state.message, style = Ui.type.caption, color = c.expenseText)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Aggiorna", true, { updates.download(release) }, icon = Icons.Rounded.Download)
                    Pill("Più tardi", false, updates::dismiss)
                }
            }
        }
    }
}
