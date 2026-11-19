package com.akto.dao;

import com.akto.dto.AtlasS3ExportIntegration;

public class AtlasS3ExportIntegrationDao extends AccountsContextDao<AtlasS3ExportIntegration> {

    public static final AtlasS3ExportIntegrationDao instance = new AtlasS3ExportIntegrationDao();

    private AtlasS3ExportIntegrationDao() {}

    @Override
    public String getCollName() {
        return "atlas_s3_export_integration";
    }

    @Override
    public Class<AtlasS3ExportIntegration> getClassT() {
        return AtlasS3ExportIntegration.class;
    }
}
