package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.mongodb.BasicDBObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akto.dto.runtime_filters.RuntimeFilter.API_ACCESS_TYPE_FILTER;
import static com.akto.dto.runtime_filters.RuntimeFilter.OPEN_ENDPOINTS_FILTER;

public class RuntimeFilterDao extends AccountsContextDao<RuntimeFilter>{
    public static RuntimeFilterDao instance = new RuntimeFilterDao();
    private static final Logger logger = LoggerFactory.getLogger(RuntimeFilterDao.class);


    public void initialiseFilters() {
        List<RuntimeFilter> runtimeFilters = instance.findAll(new BasicDBObject());
        Map<String, RuntimeFilter> runtimeFilterMap = new HashMap<>();
        int t = Context.now();
        // to avoid duplicate keys we increment the index by 1
        runtimeFilterMap.put(OPEN_ENDPOINTS_FILTER, RuntimeFilter.generateOpenEndpointsFilter(t));
        runtimeFilterMap.put(API_ACCESS_TYPE_FILTER, RuntimeFilter.generateApiAccessTypeFilter(t+1));
        for (RuntimeFilter runtimeFilter: runtimeFilters) {
            runtimeFilterMap.remove(runtimeFilter.getName());
        }

        if (runtimeFilterMap.values().size() > 0) {
            logger.info("Inserting "+ runtimeFilterMap.keySet().size()+" filters");
            RuntimeFilterDao.instance.insertMany(new ArrayList<>(runtimeFilterMap.values()));
        }

    }

    @Override
    public String getCollName() {
        return "runtime_filters";
    }

    @Override
    public Class<RuntimeFilter> getClassT() {
        return RuntimeFilter.class;
    }
}
