package com.example.livewallpaper.core.util

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/** iOS wall-clock implementation used by shared persisted timestamps. */
actual object TimeProvider {
    actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000.0).toLong()
}
