package ru.starimg.ai.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.platform.LocalContext
import ru.starimg.ai.data.model.MessageVersion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.starimg.ai.data.model.Catalog
import ru.starimg.ai.data.model.ChatMessage
import ru.starimg.ai.data.model.formatRubles
import ru.starimg.ai.data.model.formatTokens
import ru.starimg.ai.ui.theme.LocalStarPalette
import ru.starimg.ai.ui.theme.StarDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/** What a long press on a message can do. Each one is optional, so a message only offers what applies. */
data class MessageActions(
    val onCopy: () -> Unit,
    val onShare: () -> Unit,
    val onRegenerate: (() -> Unit)? = null,
    val onEdit: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
    val onQuote: ((String) -> Unit)? = null
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun Bubble(
    message: ChatMessage,
    streaming: Boolean,
    actions: MessageActions,
    onVersion: ((Int) -> Unit)? = null
) {
    val palette = LocalStarPalette.current
    var details by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val shown = message.versionAt(message.activeVersion)
    val text = shown?.text ?: message.text
    val photos = message.photos
    Box {
        if (message.user) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    modifier = Modifier.widthIn(max = 460.dp).combinedClickable(onClick = {}, onLongClick = { menu = true }),
                    // Tail sits at the bottom-right corner; the other corners stay round.
                    shape = RoundedCornerShape(StarDim.bubble, StarDim.bubble, StarDim.xs, StarDim.bubble),
                    color = palette.userBubble
                ) {
                    Column(Modifier.padding(horizontal = StarDim.lg, vertical = StarDim.md)) {
                        PhotoRow(photos)
                        if (text.isNotBlank()) Text(text, color = palette.onUserBubble, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StarDim.sm)) {
                Box(
                    Modifier.padding(top = StarDim.xs).size(28.dp).clip(CircleShape).background(Brush.linearGradient(listOf(palette.glow, palette.userBubble))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(15.dp), tint = palette.onUserBubble) }
                Column(Modifier.weight(1f).combinedClickable(onClick = {}, onLongClick = { if (!streaming) menu = true })) {
                    PhotoRow(photos)
                    val glow = if (streaming) Modifier.drawBehind {
                        drawCircle(palette.glow.copy(alpha = .10f), radius = size.maxDimension, center = center.copy(x = 0f))
                    } else Modifier
                    Box(glow) {
                        if (message.error) Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        else RichText(text, palette.assistant) { actions.onQuote?.invoke(it) }
                    }
                    if (streaming) Cursor() else {
                        Footer(message, shown, actions.onCopy, actions.onRegenerate, actions.onShare) { details = true }
                        if (message.versionCount > 1 && onVersion != null) VersionPager(message.activeVersion, message.versionCount, onVersion)
                    }
                }
            }
        }
        MessageMenu(menu, message.user, actions) { menu = false }
    }
    if (details) UsageDialog(message, shown) { details = false }
}

/** A horizontal strip of the photos attached to one message. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoRow(photos: List<ru.starimg.ai.data.model.Attachment>) {
    if (photos.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(StarDim.sm), modifier = Modifier.padding(bottom = StarDim.sm)) {
        photos.forEach { MessageImage(it.data) }
    }
}

/** Long-press menu. The same set for every message; entries that do not apply are hidden. */
@Composable
private fun MessageMenu(open: Boolean, user: Boolean, actions: MessageActions, close: () -> Unit) {
    DropdownMenu(open, close) {
        DropdownMenuItem(text = { MenuLabel(Icons.Default.ContentCopy, "РљРѕРїРёСЂРѕРІР°С‚СЊ") }, onClick = { close(); actions.onCopy() })
        DropdownMenuItem(text = { MenuLabel(Icons.Default.Share, "РџРѕРґРµР»РёС‚СЊСЃСЏ") }, onClick = { close(); actions.onShare() })
        if (actions.onRegenerate != null) DropdownMenuItem(text = { MenuLabel(Icons.Default.Refresh, "РџРµСЂРµРіРµРЅРµСЂРёСЂРѕРІР°С‚СЊ") }, onClick = { close(); actions.onRegenerate.invoke() })
        if (actions.onEdit != null) DropdownMenuItem(text = { MenuLabel(Icons.Default.Edit, if (user) "РР·РјРµРЅРёС‚СЊ Рё РѕС‚РїСЂР°РІРёС‚СЊ" else "РР·РјРµРЅРёС‚СЊ") }, onClick = { close(); actions.onEdit.invoke() })
        if (actions.onDelete != null) DropdownMenuItem(text = { MenuLabel(Icons.Default.DeleteOutline, "РЈРґР°Р»РёС‚СЊ") }, onClick = { close(); actions.onDelete.invoke() })
    }
}

@Composable
private fun MenuLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp))
        Text(label, Modifier.padding(start = StarDim.md))
    }
}

