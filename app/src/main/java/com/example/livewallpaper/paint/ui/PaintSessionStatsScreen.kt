package com.example.livewallpaper.paint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livewallpaper.R
import com.example.livewallpaper.feature.aipaint.domain.model.AspectRatio
import com.example.livewallpaper.feature.aipaint.domain.model.PaintMessage
import com.example.livewallpaper.feature.aipaint.domain.model.PaintModel
import com.example.livewallpaper.feature.aipaint.domain.model.PaintSession
import com.example.livewallpaper.feature.aipaint.domain.model.Resolution
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintDraftHintType
import com.example.livewallpaper.feature.aipaint.presentation.state.PaintSessionAnalytics
import com.example.livewallpaper.feature.aipaint.presentation.state.SelectedImage
import com.example.livewallpaper.feature.aipaint.presentation.state.calculatePaintSessionAnalytics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Session statistics screen for the current AI paint conversation.
 */
@Composable
fun PaintSessionStatsScreen(
    currentSession: PaintSession?,
    messages: List<PaintMessage>,
    promptDraft: String,
    selectedImages: List<SelectedImage>,
    generatingCount: Int,
    selectedModel: PaintModel,
    selectedAspectRatio: AspectRatio,
    selectedResolution: Resolution,
    activeProfileName: String?,
    modifier: Modifier = Modifier
) {
    val analytics = remember(
        currentSession,
        messages,
        promptDraft,
        selectedImages,
        generatingCount,
        selectedModel,
        selectedAspectRatio,
        selectedResolution,
        activeProfileName
    ) {
        calculatePaintSessionAnalytics(
            session = currentSession,
            messages = messages,
            promptDraft = promptDraft,
            selectedImages = selectedImages,
            generatingCount = generatingCount,
            selectedModel = selectedModel,
            selectedAspectRatio = selectedAspectRatio,
            selectedResolution = selectedResolution,
            activeProfileName = activeProfileName
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { StatsHeroCard(analytics) }
        item { StatsOverviewSection(analytics) }
        item { SessionConfigCard(analytics) }
        item { SuccessBreakdownCard(analytics) }
        item { DraftStatusCard(analytics) }
        item { RecentPromptsCard(analytics) }
    }
}

@Composable
private fun StatsHeroCard(analytics: PaintSessionAnalytics) {
    val gradients = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.86f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.78f)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradients))
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = analytics.sessionTitle ?: stringResource(R.string.paint_stats_default_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.paint_stats_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                    )
                }
                if (analytics.generatingCount > 0) {
                    StatusPill(
                        text = stringResource(R.string.paint_stats_generating_count, analytics.generatingCount),
                        background = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                        content = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        R.string.paint_stats_last_generated_inline,
                        formatDateTimeText(analytics.lastGeneratedAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeroInfoItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AutoAwesome,
                        label = stringResource(R.string.paint_stats_model),
                        value = analytics.modelDisplayName
                    )
                    HeroInfoItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Tune,
                        label = stringResource(R.string.paint_stats_ratio),
                        value = analytics.aspectRatioDisplayName
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeroInfoItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Image,
                        label = stringResource(R.string.paint_stats_resolution),
                        value = analytics.resolutionDisplayName ?: stringResource(R.string.paint_stats_auto)
                    )
                    HeroInfoItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Analytics,
                        label = stringResource(R.string.paint_api_settings),
                        value = analytics.activeProfileName ?: stringResource(R.string.paint_no_api)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsOverviewSection(analytics: PaintSessionAnalytics) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsValueCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Description,
                label = stringResource(R.string.paint_stats_total_prompts),
                value = analytics.promptCount.toString(),
                accent = MaterialTheme.colorScheme.primary
            )
            StatsValueCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Bolt,
                label = stringResource(R.string.paint_stats_total_attempts),
                value = analytics.generationAttemptCount.toString(),
                accent = MaterialTheme.colorScheme.tertiary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsValueCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Collections,
                label = stringResource(R.string.paint_stats_total_outputs),
                value = analytics.generatedImageCount.toString(),
                accent = MaterialTheme.colorScheme.secondary
            )
            StatsValueCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Layers,
                label = stringResource(R.string.paint_stats_retry_count),
                value = analytics.retryCount.toString(),
                accent = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SessionConfigCard(analytics: PaintSessionAnalytics) {
    SectionCard(
        icon = Icons.Default.Tune,
        title = stringResource(R.string.paint_stats_session_config)
    ) {
        StatsInfoRow(stringResource(R.string.paint_stats_model), analytics.modelDisplayName)
        StatsInfoRow(stringResource(R.string.paint_stats_ratio), analytics.aspectRatioDisplayName)
        StatsInfoRow(
            stringResource(R.string.paint_stats_resolution),
            analytics.resolutionDisplayName ?: stringResource(R.string.paint_stats_auto)
        )
        StatsInfoRow(stringResource(R.string.paint_stats_created_at), formatDateTimeText(analytics.createdAt))
        StatsInfoRow(stringResource(R.string.paint_stats_updated_at), formatDateTimeText(analytics.updatedAt))
        StatsInfoRow(stringResource(R.string.paint_stats_avg_duration), formatDurationText(analytics.averageDurationMillis))
        StatsInfoRow(
            stringResource(R.string.paint_stats_avg_output),
            formatAverageOutputText(analytics.averageOutputPerSuccess)
        )
        StatsInfoRow(
            stringResource(R.string.paint_stats_avg_prompt_chars),
            analytics.averagePromptChars?.toString() ?: stringResource(R.string.paint_stats_placeholder)
        )
    }
}

