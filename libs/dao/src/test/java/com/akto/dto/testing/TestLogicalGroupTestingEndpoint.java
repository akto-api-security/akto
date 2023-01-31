package com.akto.dto.testing;

import com.akto.dto.ApiInfo;
import com.akto.dto.data_types.*;
import com.akto.dto.type.URLMethods;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestLogicalGroupTestingEndpoint {
    @Test
    public void testContainsApi() {
        Conditions andCondition = new Conditions();
        andCondition.setOperator(Conditions.Operator.AND);
        Conditions orCondition = new Conditions();
        orCondition.setOperator(Conditions.Operator.OR);

        List<Predicate> andPredicates = new ArrayList<>();
        andPredicates.add(new ContainsPredicate("contains"));
        Set<ApiInfo.ApiInfoKey> set = new HashSet<>();
        set.add(new ApiInfo.ApiInfoKey(1234, "/admin/contains", URLMethods.Method.GET));
        andPredicates.add(new BelongsToPredicate(set));
        andCondition.setPredicates(andPredicates);
        List<Predicate> orPredicates = new ArrayList<>();
        set.clear();
        orPredicates.add(new ContainsPredicate("contains"));
        set.add(new ApiInfo.ApiInfoKey(1234, "/admin/contains", URLMethods.Method.GET));
        orPredicates.add(new NotBelongsToPredicate(set));
        orCondition.setPredicates(orPredicates);

        LogicalGroupTestingEndpoint logicalGroupTestingEndpoint = new LogicalGroupTestingEndpoint(andCondition, orCondition);

        assertTrue(logicalGroupTestingEndpoint.containsApi(new ApiInfo.ApiInfoKey(1234, "/admin/contains", URLMethods.Method.GET)));
        assertFalse(logicalGroupTestingEndpoint.containsApi(null));
        logicalGroupTestingEndpoint.setAndConditions(null);
        logicalGroupTestingEndpoint.setOrConditions(null);
        assertFalse(logicalGroupTestingEndpoint.containsApi(new ApiInfo.ApiInfoKey(1234, "/admin/contains", URLMethods.Method.GET)));
    }
}
