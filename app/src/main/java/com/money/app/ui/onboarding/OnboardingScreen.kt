package com.money.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.money.app.data.Palette
import com.money.app.data.Settings
import com.money.app.data.ThemeMode
import com.money.app.ui.components.ColorSwatches
import com.money.app.ui.components.FormLabel
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.components.Segmented
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.LocalBlobTime
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.backdropBlobs
import com.money.app.ui.theme.blob
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.sin

private data class Page(
    val icon: ImageVector,
    val color: Long,
    val title: String,
    val text: String,
    val chips: List<String> = emptyList(),
)

private val pages = listOf(
    Page(
        Icons.Rounded.Savings, Palette.LILAC,
        "Ciao, sono Money",
        "Spese ed entrate senza fatica, e con un po' di colore. Tutto resta sul tuo telefono.",
    ),
    Page(
        Icons.Rounded.TouchApp, Palette.MINT,
        "Segna in 3 tocchi",
        "Importo, categoria, fatto. Se vuoi aggiungi nota, metodo di pagamento e foto dello scontrino.",
        listOf("Tastierino rapido", "Sottocategorie", "Scontrini"),
    ),
    Page(
        Icons.Rounded.Autorenew, Palette.PEACH,
        "Fisse e abbonamenti",
        "Affitto, Netflix, palestra: li registro io alla scadenza e ti avviso prima del rinnovo.",
        listOf("Loghi pronti", "Promemoria", "Automatiche"),
    ),
    Page(
        Icons.Rounded.DonutLarge, Palette.BUTTER,
        "Il tuo mese, a colpo d'occhio",
        "Pagella da A a F, grafici, calendario e budget. Esporta in PDF o come immagine.",
        listOf("Pagella", "Trend 6 mesi", "Budget"),
    ),
    Page(
        Icons.Rounded.AutoAwesome, Palette.SKY,
        "Un assistente che ti capisce",
        "Collega l'AI da Impostazioni: commenta il mese, risponde sulle tue spese e sceglie la categoria per te.",
        listOf("Consigli", "Chat", "Categoria automatica"),
    ),
)

/** Index of the final "make it yours" page, after the feature pages. */
private val lastPage = pages.size

@Composable
fun OnboardingScreen(
    settings: Settings,
    onTheme: (ThemeMode) -> Unit,
    onAccent: (Long) -> Unit,
    onDone: () -> Unit,
) {
    val c = Ui.colors
    val pager = rememberPagerState { lastPage + 1 }
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    val finish = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else onDone()
    }

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .backdropBlobs(listOf(c.accent, c.income, Color(Palette.ROSE)), alpha = if (c.isDark) 0.10f else 0.22f)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Money", style = Ui.type.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
                val onLast = pager.currentPage == lastPage
                Text(
                    "Salta",
                    style = Ui.type.label,
                    color = c.inkSoft,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (onLast) 0f else 1f }
                        .clip(RoundedCornerShape(50))
                        .bouncyClick(enabled = !onLast) { scope.launch { pager.animateScrollToPage(lastPage) } }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            HorizontalPager(pager, Modifier.weight(1f)) { index ->
                val offset = pager.offsetFor(index)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = lerp(1f, 0.2f, offset.coerceAtMost(1f))
                            val scale = lerp(1f, 0.9f, offset.coerceAtMost(1f))
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    if (index < lastPage) FeaturePage(pages[index], index)
                    else PersonalizePage(settings, onTheme, onAccent)
                }
            }

            Dots(pager, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))

            AnimatedContent(
                pager.currentPage == lastPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboardingButton",
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            ) { onLast ->
                if (onLast) PrimaryButton("Iniziamo", finish, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                else PrimaryButton(
                    "Avanti",
                    { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    color = c.surfaceHigh,
                )
            }
        }
    }
}

private fun PagerState.offsetFor(page: Int): Float =
    ((currentPage - page) + currentPageOffsetFraction).absoluteValue

@Composable
private fun FeaturePage(page: Page, index: Int) {
    val c = Ui.colors
    val color = Color(page.color)
    val time = LocalBlobTime.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            // A small companion blob orbits the main one, each page with its own shape.
            Box(
                Modifier
                    .size(70.dp)
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        translationY = 14f * sin(time.value * 1.3f + index)
                        translationX = 10f * sin(time.value * 0.9f + index)
                    }
                    .blob(Color(Palette.ROSE).copy(alpha = 0.8f), seed = 40 + index, amplitude = 0.12f),
            )
            Box(
                Modifier
                    .size(210.dp)
                    .blob(Brush.linearGradient(listOf(color, c.accent)), seed = 20 + index, amplitude = 0.1f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(page.icon, null, tint = Ink, modifier = Modifier.size(88.dp))
            }
        }
        Spacer(Modifier.height(36.dp))
        Text(page.title, style = Ui.type.display, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(page.text, style = Ui.type.body, color = c.inkSoft, textAlign = TextAlign.Center)
        if (page.chips.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                page.chips.forEach { chip ->
                    Text(
                        chip,
                        style = Ui.type.caption,
                        color = Ink,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(color)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalizePage(settings: Settings, onTheme: (ThemeMode) -> Unit, onAccent: (Long) -> Unit) {
    val c = Ui.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(120.dp)
                .align(Alignment.CenterHorizontally)
                .blob(c.accent, seed = 31, amplitude = 0.1f, speed = 1.3f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Palette, null, tint = Ink, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Fallo tuo",
            style = Ui.type.display,
            color = c.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Scegli tema e colore. Puoi cambiarli quando vuoi dalle Impostazioni.",
            style = Ui.type.body,
            color = c.inkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
        FormLabel("Aspetto")
        Spacer(Modifier.height(8.dp))
        Segmented(
            ThemeMode.entries.map { it.label },
            ThemeMode.entries.indexOf(settings.theme),
            { onTheme(ThemeMode.entries[it]) },
        )
        Spacer(Modifier.height(20.dp))
        FormLabel("Colore principale")
        Spacer(Modifier.height(4.dp))
        ColorSwatches(Palette.all, settings.accent, onAccent)
    }
}

@Composable
private fun Dots(pager: PagerState, modifier: Modifier = Modifier) {
    val c = Ui.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(pager.pageCount) { i ->
            val selected = pager.currentPage == i
            val width by animateDpAsState(if (selected) 26.dp else 8.dp, spring(dampingRatio = 0.6f, stiffness = 400f), label = "dot")
            val color by animateColorAsState(if (selected) c.accent else c.inkFaint.copy(alpha = 0.5f), label = "dotColor")
            Box(
                Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
