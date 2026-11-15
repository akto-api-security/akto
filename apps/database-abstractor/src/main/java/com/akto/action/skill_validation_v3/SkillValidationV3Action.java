package com.akto.action.skill_validation_v3;

import com.akto.action.skill_validation_v3.SkillValidationEngine.Result;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Skill validation V3: validates a skill from the content already stored for it, for the dashboard's
 * skill revalidation job. Only the skill file is stored, so the scripts it runs are not part of this
 * validation. The validation itself is SkillValidationEngine, shared with V2.
 *
 * Nothing is ingested. The result is returned and the job writes the annotations, verdict and tags onto
 * the stored endpoint itself, replacing the old ones. The audit info is updated, and the guardrail
 * activity is raised for a malicious skill.
 */
@Getter
@Setter
public class SkillValidationV3Action extends ActionSupport {

    // Only the skill file is stored; the prompt's script section says so.
    static final String NO_FILES = "(none — only the skill file is stored, so no scripts are available)";

    // Input fields, as stored in the skill's sample
    private String skillName;
    private String skillDescription;
    private String skillContent;
    private String agentName;
    private String filePath;
    // The skill's collection host, e.g. "<device>.ai-agent.claude"
    private String collectionName;

    // Output field: tagLabels, annotations, verdict (what is stored as skill_verdict), riskScore
    private Map<String, Object> validationResult;

    public String validateSkillV3() {
        if (skillName == null || skillName.isEmpty()) {
            addActionError("skillName is required");
            return Action.ERROR.toUpperCase();
        }
        if (skillContent == null || skillContent.isEmpty()) {
            addActionError("skillContent is required");
            return Action.ERROR.toUpperCase();
        }
        if (skillDescription == null) skillDescription = "";
        if (agentName == null) agentName = "";
        // resolved once, so the model's provenance and the vendor check see where the file really is
        filePath = SkillValidationEngine.normalizePath(filePath);
        if (collectionName == null) collectionName = "";

        Result result;
        try {
            result = SkillValidationEngine.validate(new SkillValidationEngine.Request(skillName, skillDescription,
                    skillContent, filePath, null, NO_FILES, null, "[SkillValidationV3] collection=" + collectionName));
        } catch (SkillValidationEngine.ValidationException e) {
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        result.recordAudit(agentName);

        if (result.flagged && !collectionName.isEmpty()) {
            SkillThreatReporterV3.reportThreat(
                    new SkillThreatReporterV3.SkillRef(skillName, skillDescription, skillContent, agentName,
                            collectionName, SkillThreatReporterV3.requestAuthToken()),
                    result.threat());
        }

        validationResult = new HashMap<>();
        validationResult.put("tagLabels", result.tagLabels());
        validationResult.put("annotations", result.annotations());
        validationResult.put("verdict", result.verdict());
        validationResult.put("riskScore", result.riskScore);
        return Action.SUCCESS.toUpperCase();
    }
}
