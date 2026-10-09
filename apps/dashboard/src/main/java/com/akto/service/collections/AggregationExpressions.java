package com.akto.service.collections;

import org.bson.Document;

import java.util.Arrays;

/**
 * The aggregation expression operators the driver has no builders for, one named method each, so
 * pipelines read as expressions instead of nested operator documents. Everything else in a pipeline
 * (stages, accumulators, projections, merge) uses the driver's own builders.
 */
final class AggregationExpressions {

    private AggregationExpressions() {
    }

    /** A reference to a field of the document being processed. */
    static String field(String name) {
        return "$" + name;
    }

    static Document ifNull(Object expression, Object fallback) {
        return new Document("$ifNull", Arrays.asList(expression, fallback));
    }

    static Document cond(Object condition, Object then, Object otherwise) {
        return new Document("$cond", Arrays.asList(condition, then, otherwise));
    }

    static Document eq(Object left, Object right) {
        return new Document("$eq", Arrays.asList(left, right));
    }

    static Document ne(Object left, Object right) {
        return new Document("$ne", Arrays.asList(left, right));
    }

    static Document concat(Object... parts) {
        return new Document("$concat", Arrays.asList(parts));
    }

    static Document isArray(Object expression) {
        return new Document("$isArray", expression);
    }

    static Document size(Object expression) {
        return new Document("$size", expression);
    }

    /** A constant, which a $project would otherwise read as an inclusion flag. */
    static Document literal(Object value) {
        return new Document("$literal", value);
    }

    /** Maps every element of an array field; inside, the element is referenced as $$name. */
    static Document map(Object input, String name, Object in) {
        return new Document("$map", new Document("input", input).append("as", name).append("in", in));
    }

    /** The value of the document a $merge is merging in, inside its whenMatched pipeline. */
    static String merged(String fieldName) {
        return "$$new." + fieldName;
    }
}
