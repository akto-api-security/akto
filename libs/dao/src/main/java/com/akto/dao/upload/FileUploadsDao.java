package com.akto.dao.upload;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.upload.FileUpload;
import com.akto.dto.upload.PostmanWorkspaceUpload;
import com.mongodb.client.MongoCollection;

public class FileUploadsDao extends AccountsContextDao<FileUpload> {
    @Override
    public String getCollName() {
        return "file_uploads";
    }

    @Override
    public Class<FileUpload> getClassT() {
        return FileUpload.class;
    }

    public static final FileUploadsDao instance = new FileUploadsDao();

    public MongoCollection<PostmanWorkspaceUpload> getPostmanMCollection() {
        return getMCollection(getDBName(), getCollName(), PostmanWorkspaceUpload.class);
    }
}
