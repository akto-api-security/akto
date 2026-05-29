package com.akto.dao.endpoint_shield;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.endpoint_shield.FileInspectionRule;

public class FileInspectionRuleDao extends AccountsContextDao<FileInspectionRule> {

    public static final FileInspectionRuleDao instance = new FileInspectionRuleDao();
    public static final String COLLECTION_NAME = "file_inspection_rules";

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<FileInspectionRule> getClassT() {
        return FileInspectionRule.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{FileInspectionRule.UPDATED_TS}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{FileInspectionRule.PATH}, true);
    }
}
