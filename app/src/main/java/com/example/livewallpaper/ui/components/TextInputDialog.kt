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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 通用文本输入对话框
 *
 * @param title 对话框标题
 * @param initialValue 初始文本值
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
    placeholder: String = "",
    confirmText: String = "确定",
    dismissText: String = "取消",
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // 标题
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 输入框
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        // 限制最大长度
                        val limitedText = if (maxLength != null && newValue.text.length > maxLength) {
                            newValue.copy(text = newValue.text.take(maxLength))
                        } else {
                            newValue
                        }
                        textFieldValue = limitedText
                        errorMessage = null // 清除错误
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    },
                    singleLine = singleLine,
                    isError = errorMessage != null,
                    supportingText = if (errorMessage != null) {
                        { Text(errorMessage!!) }
                    } else if (maxLength != null) {
                        { Text("${textFieldValue.text.length}/$maxLength") }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { validateAndConfirm() }
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(dismissText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { validateAndConfirm() },
                        enabled = textFieldValue.text.isNotBlank()
                    ) {
                        Text(confirmText)
                    }
                }
            }
        }
    }
}
