package com.akto.data_actor;

import com.akto.dto.type.SingleTypeInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HostStiSyncTest {

    private static final int START_TS = 1_000_000;

    // Plays the DB. lastSeen drives what a delta fetch returns, like the lastSeenGte filter, and ids sort in creation
    // order, like ObjectIds, so "created after" is "id greater than".
    private static class FakeDb implements HostStiSync.Source {
        final TreeMap<String, Integer> lastSeenById = new TreeMap<>();
        int fullFetchCount;
        int seenFetchCount;
        boolean failSeenFetch;
        boolean failCreatedFetch;
        boolean truncateFullFetch;
        boolean ignoreLastSeenGte;
        long countOverride = Long.MIN_VALUE;
        int lastSeenGteRequested;
        // overlap detection for the concurrency test
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();
        volatile boolean slow;

        void addSti(String id, int lastSeen) {
            lastSeenById.put(id, lastSeen);
        }

        private static String rawSti(String id, int lastSeen) {
            return id + ":" + lastSeen;
        }

        private void enter() {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            if (slow) {
                try {
                    Thread.sleep(3);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void exit() {
            inFlight.decrementAndGet();
        }

        @Override
        public HostStiSync.FullFetch fetchAll() {
            enter();
            try {
                fullFetchCount++;
                List<SingleTypeInfo> stis = new ArrayList<>();
                Map<String, String> rawStis = new HashMap<>();
                for (Map.Entry<String, Integer> entry : lastSeenById.entrySet()) {
                    rawStis.put(entry.getKey(), rawSti(entry.getKey(), entry.getValue()));
                    stis.add(stiWith(entry.getKey(), entry.getValue()));
                }
                return new HostStiSync.FullFetch(stis, rawStis, !truncateFullFetch);
            } finally {
                exit();
            }
        }

        @Override
        public Map<String, String> fetchSeenSince(int lastSeenGte) {
            enter();
            try {
                seenFetchCount++;
                lastSeenGteRequested = lastSeenGte;
                if (failSeenFetch) {
                    return null;
                }
                Map<String, String> rawStis = new HashMap<>();
                for (Map.Entry<String, Integer> entry : lastSeenById.entrySet()) {
                    if (ignoreLastSeenGte || entry.getValue() >= lastSeenGte) {
                        rawStis.put(entry.getKey(), rawSti(entry.getKey(), entry.getValue()));
                    }
                }
                return rawStis;
            } finally {
                exit();
            }
        }

        @Override
        public Map<String, String> fetchCreatedAfter(String lastStiId) {
            enter();
            try {
                if (failCreatedFetch) {
                    return null;
                }
                Map<String, String> rawStis = new HashMap<>();
                for (Map.Entry<String, Integer> entry : lastSeenById.entrySet()) {
                    if (lastStiId == null || entry.getKey().compareTo(lastStiId) > 0) {
                        rawStis.put(entry.getKey(), rawSti(entry.getKey(), entry.getValue()));
                    }
                }
                return rawStis;
            } finally {
                exit();
            }
        }

        @Override
        public long countStis() {
            enter();
            try {
                return countOverride != Long.MIN_VALUE ? countOverride : lastSeenById.size();
            } finally {
                exit();
            }
        }

        @Override
        public List<SingleTypeInfo> parseStis(Collection<String> rawStis) {
            List<SingleTypeInfo> stis = new ArrayList<>();
            for (String rawSti : rawStis) {
                String[] parts = rawSti.split(":");
                stis.add(stiWith(parts[0], Integer.parseInt(parts[1])));
            }
            return stis;
        }
    }

    private static SingleTypeInfo stiWith(String url, int lastSeen) {
        SingleTypeInfo sti = new SingleTypeInfo();
        sti.setUrl(url);
        sti.setLastSeen(lastSeen);
        return sti;
    }

    private static List<String> urlsWithLastSeen(List<SingleTypeInfo> stis) {
        List<String> out = new ArrayList<>();
        for (SingleTypeInfo s : stis) {
            out.add(s.getUrl() + ":" + s.getLastSeen());
        }
        return out;
    }

    private final AtomicInteger now = new AtomicInteger(START_TS);

    private HostStiSync newHostStiSync(FakeDb fakeDb) {
        return new HostStiSync(fakeDb, now::get);
    }

    @Test
    public void firstFetchIsAFullFetch() {
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS - 10);
        fakeDb.addSti("b", START_TS - 20);
        assertEquals(Arrays.asList("a:" + (START_TS - 10), "b:" + (START_TS - 20)), urlsWithLastSeen(newHostStiSync(fakeDb).fetch()));
        assertEquals(1, fakeDb.fullFetchCount);
        assertEquals(0, fakeDb.seenFetchCount);
    }

    @Test
    public void laterFetchesOnlyAskForRecentlySeenStisAndStillReturnEverything() {
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS - 10_000);
        fakeDb.addSti("b", START_TS - 10_000);
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        hostStiSync.fetch();

        now.addAndGet(15 * 60);
        fakeDb.addSti("c", now.get() - 5);          // created since
        fakeDb.addSti("a", now.get() - 1);          // seen again since
        List<SingleTypeInfo> stis = hostStiSync.fetch();

        assertEquals(1, fakeDb.fullFetchCount, "no second full fetch");
        assertEquals(1, fakeDb.seenFetchCount);
        assertEquals(START_TS - HostStiSync.LAST_SEEN_LOOKBACK_SECONDS, fakeDb.lastSeenGteRequested, "looks back past the previous fetch");
        // identical to what a full fetch would have returned, in _id order
        assertEquals(Arrays.asList("a:" + (now.get() - 1), "b:" + (START_TS - 10_000), "c:" + (now.get() - 5)), urlsWithLastSeen(stis));
    }

    @Test
    public void aNewStiIsPickedUpEvenWhenItsLastSeenIsOld() {
        // lastSeen is traffic time, so a new STI can carry an old one; it must still be found without a full fetch
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS);
        fakeDb.addSti("b", START_TS);
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        hostStiSync.fetch();

        now.addAndGet(15 * 60);
        int oldLastSeen = START_TS - 5 * HostStiSync.LAST_SEEN_LOOKBACK_SECONDS;
        fakeDb.addSti("c", oldLastSeen);
        List<SingleTypeInfo> stis = hostStiSync.fetch();

        assertEquals(1, fakeDb.fullFetchCount, "found without a full fetch");
        assertEquals(Arrays.asList("a:" + START_TS, "b:" + START_TS, "c:" + oldLastSeen), urlsWithLastSeen(stis));
    }

    @Test
    public void aDeletedStiForcesAFullFetch() {
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS);
        fakeDb.addSti("b", START_TS);
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        hostStiSync.fetch();

        fakeDb.lastSeenById.remove("b");
        now.addAndGet(60);
        List<SingleTypeInfo> stis = hostStiSync.fetch();

        assertEquals(2, fakeDb.fullFetchCount, "count mismatch must trigger a full refetch");
        assertEquals(1, stis.size());
    }

    @Test
    public void aDeletionHiddenBehindAnEqualNumberOfCreationsIsStillCaught() {
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS);
        fakeDb.addSti("b", START_TS);
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        hostStiSync.fetch();

        // one STI deleted and one created, so the DB count is unchanged, and the new one has an old lastSeen
        fakeDb.lastSeenById.remove("b");
        int oldLastSeen = START_TS - 5 * HostStiSync.LAST_SEEN_LOOKBACK_SECONDS;
        fakeDb.addSti("c", oldLastSeen);
        now.addAndGet(60);
        List<SingleTypeInfo> stis = hostStiSync.fetch();

        assertEquals(2, fakeDb.fullFetchCount, "the stale deleted STI must trigger a full refetch");
        assertEquals(Arrays.asList("a:" + START_TS, "c:" + oldLastSeen), urlsWithLastSeen(stis));
    }

    @Test
    public void aFailedDeltaRequestFallsBackToAFullFetch() {
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS);
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        hostStiSync.fetch();

        fakeDb.failSeenFetch = true;
        fakeDb.addSti("b", START_TS + 30);
        now.addAndGet(60);
        assertEquals(2, hostStiSync.fetch().size());
        assertEquals(2, fakeDb.fullFetchCount);
    }

    @Test
    public void aFailedCreatedFetchFallsBackToAFullFetch() {
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS);
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        hostStiSync.fetch();

        fakeDb.failCreatedFetch = true;
        fakeDb.addSti("b", START_TS + 30);
        now.addAndGet(60);
        assertEquals(2, hostStiSync.fetch().size());
        assertEquals(2, fakeDb.fullFetchCount);
    }

    @Test
    public void anUnavailableCountNeverSeedsTheCacheSoEveryFetchIsFull() {
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS);
        fakeDb.countOverride = -1;                // e.g. an older abstractor without the count endpoint
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        hostStiSync.fetch();
        now.addAndGet(60);
        hostStiSync.fetch();

        assertEquals(2, fakeDb.fullFetchCount);
        assertEquals(0, fakeDb.seenFetchCount);
    }

    @Test
    public void anIncompleteFullFetchIsNeverCached() {
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS);
        fakeDb.truncateFullFetch = true;               // e.g. the page limit or an error part-way
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        hostStiSync.fetch();
        now.addAndGet(60);
        hostStiSync.fetch();

        assertEquals(2, fakeDb.fullFetchCount);
        assertEquals(0, fakeDb.seenFetchCount);
    }

    @Test
    public void aFullFetchHappensAtLeastOnceADay() {
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS);
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        hostStiSync.fetch();

        now.addAndGet(HostStiSync.FULL_FETCH_INTERVAL_SECONDS - 1);
        hostStiSync.fetch();
        assertEquals(1, fakeDb.fullFetchCount);

        now.addAndGet(1);
        hostStiSync.fetch();
        assertEquals(2, fakeDb.fullFetchCount);
    }

    @Test
    public void aFailedParseOfStoredDocumentsFallsBackToAFullFetch() {
        FakeDb fakeDb = new FakeDb() {
            boolean firstCall = true;

            @Override
            public List<SingleTypeInfo> parseStis(Collection<String> rawStis) {
                if (firstCall) {
                    firstCall = false;
                    return null;
                }
                return super.parseStis(rawStis);
            }
        };
        fakeDb.addSti("a", START_TS);
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        hostStiSync.fetch();
        now.addAndGet(60);
        assertEquals(1, hostStiSync.fetch().size());
        assertEquals(2, fakeDb.fullFetchCount);
    }

    @Test
    public void emptyDbIsCachedAndDeltaFetchedLikeAnyOther() {
        FakeDb fakeDb = new FakeDb();
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        assertTrue(hostStiSync.fetch().isEmpty());
        now.addAndGet(60);
        fakeDb.addSti("a", now.get());
        assertEquals(1, hostStiSync.fetch().size());
        assertEquals(1, fakeDb.fullFetchCount);
        assertEquals(1, fakeDb.seenFetchCount);
    }

    @Test
    public void concurrentCallersAreServedOneAtATimeAndAllGetTheSameList() throws Exception {
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS);
        fakeDb.addSti("b", START_TS);
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        hostStiSync.fetch();                 // seed, so the callers below do delta refreshes
        fakeDb.slow = true;

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<String>>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    List<String> last = null;
                    for (int n = 0; n < 5; n++) {
                        last = urlsWithLastSeen(hostStiSync.fetch());
                    }
                    return last;
                }));
            }
            for (Future<List<String>> result : results) {
                assertEquals(Arrays.asList("a:" + START_TS, "b:" + START_TS), result.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, fakeDb.maxInFlight.get(), "two refreshes must never talk to the DB at the same time");
        assertEquals(1, fakeDb.fullFetchCount, "concurrent callers must not each fall back to a full fetch");
    }

    // Cyborg and the mini-runtimes are updated independently: a runtime newer than the cyborg it talks to, and
    // requests that land on old and new cyborg instances during a rollout
    @Test
    public void aServerThatPredatesLastSeenGteStillGivesTheRightList() {
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS);
        fakeDb.addSti("b", START_TS - 10_000);
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        hostStiSync.fetch();

        // an old server ignores lastSeenGte and returns every STI: more than asked for, never less
        fakeDb.ignoreLastSeenGte = true;
        now.addAndGet(15 * 60);
        fakeDb.addSti("c", now.get());
        List<SingleTypeInfo> stis = hostStiSync.fetch();

        assertEquals(1, fakeDb.fullFetchCount, "no full fetch needed");
        assertEquals(Arrays.asList("a:" + START_TS, "b:" + (START_TS - 10_000), "c:" + now.get()), urlsWithLastSeen(stis));
    }

    @Test
    public void theCacheStartsOnceTheCountEndpointAppears() {
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS);
        fakeDb.countOverride = -1;            // cyborg without the count endpoint
        HostStiSync hostStiSync = newHostStiSync(fakeDb);

        hostStiSync.fetch();
        now.addAndGet(60);
        hostStiSync.fetch();
        assertEquals(2, fakeDb.fullFetchCount, "full fetches for as long as cyborg cannot count");
        assertEquals(0, fakeDb.seenFetchCount);

        fakeDb.countOverride = Long.MIN_VALUE; // cyborg is updated
        now.addAndGet(60);
        hostStiSync.fetch();                  // this full fetch is the one that starts the cache
        now.addAndGet(60);
        assertEquals(1, hostStiSync.fetch().size());

        assertEquals(3, fakeDb.fullFetchCount);
        assertEquals(1, fakeDb.seenFetchCount, "from here on only what changed is fetched");
    }

    @Test
    public void aCountThatTurnsUnavailableMidwayFallsBackToAFullFetch() {
        FakeDb fakeDb = new FakeDb();
        fakeDb.addSti("a", START_TS);
        HostStiSync hostStiSync = newHostStiSync(fakeDb);
        hostStiSync.fetch();

        // during a rollout the count call can land on an old instance
        fakeDb.countOverride = -1;
        fakeDb.addSti("b", START_TS + 30);
        now.addAndGet(60);
        assertEquals(Arrays.asList("a:" + START_TS, "b:" + (START_TS + 30)), urlsWithLastSeen(hostStiSync.fetch()));
        assertEquals(2, fakeDb.fullFetchCount);

        // and the cache comes back on its own once the count is answered again
        fakeDb.countOverride = Long.MIN_VALUE;
        now.addAndGet(60);
        hostStiSync.fetch();
        now.addAndGet(60);
        hostStiSync.fetch();
        assertEquals(3, fakeDb.fullFetchCount);
        assertEquals(2, fakeDb.seenFetchCount, "the attempt that fell back, and the one that worked afterwards");
    }
}
