package com.example.livewallpaper.core.platform

import com.example.livewallpaper.feature.aipaint.domain.model.GeneratedImageFile
import com.example.livewallpaper.feature.aipaint.domain.model.GptOutputFormat
import io.ktor.client.statement.HttpResponse

/**
 * GPT Image API 响应处理器
 *
 * GPT 返回的 JSON 格式与 Gemini 不同：
 * ```json
 * {
 *   "data": [
 *     { "b64_json": "iVBORw0KGgo..." }
 *   ]
 * }
 * ```
 *
 * 处理流程与 Gemini 类似：流式写入临时文件 → 扫描 `b64_json` 字段 → 分块解码写入图片文件。
 * 平台侧（Android / iOS）提供具体实现。
 */
interface GptImageResponseProcessor {

    /**
     * 流式处理 GPT 图片生成/编辑响应
     *
     * @param response     HTTP 响应（body 仅消费一次）
     * @param sessionId    会话 ID，用于确定输出目录
     * @param messageId    消息 ID，用于生成文件名
     * @param outputFormat 输出格式，决定文件扩展名
     * @return 保存成功的图片文件信息列表；若未提取到任何图片则返回空列表
     */
    suspend fun processResponse(
        response: HttpResponse,
        sessionId: String,
        messageId: String,
        outputFormat: GptOutputFormat = GptOutputFormat.PNG
    ): List<GeneratedImageFile>
}
