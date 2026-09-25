package ru.starimg.ai.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.content.ClipboardManager
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import ru.starimg.ai.data.model.Attachment
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Add
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ru.starimg.ai.data.model.AiModel
import ru.starimg.ai.data.model.Catalog
import ru.starimg.ai.data.model.estimateRequestCost
import ru.starimg.ai.data.model.formatCoefficient
import ru.starimg.ai.data.model.formatRubles
import ru.starimg.ai.ui.AppState
import ru.starimg.ai.ui.MainViewModel
import ru.starimg.ai.ui.components.Bubble
import ru.starimg.ai.ui.components.MessageActions
import ru.starimg.ai.ui.components.shareText
import ru.starimg.ai.ui.startOfDay
import ru.starimg.ai.ui.theme.LocalStarPalette
import ru.starimg.ai.ui.theme.StarDim
import java.io.ByteArrayOutputStream

@Composable
fun ChatScreen(vm: MainViewModel, state: AppState, onOpenChats: () -> Unit, onOpenModels: () -> Unit, onOpenAgents: () -> Unit) {
    ChatBody(vm, state, onOpenChats, onOpenModels, onOpenAgents)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatBody(vm: MainViewModel, state: AppState, onOpenChats: () -> Unit, onOpenModels: () -> Unit, onOpenAgents: () -> Unit) {
    val palette = LocalStarPalette.current
    var text by remember(state.currentId) { mutableStateOf(vm.currentChat?.draft ?: "") }
    var photos by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var attach by remember { mutableStateOf(false) }
    var quote by remember { mutableStateOf<String?>(null) }
    var resend by remember { mutableStateOf<Int?>(null) }
    var resendText by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val scroll = rememberLazyListState()
    val model = vm.model(state.selectedModel) ?: Catalog.models.first()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        photos = (photos + uris.mapNotNull { imagePayload(context, it) }).take(8)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { if (it != null) photos = (photos + imagePayload(it)).take(8) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) camera.launch(null) }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { heard ->
            text = (text.trimEnd() + " " + heard).trim()
        }
    }
    fun takePhoto() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera.launch(null)
        else permission.launch(Manifest.permission.CAMERA)
    }
    fun dictate() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите")
        }
        runCatching { speech.launch(intent) }.onFailure { text = text }
    }
    fun sendNow() {
        if (state.busy || (text.isBlank() && photos.isEmpty() && quote == null)) return
        val body = quote?.let { "> ${it.replace("\n", "\n> ")}\n\n$text" } ?: text
        vm.send(body, photos)
    }
    LaunchedEffect(state.acceptedSendId) {
        if (state.acceptedSendId > 0) { text = ""; photos = emptyList(); quote = null }
    }
    // The draft lives on the chat, so leaving the screen and coming back keeps it.
    LaunchedEffect(text) { delay(400); vm.currentChat?.id?.let { vm.saveDraft(it, text) } }
    // Only follow the stream while the reader is already at the bottom, so a flurry of tokens
    // never yanks the list out from under someone reading earlier messages.
    val pinned by remember { derivedStateOf { !scroll.canScrollForward } }
    LaunchedEffect(vm.messages.size, state.busy) { if (pinned && vm.messages.isNotEmpty()) runCatching { scroll.animateScrollToItem(vm.messages.lastIndex) } }
    val today = vm.costSince(startOfDay())
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.glow.copy(alpha = .16f), palette.bg), endY = 520f)).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = StarDim.sm, vertical = StarDim.xs), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onOpenChats) { Icon(Icons.Default.History, "Чаты") }
                Column(Modifier.weight(1f)) {
                    Text(vm.currentChat?.title ?: "Новый чат", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (state.busy) "Печатает…" else "сегодня ${formatRubles(today)}", color = palette.faint, style = MaterialTheme.typography.labelMedium)
                }
                Surface(onClick = onOpenModels, shape = RoundedCornerShape(StarDim.radiusXl), color = palette.raised) {
                    Text(model.name, modifier = Modifier.padding(horizontal = StarDim.md, vertical = StarDim.sm), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = palette.accent)
                }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Ещё") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Системная инструкция") }, onClick = { menu = false; editing = true; draft = state.baseSystemPrompt })
                        DropdownMenuItem(text = { Text("Очистить контекст") }, onClick = { menu = false; vm.clearContext() })
                        DropdownMenuItem(text = { Text("Продолжить ответ") }, onClick = { menu = false; vm.continueReply() }, enabled = vm.messages.lastOrNull()?.user == false && !state.busy)
                        DropdownMenuItem(text = { Text("Экспорт в Markdown") }, onClick = { menu = false; shareText(context, vm.chatAsMarkdown()) })
                        DropdownMenuItem(text = { Text("Экспорт в текст") }, onClick = { menu = false; shareText(context, vm.chatAsText()) })
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = StarDim.md, vertical = StarDim.xs),
                shape = RoundedCornerShape(StarDim.radius), color = palette.raised
            ) {
                Column(Modifier.padding(horizontal = StarDim.md, vertical = StarDim.sm), verticalArrangement = Arrangement.spacedBy(StarDim.xxs)) {
                    Text("Модель · ${model.name}", style = MaterialTheme.typography.labelMedium, color = palette.assistant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Reasoning · ${state.reasoningMode.ifBlank { "выключен" }}", style = MaterialTheme.typography.labelSmall, color = palette.faint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val instructions = listOf(state.baseSystemPrompt, state.systemPrompt).count { it.isNotBlank() }
                    Text("Инструкции · ${when (instructions) { 0 -> "нет"; 1 -> "активна 1"; else -> "активны $instructions" }}", style = MaterialTheme.typography.labelSmall, color = palette.faint)
                }
            }
            if (state.contextDropped > 0) {
                Text(
                    "Ранние сообщения не отправлены: ${state.contextDropped}. Контекст обрезан по размеру.",
                    color = palette.faint, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = StarDim.lg, vertical = StarDim.xs)
                )
            }
            if (vm.messages.isEmpty() && !state.busy) {
                Box(Modifier.weight(1f)) { EmptyState { text = it } }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(), state = scroll,
                    contentPadding = PaddingValues(start = StarDim.lg, end = StarDim.lg, top = StarDim.md, bottom = 156.dp),
                    verticalArrangement = Arrangement.spacedBy(StarDim.lg)
                ) {
                    itemsIndexed(vm.messages, key = { index, _ -> "$index-${vm.messages.size}" }) { index, message ->
                        val last = index == vm.messages.lastIndex
                        val streaming = state.busy && last && !message.user
                        val userIndex = if (message.user) index else vm.messages.subList(0, index).indexOfLast { it.user }
                        val shown = message.shownText
                        val actions = MessageActions(
                            onCopy = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(shown)) },
                            onShare = { shareText(context, shown) },
                            onRegenerate = if (!message.user && userIndex >= 0 && !state.busy) ({ vm.regenerate(userIndex) }) else null,
                            onEdit = if (message.user && !state.busy) ({ resend = index; resendText = message.text }) else null,
                            onDelete = if (!state.busy) ({ pendingDelete = index }) else null,
                            onQuote = if (!message.user) ({ quote = it }) else null
                        )
                        androidx.compose.animation.AnimatedVisibility(true, enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }) {
                            Bubble(message, streaming, actions, if (!message.user) ({ vm.selectVersion(index, it) }) else null)
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).imePadding()) {
            AnimatedVisibility(state.error != null, enter = fadeIn()) {
                state.error?.let { message ->
                    Surface(Modifier.padding(horizontal = StarDim.md, vertical = StarDim.xs).fillMaxWidth(), RoundedCornerShape(StarDim.radius), color = MaterialTheme.colorScheme.errorContainer) {
                        Row(Modifier.padding(start = StarDim.md), verticalAlignment = Alignment.CenterVertically) {
                            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                            TextButton(vm::retry) { Text("Повторить") }
                            IconButton(vm::clearError) { Icon(Icons.Default.Close, "Закрыть") }
                        }
                    }
                }
            }
            Composer(
                text, { text = it }, photos, quote, state.busy, model, state.currency,
                { attach = true }, ::sendNow, vm::stop, ::dictate,
                { photos = photos.filterIndexed { i, _ -> i != it } }, { quote = null },
                { photos = (photos + it).take(8) }
            )
        }
    }
    if (attach) ModalBottomSheet(onDismissRequest = { attach = false }, sheetState = rememberModalBottomSheetState()) {
        AttachSheet(
            gallery = { attach = false; gallery.launch("image/*") },
            camera = { attach = false; takePhoto() },
            agents = { attach = false; onOpenAgents() }
        )
    }
    if (editing) SystemPromptDialog(draft, state.systemPrompt, { draft = it }, { vm.setBaseSystemPrompt(draft); editing = false }, { editing = false })
    resend?.let { index ->
        AlertDialog(
            onDismissRequest = { resend = null }, title = { Text("Изменить сообщение") },
            text = { OutlinedTextField(resendText, { resendText = it }, label = { Text("Новый текст") }, minLines = 3) },
            confirmButton = { TextButton({ vm.editAndResend(index, resendText); resend = null }) { Text("Отправить") } },
            dismissButton = { TextButton({ resend = null }) { Text("Отмена") } }
        )
    }
    pendingDelete?.let { index ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null }, title = { Text("Удалить сообщение?") },
            text = { Text("Сообщение исчезнет из чата.") },
            confirmButton = { TextButton({ vm.deleteMessage(index); pendingDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton({ pendingDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun AttachSheet(gallery: () -> Unit, camera: () -> Unit, agents: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = StarDim.lg)) {
        AttachRow(Icons.Default.PhotoLibrary, "Галерея", "Выбрать изображение", gallery)
        AttachRow(Icons.Default.CameraAlt, "Камера", "Сделать снимок", camera)
        HorizontalDivider(Modifier.padding(vertical = StarDim.xs), color = LocalStarPalette.current.line)
        AttachRow(Icons.Default.AutoAwesome, "Агенты", "Роль для этого чата", agents)
    }
}

@Composable
private fun AttachRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val palette = LocalStarPalette.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = StarDim.xl, vertical = StarDim.md), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(palette.raised), contentAlignment = Alignment.Center) { Icon(icon, null, tint = palette.accent) }
        Column(Modifier.padding(start = StarDim.lg)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = palette.faint, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Composer(
    text: String, onText: (String) -> Unit, photos: List<Attachment>, quote: String?, busy: Boolean, model: AiModel, currency: String,
    attach: () -> Unit, send: () -> Unit, stop: () -> Unit, dictate: () -> Unit,
    removePhoto: (Int) -> Unit, clearQuote: () -> Unit, paste: (Attachment) -> Unit
) {
    val palette = LocalStarPalette.current
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val active by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var confirm by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf<Attachment?>(null) }
    val estimate = estimateRequestCost(text.length + photos.size * 1500, model.pricing)
    val pricey = model.pricing.inputCoefficient >= 4.0
    val canSend = !busy && (text.isNotBlank() || photos.isNotEmpty() || quote != null)
    // A picture copied elsewhere shows up here before it is sent, so a stray paste is visible.
    LaunchedEffect(active) {
        while (active == androidx.lifecycle.Lifecycle.State.RESUMED) {
            pasted = runCatching { clipboardImage(context) }.getOrNull()
            delay(1200)
        }
    }
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(palette.bg.copy(alpha = 0f), palette.bg))).padding(horizontal = StarDim.md).padding(bottom = StarDim.md).navigationBarsPadding()
    ) {
        if (quote != null) {
            Surface(Modifier.padding(bottom = StarDim.sm).fillMaxWidth(), RoundedCornerShape(StarDim.radius), color = palette.raised) {
                Row(Modifier.padding(start = StarDim.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(quote.take(140), Modifier.weight(1f).padding(vertical = StarDim.sm), maxLines = 2, overflow = TextOverflow.Ellipsis, color = palette.faint, style = MaterialTheme.typography.labelMedium)
                    IconButton(clearQuote) { Icon(Icons.Default.Close, "Убрать цитату") }
                }
            }
        }
        if (photos.isNotEmpty()) {
            Row(Modifier.padding(bottom = StarDim.sm), horizontalArrangement = Arrangement.spacedBy(StarDim.sm)) {
                photos.forEachIndexed { index, photo -> PhotoThumb(photo) { removePhoto(index) } }
            }
        } else if (pasted != null) {
            Surface(Modifier.padding(bottom = StarDim.sm), RoundedCornerShape(StarDim.radius), color = palette.raised) {
                Row(Modifier.padding(start = StarDim.sm), verticalAlignment = Alignment.CenterVertically) {
                    PhotoThumb(pasted!!) {}
                    Text("В буфере есть изображение", Modifier.padding(start = StarDim.sm).weight(1f), style = MaterialTheme.typography.labelMedium)
                    TextButton({ pasted?.let(paste); pasted = null }) { Text("Вставить") }
                }
            }
        }
        Surface(shape = RoundedCornerShape(StarDim.composer), color = palette.raised, shadowElevation = StarDim.sm, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(StarDim.xs)) {
                IconButton(attach) { Icon(Icons.Default.Add, "Прикрепить", tint = palette.accent) }
                androidx.compose.foundation.text.BasicTextField(
                    text, onText,
                    modifier = Modifier.weight(1f).padding(vertical = StarDim.md),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.assistant),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) { focus.clearFocus(); send() } }),
                    decorationBox = { inner -> if (text.isEmpty()) Box { inner(); Text("Сообщение", color = palette.faint, style = MaterialTheme.typography.bodyLarge) } else inner() }
                )
                Spacer(Modifier.width(StarDim.xs))
                if (!busy && text.isBlank()) IconButton(dictate, Modifier.size(44.dp)) { Icon(Icons.Default.Mic, "Голосовой ввод", tint = palette.accent) }
                if (busy) FilledIconButton(stop, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Stop, "Остановить") }
                else FilledIconButton({ if (pricey) confirm = true else send() }, enabled = canSend, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.ArrowUpward, "Отправить") }
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = StarDim.lg, top = StarDim.xs, end = StarDim.sm)) {
            Text("Предварительная оценка · около ${formatRubles(estimate).replace("₽", currency)}", color = palette.faint, style = MaterialTheme.typography.labelMedium)
            Text("Расчёт по длине текста и тарифу модели; итог зависит от ответа. В статистику попадут только данные сервера." + if (photos.isNotEmpty()) " Фото: ${photos.size}." else "", color = palette.faint, style = MaterialTheme.typography.labelSmall)
            Text("Вход ${formatCoefficient(model.pricing.inputCoefficient)} · выход ${formatCoefficient(model.pricing.outputCoefficient)}", color = palette.faint, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("${model.name} — дорогая модель") },
        text = { Text("Коэффициент входа ${formatCoefficient(model.pricing.inputCoefficient)}. Отправить?") },
        confirmButton = { TextButton({ confirm = false; send() }) { Text("Отправить") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Отмена") } }
    )
}

@Composable
private fun SystemPromptDialog(value: String, local: String, onValue: (String) -> Unit, save: () -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("Системный промпт") }, text = {
        Column {
            OutlinedTextField(value, onValue, label = { Text("Общая системная инструкция") }, minLines = 4)
            TextButton({ onValue("") }) { Text("Очистить общую инструкцию") }
            Text("Применяется общая инструкция, затем отдельным блоком инструкция чата или агента.")
            Text("Сейчас применится:\n" + listOf(value.trim(), local.trim().takeIf { it.isNotEmpty() }?.let { "Инструкция чата/агента:\n$it" }).filterNotNull().filter { it.isNotEmpty() }.joinToString("\n\n").ifBlank { "Нет системной инструкции" })
        }
    }, confirmButton = { TextButton(save) { Text("Готово") } }, dismissButton = { TextButton(close) { Text("Отмена") } })
}

