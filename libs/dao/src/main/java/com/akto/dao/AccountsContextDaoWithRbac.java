package com.akto.dao;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.RBACDao;
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
                    Filters.eq(prefix + "apiCollectionId", key.getApiCollectionId()),
                    Filters.eq(prefix + "method", key.getMethod()),
                    Filters.eq(prefix + "url", key.getUrl())
            )));
        }
        dao.bulkWrite(stiList, new BulkWriteOptions().ordered(false));
    }

    abstract public String getFilterKeyString();

    protected Bson modifyFilters(Bson originalQuery, boolean ignoreGroupFilter){
        try {
            if ((Context.userId.get() != null || Context.contextSource.get() != null) && Context.accountId.get() != null) {
                // Check if user is Admin first - Admin users should see ALL data
                if (RBACDao.isAdminUser()) {
                    return originalQuery;
                }
                
                // For non-Admin users, apply RBAC filtering based on accessible collections
                // This includes collections the user has access to, even if they are deleted
                List<Integer> apiCollectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(),
                        Context.accountId.get());
                
                // For RBAC disabled case, apiCollectionIds is null - show all data
                if (apiCollectionIds == null) {
                    return originalQuery;
                }
                
                // If empty list, no collections accessible - return empty filter
                if (apiCollectionIds.isEmpty()) {
                    return Filters.and(originalQuery, Filters.empty());
                }
                
                // Apply RBAC filtering with user's accessible collection list
                // This will show data for collections the user has access to, including deleted ones
                List<Bson> filters = new ArrayList<>();
                filters.add(Filters.and(Filters.exists(getFilterKeyString()),
                        Filters.in(getFilterKeyString(), apiCollectionIds)));
                if(!ignoreGroupFilter){
                    filters.add(Filters.and(Filters.exists(SingleTypeInfo._COLLECTION_IDS),
                        Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionIds)));
                }
                
                Bson rbacFilter = Filters.or(filters);
                return Filters.and(originalQuery, rbacFilter);
            }
        } catch (Exception e) {
        }
        return originalQuery;
    }

    protected Bson countRbacFilter(Bson originalQuery){
        return modifyFilters(originalQuery, true);
    }

    protected Bson addRbacFilter(Bson originalQuery) {
        return modifyFilters(originalQuery, false);
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

    public T findOneNoRbacFilter(Bson q, Bson projection) {
        return super.findOne(q, projection);
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
        Bson filteredQuery = countRbacFilter(q);
        return super.count(filteredQuery);
    }

}
