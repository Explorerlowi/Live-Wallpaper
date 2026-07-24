package com.example.livewallpaper.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher

/** Dispatchers injected into shared code so platform scheduling remains testable. */
interface CoroutineDispatcherProvider {
    /** Dispatcher for blocking database and file work. */
    val io: CoroutineDispatcher

    /** Dispatcher for CPU-bound mapping and serialization. */
    val computation: CoroutineDispatcher
}

/**
 * Default immutable implementation configured by each platform module.
 *
 * @property io Dispatcher for blocking work.
 * @property computation Dispatcher for CPU-bound work.
 */
class DefaultCoroutineDispatcherProvider(
    override val io: CoroutineDispatcher,
    override val computation: CoroutineDispatcher,
) : CoroutineDispatcherProvider
