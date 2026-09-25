package ru.starimg.ai.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import ru.starimg.ai.ui.AppState
import ru.starimg.ai.ui.MainViewModel
import ru.starimg.ai.ui.theme.LocalStarPalette
import ru.starimg.ai.ui.theme.StarDim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, state: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalStarPalette.current
    var key by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var access by remember(state.accessToken) { mutableStateOf(state.accessToken) }
    var money by remember(state.currency) { mutableStateOf(state.currency) }
    var limit by remember(state.spendLimit) { mutableStateOf(if (state.spendLimit == 0.0) "" else state.spendLimit.toString()) }
    var theme by remember(state.themeMode) { mutableIntStateOf(state.themeMode) }
    var scale by remember(state.textScale) { mutableIntStateOf(state.textScale) }
    var saved by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    Scaffold(containerColor = palette.bg, topBar = { TopAppBar(title = { Text("Настройки") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = StarDim.lg), verticalArrangement = Arrangement.spacedBy(StarDim.md)) {
            SectionCard(Icons.Default.Cloud, "Подключение") {
                OutlinedTextField(key, { key = it; saved = false }, Modifier.fillMaxWidth(), label = { Text("API-ключ") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Text("Ключ шифруется и хранится только на этом устройстве.", color = palette.faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = StarDim.xs))
                OutlinedTextField(access, { access = it.trim(); saved = false }, Modifier.fillMaxWidth().padding(top = StarDim.sm), label = { Text("Access token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Text("Короткий токен для баланса и истории запросов. Хранится так же, как ключ.", color = palette.faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = StarDim.xs))
            }
            SectionCard(Icons.Default.Palette, "Внешний вид") {
                OutlinedTextField(money, { money = it.take(3); saved = false }, Modifier.fillMaxWidth(), label = { Text("Валюта") }, singleLine = true)
                Text("Тема", modifier = Modifier.padding(top = StarDim.md))
                ChipRow(listOf("Как в системе", "Светлая", "Тёмная"), theme) { theme = it; saved = false }
                Text("Размер текста", modifier = Modifier.padding(top = StarDim.sm))
                ChipRow(listOf("Мелкий", "Обычный", "Крупный"), scale) { scale = it; saved = false }
            }
            SectionCard(Icons.Default.Speed, "Лимиты") {
                OutlinedTextField(limit, { limit = it; saved = false }, Modifier.fillMaxWidth(), label = { Text("Лимит за месяц, пусто — без лимита") }, singleLine = true)
                Text("1 000 000 эквивалентных токенов стоит 4 ₽.", color = palette.faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = StarDim.xs))
            }
            Button({ vm.saveSettings(key, state.endpoint, money, limit, theme, scale, access); vm.refreshModels(); saved = true }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Save, null); Text(if (saved) "  Сохранено" else "  Сохранить")
            }
            SectionCard(Icons.Default.Storage, "Данные") {
                OutlinedButton({
                    val send = Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_SUBJECT, "starchat-settings.json"); putExtra(Intent.EXTRA_TEXT, vm.exportSettings()) }
                    context.startActivity(Intent.createChooser(send, "Экспорт настроек"))
                }, Modifier.fillMaxWidth()) { Text("Экспорт настроек") }
                OutlinedButton({ importing = true }, Modifier.fillMaxWidth().padding(top = StarDim.sm)) { Text("Импорт настроек") }
                OutlinedButton({ confirmClear = true }, Modifier.fillMaxWidth().padding(top = StarDim.sm)) { Text("Очистить локальные данные", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Удалить всё на устройстве?") }, text = { Text("Чаты, ключ, статистика и настройки будут стёрты.") }, confirmButton = { TextButton({ vm.clearLocalData(); confirmClear = false; onBack() }) { Text("Удалить") } }, dismissButton = { TextButton({ confirmClear = false }) { Text("Отмена") } })
    if (importing) AlertDialog(onDismissRequest = { importing = false }, title = { Text("Импорт") }, text = { OutlinedTextField(importText, { importText = it }, label = { Text("Вставьте JSON настроек") }, minLines = 4) }, confirmButton = { TextButton({ if (vm.importSettings(importText)) importing = false }) { Text("Импортировать") } }, dismissButton = { TextButton({ importing = false }) { Text("Отмена") } })
}

@Composable
private fun SectionCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    val palette = LocalStarPalette.current
    Surface(shape = RoundedCornerShape(StarDim.radiusLg), color = palette.raised, modifier = Modifier.fillMaxWidth().padding(top = StarDim.sm)) {
        Column(Modifier.padding(StarDim.lg)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(icon, null, tint = palette.accent)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = StarDim.sm))
            }
            Column(Modifier.padding(top = StarDim.md)) { content() }
        }
    }
}

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.padding(top = StarDim.xs), horizontalArrangement = Arrangement.spacedBy(StarDim.sm)) {
        labels.forEachIndexed { index, label -> FilterChip(selected == index, { onSelect(index) }, label = { Text(label) }) }
    }
}
