package com.akto.runtime;

import com.akto.dao.MarkovDao;
import com.akto.dao.context.Context;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.Markov;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;

import java.util.*;

import static com.akto.runtime.Flow.getUserIdentifier;

public class MarkovSync {
    public Map<String,Map<String, Integer>> markovFromDb = new HashMap<>();
    public final Map<String,Markov> markovMap = new HashMap<>();
    private final Map<String, Markov.State> userLastState = new HashMap<>();

    private int counter;
    private final int counter_thresh;
    private int last_sync;
    private final int last_sync_thresh;
    private final int user_thresh;

    private static final LoggerMaker loggerMaker = new LoggerMaker(MarkovSync.class);

    public MarkovSync(int user_thresh,int counter_thresh, int last_sync_thresh) {
        this.last_sync = Context.now();
        this.counter = 0;
        this.counter_thresh = counter_thresh;
        this.last_sync_thresh = last_sync_thresh;
        this.user_thresh = user_thresh;
    }

    private void buildFromDb() {
        markovFromDb = new HashMap<>();
        List<Markov> markovList = MarkovDao.instance.findAll(new BasicDBObject());
        buildMarkovFromDb(markovList);
    }

    public void buildMarkovFromDb(List<Markov> markovList) {
        for (Markov markov: markovList) {
            Markov.State curr = markov.getCurrent();
            Markov.State next = markov.getNext();
            int count = markov.getTotalCount();

            String key = curr.hashCode() + "";
            if (!markovFromDb.containsKey(key)) {
                markovFromDb.put(key, new HashMap<>());
            }
            markovFromDb.get(key).put(next.hashCode()+"", count);
        }
    }

    public List<WriteModel<Markov>> getBulkUpdates() {
        List<WriteModel<Markov>> bulkUpdates = new ArrayList<>();
        String todayKey = Flow.calculateTodayKey();

        for (String key: markovMap.keySet()) {
            Markov value = markovMap.get(key);
            if (value.getUserIds().size() < user_thresh) {
                continue;
            }

            Bson filters = Filters.and(
                    Filters.eq("current.url", value.getCurrent().getUrl()),
                    Filters.eq("current.method", value.getCurrent().getMethod()),
                    Filters.eq("next.url", value.getNext().getUrl()),
                    Filters.eq("next.method", value.getNext().getMethod())
            );

            String countFieldName =  "countMap." + todayKey;
            int count = value.getCountMap().get(todayKey);
            Bson update = Updates.inc(countFieldName, count);

            bulkUpdates.add(
                    new UpdateOneModel<>(filters,update, new UpdateOptions().upsert(true))
            );

            value.setCountMap(new HashMap<>());
            value.setUserIds(new HashSet<>());
            markovMap.put(key, value);
        }

        return bulkUpdates;
    }

    private void syncWithDb() {
        List<WriteModel<Markov>> bulkUpdates = getBulkUpdates();
        loggerMaker.infoAndAddToDb("adding " + bulkUpdates.size() + " updates", LogDb.RUNTIME);
        if (bulkUpdates.size() > 0) {
            try {
                MarkovDao.instance.getMCollection().bulkWrite(bulkUpdates);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e.getMessage(), LogDb.RUNTIME);
            }
        }

        buildFromDb();

    }

    public void buildMarkov(List<HttpResponseParams> httpResponseParams, String userIdentifierName) {
        if (httpResponseParams == null) return;
        for (HttpResponseParams httpResponseParam: httpResponseParams) {
            HttpRequestParams httpRequestParams = httpResponseParam.getRequestParams();
            Markov.State nextState = new Markov.State(httpRequestParams.getURL(), httpRequestParams.getMethod());
            String userIdentifier;
            try {
                userIdentifier = getUserIdentifier(userIdentifierName, httpRequestParams);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e.getMessage(), LogDb.RUNTIME);
                continue;
            }
            this.counter += 1;

            Markov.State currentState = userLastState.get(userIdentifier);
            userLastState.put(userIdentifier, nextState);
            if (currentState == null) {
                continue;
            }

            Markov m = new Markov(currentState,nextState,new HashMap<>(), new HashSet<>());
            int hashCode = m.hashCode();

            Markov value = markovMap.get(hashCode+"");
            if (value == null) {
                value = m;
            }

            value.increaseCount(Flow.calculateTodayKey(), userIdentifier);
            markovMap.put(m.hashCode()+"", value);

        }

        if (this.counter >= this.counter_thresh || Context.now() - this.last_sync >= last_sync_thresh) {
            syncWithDb();
            this.counter = 0;
            this.last_sync = Context.now();
        }


    }



}
