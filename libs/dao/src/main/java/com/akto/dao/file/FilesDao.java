package com.akto.dao.file;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.files.File;

public class FilesDao extends AccountsContextDao<File> {

    private FilesDao() {
    }

    public static final FilesDao instance = new FilesDao();
    @Override
    public String getCollName() {
        return "files";
    }

    @Override
    public Class<File> getClassT() {
        return File.class;
    }
}
