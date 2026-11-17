package com.akto.testing.scripts;

import com.akto.dao.testing.config.TestScriptDraftsDao;
import com.akto.dto.testing.config.TestScript;

/** Reads Ask Akto validation drafts (test_script_drafts). Drafts are only ever chosen by id; there is no default draft. */
public class DraftTestScriptProvider implements TestScriptProvider {

    @Override
    public TestScript findById(String id) {
        return TestScriptDraftsDao.instance.findById(id);
    }

    @Override
    public TestScript findDefault(TestScript.Type type) {
        return null;
    }
}
