package com.example.livewallpaper.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.livewallpaper.core.design.icon.AppIcons

/**
 * 一批生成请求结束后的汇总。
 *
 * @property title 弹窗标题
 * @property successLabel 成功数量的标签
 * @property failureLabel 失败数量的标签
 * @property successCount 成功的请求数
 * @property failureCount 失败的请求数
 * @property confirmLabel 确认按钮文案
 */
data class GenerationBatchDialogSummary(
    val title: String,
    val successLabel: String,
    val failureLabel: String,
    val successCount: Int,
    val failureCount: Int,
    val confirmLabel: String,
)

/**
 * 展示生成汇总的模态弹窗。
 *
 * 点击遮罩或系统返回都不会关闭，只能通过确认按钮收起。
 *
 * @param summary 需要展示的汇总；为空时不显示弹窗
 * @param onConfirm 用户确认后回调
 */
@Composable
fun GenerationBatchDialog(
    summary: GenerationBatchDialogSummary?,
    onConfirm: () -> Unit,
) {
    val shown = summary ?: return
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        GenerationBatchDialogCard(summary = shown, onConfirm = onConfirm)
    }
}

@Composable
private fun GenerationBatchDialogCard(
    summary: GenerationBatchDialogSummary,
    onConfirm: () -> Unit,
) {
    val failureTint = if (summary.failureCount > 0) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            CountRow(summary = summary, failureTint = failureTint)
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text(text = summary.confirmLabel)
            }
        }
    }
}

@Composable
private fun CountRow(
    summary: GenerationBatchDialogSummary,
    failureTint: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BatchCountCell(
            modifier = Modifier.weight(1f),
            icon = AppIcons.checkCircle,
            label = summary.successLabel,
            count = summary.successCount,
            tint = MaterialTheme.colorScheme.primary,
        )
        BatchCountCell(
            modifier = Modifier.weight(1f),
            icon = AppIcons.warning,
            label = summary.failureLabel,
            count = summary.failureCount,
            tint = failureTint,
        )
    }
}

@Composable
private fun BatchCountCell(
    icon: ImageVector,
    label: String,
    count: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = tint.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
            Column {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                )
            }
        }
    }
}
