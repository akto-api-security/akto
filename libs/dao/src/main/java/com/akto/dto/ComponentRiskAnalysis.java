package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
public class ComponentRiskAnalysis {

    private boolean hasPrivilegedAccess;
    private boolean isComponentMalicious;

    @Getter
    @Setter
    private String evidence;

    public boolean getHasElevatedAccess() {
        return hasPrivilegedAccess;
    }

    public void setHasElevatedAccess(boolean hasPrivilegedAccess) {
        this.hasPrivilegedAccess = hasPrivilegedAccess;
    }

    public boolean getIsComponentMalicious() {
        return isComponentMalicious;
    }

    public void setIsComponentMalicious(boolean componentMalicious) {
        isComponentMalicious = componentMalicious;
    }
}