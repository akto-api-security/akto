package com.akto.util;

import java.util.ArrayList;
import java.util.List;

import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.Predicate.Type;
import com.mongodb.BasicDBObject;

public class LogicalGroupUtil {
    
    public List<Predicate> getPredicatesFromPredicatesObject(List<BasicDBObject> predicates) {
        List<Predicate> arrayList = new ArrayList<>();
        for (int index = 0; index < predicates.size(); index++) {
            Type type = Type.valueOf(predicates.get(index).getString(Predicate.TYPE));
            Predicate predicate = Predicate.generatePredicate(type, predicates.get(index));
            arrayList.add(predicate);
        }
        return arrayList;
    }
}
