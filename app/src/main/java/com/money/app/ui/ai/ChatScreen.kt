package com.money.app.ui.ai

import androidx.compose.animation.AnimatedVisibility
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.money.app.ai.VoiceRecorder
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.money.app.AppContainer
import com.money.app.ai.ChatMessage
import com.money.app.ui.appViewModel
import com.money.app.ui.components.EmptyState
import com.money.app.ui.components.Pill
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.components.RoundIconButton
import com.money.app.ui.components.TopBar
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.blob
import kotlinx.coroutines.launch

private val starters = listOf(
    "Com'è andato questo mese?",
    "Dove posso risparmiare?",
    "Quanto ho speso in cibo negli ultimi 3 mesi?",
    "Quanto mi costano gli abbonamenti in un anno?",
    "In quale mese ho speso di più?",
)

class ChatViewModel(container: AppContainer) : ViewModel() {
    private val ai = container.ai
    val config = ai.store.config
    val messages = mutableStateListOf<ChatMessage>()
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val recorder = VoiceRecorder()
    val level = recorder.level
    var recording by mutableStateOf(false)
        private set
    var transcribing by mutableStateOf(false)
        private set
    private var discardRecording = false

    /** Records until [stopRecording]; the transcript is then sent like a typed question. */
    fun startRecording() {
        if (recording || transcribing || busy) return
        error = null
        discardRecording = false
        recording = true
        viewModelScope.launch {
            val result = runCatching { recorder.record() }
            recording = false
            val wav = result.getOrElse {
                error = it.message
                return@launch
            }
            if (discardRecording) return@launch
            if (VoiceRecorder.seconds(wav) < 1) {
                error = "Vocale troppo corto: tieni il microfono acceso almeno un secondo"
                return@launch
            }
            transcribing = true
            runCatching { ai.transcribe(wav) }
                .onSuccess { send(it, voice = true) }
                .onFailure { error = it.message }
            transcribing = false
        }
    }

    fun stopRecording() = recorder.stop()

    fun cancelRecording() {
        discardRecording = true
        recorder.stop()
    }

    fun permissionDenied() {
        error = "Per i vocali serve il permesso del microfono"
    }

    fun send(text: String, voice: Boolean = false) {
        val question = text.trim()
        if (question.isEmpty() || busy) return
        val history = messages.toList()
        messages += ChatMessage("user", question, voice)
        error = null
        busy = true
        viewModelScope.launch {
            runCatching { ai.ask(history, question) }
                .onSuccess { messages += ChatMessage("assistant", it) }
                .onFailure { error = it.message }
            busy = false
        }
    }

    fun reset() {
        messages.clear()
        error = null
    }
}

