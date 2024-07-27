package com.akto.dao.notifications;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.events.EventsMetrics;

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
}
