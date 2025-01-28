package com.akto.dto.test_editor;

public class SeverityParserResult {
    
    public static final String _CHECK = "check";
    private ConfigParserResult check;
    public static final String _RETURN = "return";
    private String severity;

    public SeverityParserResult(ConfigParserResult check, String severity) {
        this.check = check;
        this.severity = severity;
    }

    public SeverityParserResult() { }

    public ConfigParserResult getCheck() {
        return check;
    }

    public void setCheck(ConfigParserResult check) {
        this.check = check;
    }


    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

}
