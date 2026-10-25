package com.example.livewallpaper.feature.aipaint.presentation.state

/**
 * 单个生成请求的完成结果。
 *
 * 用户主动取消不计入成功或失败。
 */
sealed interface GenerationCompletion {
    /** 请求成功，[imageCount] 为本次返回的图片数量。 */
    data class Success(val imageCount: Int) : GenerationCompletion

    /** 请求失败，[error] 为可展示的失败原因。 */
    data class Failed(val error: String?) : GenerationCompletion

    /** 请求被取消。 */
    data object Cancelled : GenerationCompletion
}

/**
 * 一批同时进行的生成请求全部结束后，需要展示的结果。
 */
sealed interface GenerationBatchNotice {
    /** 这批只有一个成功请求。 */
    data class SingleSuccess(val imageCount: Int) : GenerationBatchNotice

    /** 这批只有一个失败请求。 */
    data class SingleFailure(val error: String?) : GenerationBatchNotice

    /**
     * 多个请求重叠执行后的汇总。
     *
     * [successCount] 与 [failureCount] 都不包含用户取消的请求。
     */
    data class Summary(val successCount: Int, val failureCount: Int) : GenerationBatchNotice
}

/**
 * 记录同一时段内重叠的生成请求，并在该批全部结束后给出一次展示结果。
 *
 * 进行中又发起的新请求会并入当前批次。用户停止整批后，这一批不再展示汇总；
 * 停止之后新发起的请求属于下一批。
 */
class GenerationBatchState private constructor(
    private val nextWaveId: Int,
    private val waves: List<GenerationWave>,
) {
    constructor() : this(nextWaveId = 1, waves = emptyList())

    /**
     * 登记一个新请求。
     *
     * @return 更新后的状态，以及该请求所属批次的标识
     */
    fun onTaskStarted(): GenerationBatchStart {
        val openIndex = waves.indexOfLast { wave -> !wave.suppressed && wave.activeCount > 0 }
        if (openIndex >= 0) {
            val open = waves[openIndex]
            val updated = open.copy(activeCount = open.activeCount + 1)
            return GenerationBatchStart(
                state = GenerationBatchState(nextWaveId, waves.withWave(openIndex, updated)),
                waveId = open.id,
            )
        }
        val wave = GenerationWave(id = nextWaveId)
        return GenerationBatchStart(
            state = GenerationBatchState(nextWaveId + 1, waves + wave.copy(activeCount = 1)),
            waveId = wave.id,
        )
    }

    /**
     * 记录一个请求的完成结果。
     *
     * 同一批次仍有请求未完成时不产生展示结果。
     *
     * @param waveId [onTaskStarted] 返回的批次标识
     * @param result 该请求的完成结果
     * @return 更新后的状态；批次清空且需要展示时附带 [GenerationBatchNotice]
     */
    fun onTaskFinished(
        waveId: Int,
        result: GenerationCompletion,
    ): GenerationBatchFinish {
        val index = waves.indexOfFirst { wave -> wave.id == waveId }
        if (index < 0) return GenerationBatchFinish(this, notice = null)
        val wave = waves[index]
        val counted = if (wave.suppressed) {
            wave.copy(activeCount = wave.activeCount - 1)
        } else {
            wave.record(result)
        }
        if (counted.activeCount > 0) {
            return GenerationBatchFinish(
                state = GenerationBatchState(nextWaveId, waves.withWave(index, counted)),
                notice = null,
            )
        }
        val notice = if (counted.suppressed) null else counted.toNotice()
        return GenerationBatchFinish(
            state = GenerationBatchState(nextWaveId, waves.filterNot { it.id == waveId }),
            notice = notice,
        )
    }

    /**
     * 停止当前所有批次的汇总展示。
     *
     * 已在运行的请求仍会结束并减少计数，但不再弹出成功或失败汇总。
     */
    fun suppress(): GenerationBatchState = GenerationBatchState(
        nextWaveId = nextWaveId,
        waves = waves.map { wave ->
            if (wave.suppressed) {
                wave
            } else {
                wave.copy(
                    suppressed = true,
                    successCount = 0,
                    failureCount = 0,
                    cancelledCount = 0,
                    lastImageCount = 0,
                    lastError = null,
                )
            }
        },
    )
}

/** [GenerationBatchState.onTaskStarted] 的结果。 */
data class GenerationBatchStart(
    val state: GenerationBatchState,
    val waveId: Int,
)

/** [GenerationBatchState.onTaskFinished] 的结果。 */
data class GenerationBatchFinish(
    val state: GenerationBatchState,
    val notice: GenerationBatchNotice?,
)

private data class GenerationWave(
    val id: Int,
    val activeCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val cancelledCount: Int = 0,
    val lastImageCount: Int = 0,
    val lastError: String? = null,
    val suppressed: Boolean = false,
) {
    fun record(result: GenerationCompletion): GenerationWave = when (result) {
        is GenerationCompletion.Success -> copy(
            activeCount = activeCount - 1,
            successCount = successCount + 1,
            lastImageCount = result.imageCount,
        )
        is GenerationCompletion.Failed -> copy(
            activeCount = activeCount - 1,
            failureCount = failureCount + 1,
            lastError = result.error,
        )
        GenerationCompletion.Cancelled -> copy(
            activeCount = activeCount - 1,
            cancelledCount = cancelledCount + 1,
        )
    }

    fun toNotice(): GenerationBatchNotice? {
        if (successCount == 0 && failureCount == 0) return null
        val finishedCount = successCount + failureCount + cancelledCount
        if (finishedCount <= 1) {
            return if (successCount == 1) {
                GenerationBatchNotice.SingleSuccess(lastImageCount)
            } else {
                GenerationBatchNotice.SingleFailure(lastError)
            }
        }
        return GenerationBatchNotice.Summary(successCount, failureCount)
    }
}

private fun List<GenerationWave>.withWave(index: Int, wave: GenerationWave): List<GenerationWave> =
    mapIndexed { itemIndex, item -> if (itemIndex == index) wave else item }
