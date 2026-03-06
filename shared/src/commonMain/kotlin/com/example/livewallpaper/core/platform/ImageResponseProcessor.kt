package com.example.livewallpaper.core.platform

import com.example.livewallpaper.feature.aipaint.domain.model.GeneratedImageFile
import io.ktor.client.statement.HttpResponse

/**
 * 图片响应流式处理器
 *
 * 将 HTTP 响应体流式写入临时文件，再逐字节扫描提取 base64 图片数据，
 * 解码后直接写入目标图片文件。全程不会将完整响应或完整 base64 数据加载到内存。
 *
 * 平台侧（Android / iOS）提供 actual 实现。
 */
interface ImageResponseProcessor {

    /**
     * 流式处理图片生成响应
     *
     * @param response   HTTP 响应（body 仅消费一次）
     * @param sessionId  会话 ID，用于确定输出目录
     * @param messageId  消息 ID，用于生成文件名
     * @return 保存成功的图片文件信息列表；若未提取到任何图片则返回空列表
     */
    suspend fun processResponse(
        response: HttpResponse,
        sessionId: String,
        messageId: String
    ): List<GeneratedImageFile>
}
