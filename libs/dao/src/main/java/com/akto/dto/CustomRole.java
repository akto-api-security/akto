package com.akto.dto;

import java.util.List;
import java.util.Map;

import com.akto.dto.rbac.CollectionRule;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;

import lombok.Getter;
import lombok.Setter;

public class CustomRole {

    public final static String _NAME = "name";
    public final static String BASE_ROLE = "baseRole";
    public static final String API_COLLECTIONS_ID = "apiCollectionsId";
    public static final String DEFAULT_INVITE_ROLE = "defaultInviteRole";
    public static final String THREAT_PROTECTION_ENABLED = "threatProtectionEnabled";
    public static final String PERMISSION_OVERRIDES = "permissionOverrides";
    public static final String COLLECTION_RULES = "collectionRules";
    private String name;
    private String baseRole;
    private List<Integer> apiCollectionsId;
    boolean defaultInviteRole;

    /*
     * Grants threat protection to a base role that does not already have it. Only
     * consulted for base roles outside FIXED_THREAT_ACCESS_ROLES, so an unset value
     * matches what those roles grant anyway.
     * Boxed because documents written before this field existed - and any written while
     * it was nullable - carry an explicit null, and a primitive setter cannot take one.
     * A single such document would otherwise fail decoding and break the whole roles API.
     * Treat null as false; use Boolean.TRUE.equals when reading.
     */
    private Boolean threatProtectionEnabled;

    /*
     * Retained so existing documents keep their values, but nothing reads it: access is
     * resolved from the base role's map plus threatProtectionEnabled. There is no UI to
     * set it either. Do not treat a value here as granting anything.
     */
    @Getter
    @Setter
    private List<String> allowedFeaturesForUser;

    /*
     * Per-feature access set by an admin for this role (Feature name -> READ / READ_WRITE / NO_ACCESS).
     * Replaces the base role's access for that feature. Features not listed keep the base role's access.
     */
    @Getter
    @Setter
    private Map<String, String> permissionOverrides;

    // Collections included by host pattern or tag, on top of apiCollectionsId
    @Getter
    @Setter
    private List<CollectionRule> collectionRules;

    /** Features whose access is fixed by the role itself and can never be overridden. */
    public static boolean isOverridable(Feature feature) {
        return feature != Feature.ADMIN_ACTIONS && feature != Feature.USER_ACTIONS;
    }

    /** The override for a feature, or null if there is none (or it is not a valid access value). */
    public ReadWriteAccess overrideFor(Feature feature) {
        if (permissionOverrides == null || feature == null || !isOverridable(feature)) {
            return null;
        }
        String access = permissionOverrides.get(feature.name());
        try {
            return access == null ? null : ReadWriteAccess.valueOf(access);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public CustomRole() {
    }

    public CustomRole(String name, String baseRole, List<Integer> apiCollectionsId, boolean defaultInviteRole, boolean threatProtectionEnabled, List<String> allowedFeaturesForUser) {
        switch (baseRole) {
            case "ADMIN":
            case "DEVELOPER":
            case "MEMBER":
            case "GUEST":
            // Role enum names, which is what callers pass and every reader Role.valueOf()s back.
            case "THREAT_ENGINEER":
            case "THREAT_VIEWER":
                break;
            default:
                baseRole = "GUEST";
                break;
        }
        this.baseRole = baseRole;
        this.name = name;
        this.apiCollectionsId = apiCollectionsId;
        this.defaultInviteRole = defaultInviteRole;
        this.threatProtectionEnabled = threatProtectionEnabled;
        this.allowedFeaturesForUser = allowedFeaturesForUser;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseRole() {
        return baseRole;
    }

    public void setBaseRole(String baseRole) {
        this.baseRole = baseRole;
    }

    public List<Integer> getApiCollectionsId() {
        return apiCollectionsId;
    }

    public void setApiCollectionsId(List<Integer> apiCollectionsId) {
        this.apiCollectionsId = apiCollectionsId;
    }

    public boolean getDefaultInviteRole() {
        return defaultInviteRole;
    }

    public void setDefaultInviteRole(boolean defaultInviteRole) {
        this.defaultInviteRole = defaultInviteRole;
    }

    public Boolean getThreatProtectionEnabled() {
        return threatProtectionEnabled;
    }

    public void setThreatProtectionEnabled(Boolean threatProtectionEnabled) {
        this.threatProtectionEnabled = threatProtectionEnabled;
    }

}