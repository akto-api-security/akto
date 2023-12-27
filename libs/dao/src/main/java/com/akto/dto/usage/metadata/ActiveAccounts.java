package com.akto.dto.usage.metadata;

import java.util.Set;

public class ActiveAccounts {

    private Set<Integer> activeAccounts;

    public ActiveAccounts(Set<Integer> activeAccounts) {
        this.activeAccounts = activeAccounts;
    }

    public Set<Integer> getActiveAccounts() {
        return activeAccounts;
    }

    public void setActiveAccounts(Set<Integer> activeAccounts) {
        this.activeAccounts = activeAccounts;
    }

}
