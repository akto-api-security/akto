package com.akto.testing.scripts;

import com.akto.dao.testing.config.TestCollectionPropertiesDao;
import com.akto.dto.testing.config.TestScript;

/**
 * Adapts the pre-library store (at most one script per type, no name, kept in test_collection_properties)
 * to the library contract: the legacy script of a type is presented as default-test-{type}.
 * Legacy scripts were never addressable by id from a testing run, so findById always returns null.
 * Used by the runtime fallback and by the one-time migration; delete once every account is migrated.
 */
public class LegacyTestScriptAdapter implements TestScriptProvider {

    @Override
    public TestScript findById(String id) {
        return null;
    }

    @Override
    public TestScript findDefault(TestScript.Type type) {
        TestScript legacy = TestCollectionPropertiesDao.instance.findLegacyTestScript(type);
        if (legacy == null || legacy.getJavascript() == null) {
            return null;
        }
        legacy.setName(TestScript.defaultNameFor(type));
        legacy.setType(type);
        legacy.setCreatedBy(legacy.getAuthor());
        legacy.setLastUpdatedBy(legacy.getAuthor());
        return legacy;
    }
}
