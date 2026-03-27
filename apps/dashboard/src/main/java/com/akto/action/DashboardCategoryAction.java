package com.akto.action;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.enums.GlobalEnums.DashboardCategory;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DashboardCategoryAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DashboardCategoryAction.class, LogDb.DASHBOARD);

    private static final List<String> VALID_DASHBOARD_CATEGORIES = Arrays.stream(DashboardCategory.values())
            .map(DashboardCategory::getDashboardCategory)
            .collect(Collectors.toList());

    public static List<String> getValidDashboardCategories() {
        return VALID_DASHBOARD_CATEGORIES;
    }

    @Getter
    @Setter
    String dashboardCategory;

    public String switchDashboardCategory() {
        if (dashboardCategory == null || !VALID_DASHBOARD_CATEGORIES.contains(dashboardCategory)) {
            addActionError("Invalid dashboard category: " + dashboardCategory);
            return ERROR.toUpperCase();
        }
        try {
            getSession().put("dashboardCategory", dashboardCategory);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to set dashboardCategory in session: " + dashboardCategory);
            addActionError("Failed to switch dashboard category.");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }
}
