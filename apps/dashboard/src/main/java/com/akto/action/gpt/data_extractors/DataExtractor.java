package com.akto.action.gpt.data_extractors;

import com.mongodb.BasicDBObject;

import java.util.List;

public interface DataExtractor<T> {

    public List<T> extractData(BasicDBObject queryDetails);
}
