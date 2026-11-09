package com.savewise.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowLimiterTest {
    @Test
    void allowsUpToTheLimitThenBlocksWithARetryTime() {
        SlidingWindowLimiter l = new SlidingWindowLimiter(3, 60_000);
        assertEquals(0, l.tryAcquire("ip", 0));
        assertEquals(0, l.tryAcquire("ip", 1_000));
        assertEquals(0, l.tryAcquire("ip", 2_000));
        assertEquals(58, l.tryAcquire("ip", 2_000)); // first hit leaves the window at 60s
    }

    @Test
    void theWindowSlidesSoOldHitsStopCounting() {
        SlidingWindowLimiter l = new SlidingWindowLimiter(2, 10_000);
        l.tryAcquire("ip", 0);
        l.tryAcquire("ip", 5_000);
        assertTrue(l.tryAcquire("ip", 9_000) > 0);
        assertEquals(0, l.tryAcquire("ip", 10_000)); // the hit at 0 has expired
    }

    @Test
    void keysAreIndependent() {
        SlidingWindowLimiter l = new SlidingWindowLimiter(1, 60_000);
        assertEquals(0, l.tryAcquire("a", 0));
        assertEquals(0, l.tryAcquire("b", 0));
        assertTrue(l.tryAcquire("a", 1) > 0);
    }

    @Test
    void purgeDropsIdleKeys() {
        SlidingWindowLimiter l = new SlidingWindowLimiter(5, 1_000);
        l.tryAcquire("old", 0);
        l.tryAcquire("fresh", 5_000);
        l.purge(5_500);
        assertEquals(1, l.trackedKeys());
    }
}
