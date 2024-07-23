package com.akto.dao.notifications;
import java.util.Map;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.events.EventsExample;
import com.akto.dto.events.EventsMetrics;

import io.intercom.api.Event;

public class EventsMetricsDao extends AccountsContextDao<EventsMetrics> {
    public static final EventsMetricsDao instance = new EventsMetricsDao();

    private EventsMetricsDao() {}

    @Override
    public String getCollName() {
        return "event_metrics";
    }

    @Override
    public Class<EventsMetrics> getClassT() {
        return EventsMetrics.class;
    }

    public static boolean createAndSendMetaDataForEvent(Event event,EventsMetrics currentEventsMetrics){
        boolean shouldSendEvent = false;
        if(currentEventsMetrics.isDeploymentStarted()){
            shouldSendEvent = true;
            event.putMetadata(EventsMetrics.DEPLOYMENT_STARTED, true);
        }
        if(currentEventsMetrics.getMilestones() != null && !currentEventsMetrics.getMilestones().isEmpty()){
            shouldSendEvent = true;
            for(Map.Entry<String,Integer> entry: currentEventsMetrics.getMilestones().entrySet()){
                event.putMetadata(entry.getKey(), entry.getValue());
            }
        }
        if(currentEventsMetrics.getCustomTemplatesCount() > 0){
            shouldSendEvent = true;
            event.putMetadata(EventsMetrics.CUSTOM_TEMPLATE_COUNT,currentEventsMetrics.getCustomTemplatesCount());
        }
        if(currentEventsMetrics.getApiSecurityPosture() != null && !currentEventsMetrics.getApiSecurityPosture().isEmpty()){
            shouldSendEvent = true;
            for(Map.Entry<String,EventsExample> entry: currentEventsMetrics.getApiSecurityPosture().entrySet()){
                EventsExample.insertMetaDataFormat(entry.getValue(), entry.getKey(), event);
            }
        }
        if(currentEventsMetrics.getSecurityTestFindings() != null && !currentEventsMetrics.getSecurityTestFindings().isEmpty()){
            shouldSendEvent = true;
            for(Map.Entry<String,Integer> entry: currentEventsMetrics.getSecurityTestFindings().entrySet()){
                event.putMetadata(entry.getKey(), entry.getValue());
            }
        }
        return shouldSendEvent;
    }
}
