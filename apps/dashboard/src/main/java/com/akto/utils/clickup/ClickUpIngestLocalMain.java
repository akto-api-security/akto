package com.akto.utils.clickup;

import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Throwaway local runner: edit the {@code public static final} fields below, run {@link #main}, watch logs.
 * Deletes this class when done; do not commit real tokens.
 * <p>
 * Uses HAR-shaped trace-summaries URLs: {@code pageRows}, {@code pageDirection=before}, {@code pageTimestamp},
 * {@code pageTimestampEnd}, {@code isAgent}.
 * <p>
 * <strong>Backfill:</strong> walks forward from {@link #BACKFILL_FROM_YEAR} Jan 1 (UTC) in {@link #WINDOW_DAYS}-day chunks
 * until caught up to now (covers inactive agents since January). Then switches to a rolling window of the last
 * {@link #WINDOW_DAYS} days for ongoing sync.
 */
public final class ClickUpIngestLocalMain {

    /** ClickUp session JWT only (no {@code Bearer} prefix). */
    public static final String CLICKUP_TEMP_TOKEN = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InNiVkFxWkNGdVJBPSJ9.eyJ1c2VyIjo5NDQ1MTQwNywidmFsaWRhdGVkIjp0cnVlLCJ3c19rZXkiOjM2OTg5OTE5MDksInNlc3Npb25fdG9rZW4iOnRydWUsIndvcmtzcGFjZXMiOlt7InR5cGUiOiJwYXNzd29yZCJ9XSwid29ya3NwYWNlX2lkIjoxMDUyNzU5NywiaWF0IjoxNzc3NDcyODQ5LCJleHAiOjE3Nzc1NTkyNDl9.GAZ5nex9vy27XmrDCaV6Ic-PY3o-qQoUGgNEioYiwic";

    public static final String CLICKUP_WORKSPACE_ID = "10527597";
    public static final String AKTO_CLICKUP_INGEST_URL = "http://127.0.0.1:8080/api/ingestData";
    public static final String CLICKUP_API_BASE_URL =
            "https://frontdoor-prod-us-east-2-2.clickup.com/auto-auditlog-service";
    public static final String AKTO_CLICKUP_ACCOUNT_ID = "1768362636";
    public static final String AKTO_CLICKUP_VXLAN_ID = "0";

    /** Jan 1 00:00 UTC for this year (change if agents went quiet in a prior year). */
    public static final int BACKFILL_FROM_YEAR = Year.now().getValue();

    /** Matches deltadefense.clickup.com.har ({@code pageRows} on trace-summaries). */
    private static final int HAR_PAGE_ROWS = 1000;

    /** Chunk size while scanning from January toward now; same span used for rolling mode after backfill. */
    public static final int WINDOW_DAYS = 10;

    private static final long WINDOW_MS = WINDOW_DAYS * 24L * 60L * 60L * 1000L;

    /** Start of the next forward chunk (older bound); {@code null} until first run. */
    private static volatile Long nextChunkStartMs = null;

    private static volatile boolean backfillComplete = false;

    /** Min 30. */
    public static final int CRON_INTERVAL_SECONDS = 30;

    public static final int CLICKUP_MAX_INGEST_ROWS_PER_TICK = 50;

    private ClickUpIngestLocalMain() {}

    public static void main(String[] args) {
        String token = normalizeBearer(CLICKUP_TEMP_TOKEN);
        if (StringUtils.isBlank(token)) {
            System.err.println("Set CLICKUP_TEMP_TOKEN in ClickUpIngestLocalMain (JWT, no Bearer prefix).");
            System.exit(1);
        }

        int intervalSec = Math.max(30, CRON_INTERVAL_SECONDS);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "clickup-ingest-local");
            t.setDaemon(false);
            return t;
        });

        Runnable tick = () -> {
            Map<String, String> overlay = buildOverlay(token);
            ClickUpIngestService.withEnvOverlay(overlay, () -> ClickUpIngestService.runOneSync(false));
        };

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));

        System.out.println(
                "ClickUp local: backfill from Jan 1 " + BACKFILL_FROM_YEAR + " UTC in " + WINDOW_DAYS
                        + "d chunks, then rolling; every " + intervalSec + "s → " + AKTO_CLICKUP_INGEST_URL);
        tick.run();
        scheduler.scheduleAtFixedRate(tick, intervalSec, intervalSec, TimeUnit.SECONDS);
    }

    private static long startOfJanuaryUtcMs(int year) {
        return LocalDate.of(year, Month.JANUARY, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /**
     * Query shape from browser HAR; only time-related query params beyond pagination ({@code pageRows}, {@code isAgent},
     * {@code pageDirection}).
     */
    private static String buildHarStyleFetchUrl(long pageTimestampMs, long pageTimestampEndMs) {
        HttpUrl base = HttpUrl.parse(CLICKUP_API_BASE_URL.trim().replaceAll("/+$", ""));
        if (base == null) {
            throw new IllegalStateException("Invalid CLICKUP_API_BASE_URL");
        }
        HttpUrl.Builder b = base.newBuilder();
        b.addPathSegment("v1");
        b.addPathSegment("workspaces");
        b.addPathSegment(CLICKUP_WORKSPACE_ID.trim());
        b.addPathSegment("trace-summaries");
        b.addQueryParameter("pageRows", String.valueOf(HAR_PAGE_ROWS));
        b.addQueryParameter("pageDirection", "before");
        b.addQueryParameter("pageTimestamp", Long.toString(pageTimestampMs));
        b.addQueryParameter("pageTimestampEnd", Long.toString(pageTimestampEndMs));
        b.addQueryParameter("isAgent", "true");
        return b.build().toString();
    }

    private static Map<String, String> buildOverlay(String token) {
        long now = System.currentTimeMillis();
        long pageTs;
        long pageTsEnd;

        if (!backfillComplete) {
            long jan1 = startOfJanuaryUtcMs(BACKFILL_FROM_YEAR);
            long low = nextChunkStartMs != null ? nextChunkStartMs : jan1;
            if (low >= now) {
                backfillComplete = true;
                nextChunkStartMs = null;
                System.out.println("[clickup-local] backfill reached now — switching to rolling " + WINDOW_DAYS + "d window");
                pageTs = now;
                pageTsEnd = now - WINDOW_MS;
            } else {
                long high = Math.min(low + WINDOW_MS, now);
                pageTsEnd = low;
                pageTs = high;
                nextChunkStartMs = high;
                System.out.println(
                        "[clickup-local] backfill chunk Jan→now: pageTimestampEnd=" + pageTsEnd + " pageTimestamp=" + pageTs);
            }
        } else {
            pageTs = now;
            pageTsEnd = now - WINDOW_MS;
            System.out.println("[clickup-local] rolling: pageTimestamp=" + pageTs + " pageTimestampEnd=" + pageTsEnd);
        }

        String fetchUrl = buildHarStyleFetchUrl(pageTs, pageTsEnd);
        System.out.println("[clickup-local] " + fetchUrl);

        Map<String, String> m = new HashMap<>();
        m.put("CLICKUP_TEMP_TOKEN", token);
        m.put("CLICKUP_WORKSPACE_ID", CLICKUP_WORKSPACE_ID);
        m.put("AKTO_CLICKUP_INGEST_URL", AKTO_CLICKUP_INGEST_URL);
        m.put("CLICKUP_API_BASE_URL", CLICKUP_API_BASE_URL);
        m.put("AKTO_CLICKUP_ACCOUNT_ID", AKTO_CLICKUP_ACCOUNT_ID);
        m.put("AKTO_CLICKUP_VXLAN_ID", AKTO_CLICKUP_VXLAN_ID);
        m.put("CLICKUP_MAX_INGEST_ROWS_PER_TICK", String.valueOf(CLICKUP_MAX_INGEST_ROWS_PER_TICK));
        m.put("CLICKUP_FETCH_URL", fetchUrl);
        return m;
    }

    private static String normalizeBearer(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            t = t.substring(7).trim();
        }
        return t;
    }
}