@Composable
private fun SuccessBreakdownCard(analytics: PaintSessionAnalytics) {
    SectionCard(
        icon = Icons.Default.CheckCircle,
        title = stringResource(R.string.paint_stats_execution_breakdown)
    ) {
        LinearProgressIndicator(
            progress = { analytics.successRate },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BreakdownStat(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CheckCircle,
                label = stringResource(R.string.paint_stats_success_count),
                value = analytics.successCount.toString(),
                tint = MaterialTheme.colorScheme.primary
            )
            BreakdownStat(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.WarningAmber,
                label = stringResource(R.string.paint_stats_failed_count),
                value = analytics.failedCount.toString(),
                tint = MaterialTheme.colorScheme.error
            )
            BreakdownStat(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Schedule,
                label = stringResource(R.string.paint_stats_pending_count),
                value = analytics.pendingCount.toString(),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AuxiliaryMetric(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.paint_stats_reference_total),
                value = analytics.referenceImageCount.toString()
            )
            AuxiliaryMetric(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.paint_stats_visible_threads),
                value = analytics.threadCount.toString()
            )
        }
    }
}

@Composable
private fun DraftStatusCard(analytics: PaintSessionAnalytics) {
    SectionCard(
        icon = Icons.Default.PhotoLibrary,
        title = stringResource(R.string.paint_stats_draft_title)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsValueCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Description,
                label = stringResource(R.string.paint_stats_draft_prompt_chars),
                value = analytics.draftPromptLength.toString(),
                accent = MaterialTheme.colorScheme.primary
            )
            StatsValueCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Image,
                label = stringResource(R.string.paint_stats_draft_images),
                value = stringResource(
                    R.string.paint_stats_draft_image_usage,
                    analytics.draftImageCount,
                    analytics.draftImageLimit
                ),
                accent = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
        ) {
            Text(
                text = stringResource(
                    when (analytics.draftHintType) {
                        PaintDraftHintType.Empty -> R.string.paint_stats_draft_empty
                        PaintDraftHintType.LimitReached -> R.string.paint_stats_draft_limit
                        PaintDraftHintType.TextReady -> R.string.paint_stats_draft_text_ready
                        PaintDraftHintType.Ready -> R.string.paint_stats_draft_ready
                    }
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentPromptsCard(analytics: PaintSessionAnalytics) {
    SectionCard(
        icon = Icons.Default.AccessTime,
        title = stringResource(R.string.paint_stats_recent_prompts)
    ) {
        if (analytics.recentPrompts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.paint_stats_no_prompts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                analytics.recentPrompts.forEach { prompt ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            Text(
                                text = formatDateTimeText(prompt.timestamp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(
                                        R.string.paint_stats_prompt_reference_count,
                                        prompt.referenceCount
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = prompt.content.ifBlank { stringResource(R.string.paint_stats_placeholder) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
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
    accent: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = accent.copy(alpha = 0.09f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(bottom = 14.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
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
    tint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun AuxiliaryMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.wrapContentWidth()
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    background: Color,
    content: Color
) {
    Surface(shape = RoundedCornerShape(999.dp), color = background) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HeroInfoItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun formatDateTimeText(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) {
        return stringResource(R.string.paint_stats_placeholder)
    }
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@Composable
private fun formatDurationText(durationMillis: Long): String {
    if (durationMillis <= 0L) {
        return stringResource(R.string.paint_stats_placeholder)
    }

    val totalSeconds = (durationMillis / 1000L).toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return if (minutes > 0) {
        stringResource(R.string.paint_time_format_minutes, minutes, seconds)
    } else {
        stringResource(R.string.paint_time_format_seconds, seconds)
    }
}

@Composable
private fun formatAverageOutputText(value: Float?): String {
    return value?.let { String.format(Locale.getDefault(), "%.1f", it) }
        ?: stringResource(R.string.paint_stats_placeholder)
}
