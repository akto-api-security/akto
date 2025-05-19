package com.akto.action;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.User;
import com.akto.dto.traffic.Key;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.mongodb.client.model.Filters;

public class InternalAction extends UserAction {
    private static final LoggerMaker loggerMaker = new LoggerMaker(InternalAction.class, LogDb.DASHBOARD);

    String headerKey;
    boolean actuallyDelete;
    int count;

    public String deleteApisBasedOnHeader() {

        if (headerKey == null || headerKey.isEmpty()) {
            addActionError("Invalid header key");
            return ERROR.toUpperCase();
        }

        User user = getSUser();
        if (user.getLogin() != null && !user.getLogin().contains("@akto")) {
            addActionError("Illegal operation");
            return ERROR.toUpperCase();
        }

        int time = Context.now();
        Bson filter = Filters.and(Filters.eq(SingleTypeInfo._PARAM, headerKey),
                UsageMetricCalculator.excludeDemosAndDeactivated(SingleTypeInfo._API_COLLECTION_ID));
        loggerMaker.debugAndAddToDb("Executing deleteApisBasedOnHeader find query");
        List<ApiInfoKey> apiList = SingleTypeInfoDao.instance.fetchEndpointsInCollection(filter);

        int delta = Context.now() - time;
        loggerMaker.debugAndAddToDb("Finished deleteApisBasedOnHeader find query " + delta);

        if (apiList != null && !apiList.isEmpty()) {

            List<Key> keys = new ArrayList<>();
            for (ApiInfoKey apiInfoKey : apiList) {
                loggerMaker.debugAndAddToDb("deleteApisBasedOnHeader " + apiInfoKey.toString());
                keys.add(new Key(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod(), -1, 0,
                        0));
            }

            count = apiList.size();
            if (actuallyDelete) {
                try {
                    time = Context.now();
                    loggerMaker.debugAndAddToDb("deleteApisBasedOnHeader deleting APIs");
                    com.akto.utils.Utils.deleteApis(keys);
                    delta = Context.now() - time;
                    loggerMaker.debugAndAddToDb("deleteApisBasedOnHeader deleted APIs " + delta);
                } catch (Exception e) {
                    e.printStackTrace();
                    addActionError("Error deleting APIs");
                    return ERROR.toUpperCase();
                }
            }
        }
        return SUCCESS.toUpperCase();
    }

    public String getHeaderKey() {
        return headerKey;
    }

    public void setHeaderKey(String headerKey) {
        this.headerKey = headerKey;
    }

    public boolean getActuallyDelete() {
        return actuallyDelete;
    }

    public void setActuallyDelete(boolean actuallyDelete) {
        this.actuallyDelete = actuallyDelete;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
