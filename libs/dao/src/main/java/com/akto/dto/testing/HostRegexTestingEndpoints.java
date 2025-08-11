package com.akto.dto.testing;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class HostRegexTestingEndpoints extends TestingEndpoints {
    String regex;

    @BsonIgnore
    Pattern pattern;

    @BsonIgnore
    Map<Integer, String> idToHost;

    public HostRegexTestingEndpoints(Operator operator, String regex) {
        super(Type.REGEX, operator);
        this.regex = regex;
    }

    public HostRegexTestingEndpoints() {
        super(Type.REGEX, Operator.OR);
    }


    @BsonIgnore
    Type actualType;

    public Type getActualType() {
        return Type.HOST_REGEX;
    }

    public void setActualType(Type actualType) {
        this.actualType = Type.HOST_REGEX;
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        throw new NotImplementedException();
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        if (pattern == null) {
            pattern = Pattern.compile(regex);
        }

        if (idToHost == null) {
            createIdToHostMap();
        }

        String apiCollectionName = idToHost.getOrDefault(key.getApiCollectionId(), "");
        if (apiCollectionName == null) return false;

        return pattern.matcher(apiCollectionName).matches();
    }

    @Override
    public Bson createFilters(ApiCollectionUsers.CollectionType type) {
        if (pattern == null) {
            pattern = Pattern.compile(regex);
        }

        String prefix = getFilterPrefix(type);

        if (idToHost == null) {
            createIdToHostMap();
        }

        List<Integer> apiCollectionIds = new ArrayList<>();
        if (regex != null) {
            for(Map.Entry<Integer, String> entry: idToHost.entrySet()) {
                int apiCollectionId = entry.getKey();
                String hostName = entry.getValue();
                if (StringUtils.isEmpty(hostName)) continue;
                if (pattern.matcher(hostName).matches()) {
                    apiCollectionIds.add(apiCollectionId);
                }
            }
        }

        return Filters.in(prefix + SingleTypeInfo._API_COLLECTION_ID, apiCollectionIds);
    }

    private void createIdToHostMap() {
        idToHost = new HashMap<>();
        for(ApiCollection apiCollection: ApiCollectionsDao.instance.getMetaAll()) {
            idToHost.put(apiCollection.getId(), apiCollection.getHostName());
        }
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
        if (pattern == null) {
            pattern = Pattern.compile(regex);
        }
    }
}
