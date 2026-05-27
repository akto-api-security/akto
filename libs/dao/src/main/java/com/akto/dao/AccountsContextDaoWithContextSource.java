package com.akto.dao;

import com.akto.dao.context.Context;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.conversions.Bson;

public abstract class AccountsContextDaoWithContextSource<T> extends MCollection<T> {

    public static final String CONTEXT_SOURCE_FIELD = "contextSource";

    @Override
    public String getDBName() {
        return Context.accountId.get() + "";
    }

    protected Bson addContextSourceFilter(Bson originalQuery) {
        try {
            if (Context.contextSource.get() != null) {
                Bson contextFilter = Filters.eq(CONTEXT_SOURCE_FIELD, Context.contextSource.get().name());
                return Filters.and(originalQuery, contextFilter);
            }
        } catch (Exception e) {
        }
        return originalQuery;
    }

    @Override
    public java.util.List<T> findAll(Bson q, int skip, int limit, Bson sort, Bson projection) {
        return super.findAll(addContextSourceFilter(q), skip, limit, sort, projection);
    }

    @Override
    public T findOne(Bson q, Bson projection) {
        return super.findOne(addContextSourceFilter(q), projection);
    }

    @Override
    public T findLatestOne(Bson q) {
        return super.findLatestOne(addContextSourceFilter(q));
    }

    @Override
    public T updateOne(Bson q, Bson obj) {
        return super.updateOne(addContextSourceFilter(q), obj);
    }

    @Override
    public T updateOneNoUpsert(Bson q, Bson obj) {
        return super.updateOneNoUpsert(addContextSourceFilter(q), obj);
    }

    @Override
    public UpdateResult updateMany(Bson q, Bson obj) {
        return super.updateMany(addContextSourceFilter(q), obj);
    }

    @Override
    public UpdateResult updateManyNoUpsert(Bson q, Bson obj) {
        return super.updateManyNoUpsert(addContextSourceFilter(q), obj);
    }

    @Override
    public UpdateResult replaceOne(Bson q, T obj) {
        return super.replaceOne(addContextSourceFilter(q), obj);
    }

    @Override
    public DeleteResult deleteAll(Bson q) {
        return super.deleteAll(addContextSourceFilter(q));
    }

    @Override
    public long count(Bson q) {
        return super.count(addContextSourceFilter(q));
    }
}
