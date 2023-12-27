package com.akto.dto.CollectionConditions;

import java.util.ArrayList;
import java.util.List;
import org.bson.conversions.Bson;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.mongodb.client.model.Filters;

public class TimestampCondition extends CollectionCondition{

    int endTimestamp;
    int startTimestamp;
    int periodInSeconds;

    String key;

    /*
     * after x date
     * before x date
     * last x days
     * between x and y days
     */

    public TimestampCondition() {
        super(Type.TIMESTAMP, Operator.OR);
    }

    public TimestampCondition(Operator operator, String key, int endTimestamp, int startTimestamp, int periodInSeconds) {
        super(Type.TIMESTAMP, operator);
        this.endTimestamp = endTimestamp;
        this.startTimestamp = startTimestamp;
        this.periodInSeconds = periodInSeconds;
        this.key = key;
    }

    public TimestampCondition(Operator operator) {
        super(Type.TIMESTAMP, operator);
    }

    private Bson createFilter() {

        if (periodInSeconds != 0) {
            startTimestamp = Context.now() - periodInSeconds;
        }

        List<Bson> filters = new ArrayList<>();

        if (startTimestamp != 0) {
            filters.add(Filters.gte(key, startTimestamp));
        }

        if (periodInSeconds == 0 && endTimestamp != 0) {
            filters.add(Filters.lte(key, endTimestamp));
        }

        if(filters.isEmpty()) {
            return null;
        }

        return Filters.and(filters);
    }

    @Override
    public List<ApiInfoKey> returnApis() {

        Bson filter = createFilter();

        if (filter == null) {
            return new ArrayList<>();
        }

        return SingleTypeInfoDao.instance.fetchEndpointsInCollection(filter);

    }

    public int getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public int getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public int getPeriodInSeconds() {
        return periodInSeconds;
    }

    public void setPeriodInSeconds(int periodInSeconds) {
        this.periodInSeconds = periodInSeconds;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public boolean containsApi(ApiInfoKey key) {
        return checkApiUsingSTI(key, createFilter());
    }
    
}
