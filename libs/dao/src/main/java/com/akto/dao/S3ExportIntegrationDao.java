package com.akto.dao;

import com.akto.dto.s3_export.S3ExportIntegration;

public class S3ExportIntegrationDao extends AccountsContextDao<S3ExportIntegration> {

    public static final S3ExportIntegrationDao instance = new S3ExportIntegrationDao();

    private S3ExportIntegrationDao() {}

    @Override
    public String getCollName() {
        return "s3_export_integration";
    }

    @Override
    public Class<S3ExportIntegration> getClassT() {
        return S3ExportIntegration.class;
    }
}
