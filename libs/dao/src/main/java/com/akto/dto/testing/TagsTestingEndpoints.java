package com.akto.dto.testing;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.MCollection;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
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

        // Fetch matching collections by tag key/value regex
        String regex = ".*" + Pattern.quote(query) + ".*";
        Bson tagMatch = Filters.elemMatch(ApiCollection.TAGS_STRING,
            Filters.or(
                Filters.regex("keyName", regex, "i"),
                Filters.regex("value", regex, "i")
            )
        );

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
