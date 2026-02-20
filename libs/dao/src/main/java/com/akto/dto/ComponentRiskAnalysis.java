package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
public class ComponentRiskAnalysis {

    private boolean isComponentNameSuspicious;
    private boolean isComponentMalicious;

    @Getter
    @Setter
    private String evidence;

    public boolean getIsComponentNameSuspicious() {
        return isComponentNameSuspicious;
    }

    public void setIsComponentNameSuspicious(boolean componentNameSuspicious) {
        isComponentNameSuspicious = componentNameSuspicious;
    }

    public boolean getIsComponentMalicious() {
        return isComponentMalicious;
    }

    public void setIsComponentMalicious(boolean componentMalicious) {
        isComponentMalicious = componentMalicious;
    }
}