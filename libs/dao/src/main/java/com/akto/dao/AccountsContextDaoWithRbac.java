package com.akto.dao;

import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

public abstract class AccountsContextDaoWithRbac<T> extends MCollection<T>{
    
    @Override
    public String getDBName() {
        return Context.accountId.get()+"";
    }

    abstract public String getFilterKeyString();

    protected Bson addRbacFilter(Bson originalQuery) {
        int accountId = Context.accountId.get();
        Bson rbacFilter = Filters.empty();
        if(Context.userId.get() != null){
            List<Integer> apiCollectionIds = RBACDao.instance.getUserCollectionsById(Context.userId.get(), accountId);
            if(apiCollectionIds != null){
                rbacFilter = Filters.in(getFilterKeyString(), apiCollectionIds);
            }
        }
        if (originalQuery != null) {
            return Filters.and(originalQuery, rbacFilter);
        } else {
            return rbacFilter;
        }
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
        return super.updateOneNoUpsert(filteredQuery, obj);
    }

    @Override
    public UpdateResult updateManyNoUpsert (Bson q, Bson obj) {
        Bson filteredQuery = addRbacFilter(q);
        return super.updateManyNoUpsert(filteredQuery, obj);
    }

    @Override
    public UpdateResult updateMany (Bson q, Bson obj) {
        Bson filteredQuery = addRbacFilter(q);
        return super.updateManyNoUpsert(filteredQuery, obj);
    }

    @Override
    public UpdateResult replaceOne(Bson q, T obj) {
        Bson filteredQuery = addRbacFilter(q);
        return this.getMCollection().replaceOne(filteredQuery, obj, new ReplaceOptions().upsert(false));
    }

    @Override
    public DeleteResult deleteAll(Bson q) {
        Bson filteredQuery = addRbacFilter(q);
        return super.deleteAll(filteredQuery);
    }

}
