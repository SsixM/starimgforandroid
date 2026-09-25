package ru.starimg.ai.ui.prompts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.starimg.ai.data.model.Agent
import ru.starimg.ai.data.model.Catalog
import ru.starimg.ai.data.model.Prompt
import ru.starimg.ai.ui.MainViewModel
import ru.starimg.ai.ui.theme.StarDim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: MainViewModel, state: ru.starimg.ai.ui.AppState, onBack: () -> Unit, onUsePrompt: (String) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var editingPrompt by remember { mutableStateOf(false) }
    var editingAgent by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(if (tab == 0) "Промпты" else "Агенты") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } }, actions = { FilledIconButton({ if (tab == 0) editingPrompt = true else editingAgent = true }, Modifier.padding(end = StarDim.sm)) { Icon(Icons.Default.Add, "Добавить") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = StarDim.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(StarDim.sm)) {
                androidx.compose.material3.FilterChip(tab == 0, { tab = 0 }, label = { Text("Промпты") })
                androidx.compose.material3.FilterChip(tab == 1, { tab = 1 }, label = { Text("Агенты") })
            }
            if (tab == 0) {
                val all = Catalog.prompts + state.customPrompts
                LazyColumn(verticalArrangement = Arrangement.spacedBy(StarDim.sm), modifier = Modifier.padding(top = StarDim.sm)) {
                    items(all) { prompt ->
                        Surface(onClick = { onUsePrompt(prompt.body) }, shape = RoundedCornerShape(StarDim.lg), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(StarDim.md), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(prompt.title, fontWeight = FontWeight.SemiBold); Text(prompt.description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                if (prompt in state.customPrompts) IconButton({ vm.deletePrompt(prompt) }) { Icon(Icons.Default.DeleteOutline, "Удалить") }
                            }
                        }
                    }
                }
            } else {
                val all = Catalog.agents + state.customAgents
                LazyColumn(verticalArrangement = Arrangement.spacedBy(StarDim.sm), modifier = Modifier.padding(top = StarDim.sm)) {
                    items(all) { agent ->
                        Surface(onClick = { vm.setAgent(agent); onBack() }, shape = RoundedCornerShape(StarDim.lg), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(StarDim.md), verticalAlignment = Alignment.CenterVertically) {
                                Text(agent.icon, style = MaterialTheme.typography.titleLarge)
                                Column(Modifier.weight(1f).padding(start = StarDim.md)) { Text(agent.title, fontWeight = FontWeight.SemiBold); Text(agent.description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                if (agent in state.customAgents) IconButton({ vm.deleteAgent(agent) }) { Icon(Icons.Default.DeleteOutline, "Удалить") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (editingPrompt) EditorDialog("Новый промпт", listOf("Название", "Описание", "Инструкция")) { values ->
        if (values != null && values[0].isNotBlank() && values[2].isNotBlank()) vm.addPrompt(Prompt(values[0], values[1], values[2]))
        editingPrompt = false
    }
    if (editingAgent) EditorDialog("Новый агент", listOf("Название", "Описание", "Системная инструкция")) { values ->
        if (values != null && values[0].isNotBlank() && values[2].isNotBlank()) vm.addAgent(Agent(values[0], "✦", values[1], values[2]))
        editingAgent = false
    }
}

@Composable
private fun EditorDialog(title: String, fields: List<String>, done: (List<String>?) -> Unit) {
    val values = remember { fields.map { mutableStateOf("") } }
    AlertDialog(onDismissRequest = { done(null) }, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(StarDim.sm)) {
            fields.forEachIndexed { index, label -> OutlinedTextField(values[index].value, { values[index].value = it }, label = { Text(label) }, minLines = if (index == 2) 3 else 1) }
        }
    }, confirmButton = { TextButton({ done(values.map { it.value }) }) { Text("Сохранить") } }, dismissButton = { TextButton({ done(null) }) { Text("Отмена") } })
}
