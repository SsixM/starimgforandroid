package ru.starimg.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.starimg.ai.ui.MainViewModel
import ru.starimg.ai.ui.chat.ChatScreen
import ru.starimg.ai.ui.chat.ChatsScreen
import ru.starimg.ai.ui.models.ModelSheet
import ru.starimg.ai.ui.models.ModelsScreen
import ru.starimg.ai.ui.prompts.LibraryScreen
import ru.starimg.ai.ui.settings.SettingsScreen
import ru.starimg.ai.ui.stats.StatsScreen
import ru.starimg.ai.ui.news.NewsScreen
import ru.starimg.ai.ui.theme.LocalStarPalette
import ru.starimg.ai.ui.theme.StarDim
import ru.starimg.ai.ui.theme.StarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private enum class Screen { Chat, Chats, Models, Library, Stats, Settings, News }

@Composable
private fun App() {
    val context = LocalContext.current
    val vm: MainViewModel = viewModel { MainViewModel(context.applicationContext) }
    val state by vm.state.collectAsState()
    val dark = when (state.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val scale = when (state.textScale) { 0 -> 0.9f; 2 -> 1.15f; else -> 1f }
    val density = LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * scale)) {
        StarTheme(dark) {
            if (!state.ready) Box(Modifier.fillMaxSize().background(LocalStarPalette.current.bg), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (!state.onboarded) Onboarding { key -> vm.saveSettings(key, state.endpoint, state.currency, "", state.themeMode, state.textScale, state.accessToken); vm.finishOnboarding() }
            else Navigation(vm, state)
        }
    }
}

@Composable
private fun Navigation(vm: MainViewModel, state: ru.starimg.ai.ui.AppState) {
    var screen by remember { mutableStateOf(Screen.Chat) }
    var modelSheet by remember { mutableStateOf(false) }
    BackHandler(screen != Screen.Chat) { screen = Screen.Chat }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { AnimatedContent(screen, transitionSpec = {
            val forward = targetState != Screen.Chat
            if (forward) (slideInHorizontally(tween(280)) { -it / 4 } + fadeIn(tween(280))) togetherWith (slideOutHorizontally(tween(280)) { it / 5 } + fadeOut(tween(200)))
            else (slideInVertically(tween(280)) { it / 5 } + fadeIn(tween(280))) togetherWith (slideOutVertically(tween(240)) { it / 6 } + fadeOut(tween(200)))
        }, label = "screen") { current ->
            when (current) {
                Screen.Chat -> ChatScreen(vm, state, { screen = Screen.Chats }, { modelSheet = true }, { screen = Screen.Library }, { screen = Screen.Settings })
                Screen.Chats -> ChatsScreen(vm, state, { screen = Screen.Chat }, { screen = Screen.Stats }, { screen = Screen.Settings }, { screen = Screen.Library }, { screen = Screen.News })
                Screen.Models -> ModelsScreen(vm, state) { screen = Screen.Chat }
                Screen.Library -> LibraryScreen(vm, state, { screen = Screen.Chat }) { body -> vm.send(body); screen = Screen.Chat }
                Screen.Stats -> StatsScreen(vm, state) { screen = Screen.Chat }
                Screen.Settings -> SettingsScreen(vm, state) { screen = Screen.Chat }
                Screen.News -> NewsScreen(vm, state) { screen = Screen.Chat }
            }
        } }
        if (screen in setOf(Screen.Chat, Screen.Chats, Screen.News, Screen.Library)) {
            NavigationBar {
                NavigationBarItem(selected = screen == Screen.Chat, onClick = { screen = Screen.Chat }, icon = { Icon(Icons.Default.AutoAwesome, "Чат") }, label = { Text("Чат") })
                NavigationBarItem(selected = screen == Screen.Chats, onClick = { screen = Screen.Chats }, icon = { Icon(Icons.Default.History, "История") }, label = { Text("История") })
                NavigationBarItem(selected = screen == Screen.News, onClick = { screen = Screen.News }, icon = { Icon(Icons.Default.Newspaper, "Новости") }, label = { Text("Новости") })
                NavigationBarItem(selected = screen == Screen.Library, onClick = { screen = Screen.Library }, icon = { Icon(Icons.Default.MenuBook, "Библиотека") }, label = { Text("Библиотека") })
            }
        }
        }
    }
    if (modelSheet) ModelSheet(vm, state, onCompare = { modelSheet = false; screen = Screen.Models }) { modelSheet = false }
}

private data class OnboardPage(val icon: ImageVector, val title: String, val body: String)

@Composable
private fun Onboarding(done: (String) -> Unit) {
    val palette = LocalStarPalette.current
    var key by remember { mutableStateOf("") }
    val pages = listOf(
        OnboardPage(Icons.Default.AutoAwesome, "Один чат для разных моделей", "История, модель и расходы — всё в одном месте. Переключайтесь между моделями, не теряя разговор."),
        OnboardPage(Icons.Default.Key, "Ключ остаётся на устройстве", "Он шифруется и никуда не отправляется, кроме запросов к вашему API. Без ключа чат не заработает."),
        OnboardPage(Icons.Default.Payments, "Миллион токенов — 4 ₽", "Каждая модель умножает токены на свой коэффициент. Стоимость видна под каждым ответом, лимит задаётся в настройках.")
    )
    val pager = rememberPagerState { pages.size }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.glow.copy(alpha = .22f), palette.bg))).padding(StarDim.xl)) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Surface(Modifier.size(72.dp), CircleShape, color = palette.raised) {
                    Box(contentAlignment = Alignment.Center) { Icon(pages[page].icon, null, Modifier.size(32.dp), tint = palette.accent) }
                }
                Spacer(Modifier.height(StarDim.xl))
                Text(pages[page].title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(StarDim.md))
                Text(pages[page].body, color = palette.faint, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(StarDim.sm), modifier = Modifier.padding(bottom = StarDim.lg)) {
            repeat(3) { index ->
                Box(Modifier.size(if (pager.currentPage == index) 22.dp else 8.dp, 8.dp).background(if (pager.currentPage == index) palette.accent else palette.line, CircleShape))
            }
        }
        if (pager.currentPage == 2) {
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API-ключ") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Spacer(Modifier.height(StarDim.md))
            Button({ done(key.trim()) }, Modifier.fillMaxWidth(), enabled = key.isNotBlank(), shape = RoundedCornerShape(StarDim.radius)) { Text("Продолжить") }
        } else {
            val scope = rememberCoroutineScope()
            TextButton({ scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.fillMaxWidth()) { Text("Дальше") }
        }
    }
}
