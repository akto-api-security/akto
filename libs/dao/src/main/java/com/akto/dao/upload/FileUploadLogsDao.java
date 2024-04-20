package com.akto.dao.upload;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.upload.FileUploadLog;
import com.akto.dto.upload.PostmanUploadLog;
import com.akto.dto.upload.SwaggerUploadLog;
import com.mongodb.client.MongoCollection;

public class FileUploadLogsDao extends AccountsContextDao<FileUploadLog> {
    @Override
    public String getCollName() {
        return "file_upload_logs";
    }

    @Override
    public Class<FileUploadLog> getClassT() {
        return FileUploadLog.class;
    }

    public static final FileUploadLogsDao instance = new FileUploadLogsDao();

    public MongoCollection<PostmanUploadLog> getPostmanMCollection() {
        return getMCollection(getDBName(), getCollName(), PostmanUploadLog.class);
    }
    public MongoCollection<SwaggerUploadLog> getSwaggerMCollection() {
        return getMCollection(getDBName(), getCollName(), SwaggerUploadLog.class);
    }


}
