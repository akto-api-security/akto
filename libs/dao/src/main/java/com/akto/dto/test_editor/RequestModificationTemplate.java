package com.akto.dto.test_editor;

public class RequestModificationTemplate {

    private String extract;

    private String modify;

    public RequestModificationTemplate(String extract, String modify) {
        this.extract = extract;
        this.modify = modify;
    }

    public RequestModificationTemplate() { }

    public String getExtract() {
        return extract;
    }

    public void setExtract(String extract) {
        this.extract = extract;
    }

    public String getModify() {
        return modify;
    }

    public void setModify(String modify) {
        this.modify = modify;
    }
    
}
