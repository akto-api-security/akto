package com.akto.runtime;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.Markov;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.dto.HttpRequestParams;


import org.junit.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.*;

public class MarkovTest {

    @Test
    public void testBuildMarkovFromDb() {
        List<Markov> markovList = new ArrayList<>();
        Markov.State state1 = new Markov.State("api/a", "get");
        Markov.State state2 = new Markov.State("api/b", "post");
        markovList.add(new Markov(state1, state2, new HashMap<>(), new HashSet<>()));
        MarkovSync markovSync = new MarkovSync(10,10,10);
        markovSync.buildMarkovFromDb(markovList);
        Map<String, Map<String, Integer>> markovFromDb = markovSync.markovFromDb;
        assertEquals(1,markovFromDb.keySet().size());
        assertEquals(1,markovFromDb.get(state1.hashCode()+"").keySet().size());
        assertNotNull(markovFromDb.get(state1.hashCode()+"").get(state2.hashCode()+""));
    }
    public Map<String, List<String>> generateHeaders(String userId) {
        Map<String, List<String>> headers = new HashMap<>();
        String name = "Access-Token";
        headers.put(name, Collections.singletonList(userId));
        return headers;
    }

    public HttpResponseParams generateHttpResponseParamsForMarkov(String url, String method, String userId) {

        HttpRequestParams  httpRequestParams = new HttpRequestParams(
                method, url, "",generateHeaders(userId),"", 0
        );
        return new HttpResponseParams(
            "",200,"ok", generateHeaders(userId), "", httpRequestParams, Context.now(), "1111", false, Source.OTHER, "", ""
        );
    }

    @Test
    public void testNullResponses() {
        MarkovSync markovSync = new MarkovSync(2,100,100000000);
        markovSync.buildMarkov(null,"Access-Token");
        assertEquals(0,markovSync.markovMap.keySet().size());
    }

    @Test
    public void safd() {
        List<HttpResponseParams> resp = new ArrayList<>();
        resp.add(generateHttpResponseParamsForMarkov("api/a", "get", "1"));
        resp.add(generateHttpResponseParamsForMarkov("api/b", "post", "1"));
        resp.add(generateHttpResponseParamsForMarkov("api/c", "get", "1"));
        resp.add(generateHttpResponseParamsForMarkov("api/d", "get", "1"));
        resp.add(generateHttpResponseParamsForMarkov("api/b", "post", "1"));
        resp.add(generateHttpResponseParamsForMarkov("api/c", "get", "1"));
        resp.add(generateHttpResponseParamsForMarkov("api/d", "get", "2"));
        resp.add(generateHttpResponseParamsForMarkov("api/a", "get", "3"));
        resp.add(generateHttpResponseParamsForMarkov("api/b", "post", "3"));

        MarkovSync markovSync = new MarkovSync(2,100,100000000);
        markovSync.buildMarkov(resp,"Access-Token");
        Map<String,Markov> markov = markovSync.markovMap;

        assertEquals(4,markov.keySet().size());

        String todayKey = Flow.calculateTodayKey();
        Markov.State state1  = new Markov.State("api/a", "get");
        Markov.State state2  = new Markov.State("api/b", "post");
        String key = new Markov(state1, state2, new HashMap<>(), new HashSet<>()).hashCode() + "";
        assertEquals(2,markov.get(key).getCountMap().get(todayKey));
        assertEquals(2,markov.get(key).getUserIds().size());

        state1  = new Markov.State("api/b", "post");
        state2  = new Markov.State("api/c", "get");
        key = new Markov(state1, state2, new HashMap<>(), new HashSet<>()).hashCode() + "";
        assertEquals(2,markov.get(key).getCountMap().get(todayKey));
        assertEquals(1,markov.get(key).getUserIds().size());

        state1  = new Markov.State("api/c", "get");
        state2  = new Markov.State("api/d", "get");
        key = new Markov(state1, state2, new HashMap<>(), new HashSet<>()).hashCode() + "";
        assertEquals(1,markov.get(key).getCountMap().get(todayKey));
        assertEquals(1,markov.get(key).getUserIds().size());

        markovSync.getBulkUpdates();

        state1  = new Markov.State("api/a", "get");
        state2  = new Markov.State("api/b", "post");
        key = new Markov(state1, state2, new HashMap<>(), new HashSet<>()).hashCode() + "";
        Markov markov1 = markov.get(key);
        assertEquals(0,markov1.getCountMap().keySet().size());
        assertEquals(0,markov1.getUserIds().size());

        state1  = new Markov.State("api/b", "post");
        state2  = new Markov.State("api/c", "get");
        key = new Markov(state1, state2, new HashMap<>(), new HashSet<>()).hashCode() + "";
        assertEquals(2,markov.get(key).getCountMap().get(todayKey));
        assertEquals(1,markov.get(key).getUserIds().size());

    }

}

