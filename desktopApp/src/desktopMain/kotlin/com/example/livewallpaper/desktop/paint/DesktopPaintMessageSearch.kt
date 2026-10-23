package com.example.livewallpaper.desktop.paint

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.livewallpaper.core.design.icon.AppIcons
import com.example.livewallpaper.desktop.LocalDesktopStrings
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessageSearchHit
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintMessageSearchUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面端会话内搜索栏，显示在顶栏与消息列表之间。
 *
 * 键盘：`Enter` 定位到更早的一条命中，`Shift+Enter` 定位到更新的一条，`Esc` 关闭搜索。
 *
 * @param state 当前搜索状态。
 * @param onQueryChange 输入变化回调。
 * @param onStep 步进回调，`true` 走向更新的命中，`false` 走向更早的命中。
 * @param onClose 关闭搜索回调。
 * @param onFieldFocused 输入框重新获得焦点时的回调，用于再次展开结果面板。
 */
@Composable
internal fun DesktopPaintMessageSearchBar(
    state: PaintMessageSearchUiState,
    onQueryChange: (String) -> Unit,
    onStep: (forward: Boolean) -> Unit,
    onClose: () -> Unit,
    onFieldFocused: () -> Unit,
) {
    val strings = LocalDesktopStrings.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val hasHits = state.hits.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 720.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFieldFocused() }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            if (hasHits) onStep(event.isShiftPressed)
                            true
                        }
                        Key.Escape -> {
                            onClose()
                            true
                        }
                        else -> false
                    }
                },
            placeholder = { Text(strings.paintSearchHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                Icon(
                    imageVector = AppIcons.search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (state.query.isNotBlank()) {
                    Text(
                        text = strings.paintSearchResultCount(
                            if (hasHits) state.currentHitIndex + 1 else 0,
                            state.hits.size,
                        ),
                        modifier = Modifier.padding(end = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        IconButton(onClick = { onStep(false) }, enabled = hasHits) {
            Icon(AppIcons.chevronUp, contentDescription = strings.paintSearchPrevious)
        }
        IconButton(onClick = { onStep(true) }, enabled = hasHits) {
            Icon(AppIcons.chevronDown, contentDescription = strings.paintSearchNext)
        }
        IconButton(onClick = onClose) {
            Icon(AppIcons.close, contentDescription = strings.paintSearchClose)
        }
    }
}

/**
 * 桌面端搜索结果面板，覆盖在消息列表顶部；命中按最新在前排列，点击后跳转到对应消息。
 *
 * @param state 当前搜索状态。
 * @param onHitClick 点击某条命中时回调对应的消息 id。
 * @param modifier 外部布局修饰符。
 */
@Composable
internal fun DesktopPaintMessageSearchResultsPanel(
    state: PaintMessageSearchUiState,
    onHitClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalDesktopStrings.current
    val currentId = state.currentHit?.messageId
    val newestFirst = remember(state.hits) { state.hits.asReversed() }
    Surface(
        modifier = modifier.widthIn(max = 720.dp).fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 10.dp,
    ) {
        if (newestFirst.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = strings.paintSearchNoResults,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                items(newestFirst, key = { it.messageId }) { hit ->
                    DesktopPaintMessageSearchHitRow(
                        hit = hit,
                        isCurrent = hit.messageId == currentId,
                        onClick = { onHitClick(hit.messageId) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            }
        }
    }
}

@Composable
private fun DesktopPaintMessageSearchHitRow(
    hit: PaintMessageSearchHit,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalDesktopStrings.current
    val senderText = if (hit.senderIdentity == SenderIdentity.USER) {
        strings.paintSearchSenderUser
    } else {
        strings.paintSearchSenderAssistant
    }
    val timeText = remember(hit.createdAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(hit.createdAt))
    }
    val highlightColor = MaterialTheme.colorScheme.primary
    val snippet = remember(hit.snippet, hit.snippetMatchRange, highlightColor) {
        highlightedSearchSnippet(hit, highlightColor)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = senderText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = snippet,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun highlightedSearchSnippet(hit: PaintMessageSearchHit, highlightColor: Color): AnnotatedString {
    val range = hit.snippetMatchRange
    val snippet = hit.snippet
    val start = range.first.coerceIn(0, snippet.length)
    val end = (range.last + 1).coerceIn(start, snippet.length)
    return buildAnnotatedString {
        append(snippet.substring(0, start))
        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
            append(snippet.substring(start, end))
        }
        append(snippet.substring(end))
    }
}
