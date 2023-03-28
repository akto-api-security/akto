package com.akto.dao.loaders;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.loaders.Loader;
import com.akto.dto.loaders.NormalLoader;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.types.ObjectId;

import java.util.List;

public class LoadersDao extends AccountsContextDao<Loader> {

    public static final LoadersDao instance = new LoadersDao();

    public void updateCountNormalLoader(ObjectId id,int count) {
        instance.getMCollection().findOneAndUpdate(
                Filters.eq("_id", id),
                Updates.inc(NormalLoader.CURRENT_COUNT, count),
                new FindOneAndUpdateOptions().upsert(false)
        );
    }

    public void updateTotalCountNormalLoader(ObjectId id,int count) {
        instance.getMCollection().findOneAndUpdate(
                Filters.eq("_id", id),
                Updates.set(NormalLoader.TOTAL_COUNT, count),
                new FindOneAndUpdateOptions().upsert(false)
        );
    }

    public Loader find(ObjectId id) {
        return instance.findOne(Filters.eq("_id", id));
    }

    public List<Loader> findActiveLoaders(int userId) {
        return instance.findAll(
                Filters.and(
                        Filters.eq(Loader.SHOW, true),
                        Filters.eq(Loader.USER_ID, userId)
                )
        );
    }

    public void toggleShow(ObjectId id,boolean value) {
        instance.getMCollection().findOneAndUpdate(
                Filters.eq("_id", id),
                Updates.set(Loader.SHOW, value),
                new FindOneAndUpdateOptions().upsert(false)
        );
    }

    public void createNormalLoader(NormalLoader normalLoader) {
        instance.insertOne(normalLoader);
    }

    @Override
    public String getCollName() {
        return "loaders";
    }

    @Override
    public Class<Loader> getClassT() {
        return Loader.class;
    }
}
