package com.zpkdxgames.plexoncore.scheduler;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreSchedulerDelayTest {
    @Test void delayConversionNeverSchedulesEarlierThanRequested() {
        assertEquals(1L, CoreScheduler.delayTicks(Duration.ZERO));
        assertEquals(1L, CoreScheduler.delayTicks(Duration.ofMillis(1)));
        assertEquals(1L, CoreScheduler.delayTicks(Duration.ofMillis(50)));
        assertEquals(2L, CoreScheduler.delayTicks(Duration.ofMillis(51)));
        assertEquals(2L, CoreScheduler.delayTicks(Duration.ofMillis(99)));
        assertEquals(2L, CoreScheduler.delayTicks(Duration.ofMillis(100)));
        assertEquals(3L, CoreScheduler.delayTicks(Duration.ofMillis(101)));
    }

    @Test void negativeDelayUsesMinimumOneTick() {
        assertEquals(1L, CoreScheduler.delayTicks(Duration.ofMillis(-1)));
    }
}
