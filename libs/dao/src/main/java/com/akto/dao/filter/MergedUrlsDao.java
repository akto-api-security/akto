package com.akto.dao.filter;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.filter.MergedUrls;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;

import java.util.HashSet;
import java.util.Set;

public class MergedUrlsDao extends AccountsContextDao<MergedUrls> {

    public static final MergedUrlsDao instance = new MergedUrlsDao();

    @Override
    public String getCollName() {
        return "merged_urls";
    }

    private Set<MergedUrls> fetch(org.bson.conversions.Bson filter) {
        MongoCursor<MergedUrls> cursor = instance.getMCollection().find(filter).cursor();

        Set<MergedUrls> ret = new HashSet<>();

        while(cursor.hasNext()) {
            ret.add(cursor.next());
        }

        cursor.close();

        return ret;
    }

    // Urls genuinely absorbed into a template - excludes rows an advanced traffic
    // filter's demerge:true strategy flagged (those must keep their own standalone
    // SingleTypeInfo entries, see APICatalogSync#convertToMap).
    public Set<MergedUrls> getMergedUrls() {
        return fetch(Filters.ne(MergedUrls.DEMERGE, true));
    }

    // Urls flagged demerge:true - must never be folded into a template.
    public Set<MergedUrls> getDemergedUrls() {
        return fetch(Filters.eq(MergedUrls.DEMERGE, true));
    }

    @Override
    public Class<MergedUrls> getClassT() {
        return MergedUrls.class;
    }
}
