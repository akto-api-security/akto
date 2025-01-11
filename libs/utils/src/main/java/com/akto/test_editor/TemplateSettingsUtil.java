package com.akto.test_editor;

import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.util.DashboardMode;
import com.akto.util.enums.GlobalEnums;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TemplateSettingsUtil {

    public static List<GlobalEnums.TemplatePlan> getTemplatePlans(DashboardMode dashboardMode, GlobalEnums.TemplateFeatureAccess templateFeatureAccess) {
        List<GlobalEnums.TemplatePlan> templatePlans = new ArrayList<>();

        if(templateFeatureAccess != null && templateFeatureAccess.equals(GlobalEnums.TemplateFeatureAccess.ENTERPRISE_TESTS)) {
            templatePlans.add(GlobalEnums.TemplatePlan.FREE);
            templatePlans.add(GlobalEnums.TemplatePlan.STANDARD);
            templatePlans.add(GlobalEnums.TemplatePlan.PRO);
            templatePlans.add(GlobalEnums.TemplatePlan.ENTERPRISE);
        } else if(templateFeatureAccess != null && templateFeatureAccess.equals(GlobalEnums.TemplateFeatureAccess.PRO_TESTS)) {
            templatePlans.add(GlobalEnums.TemplatePlan.FREE);
            templatePlans.add(GlobalEnums.TemplatePlan.STANDARD);
            templatePlans.add(GlobalEnums.TemplatePlan.PRO);
        } else if(dashboardMode.equals(DashboardMode.ON_PREM) || dashboardMode.equals(DashboardMode.SAAS)) {
            templatePlans.add(GlobalEnums.TemplatePlan.FREE);
            templatePlans.add(GlobalEnums.TemplatePlan.STANDARD);
        } else {
            templatePlans.add(GlobalEnums.TemplatePlan.FREE);
        }

        return templatePlans;
    }

    public static FeatureAccess getFeatureAccessForTemplate(Organization organization, GlobalEnums.TemplateFeatureAccess templateFeature) {
        return Optional.ofNullable(organization.getFeatureWiseAllowed().get(templateFeature.name()))
                .orElse(FeatureAccess.noAccess);
    }

}
