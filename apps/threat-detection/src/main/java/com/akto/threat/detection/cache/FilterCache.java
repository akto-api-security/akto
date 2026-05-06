package com.akto.threat.detection.cache;

import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.data_actor.DataActor;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.utils.GzipUtils;
import io.lettuce.core.api.StatefulRedisConnection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FilterCache {

    private static final LoggerMaker logger = new LoggerMaker(FilterCache.class, LogDb.THREAT_DETECTION);
    private static final int REFRESH_INTERVAL_SEC = 300;

    private final DataActor dataActor;
    private final StatefulRedisConnection<String, String> apiCache;

    private Map<String, FilterConfig> apiFilters;
    private final List<FilterConfig> successfulExploitFilters = new ArrayList<>();
    private final List<FilterConfig> ignoredEventFilters = new ArrayList<>();
    private int lastUpdatedAt = 0;

    public FilterCache(DataActor dataActor, StatefulRedisConnection<String, String> apiCache) {
        this.dataActor = dataActor;
        this.apiCache = apiCache;
    }

    public Map<String, FilterConfig> getFilters() {
        int now = (int) (System.currentTimeMillis() / 1000);
        if (apiFilters != null && now - lastUpdatedAt < REFRESH_INTERVAL_SEC) {
            return apiFilters;
        }

        List<YamlTemplate> templates = dataActor.fetchFilterYamlTemplates();
        apiFilters = FilterYamlTemplateDao.fetchFilterConfig(false, templates, false);
        logger.debugAndAddToDb("total filters fetched " + apiFilters.size());
        this.lastUpdatedAt = now;

        successfulExploitFilters.clear();
        ignoredEventFilters.clear();

        Iterator<Map.Entry<String, FilterConfig>> iterator = apiFilters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, FilterConfig> entry = iterator.next();
            FilterConfig filter = entry.getValue();
            if (filter.getInfo() != null && filter.getInfo().getCategory() != null) {
                String categoryName = filter.getInfo().getCategory().getName();
                if (Constants.THREAT_PROTECTION_SUCCESSFUL_EXPLOIT_CATEGORY.equalsIgnoreCase(categoryName)) {
                    successfulExploitFilters.add(filter);
                    iterator.remove();
                } else if (Constants.THREAT_PROTECTION_IGNORED_EVENTS_CATEGORY.equalsIgnoreCase(categoryName)) {
                    ignoredEventFilters.add(filter);
                    iterator.remove();
                }
            }
        }
        return apiFilters;
    }

    public List<FilterConfig> getSuccessfulExploitFilters() {
        return successfulExploitFilters;
    }

    public List<FilterConfig> getIgnoredEventFilters() {
        return ignoredEventFilters;
    }

    public String getApiSchema(int apiCollectionId) {
        String apiSchema = null;
        try {
            if (this.apiCache == null) {
                return dataActor.fetchOpenApiSchema(apiCollectionId);
            }
            apiSchema = this.apiCache.sync().get(Constants.AKTO_THREAT_DETECTION_CACHE_PREFIX + apiCollectionId);
            if (apiSchema != null && !apiSchema.isEmpty()) {
                return GzipUtils.unzipString(apiSchema);
            }
            apiSchema = dataActor.fetchOpenApiSchema(apiCollectionId);
            if (apiSchema == null || apiSchema.isEmpty()) {
                logger.warnAndAddToDb("No schema found for api collection id: " + apiCollectionId);
                return null;
            }
            this.apiCache.sync().setex(Constants.AKTO_THREAT_DETECTION_CACHE_PREFIX + apiCollectionId, Constants.ONE_DAY_TIMESTAMP, apiSchema);
            apiSchema = GzipUtils.unzipString(apiSchema);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error while fetching api schema for collectionId: " + apiCollectionId);
        }
        return apiSchema;
    }
}
