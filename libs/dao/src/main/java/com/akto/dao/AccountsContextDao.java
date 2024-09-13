package com.akto.dao;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.context.Context;
import com.akto.dto.traffic.Key;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.WriteModel;

abstract public class AccountsContextDao<T> extends MCollection<T> {
    @Override
    public String getDBName() {
        return Context.accountId.get()+"";
    }

    public static <T> void deleteApisPerDao(List<Key> toBeDeleted, AccountsContextDao<T> dao, String prefix) {
        if (toBeDeleted == null || toBeDeleted.isEmpty()) return;
        List<WriteModel<T>> stiList = new ArrayList<>();

        for(Key key: toBeDeleted) {
            stiList.add(new DeleteManyModel<>(Filters.and(
                    Filters.eq(prefix + "apiCollectionId", key.getApiCollectionId()),
                    Filters.eq(prefix + "method", key.getMethod()),
                    Filters.eq(prefix + "url", key.getUrl())
            )));
        }

        dao.bulkWrite(stiList, new BulkWriteOptions().ordered(false));
    }
}
