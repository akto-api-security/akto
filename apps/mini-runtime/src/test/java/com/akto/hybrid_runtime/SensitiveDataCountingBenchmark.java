package com.akto.hybrid_runtime;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.detection.SensitiveDataEvents;
import com.akto.dto.CustomDataType;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.IgnoreData;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.RegexPredicate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.hybrid_parsers.HttpCallParser;
import com.google.gson.Gson;
import com.mongodb.ConnectionString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Cost of sensitive data counting on the runtime's batch path (computeDelta), flag off vs on, plus
 * the raw cost of the per-value hook. Not a unit test: run its main. Needs Mongo for APICatalogSync.
 */
public class SensitiveDataCountingBenchmark {

    private static final int ACCOUNT_ID = 1000000;
    private static final Gson gson = new Gson();

    private static CustomDataType type(String name, String regex) {
        Conditions values = new Conditions(Collections.<Predicate>singletonList(new RegexPredicate(regex)), Conditions.Operator.OR);
        return new CustomDataType(name, true, Collections.emptyList(), 0, true, null, values, Conditions.Operator.OR,
                new IgnoreData(new HashMap<>(), new HashSet<>()), false, false);
    }

    private static List<HttpResponseParams> messages(int n, long seed) throws Exception {
        Random rnd = new Random(seed);
        List<HttpResponseParams> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map<String, Object> req = new HashMap<>();
            req.put("id", i);
            req.put("guest", Collections.singletonMap("name", "user" + i));
            req.put("items", Arrays.asList(rnd.nextInt(100), rnd.nextInt(100), rnd.nextInt(100)));
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "ok");
            resp.put("total", rnd.nextInt(10000));
            if (rnd.nextInt(3) == 0) req.put("ref", String.format("BK-%06d", rnd.nextInt(999999)));
            if (rnd.nextInt(4) == 0) resp.put("passport", String.format("PX-%04d", rnd.nextInt(9999)));
            Map<String, String> headers = new HashMap<>();
            headers.put("host", "bench.local");
            headers.put("content-type", "application/json");
            headers.put("x-request-id", "r-" + i);
            Map<String, Object> m = new HashMap<>();
            m.put("path", "/api/v1/resource" + (i % 50) + "/action");
            m.put("method", "POST"); m.put("type", "HTTP/1.1");
            m.put("requestHeaders", gson.toJson(headers));
            m.put("responseHeaders", gson.toJson(Collections.singletonMap("content-type", "application/json")));
            m.put("requestPayload", gson.toJson(req)); m.put("responsePayload", gson.toJson(resp));
            m.put("statusCode", "200"); m.put("status", "OK"); m.put("ip", "10.0.0.5");
            m.put("time", String.valueOf(1791536400 + rnd.nextInt(7200)));
            m.put("akto_account_id", String.valueOf(ACCOUNT_ID)); m.put("akto_vxlan_id", String.valueOf(1 + (i % 4)));
            m.put("is_pending", "false"); m.put("source", "MIRRORING");
            out.add(HttpCallParser.parseKafkaMessage(gson.toJson(m)));
        }
        return out;
    }

    /** Runs the messages through computeDelta in batches of 500, per collection, like the runtime. */
    private static long runMillis(List<HttpResponseParams> msgs) {
        APICatalogSync sync = new APICatalogSync("access-token", 1, true, false);
        long start = System.nanoTime();
        for (int from = 0; from < msgs.size(); from += 500) {
            Map<Integer, URLAggregator> perCollection = new HashMap<>();
            for (HttpResponseParams p : msgs.subList(from, Math.min(from + 500, msgs.size()))) {
                perCollection.computeIfAbsent(p.getRequestParams().getApiCollectionId(), k -> new URLAggregator()).addURL(p);
            }
            for (Map.Entry<Integer, URLAggregator> e : perCollection.entrySet()) sync.computeDelta(e.getValue(), true, e.getKey());
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    public static void main(String[] args) throws Exception {
        DaoInit.init(new ConnectionString(System.getProperty("testMongo", "mongodb://localhost:27029/admini")));
        Context.accountId.set(ACCOUNT_ID);
        Context.setActualAccountId(ACCOUNT_ID);
        List<CustomDataType> types = Arrays.asList(type("BOOKING_REF", "^BK-[0-9]{6}$"), type("PASSPORT_REF", "^PX-[0-9]{4}$"));
        SingleTypeInfo.fetchCustomDataTypes(ACCOUNT_ID, types, new ArrayList<>());
        SensitiveDataCounter.instance.setWriter((key, batch) -> true);

        int n = Integer.parseInt(System.getProperty("messages", "20000"));
        int rounds = 3;
        System.out.println("messages per round: " + n);

        for (int warm = 0; warm < 2; warm++) {
            SensitiveDataEvents.setTrackedTypes(null); runMillis(messages(n, 99 + warm));
            SensitiveDataEvents.setTrackedTypes(SensitiveDataEvents.customSensitiveTypeNames(types)); runMillis(messages(n, 199 + warm));
            SensitiveDataCounter.instance.flush();
        }

        long[] off = new long[rounds], on = new long[rounds];
        for (int r = 0; r < rounds; r++) {
            List<HttpResponseParams> a = messages(n, r), b = messages(n, r);   // identical traffic for both
            SensitiveDataEvents.setTrackedTypes(null);
            off[r] = runMillis(a);
            SensitiveDataEvents.setTrackedTypes(SensitiveDataEvents.customSensitiveTypeNames(types));
            on[r] = runMillis(b);
            long counted = 0;
            for (long v : SensitiveDataCounter.instance.snapshot().values()) counted += v;
            SensitiveDataCounter.instance.flush();
            System.out.printf("round %d: flag off %d ms, flag on %d ms (%d events counted)%n", r + 1, off[r], on[r], counted);
        }
        long offAvg = Arrays.stream(off).sum() / rounds, onAvg = Arrays.stream(on).sum() / rounds;
        System.out.printf("average: off %d ms (%.1f us/msg), on %d ms (%.1f us/msg), difference %+.1f%%%n",
                offAvg, offAvg * 1000.0 / n, onAvg, onAvg * 1000.0 / n, (onAvg - offAvg) * 100.0 / offAvg);

        // raw cost of the hook itself, per value and per message
        SingleTypeInfo.SubType generic = SingleTypeInfo.GENERIC;
        long iterations = 50_000_000L, t0 = System.nanoTime();
        for (long i = 0; i < iterations; i++) SensitiveDataEvents.record(1, generic);
        double untracked = (System.nanoTime() - t0) / (double) iterations;
        t0 = System.nanoTime();
        for (long i = 0; i < iterations / 10; i++) SensitiveDataEvents.drain();
        double scope = (System.nanoTime() - t0) / (double) (iterations / 10);
        System.out.printf("hook: record(untracked type) %.1f ns/value; drain %.1f ns/message%n", untracked, scope);
        System.exit(0);
    }
}
