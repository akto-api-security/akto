package com.akto.parsers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.traffic_metrics.TrafficMetrics.Key;
import com.akto.dto.traffic_metrics.TrafficMetrics.Name;
import com.mongodb.BasicDBObject;

import static org.junit.Assert.assertEquals;

public class TrafficMetricsUpdateTest extends MongoBasedTest {

    @Test
    public void testSyncTrafficMetricsWithDB() {
        TrafficMetricsDao.instance.getMCollection().drop();

        HttpCallParser parser = new HttpCallParser("access-token", 1,40,10, true);
        Map<TrafficMetrics.Key, TrafficMetrics> trafficMetricsMap = new HashMap<>();
        parser.setTrafficMetricsMap(trafficMetricsMap);

        Key key1 = new Key("ip1", "host1", 1, Name.TOTAL_REQUESTS_RUNTIME, 19449, 19450);
        Map<String, Long> countMap1 = new HashMap<>();
        countMap1.put("466776", 100L);
        countMap1.put("466777", 200L);
        TrafficMetrics trafficMetrics1 = new TrafficMetrics(key1, countMap1);

        Key key2 = new Key("ip2", "host2", 2, Name.TOTAL_REQUESTS_RUNTIME, 19449, 19450);
        Map<String, Long> countMap2 = new HashMap<>();
        countMap2.put("466778", 200L);
        countMap2.put("466779", 300L);
        TrafficMetrics trafficMetrics2 = new TrafficMetrics(key2, countMap2);

        Key key3 = new Key("ip3", "host3", 3, Name.TOTAL_REQUESTS_RUNTIME, 19449, 19450);
        Map<String, Long> countMap3 = new HashMap<>();
        countMap3.put("466776", 100L);
        countMap3.put("466777", 200L);
        TrafficMetrics trafficMetrics3 = new TrafficMetrics(key3, countMap3);

        Key key4 = new Key("ip3", "host3", 3, Name.FILTERED_REQUESTS_RUNTIME, 19449, 19450);
        Map<String, Long> countMap4 = new HashMap<>();
        countMap4.put("466776", 100L);
        countMap4.put("466777", 200L);
        TrafficMetrics trafficMetrics4 = new TrafficMetrics(key4, countMap4);

        TrafficMetricsDao.instance.insertMany(Arrays.asList(trafficMetrics1, trafficMetrics2, trafficMetrics3, trafficMetrics4));


        TrafficMetrics trafficMetrics5 = new TrafficMetrics(key1, countMap1);
        countMap2 = new HashMap<>();
        countMap2.put("466780", 1000L);
        TrafficMetrics trafficMetrics6 = new TrafficMetrics(key2, countMap2);
        TrafficMetrics trafficMetrics7 = new TrafficMetrics(key3, countMap3);

        trafficMetricsMap.put(key1, trafficMetrics5);
        trafficMetricsMap.put(key2, trafficMetrics6);
        trafficMetricsMap.put(key3, trafficMetrics7);


        Key key5 = new Key("ip1", "host1", 1, Name.FILTERED_REQUESTS_RUNTIME, 19449, 19450);
        Map<String, Long> countMap5 = new HashMap<>();
        countMap5.put("466776", 100L);
        countMap5.put("466777", 200L);
        TrafficMetrics trafficMetrics8 = new TrafficMetrics(key5, countMap5);
        trafficMetricsMap.put(key5, trafficMetrics8);


        Key key6 = new Key("ip1", "host1", 1, Name.TOTAL_REQUESTS_RUNTIME, 19450, 19451);
        Map<String, Long> countMap6 = new HashMap<>();
        countMap6.put("466800", 100L);
        countMap6.put("466801", 100L);
        TrafficMetrics trafficMetrics9 = new TrafficMetrics(key6, countMap6);
        trafficMetricsMap.put(key6, trafficMetrics9);

        parser.syncTrafficMetricsWithDB();

        Map<TrafficMetrics.Key, TrafficMetrics> trafficMetricsMapFromDb = new HashMap<>();
        for (TrafficMetrics trafficMetrics:  TrafficMetricsDao.instance.findAll(new BasicDBObject())) {
            trafficMetricsMapFromDb.put(trafficMetrics.getId(), trafficMetrics);
        }

        assertEquals(6,trafficMetricsMapFromDb.size());

        TrafficMetrics trafficMetrics5Db = trafficMetricsMapFromDb.get(key1);
        assertEquals(2 , trafficMetrics5Db.getCountMap().size() );
        assertEquals(new Long(200) , trafficMetrics5Db.getCountMap().get("466776") );
        assertEquals(new Long(400) , trafficMetrics5Db.getCountMap().get("466777") );

        TrafficMetrics trafficMetrics6Db = trafficMetricsMapFromDb.get(key2);
        assertEquals(3 , trafficMetrics6Db.getCountMap().size() );
        assertEquals(new Long(200) , trafficMetrics6Db.getCountMap().get("466778") );
        assertEquals(new Long(300) , trafficMetrics6Db.getCountMap().get("466779") );
        assertEquals(new Long(1000) , trafficMetrics6Db.getCountMap().get("466780") );

        TrafficMetrics trafficMetrics7Db = trafficMetricsMapFromDb.get(key3);
        assertEquals(2 , trafficMetrics7Db.getCountMap().size() );
        assertEquals(new Long(200) , trafficMetrics7Db.getCountMap().get("466776") );
        assertEquals(new Long(400) , trafficMetrics7Db.getCountMap().get("466777") );

        TrafficMetrics trafficMetrics8Db = trafficMetricsMapFromDb.get(key5);
        assertEquals(2 , trafficMetrics8Db.getCountMap().size() );
        assertEquals(new Long(100) , trafficMetrics8Db.getCountMap().get("466776") );
        assertEquals(new Long(200) , trafficMetrics8Db.getCountMap().get("466777") );

        TrafficMetrics trafficMetrics9Db = trafficMetricsMapFromDb.get(key6);
        assertEquals(2 , trafficMetrics9Db.getCountMap().size() );
        assertEquals(new Long(100) , trafficMetrics9Db.getCountMap().get("466800") );
        assertEquals(new Long(100) , trafficMetrics9Db.getCountMap().get("466801") );

        TrafficMetrics trafficMetrics4Db = trafficMetricsMapFromDb.get(key4);
        assertEquals(2 , trafficMetrics4Db.getCountMap().size() );
        assertEquals(new Long(100) , trafficMetrics4Db.getCountMap().get("466776") );
        assertEquals(new Long(200) , trafficMetrics4Db.getCountMap().get("466777") );
    }

    
}
