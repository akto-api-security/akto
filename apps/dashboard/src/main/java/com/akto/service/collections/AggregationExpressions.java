package com.akto.service.collections;

import org.bson.Document;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /** A variable defined by a $lookup's let. */
    static String variable(String name) {
        return "$$" + name;
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

    static Document and(Object... operands) {
        return new Document("$and", Arrays.asList(operands));
    }

    static Document or(Object... operands) {
        return new Document("$or", Arrays.asList(operands));
    }

    static Document not(Object operand) {
        return new Document("$not", Arrays.asList(operand));
    }

    static Document gt(Object left, Object right) {
        return new Document("$gt", Arrays.asList(left, right));
    }

    static Document gte(Object left, Object right) {
        return new Document("$gte", Arrays.asList(left, right));
    }

    static Document lt(Object left, Object right) {
        return new Document("$lt", Arrays.asList(left, right));
    }

    /** Whether the element is in the array. */
    static Document in(Object element, Object array) {
        return new Document("$in", Arrays.asList(element, array));
    }

    static Document add(Object... operands) {
        return new Document("$add", Arrays.asList(operands));
    }

    /** A document with these fields (an expression building a document, or a literal one). */
    static Document object(Map<String, ?> fields) {
        return new Document(new LinkedHashMap<>(fields));
    }

    static Document multiply(Object... operands) {
        return new Document("$multiply", Arrays.asList(operands));
    }

    static Document round(Object operand) {
        return new Document("$round", Arrays.asList(operand, 0));
    }

    static Document divide(Object dividend, Object divisor) {
        return new Document("$divide", Arrays.asList(dividend, divisor));
    }

    /** The first n elements of an array. */
    static Document slice(Object array, int n) {
        return new Document("$slice", Arrays.asList(array, n));
    }

    static Document floor(Object operand) {
        return new Document("$floor", operand);
    }

    static Document min(Object... operands) {
        return new Document("$min", Arrays.asList(operands));
    }

    static Document arrayElemAt(Object array, int index) {
        return new Document("$arrayElemAt", Arrays.asList(array, index));
    }

    /** "missing" for a field the document does not have. */
    static Document type(Object operand) {
        return new Document("$type", operand);
    }

    /** One level of flattening: an array of arrays into the concatenation of its arrays. */
    static Document flatten(Object arrayOfArrays) {
        return new Document("$reduce", new Document("input", arrayOfArrays)
                .append("initialValue", java.util.Collections.emptyList())
                .append("in", new Document("$concatArrays", Arrays.asList("$$value", "$$this"))));
    }

    /** The value of the document a $merge is merging in, inside its whenMatched pipeline. */
    static String merged(String fieldName) {
        return "$$new." + fieldName;
    }
}
