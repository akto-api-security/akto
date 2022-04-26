package com.akto.parsers;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

import static com.akto.parsers.TestDump2.createSampleParams;

public class TestMergingNew extends MongoBasedTest {


    @Test
    public void testMultipleIntegerMerging() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1);
        String url = "api/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (int i=0; i< 50; i++) {
            urls.add(url + i + "/books/" + (i+1) + "/cars/" + (i+3));
        }
        System.out.println("urls:" + urls);
        for (String c: urls) {
            HttpResponseParams resp = createSampleParams("user1", c);
            responseParams.add(resp);
        }

        parser.syncFunction(responseParams.subList(0,10));
        parser.apiCatalogSync.syncWithDB();
        parser.syncFunction(responseParams.subList(10,15));
        parser.apiCatalogSync.syncWithDB();
        assertEquals(0, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(15,20));
        parser.apiCatalogSync.syncWithDB();
        assertEquals(0, getStaticURLsSize(parser));



        Map<URLTemplate, RequestTemplate> urlTemplateMap = parser.apiCatalogSync.getDelta(123).getTemplateURLToMethods();

        for (URLTemplate urlTemplate: urlTemplateMap.keySet()) {
            System.out.println(urlTemplate.getTemplateString());
        }

        System.out.println();
        assertEquals(0, getStaticURLsSize(parser));

    }

    public int getStaticURLsSize(HttpCallParser parser) {
        Map<URLStatic, RequestTemplate> urlStaticMap = parser.apiCatalogSync.getDelta(123).getStrictURLToMethods();

        return urlStaticMap.size();
    }

    @Test
    public void testParameterizedURLsTestString() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1);
        String url = "link/";
        List<HttpResponseParams> responseParams = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (String x: Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H")) {
            for (int i=0; i< 50; i++) {
                urls.add(url+x+i);
            }
        }
        System.out.println(urls);
        for (String c: urls) {
            HttpResponseParams resp = createSampleParams("user1", c);
            responseParams.add(resp);
        }

        parser.syncFunction(responseParams.subList(0,23));
        parser.apiCatalogSync.syncWithDB();
        assertEquals(23, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(23,28));
        parser.apiCatalogSync.syncWithDB();
        assertEquals(0, getStaticURLsSize(parser));

        parser.syncFunction(responseParams.subList(28,33));
        parser.apiCatalogSync.syncWithDB();
        assertEquals(0, getStaticURLsSize(parser));
    }
}
