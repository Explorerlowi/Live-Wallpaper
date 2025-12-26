package com.example.livewallpaper.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 选择项数据类
 */
data class SelectOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val iconContent: (@Composable () -> Unit)? = null
)

/**
 * 简约风格选择对话框
 * 
 * @param options 选项列表
 * @param selectedValue 当前选中的值
 * @param onSelect 选择回调
 * @param onDismiss 关闭回调
 */
@Composable
fun <T> SimpleSelectDialog(
    options: List<SelectOption<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            LazyColumn(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(options) { option ->
                    SelectOptionItem(
                        option = option,
                        isSelected = option.value == selectedValue,
                        onClick = {
                            onSelect(option.value)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SelectOptionItem(
    option: SelectOption<T>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧图标（支持自定义 Composable 或 ImageVector）
        when {
            option.iconContent != null -> {
                Box(modifier = Modifier.size(22.dp)) {
                    option.iconContent.invoke()
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            option.icon != null -> {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
        }
        
        // 文本
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        
        // 选中勾选
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
