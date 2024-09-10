package com.akto.action.traffic_metrics;

import com.akto.action.UserAction;
import com.akto.dao.traffic_metrics.RuntimeMetricsDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.traffic_metrics.RuntimeMetrics;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;

public class TrafficMetricsAction extends UserAction {
    private int startTimestamp;
    private int endTimestamp;
    private String instanceId;


    private List<TrafficMetrics.Name> names;
    private String groupBy;
    private String host;
    private int vxlanID;
    List<RuntimeMetrics> runtimeMetrics;
    List<String> instanceIds;


    public static final String ID = "_id.";

    private final Map<TrafficMetrics.Name, Map<String, Map<String, Long> >> trafficMetricsMap = new HashMap<>();

    @Override
    public String execute() {
        if (endTimestamp - startTimestamp > 60 * 60 * 24 * 30) {
            addActionError("Can't fetch data for more than 30 days");
            return ERROR.toUpperCase();
        }

        // this is done so that ui shows graph instead of empty screen
        for (TrafficMetrics.Name name: names) {
            trafficMetricsMap.put(name, new HashMap<>());
        }

        int startTimestampDays = startTimestamp/(3600*24);
        int endTimestampDays = endTimestamp/(3600*24) + 1;
        List<Bson> filters = TrafficMetricsDao.instance.basicFilters(names,startTimestampDays,endTimestampDays);

        Document idExpression = new Document("ts", "$countMap.k").append("name", "$_id.name");
        List<String> keys = new ArrayList<>();

        if (host != null) {
            filters.add(Filters.in(ID + TrafficMetrics.Key.HOST, this.host));
        }
        
        if (vxlanID != 0) {
            filters.add(Filters.in(ID + TrafficMetrics.Key.VXLAN_ID, this.vxlanID));
        }

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
            if (host == null && vxlanID == 0) {
                addActionError("Group by IP needs additional filtering");
                return ERROR.toUpperCase();
            }

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

    public String fetchRuntimeInstances() {
        instanceIds = new ArrayList<>();
        Bson filters = RuntimeMetricsDao.buildFilters(startTimestamp, endTimestamp);
        runtimeMetrics = RuntimeMetricsDao.instance.findAll(filters, 0, 0, Sorts.descending("timestamp"), Projections.include("instanceId"));
        for (RuntimeMetrics metric: runtimeMetrics) {
            instanceIds.add(metric.getInstanceId());
        }

        return SUCCESS.toUpperCase();
    }

    public String fetchRuntimeMetrics() {
        Bson filters = RuntimeMetricsDao.buildFilters(startTimestamp, endTimestamp, instanceId);
        // runtimeMetrics = RuntimeMetricsDao.instance.findAll(filters, 0, 0, Sorts.descending("timestamp"));
        runtimeMetrics = new ArrayList<>();

        try (MongoCursor<BasicDBObject> cursor = RuntimeMetricsDao.instance.getMCollection().aggregate(
                Arrays.asList(
                        Aggregates.match(filters),
                        Aggregates.sort(Sorts.descending("timestamp")),
                        Aggregates.group(new BasicDBObject("name", "$name"), Accumulators.first("latestDoc", "$$ROOT"))
                ), BasicDBObject.class
        ).cursor()) {
            while (cursor.hasNext()) {
                BasicDBObject basicDBObject = cursor.next();
                BasicDBObject latestDoc = (BasicDBObject) basicDBObject.get("latestDoc");
                runtimeMetrics.add(new RuntimeMetrics(latestDoc.getString("name"), 0, instanceId, latestDoc.getDouble("val")));
            }
        }

        return SUCCESS.toUpperCase();
    }

    public String fetchTrafficMetricsDesciptions(){
        names = Arrays.asList(TrafficMetrics.Name.values());
        return SUCCESS.toUpperCase();
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public List<TrafficMetrics.Name> getNames() {
        return names;
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


    public void setHost(String host) {
        this.host = host;
    }

    public void setVxlanID(int vxlanID) {
        this.vxlanID = vxlanID;
    }

    public List<RuntimeMetrics> getRuntimeMetrics() {
		return runtimeMetrics;
	}

    public void setRuntimeMetrics(List<RuntimeMetrics> runtimeMetrics) {
		this.runtimeMetrics = runtimeMetrics;
	}

    public List<String> getInstanceIds() {
		return instanceIds;
	}

	public void setInstanceIds(List<String> instanceIds) {
		this.instanceIds = instanceIds;
	}

    public String getInstanceId() {
		return instanceId;
	}

	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}

}
