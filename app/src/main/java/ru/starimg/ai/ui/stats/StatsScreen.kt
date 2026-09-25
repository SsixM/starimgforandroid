package ru.starimg.ai.ui.stats

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import ru.starimg.ai.data.model.AccountBalance
import ru.starimg.ai.data.model.Catalog
import ru.starimg.ai.data.model.UsageLog
import ru.starimg.ai.data.model.formatRubles
import ru.starimg.ai.data.model.formatTokens
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import ru.starimg.ai.ui.AppState
import ru.starimg.ai.ui.MainViewModel
import ru.starimg.ai.ui.theme.StarDim
import ru.starimg.ai.ui.startOfDay
import ru.starimg.ai.ui.startOfMonth
import ru.starimg.ai.ui.startOfWeek
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: MainViewModel, state: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.accessToken) { vm.refreshTelemetry() }
    var range by remember { mutableIntStateOf(2) }
    var confirmReset by remember { mutableStateOf(false) }
    val from = when (range) { 0 -> startOfDay(); 1 -> startOfWeek(); else -> startOfMonth() }
    val messages = vm.assistantMessages.filter { it.timestamp == 0L || it.timestamp >= from }
    val input = messages.sumOf { it.inputTokens }
    val output = messages.sumOf { it.outputTokens }
    val cost = messages.sumOf { it.costRubles }
    Scaffold(topBar = { TopAppBar(title = { Text("Расходы") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(StarDim.lg), verticalArrangement = Arrangement.spacedBy(StarDim.sm)) {
            item {
                Text("1 000 000 эквивалентных токенов = 4 ₽", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(StarDim.sm), modifier = Modifier.padding(top = StarDim.sm)) {
                    listOf("День", "Неделя", "Месяц").forEachIndexed { index, label ->
                        androidx.compose.material3.FilterChip(range == index, { range = index }, label = { Text(label) })
                    }
                }
            }
            item { BalanceCard(state.balance, state.telemetryError, state.telemetryLoading, state.accessToken.isBlank()) { vm.refreshTelemetry() } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(StarDim.sm), modifier = Modifier.fillMaxWidth()) {
                    Stat("Токены", formatTokens(input + output), Modifier.weight(1f))
                    Stat("Стоимость", formatRubles(cost), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(StarDim.sm), modifier = Modifier.fillMaxWidth()) {
                    Stat("Вход", formatTokens(input), Modifier.weight(1f))
                    Stat("Выход", formatTokens(output), Modifier.weight(1f))
                }
            }
            item { SpendChart(vm, state) }
            item { Text("По моделям", style = MaterialTheme.typography.titleMedium) }
            val byModel = messages.filter { it.totalTokens > 0 }.groupBy { it.modelId.ifBlank { "unknown" } }
            items(byModel.entries.toList()) { (id, group) ->
                val name = Catalog.model(id)?.name ?: id
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(StarDim.md), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(StarDim.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold); Text(formatTokens(group.sumOf { it.totalTokens }), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
                        Text(formatRubles(group.sumOf { it.costRubles }), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item { Text("Запросы с сервера", style = MaterialTheme.typography.titleMedium) }
            if (state.usageLogs.isEmpty()) item {
                Text(
                    if (state.accessToken.isBlank()) "История появится, когда в настройках будет access token." else "Запросов пока нет.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(state.usageLogs.take(50)) { log -> LogRow(log) }
            item { Text("По чатам", style = MaterialTheme.typography.titleMedium) }
            items(state.chats.filter { chat -> chat.messages.any { !it.user && (it.timestamp == 0L || it.timestamp >= from) } }) { chat ->
                val group = chat.messages.filter { !it.user && (it.timestamp == 0L || it.timestamp >= from) }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(StarDim.md), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(StarDim.md), verticalAlignment = Alignment.CenterVertically) {
                        Text(chat.title, Modifier.weight(1f), maxLines = 1)
                        Text(formatRubles(group.sumOf { it.costRubles }), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(StarDim.sm)) {
                    TextButton({ scope.launch { share(context, "starchat.json", vm.exportJson()) } }) { Text("JSON") }
                    TextButton({ share(context, "starchat.csv", vm.exportCsv()) }) { Text("CSV") }
                    TextButton({ confirmReset = true }) { Text("Сбросить", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Сбросить статистику?") }, text = { Text("Токены и стоимость в сообщениях обнулятся. Сами ответы останутся.") }, confirmButton = { TextButton({ vm.resetStats(); confirmReset = false }) { Text("Сбросить") } }, dismissButton = { TextButton({ confirmReset = false }) { Text("Отмена") } })
}

@Composable
private fun BalanceCard(balance: AccountBalance?, error: String?, loading: Boolean, missingToken: Boolean, onRefresh: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(StarDim.lg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(StarDim.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Баланс", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (loading) CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                else TextButton(onRefresh, enabled = !missingToken) { Text("Обновить") }
            }
            when {
                missingToken -> Text("Добавьте access token в настройках, чтобы видеть остаток токенов.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                error != null && balance == null -> Text(error, color = MaterialTheme.colorScheme.error)
                balance != null -> {
                    Text(formatTokens(balance.remaining), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("осталось из ${formatTokens(balance.limit)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(
                        { balance.fraction },
                        Modifier.fillMaxWidth().padding(top = StarDim.sm).height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Text("израсходовано ${formatTokens(balance.used)} · ${"%.0f".format(balance.fraction * 100)}%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = StarDim.xs))
                    if (balance.stale) Text("данные устарели", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: UsageLog) {
    val failed = log.status != "success"
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(StarDim.md), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(StarDim.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(log.model.ifBlank { "—" }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(formatTokens(log.costTokens), color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "${stamp(log.createdAt)} · вход ${formatTokens(log.inputTokens)} · выход ${formatTokens(log.outputTokens)} · кэш ${formatTokens(log.cachedTokens)} · ${log.latencyMs} мс",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium
            )
            if (failed && log.errorMessage.isNotBlank()) Text(log.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private val months = arrayOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

private fun stamp(raw: String): String = runCatching {
    val clean = raw.removeSuffix("Z").substringBefore('.')
    val date = clean.substringBefore('T').split('-')
    val time = clean.substringAfter('T').take(5)
    val calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        set(date[0].toInt(), date[1].toInt() - 1, date[2].toInt(), time.take(2).toInt(), time.takeLast(2).toInt(), 0)
    }
    val local = Calendar.getInstance().apply { timeInMillis = calendar.timeInMillis }
    "${local.get(Calendar.DAY_OF_MONTH)} ${months[local.get(Calendar.MONTH)]}, %02d:%02d".format(local.get(Calendar.HOUR_OF_DAY), local.get(Calendar.MINUTE))
}.getOrDefault(raw.take(16).replace('T', ' '))

@Composable
private fun Stat(title: String, value: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(StarDim.lg), modifier = modifier) {
        Column(Modifier.padding(StarDim.md)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SpendChart(vm: MainViewModel, state: AppState) {
    val days = remember(vm.totalCost, state.chats) {
        (6 downTo 0).map { offset ->
            val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            val from = start.timeInMillis
            val to = from + 86_400_000L
            val label = arrayOf("вс", "пн", "вт", "ср", "чт", "пт", "сб")[start.get(Calendar.DAY_OF_WEEK) - 1]
            label to vm.assistantMessages.filter { it.timestamp in from until to }.sumOf { it.costRubles }
        }
    }
    val max = days.maxOf { it.second }.coerceAtLeast(0.01)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(StarDim.lg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(StarDim.md)) {
            Text("7 дней", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().height(110.dp).padding(top = StarDim.sm), horizontalArrangement = Arrangement.spacedBy(StarDim.xs), verticalAlignment = Alignment.Bottom) {
                days.forEach { (label, value) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Surface(Modifier.fillMaxWidth().height((72f * (value / max)).dp.coerceAtLeast(3.dp)), RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.primary) {}
                        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = StarDim.xs))
                    }
                }
            }
        }
    }
}

private fun share(context: android.content.Context, name: String, body: String) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, name); putExtra(Intent.EXTRA_TEXT, body) }
    context.startActivity(Intent.createChooser(intent, name))
}
