package dev.alexis.wirelessdrive.service

import android.content.Context
import java.util.concurrent.atomic.AtomicInteger

/**
 * Call [begin] right before starting a long-running network operation that
 * should survive the screen turning off or the app going to background
 * (downloads, uploads, batch thumbnail generation), and [end] in a
 * `finally` block once it's done.
 *
 * Safe to call from multiple concurrent operations: the underlying
 * [BackgroundTaskService] is only started on the first `begin()` and only
 * stopped once every matching `end()` has been called, so e.g. a batch
 * download and a batch thumbnail generation running at the same time don't
 * fight over starting/stopping the same service.
 */
object BackgroundTaskCoordinator {

    private val activeCount = AtomicInteger(0)

    fun begin(context: Context, label: String) {
        if (activeCount.getAndIncrement() == 0) {
            BackgroundTaskService.start(context.applicationContext, label)
        }
    }

    fun end(context: Context) {
        val remaining = activeCount.decrementAndGet()
        if (remaining <= 0) {
            activeCount.set(0)
            BackgroundTaskService.stop(context.applicationContext)
        }
    }
}