private data class Starter(val icon: ImageVector, val title: String, val description: String, val prompt: String)

private val starters = listOf(
    Starter(Icons.Default.MenuBook, "Объясни тему", "Простыми словами, с примером", "Объясни тему простыми словами и приведи короткий пример: "),
    Starter(Icons.Default.Code, "Разбери код", "Найти ошибки и улучшить", "Разбери этот код, найди ошибки и предложи улучшенный вариант:\n"),
    Starter(Icons.Default.Translate, "Переведи", "С сохранением стиля", "Переведи на русский, сохранив смысл и стиль:\n"),
    Starter(Icons.Default.AutoAwesome, "Идеи", "Десять вариантов и лучшие", "Предложи 10 разных идей и выбери лучшие по теме: "),
    Starter(Icons.Default.PhotoCamera, "С фото", "Сначала прикрепите снимок", "Что на этом изображении? "),
    Starter(Icons.Default.History, "Кратко", "Суть в нескольких пунктах", "Изложи кратко, пунктами: ")
)

@Composable
private fun EmptyState(onPick: (String) -> Unit) {
    val palette = LocalStarPalette.current
    Column(Modifier.fillMaxSize().padding(horizontal = StarDim.lg)) {
        Spacer(Modifier.height(StarDim.xxl))
        Text("С чего начнём?", style = MaterialTheme.typography.headlineMedium)
        Text("Выберите заготовку или напишите своё", color = palette.faint, modifier = Modifier.padding(top = StarDim.xs, bottom = StarDim.lg))
        LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(StarDim.md), verticalArrangement = Arrangement.spacedBy(StarDim.md)) {
            items(starters) { starter ->
                Surface(onClick = { onPick(starter.prompt) }, shape = RoundedCornerShape(StarDim.radiusLg), color = palette.raised, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(StarDim.lg)) {
                        Icon(starter.icon, null, tint = palette.accent)
                        Spacer(Modifier.height(StarDim.md))
                        Text(starter.title, fontWeight = FontWeight.SemiBold)
                        Text(starter.description, color = palette.faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = StarDim.xxs))
                    }
                }
            }
        }
    }
}