/** "< 2/3 >" under an answer that has more than one version. */
@Composable
private fun VersionPager(current: Int, count: Int, onVersion: (Int) -> Unit) {
    val palette = LocalStarPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = StarDim.xxs)) {
        IconButton({ onVersion(current - 1) }, Modifier.size(28.dp), enabled = current > 0) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "РџСЂРµРґС‹РґСѓС‰Р°СЏ РІРµСЂСЃРёСЏ", Modifier.size(15.dp), tint = palette.faint)
        }
        Text("${current + 1}/$count", color = palette.faint, style = MaterialTheme.typography.labelMedium)
        IconButton({ onVersion(current + 1) }, Modifier.size(28.dp), enabled = current < count - 1) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "РЎР»РµРґСѓСЋС‰Р°СЏ РІРµСЂСЃРёСЏ", Modifier.size(15.dp), tint = palette.faint)
        }
    }
}

/** A blinking block at the end of a reply still being written. */
@Composable
private fun Cursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        0.15f, 1f,
        infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "cursor-alpha"
    )
    val palette = LocalStarPalette.current
    Box(Modifier.padding(top = StarDim.xs).size(width = 8.dp, height = 16.dp).background(palette.accent.copy(alpha = alpha), RoundedCornerShape(StarDim.xxs)))
}

@Composable
private fun Footer(message: ChatMessage, version: MessageVersion?, copy: () -> Unit, onRetry: (() -> Unit)?, onShare: () -> Unit, details: () -> Unit) {
    val palette = LocalStarPalette.current
    val tokens = version?.totalTokens ?: message.totalTokens
    val input = version?.inputTokens ?: message.inputTokens
    val output = version?.outputTokens ?: message.outputTokens
    val coefficient = version?.coefficient ?: message.coefficient
    val outputCoefficient = version?.outputCoefficient ?: message.outputCoefficient
    val cost = version?.costRubles ?: message.costRubles
    val modelId = version?.modelId ?: message.modelId
    val available = version?.usageAvailable ?: message.usageAvailable
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = StarDim.xs)) {
        val caption = if (available && tokens > 0) {
            val name = Catalog.model(modelId)?.name ?: modelId
            val equivalent = (input * coefficient + output * outputCoefficient).roundToLong()
            "${formatTokens(equivalent)} · ${formatRubles(cost)} · ${(version?.usageSource ?: message.usageSource).name}" + if (name.isNotBlank()) " · $name" else ""
        } else "Нет данных · ожидает сверки"
        SmallIcon(Icons.Default.ContentCopy, "РљРѕРїРёСЂРѕРІР°С‚СЊ", copy)
        if (onRetry != null) SmallIcon(Icons.Default.Refresh, "РџРµСЂРµРіРµРЅРµСЂРёСЂРѕРІР°С‚СЊ", onRetry)
        SmallIcon(Icons.Default.Share, "РџРѕРґРµР»РёС‚СЊСЃСЏ", onShare)
        if (available) SmallIcon(Icons.Default.Info, "РџРѕРґСЂРѕР±РЅРѕСЃС‚Рё", details)
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(28.dp)) { Icon(icon, label, Modifier.size(15.dp), tint = LocalStarPalette.current.faint) }
}

@Composable
private fun MessageImage(base64: String) {
    val bitmap = remember(base64) { runCatching { val bytes = Base64.decode(base64, Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), "РР·РѕР±СЂР°Р¶РµРЅРёРµ", Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(StarDim.radius)).padding(bottom = StarDim.sm), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun UsageDialog(message: ChatMessage, version: MessageVersion?, close: () -> Unit) {
    val modelId = version?.modelId ?: message.modelId
    val model = Catalog.model(modelId)
    val stamp = version?.timestamp ?: message.timestamp
    val time = remember(stamp) { if (stamp == 0L) "вЂ”" else SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(stamp)) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Р—Р°РїСЂРѕСЃ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(StarDim.xs)) {
                Detail("РњРѕРґРµР»СЊ", model?.name ?: modelId.ifBlank { "вЂ”" })
                val available = version?.usageAvailable ?: message.usageAvailable
                Detail("Источник", (version?.usageSource ?: message.usageSource).name)
                Detail("Вход", if (available) formatTokens(version?.inputTokens ?: message.inputTokens) else "Нет данных / ожидает сверки")
                Detail("Выход", if (available) formatTokens(version?.outputTokens ?: message.outputTokens) else "Нет данных / ожидает сверки")
                Detail("Стоимость", if (available) formatRubles(version?.costRubles ?: message.costRubles) else "Нет данных")
                Detail("Всего", if (available) formatTokens(version?.totalTokens ?: message.totalTokens) else "Нет данных / ожидает сверки")
                if (message.versionCount > 1) Detail("Версия", "${message.activeVersion + 1} из ${message.versionCount}")
                Text("Тариф: 1 000 000 эквивалентных токенов = 4 ?", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = { TextButton(close) { Text("Закрыть") } }
                Detail("Стоимость", if (available) formatRubles(version?.costRubles ?: message.costRubles) else "Нет данных")

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

fun shareText(context: android.content.Context, text: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text) }
    context.startActivity(android.content.Intent.createChooser(intent, "РџРѕРґРµР»РёС‚СЊСЃСЏ"))
}
