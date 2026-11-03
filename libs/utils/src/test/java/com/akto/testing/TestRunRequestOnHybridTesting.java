package com.akto.testing;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestingRunPlaygroundDao;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.test_editor.TestingRunPlayground;
import com.akto.dto.testing.TestingRun;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class TestRunRequestOnHybridTesting extends MongoBasedTest {

    @Before
    public void clean() {
        TestingRunPlaygroundDao.instance.getMCollection().drop();
    }

    private static OriginalHttpRequest request() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("authorization", new ArrayList<>(Collections.singletonList("")));
        return new OriginalHttpRequest("https://internal.agent/chat", "", "POST", "{\"query\":\"hi\"}", headers, "HTTP/1.1");
    }

    // plays mini-testing + cyborg: picks up the scheduled request and writes the given response back
    private static Thread fakeMiniTesting(OriginalHttpResponse response, AtomicReference<TestingRunPlayground> seen) {
        int accountId = Context.accountId.get();
        Thread t = new Thread(() -> {
            Context.accountId.set(accountId);
            for (int i = 0; i < 50; i++) {
                TestingRunPlayground scheduled = TestingRunPlaygroundDao.instance.findOne(
                        Filters.eq(TestingRunPlayground.STATE, TestingRun.State.SCHEDULED));
                if (scheduled != null) {
                    seen.set(scheduled);
                    TestingRunPlaygroundDao.instance.updateOne(Filters.eq("_id", scheduled.getId()), Updates.combine(
                            Updates.set(TestingRunPlayground.STATE, TestingRun.State.COMPLETED),
                            Updates.set("originalHttpResponse", response)));
                    return;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        t.start();
        return t;
    }

    private static OriginalHttpResponse response(int statusCode) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-type", Collections.singletonList("application/json"));
        return new OriginalHttpResponse("{\"answer\":\"ok\"}", headers, statusCode);
    }

    @Test
    public void targetsSelectedMiniTesting() throws Exception {
        AtomicReference<TestingRunPlayground> seen = new AtomicReference<>();
        Thread t = fakeMiniTesting(response(200), seen);

        OriginalHttpResponse res = Utils.runRequestOnHybridTesting(request(), "mini-testing-b");
        t.join();

        assertEquals(200, res.getStatusCode());
        assertEquals("mini-testing-b", seen.get().getMiniTestingName());
        assertEquals(TestingRunPlayground.TestingRunPlaygroundType.POSTMAN_IMPORTS, seen.get().getTestingRunPlaygroundType());
    }
}
