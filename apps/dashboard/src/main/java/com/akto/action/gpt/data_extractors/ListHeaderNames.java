package com.akto.action.gpt.data_extractors;

import com.akto.action.testing.TestCollectionConfigurationAction;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.List;

public class ListHeaderNames  implements DataExtractor<String>{

    public ListHeaderNames() {}

    @Override
    public List<String> extractData(BasicDBObject meta) {
        int apiCollectionId = meta.getInt("apiCollectionId", -1);
        return new ArrayList<>(new TestCollectionConfigurationAction().findNonStandardHeaderKeys(apiCollectionId));
    }
}