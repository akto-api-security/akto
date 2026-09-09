package com.akto.dao;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.akto.dto.CommonOrganisationMapping;
import com.mongodb.client.model.Filters;

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

    /**
     * Two domains are "siblings" if they co-occur in at least one mapping document.
     * On an array field, two equality filters mean "the array contains both values".
     */
    public boolean areSiblings(String domain1, String domain2) {
        if (domain1 == null || domain2 == null) return false;
        String d1 = domain1.trim().toLowerCase();
        String d2 = domain2.trim().toLowerCase();
        if (d1.isEmpty() || d2.isEmpty()) return false;
        return findOne(Filters.and(
            Filters.eq(CommonOrganisationMapping.DOMAINS, d1),
            Filters.eq(CommonOrganisationMapping.DOMAINS, d2)
        )) != null;
    }

    /**
     * Returns the sibling domains of the given domain (its group's other members),
     * or an empty set if the domain belongs to no group. Since a domain lives in at
     * most one group, this reads a single document.
     */
    public Set<String> getSiblings(String domain) {
        if (domain == null) return Collections.emptySet();
        String d = domain.trim().toLowerCase();
        if (d.isEmpty()) return Collections.emptySet();
        CommonOrganisationMapping mapping = findOne(Filters.eq(CommonOrganisationMapping.DOMAINS, d));
        if (mapping == null || mapping.getDomains() == null) return Collections.emptySet();
        Set<String> siblings = new HashSet<>();
        for (String other : mapping.getDomains()) {
            if (other == null) continue;
            String n = other.trim().toLowerCase();
            if (!n.isEmpty() && !n.equals(d)) siblings.add(n);
        }
        return siblings;
    }
}
