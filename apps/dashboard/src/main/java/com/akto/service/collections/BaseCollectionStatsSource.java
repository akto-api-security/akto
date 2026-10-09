package com.akto.service.collections;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Accounts without endpoint_info_views: the same single_type_info based methods the page used before. */
class BaseCollectionStatsSource extends AbstractCollectionStatsSource {

    @Override
    protected Map<Integer, Integer> buildCountMap(List<ApiCollection> collections) {
        return ApiCollectionsDao.instance.buildEndpointsCountToApiCollectionMapOptimized(deactivatedFilter(), collections);
    }

    @Override
    protected Map<Integer, ? extends Iterable<String>> buildSensitiveMap() {
        return SingleTypeInfoDao.instance.getSensitiveSubtypesDetectedForCollection(new ArrayList<>());
    }

    @Override
    protected int countSensitiveEndpoints() {
        return SingleTypeInfoDao.instance.getSensitiveApisCount(new ArrayList<>(), false, deactivatedFilter());
    }
}
