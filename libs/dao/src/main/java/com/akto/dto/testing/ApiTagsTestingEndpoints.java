package com.akto.dto.testing;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.MCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.NotImplementedException;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ApiTagsTestingEndpoints extends TestingEndpoints {
    private String query;

    public ApiTagsTestingEndpoints(Operator operator, String query) {
        super(Type.API_TAGS, operator);
        this.query = query;
    }

    public ApiTagsTestingEndpoints() {
        super(Type.API_TAGS, Operator.OR);
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        throw new NotImplementedException();
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        return true;
    }

    private Bson buildTagMatchFilter() {
        if (query == null || query.isEmpty()) {
            return null;
        }
        int eqIdx = query.indexOf('=');
        if (eqIdx > 0) {
            String keyPart = query.substring(0, eqIdx).trim();
            String valuePart = query.substring(eqIdx + 1).trim();
            String keyRegex = ".*" + Pattern.quote(keyPart) + ".*";
            String valueRegex = ".*" + Pattern.quote(valuePart) + ".*";
            return Filters.elemMatch(ApiInfo.TAGS_STRING,
                Filters.and(
                    Filters.regex(CollectionTags.KEY_NAME, keyRegex, "i"),
                    Filters.regex(CollectionTags.VALUE, valueRegex, "i")
                )
            );
        } else {
            String regex = ".*" + Pattern.quote(query) + ".*";
            return Filters.elemMatch(ApiInfo.TAGS_STRING,
                Filters.or(
                    Filters.regex(CollectionTags.KEY_NAME, regex, "i"),
                    Filters.regex(CollectionTags.VALUE, regex, "i")
                )
            );
        }
    }

    private static Bson createApiFilters(ApiCollectionUsers.CollectionType type, ApiInfo.ApiInfoKey apiKey) {
        String prefix = getFilterPrefix(type);
        return Filters.and(
            Filters.eq(prefix + SingleTypeInfo._URL, apiKey.getUrl()),
            Filters.eq(prefix + SingleTypeInfo._METHOD, apiKey.getMethod().toString()),
            Filters.in(SingleTypeInfo._COLLECTION_IDS, apiKey.getApiCollectionId())
        );
    }

    @Override
    public Bson createFilters(ApiCollectionUsers.CollectionType type) {
        if (query == null || query.isEmpty()) {
            return MCollection.noMatchFilter;
        }

        Bson tagMatchFilter = buildTagMatchFilter();
        if (tagMatchFilter == null) {
            return MCollection.noMatchFilter;
        }

        // For api_info-based collections, the tagsList field is directly on the doc
        if (type == ApiCollectionUsers.CollectionType.Id_ApiCollectionId
                || type == ApiCollectionUsers.CollectionType.Id_ApiInfoKey_ApiCollectionId) {
            return tagMatchFilter;
        }

        // For single_type_info (ApiCollectionId), query api_info first then build per-endpoint filters
        List<ApiInfo> matchedApis = ApiInfoDao.instance.findAll(tagMatchFilter);
        if (matchedApis.isEmpty()) {
            return MCollection.noMatchFilter;
        }

        List<Bson> apiFilters = new ArrayList<>();
        for (ApiInfo apiInfo : matchedApis) {
            apiFilters.add(createApiFilters(type, apiInfo.getId()));
        }
        return Filters.or(apiFilters);
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
