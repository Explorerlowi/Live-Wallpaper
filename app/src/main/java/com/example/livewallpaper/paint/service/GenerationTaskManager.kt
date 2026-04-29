package com.example.livewallpaper.paint.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 生成任务的全局状态信息
 */
data class GenerationTaskInfo(
    val messageId: String,
    val sessionId: String,
    val startTime: Long
)

/**
 * 生成任务完成时的结果通知
 */
sealed class GenerationResult {
    data class Success(val imageCount: Int) : GenerationResult()
    data class Failed(val error: String?) : GenerationResult()
    data object Cancelled : GenerationResult()
}

/**
 * 全局生成任务管理器（单例）
 *
 * 将生成任务的协程作用域和状态从 ViewModel 中提取出来，
 * 使其生命周期独立于 Activity/ViewModel，解决退出界面后状态丢失的问题。
 */
object GenerationTaskManager {

    /** 独立的协程作用域，不随 ViewModel 销毁 */
    val generationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** messageId -> Job */
    private val jobs = mutableMapOf<String, Job>()

    /** 当前所有正在生成的任务信息 */
    private val _activeTasks = MutableStateFlow<Map<String, GenerationTaskInfo>>(emptyMap())
    val activeTasks: StateFlow<Map<String, GenerationTaskInfo>> = _activeTasks.asStateFlow()

    /** 生成结果事件（供 ViewModel 监听并转为 toast） */
    private val _resultEvents = MutableSharedFlow<GenerationResult>(extraBufferCapacity = 10)
    val resultEvents: SharedFlow<GenerationResult> = _resultEvents.asSharedFlow()

    /**
     * 注册一个生成任务
     */
    fun register(messageId: String, sessionId: String, startTime: Long, job: Job) {
        jobs[messageId] = job
        _activeTasks.update { it + (messageId to GenerationTaskInfo(messageId, sessionId, startTime)) }
    }

    /**
     * 移除一个已完成的生成任务
     */
    fun unregister(messageId: String) {
        jobs.remove(messageId)
        _activeTasks.update { it - messageId }
    }

    /**
     * 取消并移除指定任务
     */
    fun cancel(messageId: String) {
        jobs[messageId]?.cancel()
        jobs.remove(messageId)
        _activeTasks.update { it - messageId }
    }

    /**
     * 取消所有任务
     */
    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _activeTasks.update { emptyMap() }
    }

    /**
     * 获取每个会话的生成任务数量
     */
    fun getSessionCounts(): Map<String, Int> {
        return _activeTasks.value.values.groupBy { it.sessionId }.mapValues { it.value.size }
    }

    /**
     * 获取最早的生成开始时间
     */
    fun getEarliestStartTime(): Long {
        return _activeTasks.value.values.minOfOrNull { it.startTime } ?: 0L
    }

    /**
     * 发射生成结果事件
     */
    fun emitResult(result: GenerationResult) {
        _resultEvents.tryEmit(result)
    }
}
