package com.akto.dto.data_types;

import com.akto.dto.CustomDataType;
import com.akto.dto.IgnoreData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestCustomDataType {


    @Test
    public void testEmptyPredicatesValidate() {
        Conditions conditions1 = new Conditions(new ArrayList<>(), Conditions.Operator.AND);
        boolean r = conditions1.validate("something");
        assertFalse(r);
    }

    @Test
    public void testValidate() {
        List<Predicate> predicateList1 = Arrays.asList(new StartsWithPredicate("w13rd"), new EndsWithPredicate("a55"));
        Conditions conditions1 = new Conditions(predicateList1, Conditions.Operator.AND);

        List<Predicate> predicateList2 = Collections.singletonList(new StartsWithPredicate("Dope"));
        Conditions conditions2 = new Conditions(predicateList2, Conditions.Operator.AND);
        IgnoreData ignoreData = new IgnoreData(new HashMap<>(), new HashSet<>());

        CustomDataType customDataType = new CustomDataType("name", true, Collections.emptyList(),
                0,true, conditions2, conditions1, Conditions.Operator.AND,ignoreData, false, true);

        assertTrue(customDataType.validate("w13rd_something_a55", "Dope_shit"));
        assertFalse(customDataType.validate("w13rd_something_a55", "BDope_shit"));
        assertFalse(customDataType.validate("Bw13rd_something_a55", "Dope_shit"));
        assertFalse(customDataType.validate("Bw13rd_something_a55", "BDope_shit"));

        customDataType.setOperator(Conditions.Operator.OR);
        assertTrue(customDataType.validate("w13rd_something_a55", "Dope_shit"));
        assertTrue(customDataType.validate("w13rd_something_a55", "BDope_shit"));
        assertTrue(customDataType.validate("Bw13rd_something_a55", "Dope_shit"));
        assertFalse(customDataType.validate("Bw13rd_something_a55", "BDope_shit"));

        customDataType.setOperator(Conditions.Operator.AND);
        customDataType.setValueConditions(null);
        assertTrue(customDataType.validate("w13rd_something_a55", "Dope_shit"));
        assertFalse(customDataType.validate("w13rd_something_a55", "BDope_shit"));
        assertTrue(customDataType.validate("Bw13rd_something_a55", "Dope_shit"));
        assertFalse(customDataType.validate("Bw13rd_something_a55", "BDope_shit"));

        customDataType.setValueConditions(conditions1);
        customDataType.setKeyConditions(null);
        assertTrue(customDataType.validate("w13rd_something_a55", "Dope_shit"));
        assertTrue(customDataType.validate("w13rd_something_a55", "BDope_shit"));
        assertFalse(customDataType.validate("Bw13rd_something_a55", "Dope_shit"));
        assertFalse(customDataType.validate("Bw13rd_something_a55", "BDope_shit"));
    }

    @Test
    public void testValidOfConditions() {
        List<Predicate> predicateList = Arrays.asList(new StartsWithPredicate("w13rd"), new EndsWithPredicate("a55"));

        Conditions conditions = new Conditions(predicateList, Conditions.Operator.OR);
        assertTrue(conditions.validate("w13rd_something_a55"));

        conditions = new Conditions(predicateList, Conditions.Operator.OR);
        assertTrue(conditions.validate("w13brd_something_a55"));

        conditions = new Conditions(predicateList, Conditions.Operator.AND);
        assertFalse(conditions.validate("w13brd_something_a55"));

        conditions = new Conditions(predicateList, Conditions.Operator.OR);
        assertFalse(conditions.validate("w13brd_something_a5w5"));
    }

}
