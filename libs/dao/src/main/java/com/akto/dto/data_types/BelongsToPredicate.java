package com.akto.dto.data_types;

import com.akto.dto.ApiInfo;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BelongsToPredicate extends Predicate{

    private List<ApiInfo.ApiInfoKey> value;

    public BelongsToPredicate() {
        super(Type.BELONGS_TO);
    }
    public BelongsToPredicate(List<BasicDBObject> value) {
        super(Type.BELONGS_TO);
        if (value != null){
            List<ApiInfo.ApiInfoKey> list = new ArrayList<>();
            for (int index = 0; index < value.size(); index++) {
                BasicDBObject item = new BasicDBObject();
                item.putAll((HashMap) value.get(index));
                ApiInfo.ApiInfoKey infoKey = new ApiInfo.ApiInfoKey(
                        item.getInt(ApiInfo.ApiInfoKey.API_COLLECTION_ID),
                        item.getString(ApiInfo.ApiInfoKey.URL),
                        URLMethods.Method.fromString(item.getString(ApiInfo.ApiInfoKey.METHOD)));
                if (list.contains(infoKey)) {
                    return;
                }
                list.add(infoKey);
            }
            this.value = list;
        }else {
            this.value = null;
        }
    }

    @Override
    public boolean validate(Object value) {
        if (this.value != null && value instanceof ApiInfo.ApiInfoKey) {
            ApiInfo.ApiInfoKey infoKey = (ApiInfo.ApiInfoKey) value;
            return this.value.contains(infoKey);
        }
        return false;
    }

    public List<ApiInfo.ApiInfoKey> getValue() {
        return value;
    }

    public void setValue(List<ApiInfo.ApiInfoKey> value) {
        this.value = value;
    }
}
