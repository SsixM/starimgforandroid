package ru.starimg.ai.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import ru.starimg.ai.data.model.formatCoefficient
import ru.starimg.ai.data.model.formatTokens
import ru.starimg.ai.ui.MainViewModel
import ru.starimg.ai.ui.theme.StarDim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(vm: MainViewModel, state: ru.starimg.ai.ui.AppState, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var vision by remember { mutableStateOf(false) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var sort by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { vm.refreshModelsIfStale() }
    val selectedModel = vm.selectedModel
    val list = state.let { vm.models }
        .filter { (it.name + it.provider).contains(query, ignoreCase = true) && (!vision || it.vision == true) && (!favoritesOnly || it.id in state.favorites) }
        .let { models -> if (sort == 1) models.sortedBy { it.pricing.inputCoefficient } else models }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Модели") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } }, actions = { IconButton({ vm.refreshModels(force = true) }, enabled = !state.modelsLoading) { Icon(Icons.Default.Refresh, "Обновить с сервера") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = StarDim.lg)) {
            androidx.compose.material3.OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Найти модель") })
            if (selectedModel != null) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(StarDim.md), modifier = Modifier.fillMaxWidth().padding(top = StarDim.sm)) {
                    Text("Текущий выбор: ${selectedModel.name} · ${selectedModel.provider}", Modifier.padding(StarDim.md), fontWeight = FontWeight.SemiBold)
                }
            }
            if (state.modelsLoading) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = StarDim.sm))
            state.modelsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = StarDim.sm)) }
            Row(Modifier.padding(vertical = StarDim.sm), horizontalArrangement = Arrangement.spacedBy(StarDim.sm)) {
                FilterChip(vision, { vision = !vision }, label = { Text("Фото") })
                FilterChip(favoritesOnly, { favoritesOnly = !favoritesOnly }, label = { Text("Избранные") })
                FilterChip(sort == 1, { sort = if (sort == 1) 0 else 1 }, label = { Text("Дешевле") })
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(StarDim.sm), contentPadding = PaddingValues(bottom = StarDim.xl)) {
                list.groupBy { it.provider }.forEach { (provider, models) ->
                    item { Text(provider, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = StarDim.sm)) }
                    items(models, key = { it.id }) { model ->
                        val selected = model.id == state.selectedModel
                        Surface(onClick = { vm.selectModel(model.id); onBack() }, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(StarDim.lg), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = StarDim.md, top = StarDim.xs, bottom = StarDim.xs), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(model.name + if (model.isNew) "  · новое" else "", fontWeight = FontWeight.SemiBold)
                                Text(buildString {
                                    append(model.context.ifBlank { "контекст ?" })
                                    append(if (model.vision == true) " · фото" else " · текст")
                                    model.contextTokens?.let { append(" · ${formatTokens(it)} токенов") }
                                    append(" · вход ${formatCoefficient(model.pricing.inputCoefficient)} · выход ${formatCoefficient(model.pricing.outputCoefficient)}")
                                    if (model.confirmedReasoningParameters?.isNotEmpty() == true) append(" · reasoning: ${model.confirmedReasoningParameters.sorted().joinToString()}")
                                }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                                }
                                IconButton({ vm.toggleFavorite(model.id) }) { Icon(if (model.id in state.favorites) Icons.Default.Star else Icons.Outlined.StarOutline, "В избранное", tint = MaterialTheme.colorScheme.primary) }
                                if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = StarDim.md))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The quick picker opened from the model badge. The full screen stays for comparing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(vm: MainViewModel, state: ru.starimg.ai.ui.AppState, onCompare: () -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val palette = ru.starimg.ai.ui.theme.LocalStarPalette.current
    val selectedModel = vm.selectedModel
    val selectedReasoningModes = vm.selectedReasoningModes
    val grouped = vm.models.filter { (it.name + it.provider).contains(query, ignoreCase = true) }.groupBy { it.provider }
    LaunchedEffect(Unit) { vm.refreshModelsIfStale() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding().padding(horizontal = StarDim.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Выбор модели", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onCompare) { Text("Все модели") }
            }
            selectedModel?.let { Text("Сейчас выбрана: ${it.name} · ${it.provider}", color = palette.accent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = StarDim.sm)) }
            androidx.compose.material3.OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(bottom = StarDim.sm), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Найти модель") })
            Text("Режим reasoning", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = StarDim.md))
            if (selectedReasoningModes.isEmpty()) {
                Text("Для этой модели режимы не подтверждены сервером.", color = palette.faint, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(StarDim.sm)) {
                    FilterChip(state.reasoningMode.isBlank(), { vm.selectReasoningMode("") }, label = { Text("Выкл.") })
                    selectedReasoningModes.forEach { mode ->
                        FilterChip(state.reasoningMode == mode, { vm.selectReasoningMode(mode) }, label = { Text(mode) })
                    }
                }
            }
            if (state.modelsLoading) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            state.modelsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(StarDim.sm), contentPadding = PaddingValues(bottom = StarDim.xl)) {
            grouped.forEach { (provider, models) ->
                    item { Text(provider, color = palette.accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = StarDim.sm)) }
                    items(models, key = { it.id }) { model ->
                        val pricey = model.pricing.inputCoefficient >= 4.0
                        Surface(onClick = { vm.selectModel(model.id); onDismiss() }, color = if (model.id == state.selectedModel) MaterialTheme.colorScheme.primaryContainer else palette.raised, shape = RoundedCornerShape(StarDim.lg), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = StarDim.md, vertical = StarDim.sm)) {
                                Text(model.name + if (model.id == state.selectedModel) "  · выбрана" else "", fontWeight = FontWeight.SemiBold)
                                Text(buildString {
                                    append(model.context.ifBlank { "контекст ?" })
                                    append(if (model.vision == true) " · фото" else " · текст")
                                    append(" · ${formatCoefficient(model.pricing.inputCoefficient)}")
                                    if (model.confirmedReasoningParameters?.isNotEmpty() == true) append(" · reasoning ${model.confirmedReasoningParameters.sorted().joinToString()}")
                                }, color = if (pricey) MaterialTheme.colorScheme.error else palette.faint, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
