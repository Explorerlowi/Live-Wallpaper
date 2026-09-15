package com.example.livewallpaper.desktop.paint

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.livewallpaper.core.design.icon.AppIcons
import com.example.livewallpaper.desktop.DesktopStrings
import com.example.livewallpaper.desktop.LocalDesktopStrings
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintDraftHintType
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintSessionAnalytics
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintUiState
import com.example.livewallpaper.feature.aipaint.presentation.state.calculatePaintSessionAnalytics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 统计页内容区最大宽度，避免在超宽窗口上卡片被拉得过长。 */
private val STATS_CONTENT_MAX_WIDTH = 1120.dp

/**
 * 桌面端 AI 绘画「会话统计」页面。
 *
 * 与 Android 端共用 shared 中的 [calculatePaintSessionAnalytics] 聚合逻辑，
 * 桌面端仅负责按宽屏布局（双栏卡片）进行格式化与渲染。
 *
 * @param uiState 当前绘画 UI 状态，统计数据全部由此派生。
 * @param onBack 点击返回按钮时回调，用于关闭统计页回到会话。
 */
@Composable
internal fun DesktopPaintSessionStatsScreen(
    uiState: PaintUiState,
    onBack: () -> Unit,
) {
    val strings = LocalDesktopStrings.current
    val analytics = remember(
        uiState.currentSession,
        uiState.messages,
        uiState.promptText,
        uiState.selectedImages,
        uiState.generatingMessageIds,
        uiState.selectedModel,
        uiState.selectedAspectRatio,
        uiState.selectedResolution,
        uiState.activeProfile,
    ) {
        calculatePaintSessionAnalytics(
            session = uiState.currentSession,
            messages = uiState.messages,
            promptDraft = uiState.promptText,
            selectedImages = uiState.selectedImages,
            generatingCount = uiState.generatingMessageIds.size,
            selectedModel = uiState.selectedModel,
            selectedAspectRatio = uiState.selectedAspectRatio,
            selectedResolution = uiState.selectedResolution,
            activeProfileName = uiState.activeProfile?.name,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        StatsTopBar(title = strings.paintStatsTitle, backLabel = strings.paintStatsBack, onBack = onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            StatsContent(
                analytics = analytics,
                strings = strings,
                modifier = Modifier
                    .widthIn(max = STATS_CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun StatsTopBar(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = AppIcons.chevronLeft,
                    contentDescription = backLabel,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatsContent(
    analytics: PaintSessionAnalytics,
    strings: DesktopStrings,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatsHeroCard(analytics, strings)
        StatsOverviewRow(analytics, strings)
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SessionConfigCard(analytics, strings, Modifier.weight(1f).fillMaxHeight())
            SuccessBreakdownCard(analytics, strings, Modifier.weight(1f).fillMaxHeight())
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DraftStatusCard(analytics, strings, Modifier.weight(1f).fillMaxHeight())
            RecentPromptsCard(analytics, strings, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun StatsHeroCard(analytics: PaintSessionAnalytics, strings: DesktopStrings) {
    val gradients = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.86f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.78f),
    )
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradients))
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = analytics.sessionTitle ?: strings.aiPaintTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.paintStatsSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onPrimary.copy(alpha = 0.82f),
                    )
                }
                if (analytics.generatingCount > 0) {
                    StatusPill(
                        text = strings.paintStatsGeneratingCount(analytics.generatingCount),
                        background = onPrimary.copy(alpha = 0.14f),
                        content = onPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.accessTime,
                    contentDescription = null,
                    tint = onPrimary.copy(alpha = 0.72f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.paintStatsLastGeneratedInline(
                        formatStatsDateTime(analytics.lastGeneratedAt, strings),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = onPrimary.copy(alpha = 0.8f),
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HeroInfoItem(
                    modifier = Modifier.weight(1f),
                    icon = AppIcons.autoAwesome,
                    label = strings.paintStatsModel,
                    value = analytics.modelDisplayName,
                )
                HeroInfoItem(
                    modifier = Modifier.weight(1f),
                    icon = AppIcons.tune,
                    label = strings.paintStatsRatio,
                    value = analytics.aspectRatioDisplayName,
                )
                HeroInfoItem(
                    modifier = Modifier.weight(1f),
                    icon = AppIcons.image,
                    label = strings.paintStatsResolution,
                    value = analytics.resolutionDisplayName ?: strings.paintStatsAuto,
                )
                HeroInfoItem(
                    modifier = Modifier.weight(1f),
                    icon = AppIcons.analytics,
                    label = strings.paintApiSettings,
                    value = analytics.activeProfileName ?: strings.paintNoApi,
                )
            }
        }
    }
}

@Composable
private fun StatsOverviewRow(analytics: PaintSessionAnalytics, strings: DesktopStrings) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatsValueCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            icon = AppIcons.description,
            label = strings.paintStatsTotalPrompts,
            value = analytics.promptCount.toString(),
            accent = MaterialTheme.colorScheme.primary,
        )
        StatsValueCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            icon = AppIcons.flash,
            label = strings.paintStatsTotalAttempts,
            value = analytics.generationAttemptCount.toString(),
            accent = MaterialTheme.colorScheme.tertiary,
        )
        StatsValueCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            icon = AppIcons.collections,
            label = strings.paintStatsTotalOutputs,
            value = analytics.generatedImageCount.toString(),
            accent = MaterialTheme.colorScheme.secondary,
        )
        StatsValueCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            icon = AppIcons.layers,
            label = strings.paintStatsRetryCount,
            value = analytics.retryCount.toString(),
            accent = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SessionConfigCard(
    analytics: PaintSessionAnalytics,
    strings: DesktopStrings,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        icon = AppIcons.tune,
        title = strings.paintStatsSessionConfig,
        modifier = modifier,
    ) {
        StatsInfoRow(strings.paintStatsModel, analytics.modelDisplayName)
        StatsInfoRow(strings.paintStatsRatio, analytics.aspectRatioDisplayName)
        StatsInfoRow(strings.paintStatsResolution, analytics.resolutionDisplayName ?: strings.paintStatsAuto)
        StatsInfoRow(strings.paintStatsCreatedAt, formatStatsDateTime(analytics.createdAt, strings))
        StatsInfoRow(strings.paintStatsUpdatedAt, formatStatsDateTime(analytics.updatedAt, strings))
        StatsInfoRow(strings.paintStatsAvgDuration, formatStatsDuration(analytics.averageDurationMillis, strings))
        StatsInfoRow(strings.paintStatsAvgOutput, formatAverageOutput(analytics.averageOutputPerSuccess, strings))
        StatsInfoRow(
            strings.paintStatsAvgPromptChars,
            analytics.averagePromptChars?.toString() ?: strings.paintStatsPlaceholder,
        )
    }
}

