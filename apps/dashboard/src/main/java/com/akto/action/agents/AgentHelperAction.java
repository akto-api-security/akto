package com.akto.action.agents;

import java.util.List;
import com.akto.action.UserAction;
import com.akto.dao.SampleDataDao;
import com.akto.dto.traffic.SampleData;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.opensymphony.xwork2.Action;

public class AgentHelperAction extends UserAction {

    int skip;
    int apiCollectionId;
    int limit;
    SampleData sample;

    public String fetchAllResponsesForApiCollectionOrdered() {

        limit = Math.min(Math.max(1, limit), 10);
        skip = Math.min(Math.max(0,skip), limit);

        List<SampleData> sampleData = SampleDataDao.instance.findAll(Filters.eq(
                "_id.apiCollectionId", apiCollectionId), skip, limit, Sorts.descending("_id"));

        if (sampleData.isEmpty()) {
            addActionError("sample data not found");
            return Action.ERROR.toUpperCase();
        }

        /*
         * TODO: optimise this to send only samples which are actually different, 
         * i.e. contain different parameters
         */
        sample = sampleData.get(0);
        return Action.SUCCESS.toUpperCase();
    }

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
