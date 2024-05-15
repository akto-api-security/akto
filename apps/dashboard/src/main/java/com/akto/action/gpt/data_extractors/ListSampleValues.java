package com.akto.action.gpt.data_extractors;

import com.akto.action.testing.TestCollectionConfigurationAction;
import com.akto.types.CappedSet;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singleton;

public class ListSampleValues  implements DataExtractor<String>{

    public ListSampleValues() {}

    @Override
    public List<String> extractData(BasicDBObject meta) {
        int apiCollectionId = meta.getInt("apiCollectionId", -1);
        int skip = Integer.parseInt(((List<String>)meta.getOrDefault("skip", Collections.singletonList("0"))).get(0));
        Map<String, CappedSet<String>> sampleValues = new TestCollectionConfigurationAction().findSampleValuesInPayload(apiCollectionId, skip);
        ArrayList<String> ret = new ArrayList<>();
        for(Map.Entry<String, CappedSet<String>> e: sampleValues.entrySet()) {
            ret.add(e.getKey()+ ": [" + StringUtils.join(e.getValue().getElements().stream().filter(x -> x.length() < 200).toArray(), ", ") + "]");
        }
        return ret;
    }
}