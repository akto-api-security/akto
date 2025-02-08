package com.akto.notifications;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.traffic_metrics.TrafficMetricsAlertsDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.traffic_metrics.TrafficMetricsAlert;
import com.akto.notifications.TrafficUpdates;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.*;

import static com.akto.notifications.TrafficUpdates.generateAlertResult;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestTrafficUpdates extends MongoBasedTest {

    @Test
    public void testPopulateTrafficDetails() {
        TrafficMetricsDao.instance.getMCollection().drop();
        TrafficMetricsAlertsDao.instance.getMCollection().drop();
        int maxH = 0;
        int lookBackPeriod = 3;

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
                            if (day <= lookBackPeriod) maxH = Math.max(maxH, h); // to keep count last hour of traffic
                        }
                        TrafficMetrics trafficMetrics = new TrafficMetrics(key, countMap);
                        trafficMetricsList.add(trafficMetrics);
                    }
                }
            }
        }

        TrafficMetricsDao.instance.insertMany(trafficMetricsList);

        TrafficUpdates trafficUpdates = new TrafficUpdates(60*60*24*lookBackPeriod);
        trafficUpdates.populateTrafficDetails(TrafficUpdates.AlertType.FILTERED_REQUESTS_RUNTIME, Arrays.asList("host0", "host3"));

        List<TrafficMetricsAlert>  trafficMetricsAlertList = TrafficMetricsAlertsDao.instance.findAll(new BasicDBObject());
        // 10 - 2 = 8 because two hosts have been deactivated
        assertEquals(8, trafficMetricsAlertList.size());
        for (TrafficMetricsAlert trafficMetricsAlert: trafficMetricsAlertList) {
            assertTrue( trafficMetricsAlert.getLastDbUpdateTs() - maxH * 3600 < 5); // max 5 seconds difference
            assertEquals(0, trafficMetricsAlert.getLastOutgoingTrafficTs());
            assertEquals(0, trafficMetricsAlert.getLastOutgoingTrafficRedAlertSentTs());
            assertEquals(0, trafficMetricsAlert.getLastOutgoingTrafficGreenAlertSentTs());
        }

    }

    @Test
    public void testGenerateAlertResult() {
        List<TrafficMetricsAlert>  trafficMetricsAlertList = new ArrayList<>();

        int thresholdSeconds = 600;
        int currentTime = Context.now();

        // no alert because traffic was there
        trafficMetricsAlertList.add(new TrafficMetricsAlert(
                "host1", currentTime, currentTime, 0, 0, 0, 0
        ));

        // only outgoing red alert but no alerts sent earlier
        trafficMetricsAlertList.add(new TrafficMetricsAlert(
                "host2", currentTime - thresholdSeconds - 1000, currentTime, 0, 0, 0, 0
        ));

        // only db red alert but no alerts sent earlier
        trafficMetricsAlertList.add(new TrafficMetricsAlert(
                "host3", currentTime, currentTime - thresholdSeconds - 1000, 0, 0, 0, 0
        ));

        // No alerts because alert sent earlier and still not fixed
        trafficMetricsAlertList.add(new TrafficMetricsAlert(
                "host4", currentTime - thresholdSeconds - 1000, currentTime - thresholdSeconds - 1000, currentTime, 1000, currentTime, 1000
        ));

        // Both red alerts because alert sent earlier and got fixed but now again error
        trafficMetricsAlertList.add(new TrafficMetricsAlert(
                "host5", currentTime - thresholdSeconds - 1000, currentTime - thresholdSeconds - 1000, 100, 101, 100, 101
        ));

        // Traffic got fixed so 2 green alerts
        trafficMetricsAlertList.add(new TrafficMetricsAlert(
                "host6", currentTime, currentTime, 100, 99, 100, 99
        ));

        TrafficUpdates.AlertResult alertResultOutgoing = generateAlertResult(thresholdSeconds, TrafficUpdates.AlertType.OUTGOING_REQUESTS_MIRRORING, trafficMetricsAlertList);
        TrafficUpdates.AlertResult alertResultDb = generateAlertResult(thresholdSeconds, TrafficUpdates.AlertType.FILTERED_REQUESTS_RUNTIME, trafficMetricsAlertList);

        assertEquals(1,alertResultOutgoing.greenAlertHosts.size());
        assertTrue(alertResultOutgoing.greenAlertHosts.contains("host6"));
        assertEquals(1, alertResultDb.greenAlertHosts.size());
        assertTrue(alertResultDb.greenAlertHosts.contains("host6"));

        assertEquals(2,alertResultOutgoing.redAlertHosts.size());
        assertTrue(alertResultOutgoing.redAlertHosts.containsAll(Arrays.asList("host2", "host5")));
        assertEquals(2, alertResultDb.redAlertHosts.size());
        assertTrue(alertResultDb.redAlertHosts.containsAll(Arrays.asList("host3", "host5")));

    }

}
