package com.akto.service.collections;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.EndpointInfoViewDao;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.Filters;

/**
 * Where the api level numbers of the home dashboard come from: the endpoint_info_views of an account
 * that has them, otherwise the per collection counts kept on api_collection_stats. Both are chosen
 * once per request, so nothing else on the page cares which one answers.
 */
interface PostureSource {

    /** The view when the account has endpoint_info_views with rows in them, otherwise the per collection counts. */
    static PostureSource forAccount() {
        return viewHasRows() ? new ViewPostureSource() : new PartialsPostureSource();
    }

    /** Whether the account reads endpoint level numbers from endpoint_info_views (enabled, and filled). */
    static boolean viewHasRows() {
        return AccountSettingsDao.isEndpointInfoViewEnabled()
                && EndpointInfoViewDao.instance.getMCollection().countDocuments(Filters.empty(), new CountOptions().limit(1)) > 0;
    }

    /** The apis of the account (as far as the current user may see them) at the start and the end of the range. */
    PostureData load(int startTimestamp, int endTimestamp);
}
