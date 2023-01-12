package com.akto.action;

import java.io.File;
import java.io.IOException;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

public class ParseHar extends MongoBasedTest{

    public void testHar() throws IOException {
        HarAction action = new HarAction();
    
        action.setApiCollectionId(123);
        action.setSkipKafka(true);

        String harString = FileUtils.readFileToString(new File("/Users/ankushjain/Downloads/NewBurp.har"));
        action.setHarString(harString);

        SingleTypeInfoDao.instance.createIndicesIfAbsent();

        action.execute();
    }


}
