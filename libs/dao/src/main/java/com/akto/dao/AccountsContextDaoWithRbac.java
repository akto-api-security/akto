package com.akto.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.traffic.Key;
import com.akto.dto.type.SingleTypeInfo;
import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

public abstract class AccountsContextDaoWithRbac<T> extends MCollection<T>{
    
    @Override
    public String getDBName() {
        return Context.accountId.get()+"";
    }

    public static <T> void deleteApisPerDao(List<Key> toBeDeleted, AccountsContextDaoWithRbac<T> dao, String prefix) {
        if (toBeDeleted == null || toBeDeleted.isEmpty()) return;
        List<WriteModel<T>> stiList = new ArrayList<>();

        for(Key key: toBeDeleted) {
            stiList.add(new DeleteManyModel<>(Filters.and(
                    Filters.eq(prefix + SingleTypeInfo._API_COLLECTION_ID, key.getApiCollectionId()),
                    Filters.eq(prefix + SingleTypeInfo._METHOD, key.getMethod()),
                    Filters.eq(prefix + SingleTypeInfo._URL, key.getUrl())
            )));
        }
        dao.bulkWrite(stiList, new BulkWriteOptions().ordered(false));
    }

    public static <T> Bson generateCommonFilter(Map<Key, Integer> toBeInserted,  AccountsContextDaoWithRbac<T> dao, String prefix){
        List<Bson> filterList = new ArrayList<>();
        for(Key key: toBeInserted.keySet()){
            Bson filter = Filters.and(
                Filters.eq(prefix + SingleTypeInfo._API_COLLECTION_ID, key.getApiCollectionId()),
                Filters.eq(prefix + SingleTypeInfo._METHOD, key.getMethod()),
                Filters.eq(prefix + SingleTypeInfo._URL, key.getUrl())
            );

            filterList.add(filter);
        }

        Bson finalFilter = Filters.or(filterList);
        return finalFilter;
    }

    abstract public String getFilterKeyString();

    protected Bson addRbacFilter(Bson originalQuery) {
        try {
            if (Context.userId.get() != null && Context.accountId.get() != null) {
                List<Integer> apiCollectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(),
                        Context.accountId.get());
                if (apiCollectionIds != null) {
                    List<Bson> filters = new ArrayList<>();
                    filters.add(Filters.and(Filters.exists(getFilterKeyString()),
                            Filters.in(getFilterKeyString(), apiCollectionIds)));
                    filters.add(Filters.and(Filters.exists(SingleTypeInfo._COLLECTION_IDS),
                            Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionIds)));
                    Bson rbacFilter = Filters.or(filters);
                    return Filters.and(originalQuery, rbacFilter);
                }
            }
        } catch (Exception e) {
        }
        return originalQuery;
    }

    @Override
    public List<T> findAll(Bson q, int skip, int limit, Bson sort, Bson projection) {
        Bson filteredQuery = addRbacFilter(q);
        return super.findAll(filteredQuery, skip, limit, sort, projection);
    }

    @Override
    public T findOne(Bson q, Bson projection) {
        Bson filteredQuery = addRbacFilter(q);
        return super.findOne(filteredQuery, projection);
    }

    @Override
    public T findLatestOne(Bson q){
        Bson filteredQuery = addRbacFilter(q);
        return super.findLatestOne(filteredQuery);
    }

    @Override
    public T updateOneNoUpsert(Bson q, Bson obj) {
        Bson filteredQuery = addRbacFilter(q);
        return super.updateOneNoUpsert(filteredQuery, obj);
    }

    @Override
    public T updateOne(Bson q, Bson obj){
        Bson filteredQuery = addRbacFilter(q);
        return super.updateOne(filteredQuery, obj);
    }

    @Override
    public UpdateResult updateManyNoUpsert (Bson q, Bson obj) {
        Bson filteredQuery = addRbacFilter(q);
        return super.updateManyNoUpsert(filteredQuery, obj);
    }

    @Override
    public UpdateResult updateMany (Bson q, Bson obj) {
        Bson filteredQuery = addRbacFilter(q);
        return super.updateMany(filteredQuery, obj);
    }

    @Override
    public UpdateResult replaceOne(Bson q, T obj) {
        Bson filteredQuery = addRbacFilter(q);
        return super.replaceOne(filteredQuery, obj);
    }

    @Override
    public DeleteResult deleteAll(Bson q) {
        Bson filteredQuery = addRbacFilter(q);
        return super.deleteAll(filteredQuery);
    }

    @Override
    public long count(Bson q) {
        Bson filteredQuery = addRbacFilter(q);
        return super.count(filteredQuery);
    }

}
