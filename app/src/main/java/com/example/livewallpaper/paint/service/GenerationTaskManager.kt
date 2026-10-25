package com.example.livewallpaper.paint.service

import com.example.livewallpaper.feature.aipaint.presentation.state.GenerationBatchFinish
import com.example.livewallpaper.feature.aipaint.presentation.state.GenerationBatchNotice
import com.example.livewallpaper.feature.aipaint.presentation.state.GenerationBatchState
import com.example.livewallpaper.feature.aipaint.presentation.state.GenerationCompletion
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

/**
 * 生成任务的全局状态信息
 */
data class GenerationTaskInfo(
    val messageId: String,
    val sessionId: String,
    val startTime: Long
)

/**
 * 一个生成任务结束后的展示决定。
 *
 * @property notice 当前批次已经全部结束时需要展示的结果；仍有请求进行中时为空
 * @property noTasksLeft 是否已经没有登记中的任务，用于决定能否关闭前台服务
 */
data class GenerationTaskCompletion(
    val notice: GenerationBatchNotice?,
    val noTasksLeft: Boolean,
)

/**
 * 全局生成任务管理器（单例）
 *
 * 将生成任务的协程作用域和状态从 ViewModel 中提取出来，
 * 使其生命周期独立于 Activity/ViewModel，解决退出界面后状态丢失的问题。
 * 重叠进行的请求会计入同一批，全部结束后才发出一次结果。
 */
object GenerationTaskManager {

    /** 独立的协程作用域，不随 ViewModel 销毁 */
    val generationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** messageId -> Job */
    private val jobs = mutableMapOf<String, Job>()

    /** messageId -> 所属批次 */
    private val waveIds = mutableMapOf<String, Int>()

    private val tasks = mutableMapOf<String, GenerationTaskInfo>()

    private val stateLock = Any()

    private var batchState = GenerationBatchState()

    /** 当前所有正在生成的任务信息 */
    private val _activeTasks = MutableStateFlow<Map<String, GenerationTaskInfo>>(emptyMap())
    val activeTasks: StateFlow<Map<String, GenerationTaskInfo>> = _activeTasks.asStateFlow()

    /** 一批请求全部完成后的结果，供界面显示 Toast。 */
    private val _resultEvents = MutableSharedFlow<GenerationBatchNotice>(extraBufferCapacity = 8)
    val resultEvents: SharedFlow<GenerationBatchNotice> = _resultEvents.asSharedFlow()

    /**
     * 注册一个生成任务。
     *
     * 任务协程应在注册完成后再启动，避免请求先结束、批次还没记上。
     */
    fun register(messageId: String, sessionId: String, startTime: Long, job: Job) {
        synchronized(stateLock) {
            val started = batchState.onTaskStarted()
            batchState = started.state
            jobs[messageId] = job
            waveIds[messageId] = started.waveId
            tasks[messageId] = GenerationTaskInfo(messageId, sessionId, startTime)
            publishLocked()
        }
    }

    /**
     * 记录任务结果。当前批次仍有请求时不产生展示内容。
     *
     * @param messageId 完成的消息 ID
     * @param result 成功、失败或取消
     */
    fun complete(messageId: String, result: GenerationCompletion): GenerationTaskCompletion {
        synchronized(stateLock) {
            jobs.remove(messageId)
            tasks.remove(messageId)
            val waveId = waveIds.remove(messageId)
            publishLocked()
            val finish = if (waveId == null) {
                GenerationBatchFinish(batchState, notice = null)
            } else {
                batchState.onTaskFinished(waveId, result).also { finished ->
                    batchState = finished.state
                }
            }
            finish.notice?.let { notice -> _resultEvents.tryEmit(notice) }
            return GenerationTaskCompletion(
                notice = finish.notice,
                noTasksLeft = tasks.isEmpty(),
            )
        }
    }

    /**
     * 取消并移除指定任务。完成回调仍会把它记入当前批次。
     */
    fun cancel(messageId: String) {
        val job = synchronized(stateLock) { jobs[messageId] }
        job?.cancel()
    }

    /**
     * 取消所有任务，并放弃这一批的成功/失败汇总。
     */
    fun cancelAll() {
        val running = synchronized(stateLock) {
            batchState = batchState.suppress()
            val runningJobs = jobs.values.toList()
            jobs.clear()
            tasks.clear()
            publishLocked()
            runningJobs
        }
        running.forEach { job -> job.cancel() }
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

    private fun publishLocked() {
        _activeTasks.value = tasks.toMap()
    }
}
