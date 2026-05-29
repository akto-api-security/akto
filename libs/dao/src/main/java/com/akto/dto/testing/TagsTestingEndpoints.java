package com.akto.dto.testing;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.MCollection;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.traffic.CollectionTags;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.NotImplementedException;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TagsTestingEndpoints extends TestingEndpoints {
    private String query;

    public TagsTestingEndpoints(Operator operator, String query) {
        super(Type.TAGS, operator);
        this.query = query;
    }

    public TagsTestingEndpoints() {
        super(Type.TAGS, Operator.OR);
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        throw new NotImplementedException();
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        // Defer to DB filtering; not used in memory
        return true;
    }

    @Override
    public Bson createFilters(ApiCollectionUsers.CollectionType type) {
        if (query == null || query.isEmpty()) {
            return MCollection.noMatchFilter;
        }

        String prefix = getFilterPrefix(type);
        String fieldForFilter = prefix + SingleTypeInfo._API_COLLECTION_ID;

        Bson tagMatch;
        int eqIdx = query.indexOf('=');
        if (eqIdx > 0) {
            // key=value format: match keyName to key and value to value
            String keyPart = query.substring(0, eqIdx).trim();
            String valuePart = query.substring(eqIdx + 1).trim();
            String keyRegex = ".*" + Pattern.quote(keyPart) + ".*";
            String valueRegex = ".*" + Pattern.quote(valuePart) + ".*";
            tagMatch = Filters.elemMatch(ApiCollection.TAGS_STRING,
                Filters.and(
                    Filters.regex(CollectionTags.KEY_NAME, keyRegex, "i"),
                    Filters.regex(CollectionTags.VALUE, valueRegex, "i")
                )
            );
        } else {
            // Single term: match against either keyName or value
            String regex = ".*" + Pattern.quote(query) + ".*";
            tagMatch = Filters.elemMatch(ApiCollection.TAGS_STRING,
                Filters.or(
                    Filters.regex(CollectionTags.KEY_NAME, regex, "i"),
                    Filters.regex(CollectionTags.VALUE, regex, "i")
                )
            );
        }

        List<ApiCollection> matched = ApiCollectionsDao.instance.findAll(tagMatch, null);
        List<Integer> ids = matched.stream().map(ApiCollection::getId).collect(Collectors.toList());
        if (ids.isEmpty()) return MCollection.noMatchFilter;

        return Filters.in(fieldForFilter, ids);
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
