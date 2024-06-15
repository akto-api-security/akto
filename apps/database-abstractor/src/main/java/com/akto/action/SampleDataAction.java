package com.akto.action;

import com.akto.dao.SampleDataDao;
import com.akto.dto.traffic.SampleData;
import com.mongodb.client.MongoCursor;
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

        MongoCursor<SampleData> cursor = SampleDataDao.instance.getMCollection()
                .find(Filters.exists(SampleData.SAMPLE_IDS))
                .projection(Projections.fields(
                        Projections.excludeId(),
                        Projections.include(SampleData.SAMPLE_IDS)))
                .skip(skip)
                .limit(limit)
                .sort(Sorts.descending("_id"))
                .cursor();

        return Action.SUCCESS.toUpperCase();
    }

}
