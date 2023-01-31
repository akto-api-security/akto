package com.akto.dao.testing.sources;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.mongodb.client.model.Filters;
import io.swagger.models.auth.In;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.akto.util.Constants.ID;

public class TestSourceConfigsDao extends AccountsContextDao<TestSourceConfig> {

    ConcurrentHashMap<String, TestSourceConfig> cacheMap = new ConcurrentHashMap<>();
    AtomicInteger lastUpdatedTs = new AtomicInteger(0);
    private final int REFRESH_TIME = 10 * 60;
    public static final TestSourceConfigsDao instance = new TestSourceConfigsDao();

    private TestSourceConfigsDao() {}

    @Override
    public String getCollName() {
        
        return "test_source_configs";
    }

    public TestSourceConfig getTestSourceConfig (String id) {
        int now = Context.now();
        if (cacheMap.containsKey(id) && (now - REFRESH_TIME) < lastUpdatedTs.get()) {
            return cacheMap.get(id);
        }
        TestSourceConfig config = this.findOne(Filters.eq(ID, id));
        cacheMap.put(id, config);
        lastUpdatedTs.set(Context.now());
        return cacheMap.get(id);
    }

    @Override
    public Class<TestSourceConfig> getClassT() {
        return TestSourceConfig.class;
    }
        
    
}
