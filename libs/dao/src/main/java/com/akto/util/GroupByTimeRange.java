package com.akto.util;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.List;

public class GroupByTimeRange {

    public static String groupByAllRange(long daysBetween, List<Bson> pipeline, String timestampField, String groupByField, int maxGraphPoints, BasicDBObject groupedId) {
        if (daysBetween <= maxGraphPoints) {
            return groupByDay(pipeline, timestampField, groupByField, groupedId);
        } else if (daysBetween <= (maxGraphPoints * 7)) {
            return groupByWeek(pipeline, timestampField, groupByField, groupedId);
        } else {
            return groupByMonth(pipeline, timestampField, groupByField, groupedId);
        }
    }

    public static String groupByDay(List<Bson> pipeline, String timestampField, String groupByField, BasicDBObject groupedId) {
        Bson addFieldsStage;
        if (groupedId != null) {
            addFieldsStage = Aggregates.addFields(
                new Field<>("year", new BasicDBObject("$year", new BasicDBObject("$toDate",
                    new BasicDBObject("$multiply", Arrays.asList("$"+timestampField, 1000))))),
                new Field<>("day", new BasicDBObject("$dateToString",
                    new BasicDBObject("format", "%Y-%m-%d")
                        .append("date", new BasicDBObject("$toDate",
                            new BasicDBObject("$multiply", Arrays.asList("$"+timestampField, 1000)))))
                ));
        } else {
            addFieldsStage = Aggregates.addFields(
                new Field<>("day", new BasicDBObject("$dateToString",
                    new BasicDBObject("format", "%Y-%m-%d")
                        .append("date", new BasicDBObject("$toDate",
                            new BasicDBObject("$multiply", Arrays.asList("$"+timestampField, 1000)))))
                ));
        }
    
        Bson groupStage;
        if (groupedId == null) {
            groupStage = Aggregates.group("$day", Accumulators.sum(groupByField, 1));
        } else {
            BasicDBObject finalId = new BasicDBObject("year", "$year").append("dayOfYear", "$day");
            finalId.putAll(groupedId.toMap());
            groupStage = Aggregates.group(finalId, Accumulators.sum(groupByField, 1));
        }
    
        Bson sortStage = Aggregates.sort(new BasicDBObject("_id", 1));
        pipeline.add(addFieldsStage);
        pipeline.add(groupStage);
        pipeline.add(sortStage);
    
        return groupedId != null ? "dayOfYear" : "";
    }
    
    public static String groupByWeek(List<Bson> pipeline, String timestampField, String groupByField, BasicDBObject groupedId) {
        Bson addFieldsStage;
        if (groupedId != null) {
            addFieldsStage = Aggregates.addFields(
                new Field<>("year", new BasicDBObject("$year", new BasicDBObject("$toDate",
                    new BasicDBObject("$multiply", Arrays.asList("$"+timestampField, 1000))))),
                new Field<>("week", new BasicDBObject("$week", new BasicDBObject("$toDate",
                    new BasicDBObject("$multiply", Arrays.asList("$"+timestampField, 1000)))))
            );
        } else {
            addFieldsStage = Aggregates.addFields(
                new Field<>("week", new BasicDBObject("$week", new BasicDBObject("$toDate",
                    new BasicDBObject("$multiply", Arrays.asList("$"+timestampField, 1000)))))
            );
        }
    
        Bson groupStage;
        if (groupedId == null) {
            groupStage = Aggregates.group("$week", Accumulators.sum(groupByField, 1));
        } else {
            BasicDBObject finalId = new BasicDBObject("year", "$year").append("weekOfYear", "$week");
            finalId.putAll(groupedId.toMap());
            groupStage = Aggregates.group(finalId, Accumulators.sum(groupByField, 1));
        }
    
        Bson sortStage = Aggregates.sort(new BasicDBObject("_id", 1));
        pipeline.add(addFieldsStage);
        pipeline.add(groupStage);
        pipeline.add(sortStage);
    
        return groupedId != null ? "weekOfYear" : "";
    }
    
    public static String groupByMonth(List<Bson> pipeline, String timestampField, String groupByField, BasicDBObject groupedId) {
        Bson addFieldsStage;
        if (groupedId != null) {
            addFieldsStage = Aggregates.addFields(
                new Field<>("year", new BasicDBObject("$year", new BasicDBObject("$toDate",
                    new BasicDBObject("$multiply", Arrays.asList("$"+timestampField, 1000))))),
                new Field<>("month", new BasicDBObject("$month", new BasicDBObject("$toDate",
                    new BasicDBObject("$multiply", Arrays.asList("$"+timestampField, 1000)))))
            );
        } else {
            addFieldsStage = Aggregates.addFields(
                new Field<>("month", new BasicDBObject("$month", new BasicDBObject("$toDate",
                    new BasicDBObject("$multiply", Arrays.asList("$"+timestampField, 1000)))))
            );
        }
    
        Bson groupStage;
        if (groupedId == null) {
            groupStage = Aggregates.group("$month", Accumulators.sum(groupByField, 1));
        } else {
            BasicDBObject finalId = new BasicDBObject("year", "$year").append("monthOfYear", "$month");
            finalId.putAll(groupedId.toMap());
            groupStage = Aggregates.group(finalId, Accumulators.sum(groupByField, 1));
        }
    
        Bson sortStage = Aggregates.sort(new BasicDBObject("_id", 1));
        pipeline.add(addFieldsStage);
        pipeline.add(groupStage);
        pipeline.add(sortStage);
    
        return groupedId != null ? "monthOfYear" : "";
    }    
}
