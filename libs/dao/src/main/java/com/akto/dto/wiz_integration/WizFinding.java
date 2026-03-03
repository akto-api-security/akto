package com.akto.dto.wiz_integration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WizFinding {

    /*
        whenever we create a finding for an issue in the ui, mark the wiz finding status for the issue as CREATION_REQUESTED
    */

    public enum Status {
        CREATION_REQUESTED,
        CREATION_INITIATED,
        CREATION_SUCCESSFUL,
        CREATION_FAILED
    }

    public static final String STATUS = "status";
    private Status status;

    public static final String URL = "url";
    private String url;
}