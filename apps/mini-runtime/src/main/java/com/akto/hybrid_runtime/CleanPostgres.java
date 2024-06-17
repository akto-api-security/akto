package com.akto.hybrid_runtime;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.context.Context;
import com.akto.data_actor.ClientActor;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.sql.SampleDataAltDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.google.api.client.util.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.google.gson.Gson;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class CleanPostgres {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CleanPostgres.class, LogDb.RUNTIME);
    final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final Gson gson = new Gson();
    private static final int LIMIT = 100;
    private static final String url = ClientActor.buildDbAbstractorUrl();

    /*
     * The response for bloom filter can be 4-5 MiB and may take some time,
     * thus larger timeouts for this client.
     */
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build();

    public static void cleanPostgresCron() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                cleanPostgresJob();
            }
        }, 0, 1, TimeUnit.HOURS);
    }

    private static void cleanPostgresJob() {

        Request request = new Request.Builder()
                .url(url + "/fetchSampleIdsFilter")
                .header(ClientActor.AUTHORIZATION, ClientActor.getAuthToken())
                .build();

        Response response = null;

        BloomFilter<CharSequence> sampleIdsFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8),
                1_000_000, 0.01);

        try {
            response = client.newCall(request).execute();
            ResponseBody responseBody = response.body();
            String body = responseBody.string();
            Map<String, Object> json = gson.fromJson(body, Map.class);
            Object outStrObj = json.get("outStr");
            if (outStrObj == null) {
                loggerMaker.errorAndAddToDb("Filter not found");
                return;
            }
            String outStr = outStrObj.toString();
            if (outStr == null || outStr.isEmpty()) {
                loggerMaker.errorAndAddToDb("Filter null or empty");
                return;
            }

            byte[] byteArr = gson.fromJson(outStr, byte[].class);

            InputStream is = new ByteArrayInputStream(byteArr);
            sampleIdsFilter = BloomFilter.readFrom(is, Funnels.stringFunnel(Charsets.UTF_8));

        } catch (Exception ex) {
            loggerMaker.errorAndAddToDb(ex,
                    String.format("Failed to call service from url %s. Error %s", url, ex.getMessage()));
        } finally {
            if (response != null) {
                response.close();
            }
        }

        if (sampleIdsFilter.approximateElementCount() == 0) {
            loggerMaker.infoAndAddToDb("Filter empty, skipping deleting data in db");
            return;
        }

        int oldDataTime = Context.now() - 60*60;
        try {
            int skip = 0;
            List<String> ids = SampleDataAltDb.iterateAndGetIds(LIMIT, skip);
            List<String> killList = new ArrayList<>();
            int readCount = 0;
            while (ids != null && !ids.isEmpty()) {
                readCount += ids.size();
                for (String id : ids) {
                    if (!sampleIdsFilter.mightContain(id)) {
                        killList.add(id);
                    }
                }
                skip += LIMIT;
                ids = SampleDataAltDb.iterateAndGetIds(LIMIT, skip);
            }
            loggerMaker.infoAndAddToDb("Read " + readCount + " rows in db");
            if (!killList.isEmpty()) {
                loggerMaker.infoAndAddToDb("Attempting to delete " + killList.size() + " rows in db");
                SampleDataAltDb.delete(killList, oldDataTime);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "ERROR in executing sql query");
        }
    }

    public static void main(String[] args) {
        cleanPostgresCron();
    }

}