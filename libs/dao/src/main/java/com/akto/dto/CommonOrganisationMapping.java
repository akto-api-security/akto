package com.akto.dto;

import java.util.List;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

/**
 * A single sibling-domain group. All domains listed in {@link #domains} are
 * treated as belonging to the same organisation for invite validation and
 * signup org-matching.
 *
 * A domain belongs to exactly one group: the {@code domains} field carries a
 * unique multikey index, so a domain present in one document cannot be added to
 * another. This keeps signup org-resolution unambiguous (a domain resolves to a
 * single group).
 *
 * Example: { "domains": ["a.com", "b.com", "c.com"] }
 */
public class CommonOrganisationMapping {

    @BsonId
    private ObjectId id;
    public static final String ID = "_id";

    private List<String> domains;
    public static final String DOMAINS = "domains";

    public CommonOrganisationMapping() {
    }

    public CommonOrganisationMapping(List<String> domains) {
        this.domains = domains;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public List<String> getDomains() {
        return domains;
    }

    public void setDomains(List<String> domains) {
        this.domains = domains;
    }
}
