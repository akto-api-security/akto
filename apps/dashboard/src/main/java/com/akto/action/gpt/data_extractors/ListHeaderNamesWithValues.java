package com.akto.action.gpt.data_extractors;

import java.util.List;

import com.akto.action.testing.TestCollectionConfigurationAction;
import com.akto.dto.type.URLMethods;
import com.akto.util.Pair;
import com.mongodb.BasicDBObject;

public class ListHeaderNamesWithValues implements DataExtractor<Pair<String,String>>{

    public ListHeaderNamesWithValues() {}

    @Override
    public List<Pair<String,String>> extractData(BasicDBObject meta) {
        int apiCollectionId = meta.getInt("apiCollectionId", -1);
        String url = meta.getString("url", "");
        String methodString = meta.getString("method", "");
        URLMethods.Method method = URLMethods.Method.valueOf(methodString.toUpperCase());

        return new TestCollectionConfigurationAction().extractHeaderKeysWithValues(apiCollectionId, url, method);
    }
    
}
