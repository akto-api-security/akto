package com.akto.dao;

import com.akto.dto.GuardrailTopic;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.List;

public class GuardrailTopicsDao extends CommonContextDao<GuardrailTopic> {
    public static final String COLLECTION_NAME = "guardrail_topics";
    public static final GuardrailTopicsDao instance = new GuardrailTopicsDao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<GuardrailTopic> getClassT() {
        return GuardrailTopic.class;
    }

    public void createIndices() {
        getMCollection().createIndex(Indexes.ascending("topic"), new IndexOptions().unique(true));
    }

    public List<GuardrailTopic> findAll() {
        Bson sort = Sorts.ascending("topic");
        return getMCollection().find().sort(sort).into(new java.util.ArrayList<>());
    }
}
