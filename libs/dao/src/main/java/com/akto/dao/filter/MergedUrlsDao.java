package com.akto.dao.filter;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.filter.MergedUrls;
import com.mongodb.client.MongoCursor;

import java.util.HashSet;
import java.util.Set;

public class MergedUrlsDao extends AccountsContextDao<MergedUrls> {

    public static final MergedUrlsDao instance = new MergedUrlsDao();

    @Override
    public String getCollName() {
        return "merged_urls";
    }

    public Set<MergedUrls> getMergedUrls() {
        MongoCursor<MergedUrls> cursor = instance.getMCollection().find().cursor();

        Set<MergedUrls> mergedUrls = new HashSet<>();

        while(cursor.hasNext()) {
            MergedUrls mergedUrlsObj = cursor.next();
            mergedUrls.add(mergedUrlsObj);
        }

        cursor.close();

        return mergedUrls;
    }

    @Override
    public Class<MergedUrls> getClassT() {
        return MergedUrls.class;
    }
}
