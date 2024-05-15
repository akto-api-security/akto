package com.akto.dao;

import com.akto.dao.loaders.LoadersDao;
import com.akto.dto.loaders.Loader;
import com.akto.dto.loaders.PostmanUploadLoader;
import com.akto.utils.MongoBasedTest;
import com.mongodb.client.model.Filters;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestMCollections extends MongoBasedTest {

    @Test
    public void testFindNthDocumentFromEnd() {
        LoadersDao.instance.getMCollection().drop();

        int limit = 100;
        List<Loader> loaderList = new ArrayList<>();
        for (int i=0; i < limit*2; i++) {
            loaderList.add(new PostmanUploadLoader(i,i,i,false));
        }
        LoadersDao.instance.insertMany(loaderList);

        long count = LoadersDao.instance.getMCollection().estimatedDocumentCount();
        assertEquals(limit*2, count);

        LoadersDao.instance.trimCollection(limit);

        count = LoadersDao.instance.getMCollection().estimatedDocumentCount();
        assertEquals(limit/2+1, count);

        PostmanUploadLoader postmanUploadLoader = (PostmanUploadLoader)  LoadersDao.instance.findOne(Filters.eq("userId", 0));
        assertNull(postmanUploadLoader);

        postmanUploadLoader = (PostmanUploadLoader)  LoadersDao.instance.findOne(Filters.eq("userId", limit-2));
        assertNull(postmanUploadLoader);

        postmanUploadLoader = (PostmanUploadLoader)  LoadersDao.instance.findOne(Filters.eq("userId", limit * (1.5) + 1));
        assertNotNull(postmanUploadLoader);

        postmanUploadLoader = (PostmanUploadLoader)  LoadersDao.instance.findOne(Filters.eq("userId", limit * 2 - 1));
        Assert.assertNotNull(postmanUploadLoader);
    }

}
