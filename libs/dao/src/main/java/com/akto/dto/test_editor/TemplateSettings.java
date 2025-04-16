package com.akto.dto.test_editor;

import com.akto.util.enums.GlobalEnums;

public class TemplateSettings {
    private GlobalEnums.TemplatePlan plan;
    private GlobalEnums.TemplateNature nature;
    private GlobalEnums.TemplateDuration duration;

    public TemplateSettings(GlobalEnums.TemplatePlan plan, GlobalEnums.TemplateNature nature, GlobalEnums.TemplateDuration duration) {
        this.plan = plan;
        this.nature = nature;
        this.duration = duration;
    }

    public TemplateSettings() {}

    public GlobalEnums.TemplatePlan getPlan() {
        return plan;
    }

    public void setPlan(GlobalEnums.TemplatePlan plan) {
        this.plan = plan;
    }

    public GlobalEnums.TemplateNature getNature() {
        return nature;
    }

    public void setNature(GlobalEnums.TemplateNature nature) {
        this.nature = nature;
    }

    public GlobalEnums.TemplateDuration getDuration() {
        return duration;
    }

    public void setDuration(GlobalEnums.TemplateDuration duration) {
        this.duration = duration;
    }
}
