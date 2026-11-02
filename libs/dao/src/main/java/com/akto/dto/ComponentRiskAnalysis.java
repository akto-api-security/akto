package com.akto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
public class ComponentRiskAnalysis {

    private boolean hasPrivilegedAccess;
    private boolean isComponentMalicious;

    @Getter
    @Setter
    private String evidence;

    // Each entry: { "id": "AST01", "name": "Malicious Skills", "severity": "CRITICAL", "confidence": "HIGH"|"MEDIUM" }
    @Getter
    @Setter
    private List<Map<String, String>> owaspCategories;

    public ComponentRiskAnalysis(boolean hasPrivilegedAccess, boolean isComponentMalicious, String evidence) {
        this.hasPrivilegedAccess = hasPrivilegedAccess;
        this.isComponentMalicious = isComponentMalicious;
        this.evidence = evidence;
    }

    public ComponentRiskAnalysis(boolean hasPrivilegedAccess, boolean isComponentMalicious, String evidence,
            List<Map<String, String>> owaspCategories) {
        this.hasPrivilegedAccess = hasPrivilegedAccess;
        this.isComponentMalicious = isComponentMalicious;
        this.evidence = evidence;
        this.owaspCategories = owaspCategories;
    }

    public boolean getHasPrivilegedAccess() {
        return hasPrivilegedAccess;
    }

    public void setHasPrivilegedAccess(boolean hasPrivilegedAccess) {
        this.hasPrivilegedAccess = hasPrivilegedAccess;
    }

    public boolean getIsComponentMalicious() {
        return isComponentMalicious;
    }

    public void setIsComponentMalicious(boolean componentMalicious) {
        isComponentMalicious = componentMalicious;
    }
}
