package com.akto.action.traffic_metrics;

import com.akto.DaoInit;
import com.akto.action.UserAction;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.traffic_metrics.TrafficMetrics.Name;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.checkerframework.checker.units.qual.K;

import java.util.*;

public class TrafficMetricsAction extends UserAction {
    private int startTimestamp;
    private int endTimestamp;

    private List<TrafficMetrics.Name> names;
    private String groupBy;

    public static final String ID = "_id.";

    private Map<TrafficMetrics.Name, Map<String, Map<String, Long> >> trafficMetricsMap = new HashMap<>();

    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);

        // TrafficMetricsAction trafficMetricsAction = new TrafficMetricsAction();
        // trafficMetricsAction.setStartTimestamp(0);
        // trafficMetricsAction.setEndTimestamp(Context.now());
        // trafficMetricsAction.setHost("host1");
//        trafficMetricsAction.setVxlanID(1);
        // trafficMetricsAction.setNames(Collections.singletonList(TrafficMetrics.Name.INCOMING_PACKETS_MIRRORING));

        // trafficMetricsAction.execute();

        TrafficMetrics.Key key = new TrafficMetrics.Key("ip1", "host2", 1, TrafficMetrics.Name.INCOMING_PACKETS_MIRRORING, 19447, 19448);
        Map<String, Long> countMap = new HashMap<>();
        for (int h = 466641; h<466741; h++ ) {
            Random random = new Random();
            int randomNumber = random.nextInt(1001) + 1000;
            countMap.put(""+h, new Long(randomNumber));
        }
        TrafficMetrics trafficMetrics = new TrafficMetrics(key, countMap);
        TrafficMetricsDao.instance.insertOne(trafficMetrics);
    }

    @Override
    public String execute() {
        if (endTimestamp - startTimestamp > 60 * 60 * 24 * 30) {
            addActionError("Can't fetch data for more than 30 days");
            return ERROR.toUpperCase();
        }

        int startTimestampDays = startTimestamp/(3600*24);
        int endTimestampDays = endTimestamp/(3600*24) + 1;
        List<Bson> filters = TrafficMetricsDao.instance.basicFilters(names,startTimestampDays,endTimestampDays);

        Document idExpression = new Document("ts", "$countMap.k").append("name", "$_id.name");
        List<String> keys = new ArrayList<>();

        if (this.groupBy == null || this.groupBy.equals("ALL")) {
            keys.add("host");
            keys.add("vxlanID");
            keys.add("ip");
        } else if (this.groupBy.equals("HOST")) {
            idExpression.append("host", "$_id.host");
            keys.add("host");
        } else if (this.groupBy.equals("VXLANID")) {
            idExpression.append("vxlanID", "$_id.vxlanID");
            keys.add("vxlanID");
        } else if (this.groupBy.equals("IP")) {
            idExpression.append("ip", "$_id.ip");
            keys.add("ip");
        } else {
            addActionError("Invalid group by value");
            return ERROR.toUpperCase();
        }

        List<Bson> pipeline = new ArrayList<>();

        pipeline.add(Aggregates.match(Filters.and(filters)));
        pipeline.add(Aggregates.project(Document.parse("{'countMap': { '$objectToArray': \"$countMap\" }}")));
        pipeline.add(Aggregates.unwind("$countMap"));
        pipeline.add(Aggregates.group(
                idExpression,
                Accumulators.sum("sum", "$countMap.v")
        ));

        MongoCursor<BasicDBObject> endpointsCursor = TrafficMetricsDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(endpointsCursor.hasNext()) {
            try {
                BasicDBObject basicDBObject = endpointsCursor.next();
                BasicDBObject idObject = (BasicDBObject) basicDBObject.get("_id");
                String ts = idObject.getString("ts");
                TrafficMetrics.Name name = TrafficMetrics.Name.valueOf(idObject.getString("name"));
                long sum = basicDBObject.getLong("sum");
                Map<String, Map<String, Long>> trafficMetricsToStringMap = trafficMetricsMap.computeIfAbsent(name, k -> new HashMap<>());

                StringBuilder key = new StringBuilder();
                for (String k: keys) {
                    if (key.length() > 0) key.append("-");
                    Object val = idObject.get(k);
                    if (val != null) key.append(idObject.getString(k));
                }

                Map<String, Long> countMap = trafficMetricsToStringMap.computeIfAbsent(key.toString(), k -> new HashMap<>());
                countMap.put(ts, sum);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return SUCCESS.toUpperCase();
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public void setNames(List<TrafficMetrics.Name> names) {
        this.names = names;
    }

    public void setGroupBy(String groupBy) {
        this.groupBy = groupBy;
    }

    public Map<TrafficMetrics.Name, Map<String, Map<String, Long>>> getTrafficMetricsMap() {
        return trafficMetricsMap;
    }

    
}
