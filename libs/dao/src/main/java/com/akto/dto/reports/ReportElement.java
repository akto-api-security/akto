package com.akto.dto.reports;

import com.akto.dto.messaging.InboxParams;

public abstract class ReportElement {

    InboxParams.Source source;

    public InboxParams.Source getSource() {
        return source;
    }

    public void setSource(InboxParams.Source source) {
        this.source = source;
    }
}
