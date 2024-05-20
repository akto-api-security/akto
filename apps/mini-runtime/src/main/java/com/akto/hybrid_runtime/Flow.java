package com.akto.hybrid_runtime;

import com.akto.DaoInit;
import com.akto.dao.RelationshipDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Markov;
import com.akto.dto.Relationship;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.hybrid_parsers.HttpCallParser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpRequestParams;


import java.io.*;
import java.util.*;

public class Flow {
    MarkovSync markovSync;
    RelationshipSync relationshipSync;
    String userIdentifierName;

    public Flow(int relationship_user_thresh, int relationship_counter_thresh, int relationship_last_sync_thresh,
                int markov_user_thresh, int markov_counter_thresh, int markov_last_sync_thresh, String userIdentifierName) {
        this.userIdentifierName = userIdentifierName;
        this.markovSync = new MarkovSync(markov_user_thresh ,markov_counter_thresh, markov_last_sync_thresh);
        this.relationshipSync = new RelationshipSync(relationship_user_thresh, relationship_counter_thresh,
                relationship_last_sync_thresh);
    }

    public void init(List<HttpResponseParams> httpResponseParams) throws Exception{
        markovSync.buildMarkov(httpResponseParams, userIdentifierName);
        relationshipSync.init(httpResponseParams, userIdentifierName);
    }

    public static String getUserIdentifier(String name, HttpRequestParams requestParams) throws Exception {
        Map<String,List<String>> headers = requestParams.getHeaders();
        List<String> l = headers.get(name);
        if (l == null) throw new Exception("Header doesn't exist");
        if (l.size() < 1) throw new Exception("User identifier headers list is empty");
        String userIdentifier = l.get(0);
        if (userIdentifier != null) return userIdentifier;
        throw new Exception("User identifier not found");
    }

    public static String calculateTodayKey() {
        int date = Context.convertEpochToDateInt(Context.now(), "UTC");
        return date + "";
    }





}
