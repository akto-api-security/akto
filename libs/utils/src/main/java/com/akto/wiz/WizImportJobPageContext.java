package com.akto.wiz;

public class WizImportJobPageContext {

    private final boolean isFirstPage;
    private final boolean isLastPage;

    public WizImportJobPageContext(boolean isFirstPage, boolean isLastPage) {
        this.isFirstPage = isFirstPage;
        this.isLastPage = isLastPage;
    }

    public boolean isFirstPage() {
        return isFirstPage;
    }

    public boolean isLastPage() {
        return isLastPage;
    }
}