@Composable
fun ChatScreen(onBack: () -> Unit, onOpenAiSettings: () -> Unit) {
    val vm = appViewModel { c, _ -> ChatViewModel(c) }
    val config by vm.config.collectAsStateWithLifecycle()
    val c = Ui.colors
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val context = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording() else vm.permissionDenied()
    }
    val startVoice = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startRecording()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(vm.messages.size, vm.busy) {
        val last = vm.messages.size + (if (vm.busy) 1 else 0)
        if (last > 0) list.animateScrollToItem(last)
    }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        TopBar("Chiedi a Money", onBack) {
            if (vm.messages.isNotEmpty()) RoundIconButton(Icons.Rounded.DeleteSweep, vm::reset, contentDescription = "Nuova chat")
        }
        if (config == null) {
            Column(Modifier.padding(20.dp)) {
                EmptyState(
                    "AI non collegata",
                    "Inserisci Account ID e token di Cloudflare Workers AI per fare domande sulle tue spese.",
                    icon = "savings",
                )
                PrimaryButton("Configura", onOpenAiSettings, icon = Icons.Rounded.AutoAwesome)
            }
            return@Column
        }

        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).blob(c.accent, seed = 61, amplitude = 0.1f), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Ink, modifier = Modifier.size(38.dp))
                    }
                    Spacer(Modifier.size(10.dp))
                    Text("Chiedimi qualcosa sui tuoi soldi", style = Ui.type.titleSmall, color = c.ink)
                    Text("Leggo i movimenti degli ultimi 12 mesi.", style = Ui.type.caption, color = c.inkSoft)
                    if (vm.messages.isEmpty()) {
                        Spacer(Modifier.size(16.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            starters.forEach { Pill(it, false, { vm.send(it) }) }
                        }
                    }
                }
            }
            itemsIndexed(vm.messages) { _, message -> Bubble(message) }
            if (vm.busy) {
                item {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp))
                            .background(c.surface)
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) { ThinkingDots(c.accentText) }
                }
            }
            vm.error?.let { err ->
                item { Text(err, style = Ui.type.caption, color = c.expenseText, modifier = Modifier.padding(start = 6.dp)) }
            }
        }

        val send = {
            vm.send(input)
            input = ""
        }
        if (vm.recording || vm.transcribing) {
            VoiceBar(
                recording = vm.recording,
                level = vm.level.collectAsStateWithLifecycle().value,
                onCancel = vm::cancelRecording,
                onSend = vm::stopRecording,
            )
            return@Column
        }
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = Ui.type.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.accentText),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(c.surface)
                            .padding(horizontal = 18.dp, vertical = 15.dp)
                    ) {
                        if (input.isEmpty()) Text("Scrivi o manda un vocale…", style = Ui.type.body, color = c.inkFaint)
                        inner()
                    }
                },
            )
            Spacer(Modifier.size(8.dp))
            AnimatedVisibility(!vm.busy, enter = fadeIn(), exit = fadeOut()) {
                val typing = input.isNotBlank()
                Box(
                    Modifier
                        .size(52.dp)
                        .bouncyClick(pressedScale = 0.85f) { if (typing) send() else startVoice() }
                        .blob(c.accent, seed = 8, amplitude = 0.08f),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(typing, label = "sendOrMic") { isTyping ->
                        Icon(
                            if (isTyping) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Mic,
                            if (isTyping) "Invia" else "Registra un vocale",
                            tint = Ink,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Replaces the input row while dictating: live level blob, timer, cancel and send. */
@Composable
private fun VoiceBar(recording: Boolean, level: Float, onCancel: () -> Unit, onSend: () -> Unit) {
    val c = Ui.colors
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(recording) {
        seconds = 0
        while (recording) {
            delay(1_000)
            seconds++
        }
    }
    val pulse by animateFloatAsState(1f + level * 1.6f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "level")
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(c.surface)
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (recording) {
                Box(
                    Modifier
                        .size(14.dp)
                        .graphicsLayer { scaleX = pulse; scaleY = pulse }
                        .blob(c.expense, seed = 4, amplitude = 0.12f)
                )
                Spacer(Modifier.size(12.dp))
                Text("%d:%02d".format(seconds / 60, seconds % 60), style = Ui.type.bodyStrong, color = c.ink)
                Spacer(Modifier.size(10.dp))
                Text("Ti ascolto…", style = Ui.type.body, color = c.inkSoft, modifier = Modifier.weight(1f))
                Text(
                    "Annulla",
                    style = Ui.type.label,
                    color = c.expenseText,
                    modifier = Modifier.clip(RoundedCornerShape(50)).bouncyClick(onClick = onCancel).padding(horizontal = 6.dp, vertical = 4.dp),
                )
            } else {
                ThinkingDots(c.accentText)
                Spacer(Modifier.size(12.dp))
                Text("Trascrivo il vocale…", style = Ui.type.body, color = c.inkSoft)
            }
        }
        if (recording) {
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .bouncyClick(pressedScale = 0.85f, onClick = onSend)
                    .blob(c.accent, seed = 8, amplitude = 0.08f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Invia vocale", tint = Ink, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val c = Ui.colors
    val mine = message.role == "user"
    val pop = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Text(
            if (mine) androidx.compose.ui.text.AnnotatedString(if (message.voice) "🎙️ ${message.content}" else message.content)
            else aiText(message.content),
            style = Ui.type.body,
            color = if (mine) Ink else c.ink,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (mine) 1f else 0f, 1f)
                }
                .clip(
                    if (mine) RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
                    else RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
                )
                .background(if (mine) c.accent else c.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
