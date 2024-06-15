package com.akto.action;

import java.util.List;

import com.akto.dao.SampleDataDao;
import com.akto.dto.traffic.SampleData;
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

    BloomFilter<CharSequence> sampleIdsFilter;

    public BloomFilter<CharSequence> getSampleIdsFilter() {
        return sampleIdsFilter;
    }

    public void setSampleIdsFilter(BloomFilter<CharSequence> sampleIdsFilter) {
        this.sampleIdsFilter = sampleIdsFilter;
    }

    final static int limit = 100;

    public String fetchSampleIdsFilter() {

        sampleIdsFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8),
                1_000_000, 0.001);

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

        return Action.SUCCESS.toUpperCase();
    }

}
