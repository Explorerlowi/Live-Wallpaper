package com.example.livewallpaper.paint.viewmodel

/**
 * 绘画界面的 Toast 消息类型
 * 用于支持国际化
 */
sealed class PaintToastMessage {
    data object PleaseConfigApi : PaintToastMessage()
    data object GenerateSuccess : PaintToastMessage()
    data class GenerateMultipleSuccess(val count: Int) : PaintToastMessage()  // 生成多张图片成功
    data class GenerateFailed(val error: String?) : PaintToastMessage()
    data object Stopped : PaintToastMessage()
    data object SaveSuccess : PaintToastMessage()
    data object Deleted : PaintToastMessage()
    data object EnhanceSuccess : PaintToastMessage()
    data class EnhanceFailed(val error: String?) : PaintToastMessage()
    data object GeneratingInProgress : PaintToastMessage()
    data object DownloadSuccess : PaintToastMessage()
    data class DownloadFailed(val error: String?) : PaintToastMessage()
    data object CannotRegenerate : PaintToastMessage()  // 无法重新生成（用户消息已删除）
}
