package com.money.app.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.components.SoftCard
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.theme.Ui
import kotlin.math.min
import kotlin.math.roundToInt

enum class TourAction { ADD_ACCOUNT, ADD_EXPENSE }

data class TourStep(
    /** Key given to [tourTarget] on the element to light up. */
    val target: String,
    val title: String,
    val text: String,
    val action: String? = null,
    val kind: TourAction? = null,
)

val tourSteps = listOf(
    TourStep(
        "hero", "Il tuo mese",
        "Qui vedi il saldo del mese. Tocca la bolla per nascondere gli importi, tocca entrate o uscite per aggiungerne una al volo.",
    ),
    TourStep(
        "accounts", "I tuoi conti",
        "Banca, contanti, carte: aggiungi i tuoi conti e saprai sempre quanto hai a disposizione. Partiamo dal primo?",
        "Aggiungi un conto", TourAction.ADD_ACCOUNT,
    ),
    TourStep(
        "tabs", "Tutto a portata di pollice",
        "Movimenti per l'elenco completo, Fisse per affitto e abbonamenti, Report per la pagella e i grafici del mese.",
    ),
    TourStep(
        "chat", "Chiedi a Money",
        "L'assistente AI risponde sulle tue spese e aggiunge movimenti anche a voce. Lo colleghi dalle Impostazioni.",
    ),
    TourStep(
        "settings", "Impostazioni",
        "Persone, metodi di pagamento, categorie, backup e server. Da qui puoi rivedere presentazione e tour quando vuoi.",
    ),
    TourStep(
        "add", "La tua prima spesa",
        "Il + è sempre qui: importo, categoria, fatto. Proviamo adesso?",
        "Aggiungi una spesa", TourAction.ADD_EXPENSE,
    ),
)

/** Onboarding and guided tour state, shared by the activity, the root and the screens the tour points at. */
class Guide(onboarding: Boolean) {
    var onboarding by mutableStateOf(onboarding)
    var active by mutableStateOf(false)
        private set
    var step by mutableIntStateOf(0)
        private set

    /** A guided action (the account editor) is open: the tour waits under it. */
    var paused by mutableStateOf(false)
        private set

    /** Read and cleared by the accounts card, which opens its editor. */
    var addAccountRequest by mutableStateOf(false)

    val targets = mutableStateMapOf<String, Rect>()

    fun start() {
        step = 0
        paused = false
        active = true
    }

    fun next() {
        if (step + 1 < tourSteps.size) step++ else stop()
    }

    fun stop() {
        active = false
        paused = false
    }

    fun pauseForAccount() {
        paused = true
        addAccountRequest = true
    }

    /** The guided action is over: the tour goes on from the next step. */
    fun resume() {
        if (!paused) return
        paused = false
        next()
    }
}

val LocalGuide = compositionLocalOf { Guide(onboarding = false) }

/** Marks this element as something the tour can point at. */
@Composable
fun Modifier.tourTarget(key: String): Modifier {
    val guide = LocalGuide.current
    return onGloballyPositioned { guide.targets[key] = it.boundsInRoot() }
}

/** Dims the screen around the current step's element and explains it in a bubble. */
@Composable
fun TourOverlay(guide: Guide, onAddExpense: () -> Unit) {
    val c = Ui.colors
    val density = LocalDensity.current
    val index = guide.step
    val step = tourSteps[index]
    val target = guide.targets[step.target]?.inflate(with(density) { 10.dp.toPx() }) ?: Rect.Zero
    val hole by animateRectAsState(target, spring(dampingRatio = 0.78f, stiffness = 260f), label = "tourHole")
    val pulse by rememberInfiniteTransition(label = "tourPulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "tourPulseValue",
    )
    val accent = c.accent
    val statusTop = WindowInsets.statusBars.getTop(density)

    BackHandler(enabled = guide.active) { guide.stop() }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.64f))
            if (hole.width > 0f) {
                val radius = min(min(hole.width, hole.height) / 2, 48.dp.toPx())
                drawRoundRect(Color.Black, hole.topLeft, hole.size, CornerRadius(radius), blendMode = BlendMode.Clear)
                // A ring that keeps growing out of the hole, so the eye goes there.
                val grow = pulse * 16.dp.toPx()
                drawRoundRect(
                    accent.copy(alpha = 1f - pulse),
                    hole.topLeft - Offset(grow, grow),
                    Size(hole.width + grow * 2, hole.height + grow * 2),
                    CornerRadius(radius + grow),
                    style = Stroke(3.dp.toPx()),
                )
            }
        }

        Layout(
            content = {
                AnimatedContent(
                    index,
                    transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(120)) },
                    label = "tourBubble",
                ) { i -> Bubble(i, guide, onAddExpense) }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val margin = 20.dp.roundToPx()
            val gap = 18.dp.roundToPx()
            val bubble = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth - margin * 2))
            layout(constraints.maxWidth, constraints.maxHeight) {
                // Below the element when it sits in the top half, above it otherwise.
                val below = target.center.y < constraints.maxHeight / 2f
                val wanted = if (below) hole.bottom + gap else hole.top - gap - bubble.height
                val maxY = (constraints.maxHeight - bubble.height - margin).coerceAtLeast(statusTop + margin)
                bubble.place(margin, wanted.roundToInt().coerceIn(statusTop + margin, maxY))
            }
        }
    }
}

@Composable
private fun Bubble(index: Int, guide: Guide, onAddExpense: () -> Unit) {
    val c = Ui.colors
    val step = tourSteps[index]
    val last = index == tourSteps.lastIndex
    SoftCard(padding = 20.dp) {
        Row {
            Text("${index + 1} di ${tourSteps.size}", style = Ui.type.caption, color = c.accentText, modifier = Modifier.weight(1f))
            if (!last) {
                Text(
                    "Salta",
                    style = Ui.type.label,
                    color = c.inkSoft,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .bouncyClick { guide.stop() }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(step.title, style = Ui.type.titleSmall, color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(step.text, style = Ui.type.body, color = c.inkSoft)
        Spacer(Modifier.height(18.dp))
        if (step.action != null) {
            PrimaryButton(step.action, {
                when (step.kind) {
                    TourAction.ADD_ACCOUNT -> guide.pauseForAccount()
                    TourAction.ADD_EXPENSE -> {
                        guide.stop()
                        onAddExpense()
                    }
                    null -> guide.next()
                }
            }, icon = Icons.Rounded.Add)
            Spacer(Modifier.height(8.dp))
            PrimaryButton(if (last) "Fine" else "Più tardi", guide::next, color = c.surfaceHigh)
        } else {
            PrimaryButton(
                if (last) "Fine" else "Avanti",
                guide::next,
                icon = if (last) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
            )
        }
    }
}
