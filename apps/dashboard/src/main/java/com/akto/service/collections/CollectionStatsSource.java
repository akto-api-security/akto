package com.akto.service.collections;

import com.akto.dao.AccountSettingsDao;

/**
 * Where the expensive per collection numbers come from: endpoint_info_views when the account has
 * the view, single_type_info otherwise. riskScore / lastSeen are not here because both come from
 * api_info in either case and stay real time (see ApiInfoMetricsRefresh).
 */
public interface CollectionStatsSource {

    void refreshEndpointsCount();

    void refreshSensitive();

    static CollectionStatsSource forAccount() {
        return AccountSettingsDao.isEndpointInfoViewEnabled()
                ? new ViewCollectionStatsSource() : new BaseCollectionStatsSource();
    }
}
