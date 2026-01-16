package com.example.livewallpaper.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * 通用文本输入对话框
 *
 * @param title 对话框标题
 * @param initialValue 初始文本值
 * @param label 输入框 label（显示在输入框上方）
 * @param description 输入区说明文案（显示在输入框上方）
 * @param placeholder 输入框占位文本
 * @param confirmText 确认按钮文本
 * @param dismissText 取消按钮文本
 * @param singleLine 是否单行输入
 * @param maxLength 最大字符数，null 表示不限制
 * @param validator 输入验证器，返回 null 表示验证通过，否则返回错误信息
 * @param onConfirm 确认回调，传入输入的文本
 * @param onDismiss 关闭回调
 */
@Composable
fun TextInputDialog(
    title: String,
    initialValue: String = "",
    label: String? = null,
    description: String? = null,
    placeholder: String = "",
    confirmText: String,
    dismissText: String,
    singleLine: Boolean = true,
    maxLength: Int? = null,
    validator: ((String) -> String?)? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 使用 TextFieldValue 以便控制光标位置
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = TextRange(initialValue.length) // 光标放在末尾
            )
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // 自动聚焦
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun validateAndConfirm() {
        val text = textFieldValue.text.trim()
        val error = validator?.invoke(text)
        if (error != null) {
            errorMessage = error
        } else {
            onConfirm(text)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val limitedText = if (maxLength != null && newValue.text.length > maxLength) {
                            newValue.copy(text = newValue.text.take(maxLength))
                        } else {
                            newValue
                        }
                        textFieldValue = limitedText
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = label?.let { labelText ->
                        { Text(text = labelText) }
                    },
                    placeholder = {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 3,
                    maxLines = if (singleLine) 1 else 6,
                    isError = errorMessage != null,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
                    ),
                    keyboardActions = if (singleLine) {
                        KeyboardActions(onDone = { validateAndConfirm() })
                    } else {
                        KeyboardActions()
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (maxLength != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "${textFieldValue.text.length}/$maxLength",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { validateAndConfirm() },
                enabled = textFieldValue.text.trim().isNotBlank()
            ) {
                Text(
                    text = confirmText,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}