/** Small preview of one attached photo, with a cross to drop it. */
@Composable
private fun PhotoThumb(photo: Attachment, remove: () -> Unit) {
    val bitmap = remember(photo.data) {
        runCatching { val bytes = android.util.Base64.decode(photo.data, android.util.Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }
    Box(Modifier.size(64.dp)) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), "Фото", Modifier.fillMaxSize().clip(RoundedCornerShape(StarDim.radiusSm)), contentScale = ContentScale.Crop)
        else Surface(Modifier.fillMaxSize(), RoundedCornerShape(StarDim.radiusSm), color = LocalStarPalette.current.raised) {}
        IconButton(remove, Modifier.align(Alignment.TopEnd).size(22.dp)) { Icon(Icons.Default.Close, "Убрать фото", Modifier.size(14.dp)) }
    }
}

/** The image currently on the clipboard, scaled down, or null when there is only text. */
private fun clipboardImage(context: Context): Attachment? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val description = manager.primaryClipDescription ?: return null
    if (!description.hasMimeType("image/*")) return null
    val uri = manager.primaryClip?.getItemAt(0)?.uri ?: return null
    return imagePayload(context, uri)
}

private fun imagePayload(bitmap: Bitmap): Attachment {
    val scale = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height))
    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
    if (scaled !== bitmap) scaled.recycle()
    return Attachment(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), "image/jpeg")
}

private fun imagePayload(context: Context, uri: Uri): Attachment? =
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.let(::imagePayload)
