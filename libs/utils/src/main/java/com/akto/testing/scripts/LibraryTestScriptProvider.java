package com.akto.testing.scripts;

import com.akto.dao.testing.config.TestScriptsDao;
import com.akto.dto.testing.config.TestScript;

/** Reads the script library (test_scripts) for the current account. */
public class LibraryTestScriptProvider implements TestScriptProvider {

    @Override
    public TestScript findById(String id) {
        return TestScriptsDao.instance.findById(id);
    }

    @Override
    public TestScript findDefault(TestScript.Type type) {
        return TestScriptsDao.instance.findByName(TestScript.defaultNameFor(type));
    }
}
