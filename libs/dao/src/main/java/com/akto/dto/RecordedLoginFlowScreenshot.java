package com.akto.dto;

import java.util.ArrayList;
import java.util.List;

public class RecordedLoginFlowScreenshot {

    private String roleName;
    private int userId;
    private List<String> screenshotsBase64;
    private int updatedAt;

    public RecordedLoginFlowScreenshot() {
        this.screenshotsBase64 = new ArrayList<>();
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public List<String> getScreenshotsBase64() {
        return screenshotsBase64;
    }

    public void setScreenshotsBase64(List<String> screenshotsBase64) {
        this.screenshotsBase64 = screenshotsBase64 != null ? screenshotsBase64 : new ArrayList<>();
    }

    public int getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(int updatedAt) {
        this.updatedAt = updatedAt;
    }
}
