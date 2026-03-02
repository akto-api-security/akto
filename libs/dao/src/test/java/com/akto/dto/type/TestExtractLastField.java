package com.akto.dto.type;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestExtractLastField {

    @Test
    public void testSimpleField() {
        assertEquals("email", KeyTypes.extractLastField("email"));
    }

    @Test
    public void testDotSeparatedPath() {
        assertEquals("email", KeyTypes.extractLastField("users.profile.email"));
        assertEquals("name", KeyTypes.extractLastField("data.user.name"));
        assertEquals("field", KeyTypes.extractLastField("a.b.c.field"));
    }

    @Test
    public void testHashSeparatedPath() {
        assertEquals("name", KeyTypes.extractLastField("data#user#name"));
        assertEquals("field", KeyTypes.extractLastField("root#child#field"));
    }

    @Test
    public void testMixedSeparators() {
        assertEquals("field", KeyTypes.extractLastField("data.user#profile.field"));
        assertEquals("value", KeyTypes.extractLastField("a#b.c#value"));
    }

    @Test
    public void testTrailingDollarSign() {
        assertEquals("field", KeyTypes.extractLastField("path.field.$"));
        assertEquals("email", KeyTypes.extractLastField("users.profile.email.$"));
        assertEquals("b", KeyTypes.extractLastField("a.b.$"));
    }

    @Test
    public void testQueryParamSuffix() {
        assertEquals("filter", KeyTypes.extractLastField("filter_queryParam"));
        assertEquals("field", KeyTypes.extractLastField("user.field_queryParam"));
        assertEquals("value", KeyTypes.extractLastField("data#value_queryParam"));
    }

    @Test
    public void testQueryParamWithDollarSign() {
        assertEquals("field", KeyTypes.extractLastField("user.field_queryParam.$"));
        assertEquals("filter", KeyTypes.extractLastField("filter_queryParam.$"));
    }

    @Test
    public void testNoSeparator() {
        assertEquals("field", KeyTypes.extractLastField("field"));
        assertEquals("value", KeyTypes.extractLastField("value"));
    }

    @Test
    public void testNullAndEmpty() {
        assertNull(KeyTypes.extractLastField(null));
        assertEquals("", KeyTypes.extractLastField(""));
    }

    @Test
    public void testOnlyQueryParamSuffix() {
        assertEquals("", KeyTypes.extractLastField("_queryParam"));
        assertEquals("", KeyTypes.extractLastField("._queryParam"));
        assertEquals("", KeyTypes.extractLastField("#_queryParam"));
    }

    @Test
    public void testPartialQueryParamMatch() {
        // Should not strip partial matches
        assertEquals("field_query", KeyTypes.extractLastField("field_query"));
        assertEquals("value_queryPara", KeyTypes.extractLastField("value_queryPara"));
        assertEquals("_query", KeyTypes.extractLastField("_query"));
    }

    @Test
    public void testMultipleDots() {
        assertEquals("field", KeyTypes.extractLastField("a.b.c.d.e.field"));
        assertEquals("z", KeyTypes.extractLastField("a.b.c.d.e.f.g.h.i.j.z"));
    }

    @Test
    public void testMultipleHashes() {
        assertEquals("field", KeyTypes.extractLastField("a#b#c#d#e#field"));
    }

    @Test
    public void testOnlyDollarSign() {
        assertEquals("$", KeyTypes.extractLastField("$"));
        // ".$" gets stripped to empty string (correct behavior)
        assertEquals("", KeyTypes.extractLastField(".$"));
    }

    @Test
    public void testSingleCharacterFields() {
        assertEquals("a", KeyTypes.extractLastField("a"));
        assertEquals("z", KeyTypes.extractLastField("x.y.z"));
        assertEquals("b", KeyTypes.extractLastField("a#b"));
    }

    @Test
    public void testComplexRealWorldCases() {
        // Real-world GraphQL field
        assertEquals("email", KeyTypes.extractLastField("query.user.profile.email"));

        // Request with query param
        assertEquals("id", KeyTypes.extractLastField("users.id_queryParam"));

        // Mixed separators with trailing dollar
        assertEquals("value", KeyTypes.extractLastField("root#child.parent#value.$"));

        // Nested object path
        assertEquals("zipCode", KeyTypes.extractLastField("customer.address.billing.zipCode"));

        // With query param and dollar
        assertEquals("token", KeyTypes.extractLastField("auth.token_queryParam.$"));
    }

    @Test
    public void testEdgeCasesWithSpecialChars() {
        // Field names that contain underscores (but not _queryParam)
        assertEquals("user_id", KeyTypes.extractLastField("data.user_id"));
        assertEquals("field_name", KeyTypes.extractLastField("obj.field_name"));

        // Multiple underscores
        assertEquals("some_field_name", KeyTypes.extractLastField("root.some_field_name"));
    }

    @Test
    public void testFieldEndingWithQueryParamButNotSuffix() {
        // Field that ends with 'queryParam' but doesn't have underscore prefix
        assertEquals("myqueryParam", KeyTypes.extractLastField("data.myqueryParam"));
    }

    @Test
    public void testVeryLongPath() {
        String longPath = "level1.level2.level3.level4.level5.level6.level7.level8.level9.level10.finalField";
        assertEquals("finalField", KeyTypes.extractLastField(longPath));
    }

    @Test
    public void testPathWithOnlyDollarAtEnd() {
        assertEquals("field", KeyTypes.extractLastField("field.$"));
        // ".$" gets stripped to empty string after removing .$ suffix
        assertEquals("", KeyTypes.extractLastField(".$"));
    }

    @Test
    public void testConsecutiveSeparators() {
        assertEquals("field", KeyTypes.extractLastField("a..field"));
        assertEquals("field", KeyTypes.extractLastField("a##field"));
        assertEquals("", KeyTypes.extractLastField("a."));
        assertEquals("", KeyTypes.extractLastField("a#"));
    }

    @Test
    public void testOriginalBehaviorCompatibility() {
        // These test cases verify compatibility with the original regex-based implementation

        // Original: param.replaceAll("#", ".").replaceAll("\\.$", "").split("\\.")[last].split("_queryParam")[0]

        // Test case 1: Simple path
        String result1 = KeyTypes.extractLastField("users.profile.email");
        assertEquals("email", result1);

        // Test case 2: With # separator
        String result2 = KeyTypes.extractLastField("data#user#name");
        assertEquals("name", result2);

        // Test case 3: With .$ suffix
        String result3 = KeyTypes.extractLastField("path.field.$");
        assertEquals("field", result3);

        // Test case 4: With _queryParam suffix
        String result4 = KeyTypes.extractLastField("filter_queryParam");
        assertEquals("filter", result4);

        // Test case 5: Complex case
        String result5 = KeyTypes.extractLastField("user.field_queryParam.$");
        assertEquals("field", result5);
    }
}
