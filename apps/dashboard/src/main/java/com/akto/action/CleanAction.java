package com.akto.action;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.akto.dao.context.Context;
import com.akto.utils.jobs.CleanInventory;
import com.opensymphony.xwork2.Action;

public class CleanAction extends UserAction {

    private static final ExecutorService service = Executors.newFixedThreadPool(1);

    /*
     * delete api info if corresponding sti not found.
     */

    List<Integer> apiCollectionIds;
    boolean runActually;

    public String deleteExtraApiInfo() {
        int accountId = Context.accountId.get();
        service.submit(() -> {
            Context.accountId.set(accountId);
            CleanInventory.deleteExtraApiInfo(apiCollectionIds, runActually);
        });
        return Action.SUCCESS.toUpperCase();
    }

    public String deleteNonHostSTIs() {
        int accountId = Context.accountId.get();
        service.submit(() -> {
            Context.accountId.set(accountId);
            CleanInventory.deleteNonHostSTIs(apiCollectionIds, runActually);
        });
        return Action.SUCCESS.toUpperCase();
    }

    public String unsetTemp() {
        CleanInventory.unsetTemp(apiCollectionIds);
        return Action.SUCCESS.toUpperCase();
    }

    public List<Integer> getApiCollectionIds() {
        return apiCollectionIds;
    }

    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }

    public boolean getRunActually() {
        return runActually;
    }

    public void setRunActually(boolean runActually) {
        this.runActually = runActually;
    }
}
