package com.akto.dao;

import com.akto.dto.CommonOrganisationMapping;

public class CommonOrganisationMappingDao extends CommonContextDao<CommonOrganisationMapping> {

    public static final CommonOrganisationMappingDao instance = new CommonOrganisationMappingDao();

    public void createIndexIfAbsent() {
        // Unique multikey index on the array: enforces that a given domain can appear
        // in at most one document (i.e. one domain belongs to exactly one org group).
        // Inserting/updating a doc with a domain already used elsewhere fails with a
        // duplicate-key error.
        String[] fieldNames = { CommonOrganisationMapping.DOMAINS };
        MCollection.createUniqueIndex(getDBName(), getCollName(), fieldNames, true);
    }

    @Override
    public String getCollName() {
        return "common_organisation_mappings";
    }

    @Override
    public Class<CommonOrganisationMapping> getClassT() {
        return CommonOrganisationMapping.class;
    }
}
