package ru.starimg.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import ru.starimg.ai.ui.components.shareText
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.starimg.ai.data.model.ChatSession
import ru.starimg.ai.ui.AppState
import ru.starimg.ai.ui.MainViewModel
import ru.starimg.ai.ui.startOfDay
import ru.starimg.ai.ui.theme.LocalStarPalette
import ru.starimg.ai.ui.theme.StarDim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(vm: MainViewModel, state: AppState, onBack: () -> Unit, onStats: () -> Unit, onSettings: () -> Unit, onLibrary: () -> Unit) {
    val palette = LocalStarPalette.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<ChatSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var filing by remember { mutableStateOf<ChatSession?>(null) }
    var folderText by remember { mutableStateOf("") }
    var tagging by remember { mutableStateOf<ChatSession?>(null) }
    var tagText by remember { mutableStateOf("") }
    val today = startOfDay()
    val matched = state.chats.filter { chat ->
        (folder.isBlank() || chat.folder == folder) &&
            (tag.isBlank() || tag in chat.tags) &&
            (query.isBlank() || chat.title.contains(query, ignoreCase = true) ||
                chat.messages.any { it.shownText.contains(query, ignoreCase = true) })
    }
    val pinned = matched.filter { it.pinned }.sortedByDescending { it.createdAt }
    val recent = matched.filter { !it.pinned && (it.messages.lastOrNull()?.timestamp ?: it.createdAt) >= today }
    val earlier = matched.filter { !it.pinned && (it.messages.lastOrNull()?.timestamp ?: it.createdAt) < today }
    Scaffold(containerColor = palette.bg, topBar = {
        TopAppBar(title = { Text("Чаты") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } }, actions = {
            IconButton({ vm.newChat(); onBack() }) { Icon(Icons.Default.Add, "Новый чат") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = StarDim.xl)) {
            item {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = StarDim.lg, vertical = StarDim.sm),
                    singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Поиск по всем чатам") }
                )
            }
            if (vm.folders.isNotEmpty() || vm.allTags.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = StarDim.lg), horizontalArrangement = Arrangement.spacedBy(StarDim.sm)) {
                    if (folder.isNotBlank() || tag.isNotBlank()) FilterChip(true, { folder = ""; tag = "" }, { Text("Все") })
                    vm.folders.forEach { name -> FilterChip(folder == name, { folder = if (folder == name) "" else name }, { Text(name) }, leadingIcon = { Icon(Icons.Default.Folder, null, Modifier.size(16.dp)) }) }
                    vm.allTags.forEach { name -> FilterChip(tag == name, { tag = if (tag == name) "" else name }, { Text("#$name") }) }
                }
            }
            item { Section("Закреплённые", pinned, state.currentId, vm, { pendingDelete = it }, onBack, { renaming = it; renameText = it.title }, { filing = it; folderText = it.folder }, { tagging = it; tagText = it.tags.joinToString(", ") }, context) }
            item { Section("Сегодня", recent, state.currentId, vm, { pendingDelete = it }, onBack, { renaming = it; renameText = it.title }, { filing = it; folderText = it.folder }, { tagging = it; tagText = it.tags.joinToString(", ") }, context) }
            item { Section("Ранее", earlier, state.currentId, vm, { pendingDelete = it }, onBack, { renaming = it; renameText = it.title }, { filing = it; folderText = it.folder }, { tagging = it; tagText = it.tags.joinToString(", ") }, context) }
            if (matched.isEmpty()) item { Text("Ничего не нашлось", color = palette.faint, modifier = Modifier.padding(StarDim.lg)) }
            item {
                HorizontalDivider(Modifier.padding(vertical = StarDim.sm), color = palette.line)
                FooterRow(Icons.Default.MenuBook, "Библиотека", onLibrary)
                FooterRow(Icons.Default.BarChart, "Расходы", onStats)
                FooterRow(Icons.Default.Settings, "Настройки", onSettings)
            }
        }
    }
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null }, title = { Text("Удалить чат?") },
            text = { Text("Сообщения и статистика этого чата исчезнут.") },
            confirmButton = { TextButton({ vm.deleteChat(id); pendingDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton({ pendingDelete = null }) { Text("Отмена") } }
        )
    }
    renaming?.let { chat ->
        AlertDialog(
            onDismissRequest = { renaming = null }, title = { Text("Переименовать") },
            text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("Название") }, singleLine = true) },
            confirmButton = { TextButton({ vm.renameChat(chat.id, renameText); renaming = null }) { Text("Готово") } },
            dismissButton = { TextButton({ renaming = null }) { Text("Отмена") } }
        )
    }
    filing?.let { chat ->
        AlertDialog(
            onDismissRequest = { filing = null }, title = { Text("Папка") },
            text = { OutlinedTextField(folderText, { folderText = it }, label = { Text("Название папки") }, placeholder = { Text("Пусто — без папки") }, singleLine = true) },
            confirmButton = { TextButton({ vm.setFolder(chat.id, folderText); filing = null }) { Text("Готово") } },
            dismissButton = { TextButton({ filing = null }) { Text("Отмена") } }
        )
    }
    tagging?.let { chat ->
        AlertDialog(
            onDismissRequest = { tagging = null }, title = { Text("Теги") },
            text = { OutlinedTextField(tagText, { tagText = it }, label = { Text("Через запятую") }, singleLine = true) },
            confirmButton = { TextButton({ vm.setTags(chat.id, tagText.split(",")); tagging = null }) { Text("Готово") } },
            dismissButton = { TextButton({ tagging = null }) { Text("Отмена") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Section(
    title: String, chats: List<ChatSession>, current: String, vm: MainViewModel, onDelete: (String) -> Unit, onOpen: () -> Unit,
    onRename: (ChatSession) -> Unit, onFolder: (ChatSession) -> Unit, onTag: (ChatSession) -> Unit, context: android.content.Context
) {
    if (chats.isEmpty()) return
    Text(title, color = LocalStarPalette.current.faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = StarDim.lg, top = StarDim.lg, bottom = StarDim.xs))
    chats.forEach { chat ->
        ChatRow(chat, chat.id == current, { vm.selectChat(chat.id); onOpen() }, { vm.togglePin(chat.id) }, { onDelete(chat.id) }, { onRename(chat) }, { onFolder(chat) }, { onTag(chat) }, { shareText(context, vm.chatAsMarkdown(chat)) }, { shareText(context, vm.chatAsText(chat)) })
    }
}

@Composable
private fun ChatRow(
    chat: ChatSession, selected: Boolean, open: () -> Unit, pin: () -> Unit, delete: () -> Unit,
    rename: () -> Unit, folder: () -> Unit, tag: () -> Unit, shareMarkdown: () -> Unit, shareText: () -> Unit
) {
    val palette = LocalStarPalette.current
    var menu by remember { mutableStateOf(false) }
    val dismiss = rememberSwipeToDismissBoxState()
    if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) {
        androidx.compose.runtime.LaunchedEffect(dismiss.currentValue) {
            if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) delete() else pin()
            dismiss.reset()
        }
    }
    SwipeToDismissBox(
        state = dismiss,
        backgroundContent = {
            val pinning = dismiss.targetValue == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().background(if (pinning) palette.raised else MaterialTheme.colorScheme.errorContainer).padding(horizontal = StarDim.lg),
                horizontalArrangement = if (pinning) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) { Icon(if (pinning) Icons.Default.PushPin else Icons.Default.DeleteOutline, null) }
        },
        enableDismissFromStartToEnd = true, enableDismissFromEndToStart = true
    ) {
        Row(
            Modifier.fillMaxWidth().background(if (selected) palette.raised else palette.bg).clickable(onClick = open).padding(horizontal = StarDim.lg, vertical = StarDim.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (chat.pinned) Icon(Icons.Outlined.PushPin, null, Modifier.size(14.dp).padding(end = StarDim.xs), tint = palette.accent)
                    Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) palette.accent else palette.assistant)
                }
                val meta = buildList {
                    if (chat.folder.isNotBlank()) add(chat.folder)
                    addAll(chat.tags.map { "#$it" })
                    add("${chat.messages.size} сообщ.")
                }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.labelMedium, color = palette.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Действия") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Переименовать") }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) }, onClick = { menu = false; rename() })
                    DropdownMenuItem(text = { Text("Папка") }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }, onClick = { menu = false; folder() })
                    DropdownMenuItem(text = { Text("Теги") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) }, onClick = { menu = false; tag() })
                    DropdownMenuItem(text = { Text("Экспорт в Markdown") }, leadingIcon = { Icon(Icons.Default.IosShare, null) }, onClick = { menu = false; shareMarkdown() })
                    DropdownMenuItem(text = { Text("Экспорт в текст") }, leadingIcon = { Icon(Icons.Default.IosShare, null) }, onClick = { menu = false; shareText() })
                    DropdownMenuItem(text = { Text(if (chat.pinned) "Открепить" else "Закрепить") }, leadingIcon = { Icon(Icons.Default.PushPin, null) }, onClick = { menu = false; pin() })
                    DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { menu = false; delete() })
                }
            }
        }
    }
}

@Composable
private fun FooterRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = StarDim.lg, vertical = StarDim.md), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalStarPalette.current.faint)
        Text(label, Modifier.padding(start = StarDim.lg))
    }
}
