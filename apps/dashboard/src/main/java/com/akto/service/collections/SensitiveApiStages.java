package com.akto.service.collections;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Variable;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.akto.service.collections.AggregationExpressions.*;

/**
 * Pipeline stages that keep the api_info documents that are sensitive, i.e. have a row of an active
 * sensitive data type in single_type_info (as a request or as a response). Nothing about sensitivity is
 * stored on the api_info documents: each document that reaches these stages costs one indexed seek
 * into single_type_info, by its {url, method, apiCollectionId}, that stops at the first such row. So
 * put them after every filter that can drop documents, and a limit after them.
 */
final class SensitiveApiStages {

    private static final String SENSITIVE_ROWS = "sensitiveRows";
    private static final String COLLECTION = "collection";
    private static final String URL = "url";
    private static final String METHOD = "method";

    private SensitiveApiStages() {
    }

    /** The lookup and the match on it. Leaves the lookup's array on the documents, see {@link #dropLookup()}. */
    static List<Bson> keepSensitive() {
        List<Variable<String>> let = Arrays.asList(
                new Variable<>(COLLECTION, field(ApiInfo.ID_API_COLLECTION_ID)),
                new Variable<>(URL, field(ApiInfo.ID_URL)),
                new Variable<>(METHOD, field(ApiInfo.ID_METHOD)));
        List<Bson> sensitiveRowOfTheApi = Arrays.asList(
                Aggregates.match(Filters.expr(and(
                        eq(field(SingleTypeInfo._URL), variable(URL)),
                        eq(field(SingleTypeInfo._METHOD), variable(METHOD)),
                        eq(field(SingleTypeInfo._API_COLLECTION_ID), variable(COLLECTION))))),
                Aggregates.match(SingleTypeInfoDao.instance.sensitiveRowsFilter(Filters.empty())),
                Aggregates.limit(1),
                Aggregates.project(Projections.include(Constants.ID)));

        List<Bson> stages = new ArrayList<>();
        stages.add(Aggregates.lookup(SingleTypeInfoDao.instance.getCollName(), let, sensitiveRowOfTheApi, SENSITIVE_ROWS));
        stages.add(Aggregates.match(Filters.expr(gt(size(field(SENSITIVE_ROWS)), 0))));
        return stages;
    }

    /** Takes the lookup's array off the documents again, for stages that read them as ApiInfo. */
    static Bson dropLookup() {
        return Aggregates.project(Projections.exclude(SENSITIVE_ROWS));
    }
}
