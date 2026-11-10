package com.akto.testing.scripts;

import com.akto.dto.testing.config.TestScript;

/**
 * Read contract for test scripts, shared by every runtime (dashboard/testing read Mongo directly,
 * mini-testing reads through the database-abstractor). Implementations return null for "not found"
 * and throw for "could not read"; callers rely on that distinction (only not-found may fall back or be
 * cached).
 */
public interface TestScriptProvider {

    /** The library script with this id, or null if there is none. */
    TestScript findById(String id);

    /** The script runs use when they select none explicitly (default-test-{type}), or null if there is none. */
    TestScript findDefault(TestScript.Type type);
}
