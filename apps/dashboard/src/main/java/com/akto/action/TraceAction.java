package com.akto.action;

import com.akto.dao.tracing.SpanDao;
import com.akto.dao.tracing.TraceDao;
import com.akto.dto.tracing.model.Span;
import com.akto.dto.tracing.model.Trace;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.*;

public class TraceAction extends UserAction {

    private int apiCollectionId;
    private String traceId;

    private List<Trace> traces;
    private List<Span> spans;

    private static final LoggerMaker loggerMaker = new LoggerMaker(TraceAction.class, LogDb.DASHBOARD);

    public String fetchLatestTraces() {
        try {

            Bson filter = Filters.eq("apiCollectionId", apiCollectionId);
            Bson projection = Projections.fields(
                Projections.include(
                    "_id",
                    "rootSpanId",
                    "aiAgentName",
                    "name",
                    "totalSpans"
                )
            );

            traces = TraceDao.instance.findAll(filter, 0, 10, Sorts.descending("startTimeMillis"), projection);

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching latest traces for apiCollectionId: " + apiCollectionId);
            addActionError("Failed to fetch traces: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }
    public String fetchSpansForTrace() {
        try {
            if (traceId == null || traceId.isEmpty()) {
                addActionError("Trace ID is required");
                return ERROR.toUpperCase();
            }

            Bson filter = Filters.eq("traceId", traceId);

            Bson projection = Projections.fields(
                Projections.include(
                    "_id",
                    "traceId",
                    "parentSpanId",
                    "spanKind",
                    "name",
                    "depth",
                    "input",
                    "output"
                )
            );

            // Sort by depth and start time
            spans = SpanDao.instance.findAll(
                filter, 0, 10000, Sorts.ascending("depth", "startTimeMillis"), projection);
            // Truncate input/output to first 4 lines and 2 lines respectively
            for (Span span : spans) {
                span.setInput(truncateMap(span.getInput(), 2));
                span.setOutput(truncateMap(span.getOutput(), 4));
            }

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching spans for traceId: " + traceId);
            addActionError("Failed to fetch spans: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }
    private Map<String, Object> truncateMap(Map<String, Object> data, int maxLines) {
        if (data == null || data.isEmpty()) {
            return new HashMap<>();
        }

        // For display purposes, we'll limit the number of top-level keys
        Map<String, Object> truncated = new HashMap<>();
        int count = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (count >= maxLines) break;

            Object value = entry.getValue();
            // Truncate string values if they're too long
            if (value instanceof String) {
                String strValue = (String) value;
                if (strValue.length() > 200) {
                    value = strValue.substring(0, 200) + "...";
                }
            } else if (value instanceof Map) {
                // For nested maps, just indicate there's more data
                value = "{...}";
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (list.size() > 2) {
                    value = "[" + list.size() + " items]";
                }
            }

            truncated.put(entry.getKey(), value);
            count++;
        }

        return truncated;
    }

    // Getters and Setters

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public List<Trace> getTraces() {
        return traces;
    }

    public List<Span> getSpans() {
        return spans;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public String getTraceId() {
        return traceId;
    }
}
