package com.example.livewallpaper.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 通用确认对话框组件
 * 
 * @param title 对话框标题
 * @param message 对话框消息内容
 * @param confirmText 确认按钮文本，默认为"确认"
 * @param dismissText 取消按钮文本，默认为"取消"
 * @param isDangerous 是否为危险操作（如删除），会将确认按钮显示为错误色
 * @param onConfirm 点击确认按钮的回调
 * @param onDismiss 点击取消或对话框外部的回调
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确认",
    dismissText: String = "取消",
    isDangerous: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            ) 
        },
        text = { 
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            ) 
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text(
                    text = confirmText,
                    color = if (isDangerous) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}
