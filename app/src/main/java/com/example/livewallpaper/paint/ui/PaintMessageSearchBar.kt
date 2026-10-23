package com.example.livewallpaper.paint.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.livewallpaper.R
import com.example.livewallpaper.core.design.icon.AppIcons
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessageSearchHit
import com.example.livewallpaper.feature.aipaint.domain.model.SenderIdentity
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintMessageSearchUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话内关键字搜索的顶栏，替代普通顶栏显示。
 *
 * 包含返回按钮、输入框、`当前/总数` 计数以及上一条 / 下一条按钮。输入框在首次显示时自动聚焦，
 * 软键盘的「搜索」动作会定位到更早的一条命中。
 *
 * @param state 当前搜索状态。
 * @param onQueryChange 输入变化回调。
 * @param onStep 步进回调，`true` 走向更新的命中，`false` 走向更早的命中。
 * @param onClose 关闭搜索回调。
 * @param onFieldFocused 输入框重新获得焦点时的回调，用于再次展开结果面板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaintMessageSearchTopBar(
    state: PaintMessageSearchUiState,
    onQueryChange: (String) -> Unit,
    onStep: (forward: Boolean) -> Unit,
    onClose: () -> Unit,
    onFieldFocused: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val hasHits = state.hits.isNotEmpty()

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(AppIcons.arrowBack, contentDescription = stringResource(R.string.paint_search_close))
            }
        },
        title = {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onFieldFocused() },
                placeholder = {
                    Text(
                        text = stringResource(R.string.paint_search_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (hasHits) onStep(false) }),
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                AppIcons.close,
                                contentDescription = stringResource(R.string.paint_search_close),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        },
        actions = {
            if (state.query.isNotBlank()) {
                Text(
                    text = stringResource(
                        R.string.paint_search_result_count,
                        if (hasHits) state.currentHitIndex + 1 else 0,
                        state.hits.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            IconButton(onClick = { onStep(false) }, enabled = hasHits) {
                Icon(AppIcons.chevronUp, contentDescription = stringResource(R.string.paint_search_previous))
            }
            IconButton(onClick = { onStep(true) }, enabled = hasHits) {
                Icon(AppIcons.chevronDown, contentDescription = stringResource(R.string.paint_search_next))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/**
 * 搜索结果面板，覆盖在消息列表顶部；命中按最新在前排列，点击后跳转到对应消息。
 *
 * @param state 当前搜索状态，使用其中的 `hits` 与 `currentHitIndex`。
 * @param onHitClick 点击某条命中时回调对应的消息 id。
 * @param modifier 外部布局修饰符。
 */
@Composable
internal fun PaintMessageSearchResults(
    state: PaintMessageSearchUiState,
    onHitClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentId = state.currentHit?.messageId
    val newestFirst = remember(state.hits) { state.hits.asReversed() }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        if (newestFirst.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.paint_search_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(newestFirst, key = { it.messageId }) { hit ->
                    PaintMessageSearchHitRow(
                        hit = hit,
                        isCurrent = hit.messageId == currentId,
                        onClick = { onHitClick(hit.messageId) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun PaintMessageSearchHitRow(
    hit: PaintMessageSearchHit,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val senderText = stringResource(
        if (hit.senderIdentity == SenderIdentity.USER) {
            R.string.paint_search_sender_user
        } else {
            R.string.paint_search_sender_assistant
        },
    )
    val timeText = remember(hit.createdAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(hit.createdAt))
    }
    val highlightColor = MaterialTheme.colorScheme.primary
    val snippet = remember(hit.snippet, hit.snippetMatchRange, highlightColor) {
        highlightedSnippet(hit, highlightColor)
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
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun highlightedSnippet(hit: PaintMessageSearchHit, highlightColor: Color): AnnotatedString {
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
