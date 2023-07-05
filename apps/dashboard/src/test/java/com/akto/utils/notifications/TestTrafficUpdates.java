package com.akto.utils.notifications;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.traffic_metrics.TrafficMetricsAlertsDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.traffic_metrics.TrafficMetricsAlert;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class TestTrafficUpdates extends MongoBasedTest {

    @Test
    public void testPopulateTrafficDetails() {
        TrafficMetricsDao.instance.getMCollection().drop();
        TrafficMetricsAlertsDao.instance.getMCollection().drop();
        int maxH = 0;

        List<TrafficMetrics> trafficMetricsList = new ArrayList<>();
        for (int day=1; day<10; day++) {
            int time = Context.now() - day*60*60*24;
            int start = (int) Math.floor(time/(60.0*60.0*24.0)); // start day
            int end = (int) Math.ceil(time/(60.0*60.0*24.0)); // end day

            for (int host=0; host<10; host++) {
                for (int vxlanID=0; vxlanID<10; vxlanID++) {
                    for (int ip=0; ip<5; ip++) {
                        TrafficMetrics.Key key = new TrafficMetrics.Key("ip_"+ip+"-"+host+"-"+vxlanID, "host"+host, vxlanID*host, TrafficMetrics.Name.FILTERED_REQUESTS_RUNTIME, start, end);
                        Map<String, Long> countMap = new HashMap<>();
                        for (int h =start*24; h<end*24; h ++ ) {
                            // h is hour of the day
                            int randomNumber = 1000;
                            countMap.put(""+h, (long) randomNumber);
                            maxH = Math.max(maxH, h); // to keep count last hour of traffic
                        }
                        TrafficMetrics trafficMetrics = new TrafficMetrics(key, countMap);
                        trafficMetricsList.add(trafficMetrics);
                    }
                }
            }
        }

        TrafficMetricsDao.instance.insertMany(trafficMetricsList);

        TrafficUpdates trafficUpdates = new TrafficUpdates("", "");
        trafficUpdates.populateTrafficDetails(TrafficUpdates.AlertType.FILTERED_REQUESTS_RUNTIME);

        List<TrafficMetricsAlert>  trafficMetricsAlertList = TrafficMetricsAlertsDao.instance.findAll(new BasicDBObject());
        assertEquals(10, trafficMetricsAlertList.size());
        for (TrafficMetricsAlert trafficMetricsAlert: trafficMetricsAlertList) {
            assertEquals(maxH, trafficMetricsAlert.getLastDbUpdateTs());
            assertEquals(0, trafficMetricsAlert.getLastOutgoingTrafficTs());
            assertEquals(0, trafficMetricsAlert.getLastRedAlertSentTs());
            assertEquals(0, trafficMetricsAlert.getLastGreenAlertSentTs());
        }

    }
}