@Composable
private fun SuccessBreakdownCard(
    analytics: PaintSessionAnalytics,
    strings: DesktopStrings,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        icon = AppIcons.checkCircle,
        title = strings.paintStatsExecutionBreakdown,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.paintStatsSuccessRate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${(analytics.successRate * 100f).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { analytics.successRate },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BreakdownStat(
                modifier = Modifier.weight(1f),
                icon = AppIcons.checkCircle,
                label = strings.paintStatsSuccessCount,
                value = analytics.successCount.toString(),
                tint = MaterialTheme.colorScheme.primary,
            )
            BreakdownStat(
                modifier = Modifier.weight(1f),
                icon = AppIcons.warning,
                label = strings.paintStatsFailedCount,
                value = analytics.failedCount.toString(),
                tint = MaterialTheme.colorScheme.error,
            )
            BreakdownStat(
                modifier = Modifier.weight(1f),
                icon = AppIcons.schedule,
                label = strings.paintStatsPendingCount,
                value = analytics.pendingCount.toString(),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AuxiliaryMetric(
                modifier = Modifier.weight(1f),
                label = strings.paintStatsReferenceTotal,
                value = analytics.referenceImageCount.toString(),
            )
            AuxiliaryMetric(
                modifier = Modifier.weight(1f),
                label = strings.paintStatsVisibleThreads,
                value = analytics.threadCount.toString(),
            )
        }
    }
}

@Composable
private fun DraftStatusCard(
    analytics: PaintSessionAnalytics,
    strings: DesktopStrings,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        icon = AppIcons.collections,
        title = strings.paintStatsDraftTitle,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatsValueCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = AppIcons.description,
                label = strings.paintStatsDraftPromptChars,
                value = analytics.draftPromptLength.toString(),
                accent = MaterialTheme.colorScheme.primary,
            )
            StatsValueCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = AppIcons.image,
                label = strings.paintStatsDraftImages,
                value = strings.paintStatsDraftImageUsage(analytics.draftImageCount, analytics.draftImageLimit),
                accent = MaterialTheme.colorScheme.secondary,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        ) {
            Text(
                text = when (analytics.draftHintType) {
                    PaintDraftHintType.Empty -> strings.paintStatsDraftEmpty
                    PaintDraftHintType.LimitReached -> strings.paintStatsDraftLimit
                    PaintDraftHintType.TextReady -> strings.paintStatsDraftTextReady
                    PaintDraftHintType.Ready -> strings.paintStatsDraftReady
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentPromptsCard(
    analytics: PaintSessionAnalytics,
    strings: DesktopStrings,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        icon = AppIcons.accessTime,
        title = strings.paintStatsRecentPrompts,
        modifier = modifier,
    ) {
        if (analytics.recentPrompts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = strings.paintStatsNoPrompts,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                analytics.recentPrompts.forEach { prompt ->
                    RecentPromptItem(
                        timeText = formatStatsDateTime(prompt.timestamp, strings),
                        referenceText = strings.paintStatsPromptReferenceCount(prompt.referenceCount),
                        content = prompt.content.ifBlank { strings.paintStatsPlaceholder },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentPromptItem(
    timeText: String,
    referenceText: String,
    content: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = referenceText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(9.dp).size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun StatsValueCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.09f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BreakdownStat(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun AuxiliaryMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StatsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    background: Color,
    content: Color,
) {
    Surface(shape = RoundedCornerShape(999.dp), color = background) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HeroInfoItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onPrimary.copy(alpha = 0.72f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = onPrimary.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatStatsDateTime(timestamp: Long?, strings: DesktopStrings): String {
    if (timestamp == null || timestamp <= 0L) return strings.paintStatsPlaceholder
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatStatsDuration(durationMillis: Long, strings: DesktopStrings): String {
    if (durationMillis <= 0L) return strings.paintStatsPlaceholder
    val totalSeconds = (durationMillis / 1000L).toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        strings.paintTimeFormatMinutes(minutes, seconds)
    } else {
        strings.paintTimeFormatSeconds(seconds)
    }
}

private fun formatAverageOutput(value: Float?, strings: DesktopStrings): String {
    return value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: strings.paintStatsPlaceholder
}
