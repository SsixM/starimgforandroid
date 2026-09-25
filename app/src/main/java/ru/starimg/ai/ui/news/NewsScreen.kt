package ru.starimg.ai.ui.news

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.starimg.ai.data.model.SiteNews
import ru.starimg.ai.ui.AppState
import ru.starimg.ai.ui.MainViewModel
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(vm: MainViewModel, state: AppState, onBack: () -> Unit) {
    val en = remember { Locale.getDefault().language == "en" }
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadNewsCache(); vm.refreshNews() }
    DisposableEffect(Unit) { onDispose { vm.cancelNewsRefresh() } }
    Scaffold(topBar = { TopAppBar(title = { Text(if (en) "News" else "Новости") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, actions = { IconButton(vm::refreshNews) { Icon(Icons.Default.Refresh, if (en) "Refresh" else "Обновить") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (state.newsLoading && state.news.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.newsUpdatedAt?.let { Text((if (en) "Updated: " else "Обновлено: ") + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(it)), style = MaterialTheme.typography.labelMedium) }
                state.newsError?.let { Text(if (state.news.isNotEmpty()) (if (en) "Offline; showing saved news. " else "Нет сети; показан кэш. ") + it else it, color = MaterialTheme.colorScheme.error) }
                if (!state.newsLoading && state.news.isEmpty() && state.newsError == null) Text(if (en) "No published news yet." else "Опубликованных новостей пока нет.")
            }
            items(state.news, key = { it.id }) { post -> NewsCard(post, en) }
        }
    }
}

@Composable
private fun NewsCard(post: SiteNews, en: Boolean) {
    val title = post.titleEn?.takeIf { en && it.isNotBlank() } ?: post.title
    val body = post.textEn?.takeIf { en && it.isNotBlank() } ?: post.text
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title.ifBlank { "#${post.id}" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (post.tagLabel ?: post.tag)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                Text("#${post.id}", style = MaterialTheme.typography.labelMedium)
                post.date?.let { Text(it.take(10), style = MaterialTheme.typography.labelMedium) }
            }
            post.images.forEach { raw -> SafeNewsImage(raw) }
            Text(body)
        }
    }
}

private fun trustedImage(raw: String): String? = runCatching {
    val uri = URI("https://ai.starimg.ru").resolve(raw.trim())
    if (uri.scheme == "https" && uri.host.equals("ai.starimg.ru", true) && uri.userInfo == null && uri.port in listOf(-1, 443)) uri.toASCIIString() else null
}.getOrNull()

@Composable
private fun SafeNewsImage(raw: String) {
    val url = remember(raw) { trustedImage(raw) } ?: return
    var bytes by remember(url) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(url) {
        bytes = withContext(Dispatchers.IO) {
            runCatching {
                OkHttpClient.Builder().connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS).readTimeout(8, java.util.concurrent.TimeUnit.SECONDS).followRedirects(false).build()
                    .newCall(Request.Builder().url(url).get().build()).execute().use { response -> if (response.isSuccessful) response.body?.bytes()?.takeIf { it.size <= 5_000_000 } else null }
            }.getOrNull()
        }
    }
    bytes?.let { data ->
        val image = remember(data) { BitmapFactory.decodeByteArray(data, 0, data.size) }
        if (image != null) AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } }, update = { it.setImageBitmap(image) }, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
    }
}
