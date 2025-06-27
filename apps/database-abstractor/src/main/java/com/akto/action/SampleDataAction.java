package com.akto.action;

import java.io.ByteArrayOutputStream;
import java.util.List;
import com.akto.dao.SampleDataDao;
import com.akto.dto.traffic.SampleData;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import com.google.api.client.util.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

public class SampleDataAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SampleDataAction.class, LogDb.DASHBOARD);

    final static int limit = 100;

    public String fetchSampleIdsFilter() {

        // Expected max size 4-5 MiB
        BloomFilter<CharSequence> sampleIdsFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8),
                1_000_000, 0.01);

        int skip = 0;
        List<SampleData> sampleDataList = SampleDataDao.instance.findAll(
                Filters.exists(SampleData.SAMPLE_IDS),
                skip, limit,
                Sorts.descending(Constants.ID),
                Projections.include(SampleData.SAMPLE_IDS));

        while (sampleDataList != null && !sampleDataList.isEmpty()) {
            for (SampleData sampleData : sampleDataList) {
                if (sampleData.getSampleIds() != null && !sampleData.getSampleIds().isEmpty()) {
                    for (String id : sampleData.getSampleIds()) {
                        sampleIdsFilter.put(id);
                    }
                }
            }

            skip += limit;
            sampleDataList = SampleDataDao.instance.findAll(Filters.exists(SampleData.SAMPLE_IDS),
                    skip, limit,
                    Sorts.descending(Constants.ID),
                    Projections.include(SampleData.SAMPLE_IDS));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            sampleIdsFilter.writeTo(out);
            outStr = out.toByteArray();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Unable to write bloom filter");
        }

        return Action.SUCCESS.toUpperCase();
    }

    byte[] outStr;

    public byte[] getOutStr() {
        return outStr;
    }

    public void setOutStr(byte[] outStr) {
        this.outStr = outStr;
    }

}
