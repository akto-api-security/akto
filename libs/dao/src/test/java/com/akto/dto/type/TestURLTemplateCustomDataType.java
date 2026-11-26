package com.akto.dto.type;

import com.akto.dao.context.Context;
import com.akto.dto.CustomDataType;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.RegexPredicate;
import com.akto.util.filter.DictionaryFilter;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Location codes like LAND / JEEP / MIN are dictionary words, so URLTemplate.match rejects them for a STRING segment
 * and the runtime keeps creating static URLs. For TEMPLATE_MERGING_ACCOUNT_ID, a segment matching an active custom
 * data type is a value instead.
 */
public class TestURLTemplateCustomDataType {

    private static final int OTHER_ACCOUNT_ID = 12389;
    private static final URLTemplate MODELS = new URLTemplate(
            new String[]{"api", "v2", "models", null},
            new SingleTypeInfo.SuperType[]{null, null, null, SingleTypeInfo.SuperType.STRING},
            URLMethods.Method.GET);

    @BeforeClass
    public static void loadDictionary() {
        DictionaryFilter.readDictionaryBinary();
    }

    @After
    public void reset() {
        setCustomDataTypes(URLTemplate.TEMPLATE_MERGING_ACCOUNT_ID);
        setCustomDataTypes(OTHER_ACCOUNT_ID);
        Context.accountId.set(OTHER_ACCOUNT_ID);
    }

    private static void setCustomDataTypes(int accountId, CustomDataType... customDataTypes) {
        SingleTypeInfo.fetchCustomDataTypes(accountId, Arrays.asList(customDataTypes), Collections.emptyList());
    }

    private static CustomDataType locationCode(boolean active) {
        List<Predicate> predicates = Collections.singletonList(new RegexPredicate("^[A-Z0-9]{3,4}$"));
        return new CustomDataType("LOCATION_CODE", false, Collections.emptyList(), 0, active, null,
                new Conditions(predicates, Conditions.Operator.OR), Conditions.Operator.OR, null, false, true);
    }

    private static boolean matches(String url) {
        return MODELS.match(url, URLMethods.Method.GET);
    }

    @Test
    public void englishWordCodesStayStaticWithoutACustomDataType() {
        Context.accountId.set(URLTemplate.TEMPLATE_MERGING_ACCOUNT_ID);

        assertTrue(URLTemplate.isStaticWord("LAND", 3));
        assertFalse(matches("/api/v2/models/LAND"));
        assertFalse(matches("/api/v2/models/JEEP"));
        assertTrue(matches("/api/v2/models/BI06"));
    }

    @Test
    public void englishWordCodesMatchingACustomDataTypeMatchTheTemplate() {
        Context.accountId.set(URLTemplate.TEMPLATE_MERGING_ACCOUNT_ID);
        setCustomDataTypes(URLTemplate.TEMPLATE_MERGING_ACCOUNT_ID, locationCode(true));

        assertFalse(URLTemplate.isStaticWord("LAND", 3));
        assertTrue(matches("/api/v2/models/LAND"));
        assertTrue(matches("/api/v2/models/JEEP"));
        assertTrue(matches("/api/v2/models/MIN"));
        // dictionary words outside the regex stay static
        assertFalse(matches("/api/v2/models/models"));
        assertFalse(matches("/api/v2/models/land"));
    }

    @Test
    public void inactiveCustomDataTypeKeepsEnglishWordsStatic() {
        Context.accountId.set(URLTemplate.TEMPLATE_MERGING_ACCOUNT_ID);
        setCustomDataTypes(URLTemplate.TEMPLATE_MERGING_ACCOUNT_ID, locationCode(false));

        assertFalse(matches("/api/v2/models/LAND"));
    }

    @Test
    public void customDataTypesOfOtherAccountsKeepEnglishWordsStatic() {
        Context.accountId.set(OTHER_ACCOUNT_ID);
        setCustomDataTypes(OTHER_ACCOUNT_ID, locationCode(true));

        assertTrue(URLTemplate.isStaticWord("LAND", 3));
        assertFalse(matches("/api/v2/models/LAND"));
    }
}
