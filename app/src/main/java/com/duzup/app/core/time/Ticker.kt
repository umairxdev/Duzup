package com.duzup.app.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

/**
 * Emits the current instant immediately and then every minute, so derived state such as OVERDUE and
 * "days remaining" stays correct while a screen stays open across a due time or midnight.
 */
fun TimeProvider.ticker(periodMillis: Long = 60_000L): Flow<Instant> = flow {
    while (true) {
        emit(now())
        delay(periodMillis)
    }
}
