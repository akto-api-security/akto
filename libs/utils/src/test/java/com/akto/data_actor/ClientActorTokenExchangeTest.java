package com.akto.data_actor;

import com.akto.dto.OriginalHttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ClientActorTokenExchangeTest {

    private static final String EXCHANGED = "exchanged-token";

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final AtomicInteger calls = new AtomicInteger();
    private final Deque<Integer> statuses = new ArrayDeque<>();

    private static OriginalHttpResponse response(int status) {
        String body = status == 200 ? "{\"token\": \"" + EXCHANGED + "\"}" : "{}";
        return new OriginalHttpResponse(body, Collections.emptyMap(), status);
    }

    @BeforeEach
    public void setUp() {
        ClientActor.resetTokenExchangeForTests();
        ClientActor.exchangeClock = now::get;
        ClientActor.tokenExchangeTransport = request -> {
            calls.incrementAndGet();
            Integer status = statuses.poll();
            return status == null ? null : response(status);
        };
        ClientActor.setPendingModuleType("MINI_RUNTIME");
    }

    @AfterEach
    public void tearDown() {
        ClientActor.resetTokenExchangeForTests();
        ClientActor.exchangeClock = System::currentTimeMillis;
    }

    @Test
    public void aFailedExchangeIsRetriedAfterTheBackoffAndThenUsed() {
        statuses.add(502);
        statuses.add(200);

        assertNotEquals(EXCHANGED, ClientActor.getAuthToken());
        assertEquals(1, calls.get());

        // still inside the backoff window: no new request
        now.addAndGet(ClientActor.TOKEN_EXCHANGE_INITIAL_RETRY_DELAY_MS - 1);
        assertNotEquals(EXCHANGED, ClientActor.getAuthToken());
        assertEquals(1, calls.get());

        now.addAndGet(1);
        assertEquals(EXCHANGED, ClientActor.getAuthToken());
        assertEquals(2, calls.get());

        // settled: no more requests
        now.addAndGet(ClientActor.TOKEN_EXCHANGE_MAX_RETRY_DELAY_MS * 10);
        assertEquals(EXCHANGED, ClientActor.getAuthToken());
        assertEquals(2, calls.get());
    }

    @Test
    public void theBackoffDoublesUpToTheCap() {
        for (int i = 0; i < 20; i++) {
            statuses.add(503);
        }
        ClientActor.getAuthToken();
        long expectedDelay = ClientActor.TOKEN_EXCHANGE_INITIAL_RETRY_DELAY_MS;
        for (int attempt = 2; attempt <= 10; attempt++) {
            now.addAndGet(expectedDelay - 1);
            ClientActor.getAuthToken();
            assertEquals(attempt - 1, calls.get(), "retried before the backoff of " + expectedDelay + " ms elapsed");
            now.addAndGet(1);
            ClientActor.getAuthToken();
            assertEquals(attempt, calls.get());
            expectedDelay = Math.min(expectedDelay * 2, ClientActor.TOKEN_EXCHANGE_MAX_RETRY_DELAY_MS);
        }
        assertEquals(ClientActor.TOKEN_EXCHANGE_MAX_RETRY_DELAY_MS, expectedDelay);
    }

    @Test
    public void noResponseAtAllIsRetried() {
        // the transport returns null when the queue is empty, as sendRequestBackOff does when every try threw
        ClientActor.getAuthToken();
        statuses.add(200);
        now.addAndGet(ClientActor.TOKEN_EXCHANGE_INITIAL_RETRY_DELAY_MS);
        assertEquals(EXCHANGED, ClientActor.getAuthToken());
        assertEquals(2, calls.get());
    }

    @Test
    public void aRefusalIsNotRetried() {
        // e.g. a legacy token that cannot be exchanged: keep the raw token, as before
        statuses.add(403);
        statuses.add(200);
        ClientActor.getAuthToken();
        now.addAndGet(ClientActor.TOKEN_EXCHANGE_MAX_RETRY_DELAY_MS * 10);
        assertNotEquals(EXCHANGED, ClientActor.getAuthToken());
        assertEquals(1, calls.get());
    }

    @Test
    public void rateLimitAndTimeoutAreRetried() {
        statuses.add(429);
        statuses.add(408);
        statuses.add(200);
        ClientActor.getAuthToken();
        now.addAndGet(ClientActor.TOKEN_EXCHANGE_MAX_RETRY_DELAY_MS);
        ClientActor.getAuthToken();
        now.addAndGet(ClientActor.TOKEN_EXCHANGE_MAX_RETRY_DELAY_MS);
        assertEquals(EXCHANGED, ClientActor.getAuthToken());
        assertEquals(3, calls.get());
    }

    @Test
    public void withoutAModuleTypeNothingIsExchanged() {
        ClientActor.resetTokenExchangeForTests();
        statuses.add(200);
        assertNotEquals(EXCHANGED, ClientActor.getAuthToken());
        assertEquals(0, calls.get());
    }

    @Test
    public void callersWaitForTheFirstExchangeAndOnlyOneRequestIsMade() throws Exception {
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ClientActor.tokenExchangeTransport = request -> {
            calls.incrementAndGet();
            inFlight.countDown();
            release.await(5, TimeUnit.SECONDS);
            return response(200);
        };
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            Future<String> first = pool.submit(ClientActor::getAuthToken);
            inFlight.await(5, TimeUnit.SECONDS);
            Future<?>[] others = new Future<?>[7];
            for (int i = 0; i < others.length; i++) {
                others[i] = pool.submit(ClientActor::getAuthToken);
            }
            Thread.sleep(200);
            release.countDown();
            assertEquals(EXCHANGED, first.get(5, TimeUnit.SECONDS));
            for (Future<?> other : others) {
                assertEquals(EXCHANGED, other.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, calls.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void onlyOneCallerRetriesAtATime() throws Exception {
        statuses.add(502);
        ClientActor.getAuthToken();
        now.addAndGet(ClientActor.TOKEN_EXCHANGE_INITIAL_RETRY_DELAY_MS);

        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ClientActor.tokenExchangeTransport = request -> {
            calls.incrementAndGet();
            inFlight.countDown();
            release.await(5, TimeUnit.SECONDS);
            return response(200);
        };
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            Future<String> retrying = pool.submit(ClientActor::getAuthToken);
            inFlight.await(5, TimeUnit.SECONDS);
            // others do not wait on the retry: they get the token in use right away
            for (int i = 0; i < 7; i++) {
                assertNotEquals(EXCHANGED, pool.submit(ClientActor::getAuthToken).get(1, TimeUnit.SECONDS));
            }
            release.countDown();
            assertEquals(EXCHANGED, retrying.get(5, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
