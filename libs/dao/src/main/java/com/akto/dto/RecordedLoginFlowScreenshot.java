package com.akto.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

public class RecordedLoginFlowScreenshot {

    @Getter
    @Setter
    private String roleName;

    @Getter
    @Setter
    private int userId;

    @Getter
    private List<String> screenshotsBase64;

    @Getter
    @Setter
    private int updatedAt;

    public RecordedLoginFlowScreenshot() {
        this.screenshotsBase64 = new ArrayList<>();
    }

    public void setScreenshotsBase64(List<String> screenshotsBase64) {
        this.screenshotsBase64 = screenshotsBase64 != null ? screenshotsBase64 : new ArrayList<>();
    }
}
