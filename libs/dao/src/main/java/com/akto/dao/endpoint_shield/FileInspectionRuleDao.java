package com.akto.dao.endpoint_shield;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.endpoint_shield.FileInspectionRule;

public class FileInspectionRuleDao extends AccountsContextDao<FileInspectionRule> {

    private static volatile FileInspectionRuleDao instance;
    public static final String COLLECTION_NAME = "file_inspection_rules";

    private FileInspectionRuleDao() {}

    public static FileInspectionRuleDao getInstance() {
        FileInspectionRuleDao local = instance;
        if (local == null) {
            synchronized (FileInspectionRuleDao.class) {
                local = instance;
                if (local == null) {
                    local = new FileInspectionRuleDao();
                    instance = local;
                }
            }
        }
        return local;
    }

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
