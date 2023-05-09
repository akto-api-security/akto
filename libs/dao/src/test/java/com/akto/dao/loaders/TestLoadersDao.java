package com.akto.dao.loaders;
import com.akto.dto.loaders.Loader;
import com.akto.dto.loaders.PostmanUploadLoader;
import com.akto.utils.MongoBasedTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.Test;

public class TestLoadersDao extends MongoBasedTest {

    public static final int userId = 123;
    
    @Test
    public void testupdateIncrementalCount() {
        LoadersDao.instance.getMCollection().drop();

        PostmanUploadLoader postmanUploadLoader = new PostmanUploadLoader(userId, 10, 20, false);
        LoadersDao.instance.insertOne(postmanUploadLoader);
        int inc = 5;
        LoadersDao.instance.updateIncrementalCount(postmanUploadLoader.getId(), inc);

        PostmanUploadLoader postmanUploadLoaderFromDb = (PostmanUploadLoader) LoadersDao.instance.find(postmanUploadLoader.getId());

        assertEquals(postmanUploadLoader.getCurrentCount() + inc, postmanUploadLoaderFromDb.getCurrentCount());
        assertEquals(postmanUploadLoader.getTotalCount(), postmanUploadLoaderFromDb.getTotalCount());
        assertEquals(postmanUploadLoader.isShow(), postmanUploadLoaderFromDb.isShow());


        ObjectId objectId = new ObjectId();
        LoadersDao.instance.updateIncrementalCount(objectId, inc);
        PostmanUploadLoader postmanUploadLoaderFromDbNewId = (PostmanUploadLoader) LoadersDao.instance.find(objectId);
        assertNull(postmanUploadLoaderFromDbNewId);
    }

    @Test
    public void testUpdateTotalCountNormalLoader() {
        LoadersDao.instance.getMCollection().drop();

        PostmanUploadLoader postmanUploadLoader = new PostmanUploadLoader(userId, 10, 20, false);
        LoadersDao.instance.insertOne(postmanUploadLoader);
        int total = 50;
        LoadersDao.instance.updateTotalCountNormalLoader(postmanUploadLoader.getId(), total);

        PostmanUploadLoader postmanUploadLoaderFromDb = (PostmanUploadLoader) LoadersDao.instance.find(postmanUploadLoader.getId());

        assertEquals(postmanUploadLoader.getCurrentCount(), postmanUploadLoaderFromDb.getCurrentCount());
        assertEquals(total, postmanUploadLoaderFromDb.getTotalCount());
        assertEquals(postmanUploadLoader.isShow(), postmanUploadLoaderFromDb.isShow());


        ObjectId objectId = new ObjectId();
        LoadersDao.instance.updateTotalCountNormalLoader(objectId, total);
        PostmanUploadLoader postmanUploadLoaderFromDbNewId = (PostmanUploadLoader) LoadersDao.instance.find(objectId);
        assertNull(postmanUploadLoaderFromDbNewId);
    }


    @Test
    public void testFindActiveLoaders() {
        LoadersDao.instance.getMCollection().drop();

        PostmanUploadLoader loader1 = new PostmanUploadLoader(userId, 10, 20, false);
        PostmanUploadLoader loader2 = new PostmanUploadLoader(userId, 11, 20, true);
        PostmanUploadLoader loader3 = new PostmanUploadLoader(userId, 12, 20, false);
        PostmanUploadLoader loader4 = new PostmanUploadLoader(userId, 13, 20,true);
        PostmanUploadLoader loader5 = new PostmanUploadLoader(userId, 12, 20, false);

        LoadersDao.instance.insertMany(Arrays.asList(loader1, loader2, loader3, loader4, loader5));

        List<Loader> loaders = LoadersDao.instance.findActiveLoaders(userId);
        assertEquals(2, loaders.size());
        
    }
}
