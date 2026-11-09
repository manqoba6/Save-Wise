package com.savewise.util;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Allows at most {@code max} hits per key in any window of {@code windowMillis}. In memory, so per server instance. */
public class SlidingWindowLimiter {
    private static final int PURGE_EVERY = 1000;

    private final int max;
    private final long windowMillis;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();

    public SlidingWindowLimiter(int max, long windowMillis) {
        this.max = max;
        this.windowMillis = windowMillis;
    }

    /** @return 0 if the hit is allowed (and counted), otherwise how many whole seconds to wait before trying again. */
    public long tryAcquire(String key, long nowMillis) {
        if (calls.incrementAndGet() % PURGE_EVERY == 0) purge(nowMillis);
        ArrayDeque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && nowMillis - queue.peekFirst() >= windowMillis) queue.pollFirst();
            if (queue.size() >= max) {
                long waitMillis = queue.peekFirst() + windowMillis - nowMillis;
                return Math.max(1, (waitMillis + 999) / 1000);
            }
            queue.addLast(nowMillis);
            return 0;
        }
    }

    /** Forgets keys with no recent hits so the map cannot grow without limit. */
    void purge(long nowMillis) {
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || nowMillis - e.getValue().peekLast() >= windowMillis;
            }
        });
    }

    int trackedKeys() { return hits.size(); }
}
