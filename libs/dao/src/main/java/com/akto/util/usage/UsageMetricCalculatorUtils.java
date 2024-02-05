package com.akto.util.usage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.mongodb.client.model.Filters;

public class UsageMetricCalculatorUtils {

    public static final String JUICE_SHOP_DEMO_COLLECTION_NAME = "juice_shop_demo";
    public static final int VULNERABLE_API_COLLECTION_ID = 1111111111;

    public static List<Integer> getDemoApiCollectionIds() {
        ApiCollection juiceShop = ApiCollectionsDao.instance.findByName(JUICE_SHOP_DEMO_COLLECTION_NAME);

        List<Integer> demos = new ArrayList<>();
        demos.add(VULNERABLE_API_COLLECTION_ID);

        if (juiceShop != null) {
            demos.add(juiceShop.getId());
        }

        return demos;
    }

    private static int lastFetched = -1;
    private static final int minRefreshTime = 60;
    private static List<Integer> deactivatedIds = new ArrayList<>();

    public static List<Integer> getDeactivatedApiCollectionIds() {

        int now = Context.now();
        if ((lastFetched + minRefreshTime) < now) {
            List<ApiCollection> deactivated = ApiCollectionsDao.instance.findAll(Filters.eq(ApiCollection._DEACTIVATED, true));
            deactivatedIds = deactivated.stream().map(apiCollection -> apiCollection.getId()).collect(Collectors.toList());
            lastFetched = now;
        }
        return deactivatedIds;
    }
}