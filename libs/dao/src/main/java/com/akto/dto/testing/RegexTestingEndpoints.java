package com.akto.dto.testing;

import com.akto.dao.MCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.NotImplementedException;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.regex.Pattern;

public class RegexTestingEndpoints extends TestingEndpoints {
    String regex;

    @BsonIgnore
    Pattern pattern;

    public RegexTestingEndpoints(Operator operator, String regex) {
        super(Type.REGEX, operator);
        this.regex = regex;
    }

    public RegexTestingEndpoints() {
        super(Type.REGEX, Operator.OR);
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
        return pattern.matcher(key.getUrl()).matches();
    }

    @Override
    public Bson createFilters(ApiCollectionUsers.CollectionType type) {
        String prefix = getFilterPrefix(type);

        if (regex != null) {
            return Filters.regex(prefix + SingleTypeInfo._URL, regex);
        }

        return MCollection.noMatchFilter;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }
}
