package com.akto.util;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Represents organization information for domain-based caching
 * Contains organization ID, admin email, and plan type
 */
@Data
@AllArgsConstructor
public class OrganizationInfo {
    private final String organizationId;
    private final String adminEmail;
    private final String planType;
}