package com.salon.fichajes.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration

/**
 * Emite la hora actual cada [period]. Es un Flow "frío": solo corre mientras alguien lo recoge,
 * así que con stateIn(WhileSubscribed) se detiene al salir de la pantalla.
 */
fun tickerFlow(period: Duration, clock: Clock): Flow<Instant> = flow {
    while (true) {
        emit(clock.instant())
        delay(period)
    }
}
