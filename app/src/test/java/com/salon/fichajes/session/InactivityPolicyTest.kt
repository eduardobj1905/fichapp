package com.salon.fichajes.session

import com.salon.fichajes.domain.model.Role
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class InactivityPolicyTest {

    @Test
    fun `cuenta atras redondea hacia arriba y llega a cero`() {
        val timeout = 1.minutes
        assertEquals(60, InactivityPolicy.secondsLeft(lastInteractionMs = 0, nowMs = 0, timeout = timeout))
        assertEquals(15, InactivityPolicy.secondsLeft(0, 45_000, timeout))
        assertEquals(1, InactivityPolicy.secondsLeft(0, 59_500, timeout))
        assertEquals(0, InactivityPolicy.secondsLeft(0, 60_000, timeout))
        assertEquals(0, InactivityPolicy.secondsLeft(0, 120_000, timeout))
    }

    @Test
    fun `la jefa tiene mas margen que una empleada`() {
        assertEquals(3.minutes, InactivityPolicy.timeoutFor(Role.OWNER))
        assertEquals(3.minutes, InactivityPolicy.timeoutFor(Role.MANAGER))
        assertEquals(1.minutes, InactivityPolicy.timeoutFor(Role.EMPLOYEE))
    }
}
