package com.example.livewallpaper.feature.aipaint.presentation.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 校验重叠生成请求要等全部结束后才汇总成功和失败数量。 */
class GenerationBatchTest {
    @Test
    fun singleSuccessAndFailureKeepTheirOwnNotice() {
        val started = GenerationBatchState().onTaskStarted()
        val success = started.state.onTaskFinished(started.waveId, GenerationCompletion.Success(3))
        assertEquals(GenerationBatchNotice.SingleSuccess(3), success.notice)

        val failedStart = success.state.onTaskStarted()
        val failure = failedStart.state.onTaskFinished(
            failedStart.waveId,
            GenerationCompletion.Failed("network"),
        )
        assertEquals(GenerationBatchNotice.SingleFailure("network"), failure.notice)
    }

    @Test
    fun overlappingRequestsNotifyOnceWhenTheWaveDrains() {
        val first = GenerationBatchState().onTaskStarted()
        val second = first.state.onTaskStarted()
        val third = second.state.onTaskStarted()
        assertEquals(first.waveId, second.waveId)
        assertEquals(first.waveId, third.waveId)

        val afterSuccess = third.state.onTaskFinished(first.waveId, GenerationCompletion.Success(2))
        assertNull(afterSuccess.notice)
        val afterFailure = afterSuccess.state.onTaskFinished(
            first.waveId,
            GenerationCompletion.Failed("busy"),
        )
        assertNull(afterFailure.notice)
        val finished = afterFailure.state.onTaskFinished(first.waveId, GenerationCompletion.Success(1))

        assertEquals(GenerationBatchNotice.Summary(successCount = 2, failureCount = 1), finished.notice)
    }

    @Test
    fun cancelledRequestIsNotCountedAsFailure() {
        val first = GenerationBatchState().onTaskStarted()
        val second = first.state.onTaskStarted()
        val afterCancel = second.state.onTaskFinished(first.waveId, GenerationCompletion.Cancelled)
        val finished = afterCancel.state.onTaskFinished(first.waveId, GenerationCompletion.Success(1))

        assertEquals(GenerationBatchNotice.Summary(successCount = 1, failureCount = 0), finished.notice)
    }

    @Test
    fun cancellationOnlyWaveProducesNoNotice() {
        val started = GenerationBatchState().onTaskStarted()
        val finished = started.state.onTaskFinished(started.waveId, GenerationCompletion.Cancelled)
        assertNull(finished.notice)
    }

    @Test
    fun requestStartedAfterAWaveClosesBelongsToTheNextWave() {
        val first = GenerationBatchState().onTaskStarted()
        val finished = first.state.onTaskFinished(first.waveId, GenerationCompletion.Success(1))
        val second = finished.state.onTaskStarted()

        assertTrue(second.waveId != first.waveId)
        val secondFinished = second.state.onTaskFinished(second.waveId, GenerationCompletion.Failed(null))
        assertEquals(GenerationBatchNotice.SingleFailure(null), secondFinished.notice)
    }

    @Test
    fun suppressDropsTheCurrentWaveWithoutAffectingTheNextOne() {
        val first = GenerationBatchState().onTaskStarted()
        val second = first.state.onTaskStarted()
        val suppressed = second.state.suppress()
        val restarted = suppressed.onTaskStarted()
        assertTrue(restarted.waveId != first.waveId)

        val oldFirst = restarted.state.onTaskFinished(first.waveId, GenerationCompletion.Success(4))
        assertNull(oldFirst.notice)
        val oldSecond = oldFirst.state.onTaskFinished(first.waveId, GenerationCompletion.Failed("stopped"))
        assertNull(oldSecond.notice)
        val fresh = oldSecond.state.onTaskFinished(restarted.waveId, GenerationCompletion.Failed("later"))

        assertEquals(GenerationBatchNotice.SingleFailure("later"), fresh.notice)
    }
}
