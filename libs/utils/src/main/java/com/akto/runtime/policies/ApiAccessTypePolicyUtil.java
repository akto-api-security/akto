package com.akto.runtime.policies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.mongodb.BasicDBObject;

public class ApiAccessTypePolicyUtil {
    private static Map<Integer, ApiAccessTypePolicy> apiAccessTypePolicyMap = new HashMap<>();
    private static Map<Integer, Integer> lastFetchedAccessPolicyMap = new HashMap<>();
    private static final int INTERVAL = 10 * 60; // 10 minutes

    public static ApiAccessTypePolicy getPolicy() {
        int accountId = Context.accountId.get();
        int now = Context.now();
        if (apiAccessTypePolicyMap.containsKey(accountId) &&
                lastFetchedAccessPolicyMap.containsKey(accountId) &&
                (lastFetchedAccessPolicyMap.get(accountId) + INTERVAL) > now) {
            return apiAccessTypePolicyMap.get(accountId);
        }

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(new BasicDBObject());
        apiAccessTypePolicyMap.put(accountId, new ApiAccessTypePolicy(null, null));
        if (accountSettings != null) {
            List<String> cidrList = accountSettings.getPrivateCidrList();
            if (cidrList != null && !cidrList.isEmpty()) {
                apiAccessTypePolicyMap.get(accountId).setPrivateCidrList(cidrList);
            }
            List<String> partnerIpsList = new ArrayList<>();
            if (accountSettings.getPartnerIpList() != null) {
                partnerIpsList = accountSettings.getPartnerIpList();
            }
            apiAccessTypePolicyMap.get(accountId).setPartnerIpList(partnerIpsList);
        }
        lastFetchedAccessPolicyMap.put(accountId, now);

        return apiAccessTypePolicyMap.get(accountId);
    }
}
