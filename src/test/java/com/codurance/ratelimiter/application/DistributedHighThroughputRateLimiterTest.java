package com.codurance.ratelimiter.application;

import com.codurance.ratelimiter.testutil.FakeClock;
import com.codurance.ratelimiter.testutil.FakeCounterStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class DistributedHighThroughputRateLimiterTest {

    private static final String TEST_KEY = "user-1";
    private static final int LIMIT = 10;

    private static final int WINDOW_SECONDS = 60;
    private static final int FLUSH_THRESHOLD = 5;
    private static final int FLUSH_INTERVAL_MILLIS = 1000;

    private FakeCounterStore store;
    private FakeClock clock;
    private DistributedHighThroughputRateLimiter limiter;
    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp() {
        store = new FakeCounterStore();
        clock = new FakeClock(0);
        scheduler = Executors.newSingleThreadScheduledExecutor();

        limiter = new DistributedHighThroughputRateLimiter(
                store,
                new RateLimiterConfig(
                        WINDOW_SECONDS,
                        FLUSH_THRESHOLD,
                        FLUSH_INTERVAL_MILLIS
                ),
                scheduler,
                clock
        );
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void shouldAllowRequestsUpToLimit() {
        for (int i = 0; i < LIMIT; i++) {
            boolean allowed = limiter.isAllowed(TEST_KEY, LIMIT).join();
            assertThat(allowed).isTrue();
        }
    }

   @Test
    void shouldDenyRequestsWhenLimitExceeded() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.isAllowed(TEST_KEY, LIMIT).join();
        }
        boolean allowed = limiter.isAllowed(TEST_KEY, LIMIT).join();
        assertThat(allowed).isFalse();
    }

    @Test
    void shouldResetWindowAfterWindowDuration() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.isAllowed(TEST_KEY, LIMIT).join();
        }

        clock.advanceSeconds(WINDOW_SECONDS);

        assertThat(limiter.isAllowed(TEST_KEY, LIMIT).join())
                .isTrue();
    }

    @Test
    void shouldIsolateLimitsBetweenDifferentKeys() {
        for (int i = 0; i < LIMIT; i++) {
            limiter.isAllowed(TEST_KEY, LIMIT).join();
        }

        assertThat(limiter.isAllowed(TEST_KEY, LIMIT).join()).isFalse();
        assertThat(limiter.isAllowed("key2", LIMIT).join()).isTrue();
    }

    @Test
    void shouldAccumulateLocallyBeforeThresholdFlush() {
        int callsBeforeFlush = FLUSH_THRESHOLD - 1;

        for (int i = 0; i < callsBeforeFlush; i++) {
            limiter.isAllowed(TEST_KEY, LIMIT).join();
        }

        assertThat(store.getRequestCount()).isZero();

        limiter.isAllowed(TEST_KEY, LIMIT).join();

        assertThat(store.getRequestCount())
                .isEqualTo(FLUSH_THRESHOLD);
    }

    @Test
    void flushAllShouldFlushPendingRequests() {
        limiter.isAllowed(TEST_KEY, LIMIT).join();
        limiter.isAllowed(TEST_KEY, LIMIT).join();

        limiter.flushAllKeys();

        assertThat(store.getRequestCount()).isEqualTo(2);
    }

    @Test
    void shouldRestorePendingRequestsOnStoreFailure() {
        store.setFail(true);

        for (int i = 0; i < FLUSH_THRESHOLD; i++) {
            limiter.isAllowed(TEST_KEY, LIMIT).join();
        }

        assertThat(store.getRequestCount()).isZero();

        store.setFail(false);

        limiter.flushAllKeys();

        assertThat(store.getRequestCount())
                .isEqualTo(FLUSH_THRESHOLD);
    }

    @Test
    void shouldNotRestorePendingIfWindowChanged() {
        store.setFail(true);

        for (int i = 0; i < FLUSH_THRESHOLD; i++) {
            limiter.isAllowed(TEST_KEY, LIMIT).join();
        }

        clock.advanceSeconds(WINDOW_SECONDS);

        store.setFail(false);

        assertThat(limiter.isAllowed(TEST_KEY, LIMIT).join())
                .isTrue();

        limiter.flushAllKeys();

        assertThat(store.getRequestCount())
                .isEqualTo(1);
    }

    @Test
    void shouldNotLoseRequestsUnderParallelLoad() throws Exception {
        int requestCount = 1000;
        int threads = 8;

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<Callable<Void>> tasks = IntStream.range(0, requestCount)
            .mapToObj(i -> (Callable<Void>) () -> {
                limiter.isAllowed(TEST_KEY, 10000).join();
                return null;
            })
            .toList();

        executor.invokeAll(tasks);
        executor.shutdown();

        limiter.flushAllKeys();

        assertThat(store.getRequestCount())
            .isEqualTo(requestCount);
    }

    @Test
    void shouldRemoveInactiveKeys() {
        limiter.isAllowed(TEST_KEY, LIMIT).join();

        assertThat(limiter.hasKey(TEST_KEY)).isTrue();

        clock.advanceSeconds(WINDOW_SECONDS + 1);

        limiter.removeInactiveKeys();

        assertThat(limiter.hasKey(TEST_KEY)).isFalse();
    }
}
