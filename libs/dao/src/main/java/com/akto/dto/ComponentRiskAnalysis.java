package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
public class ComponentRiskAnalysis {

    private boolean hasElevatedAccess;
    private boolean isComponentMalicious;

    @Getter
    @Setter
    private String evidence;

    public boolean getHasElevatedAccess() {
        return hasElevatedAccess;
    }

    public void setHasElevatedAccess(boolean hasElevatedAccess) {
        this.hasElevatedAccess = hasElevatedAccess;
    }

    public boolean getIsComponentMalicious() {
        return isComponentMalicious;
    }

    public void setIsComponentMalicious(boolean componentMalicious) {
        isComponentMalicious = componentMalicious;
    }
}