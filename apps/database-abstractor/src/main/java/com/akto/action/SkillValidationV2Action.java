package com.akto.action;

import com.akto.action.skill_validation_v3.SkillAgentLogin;
import com.akto.action.skill_validation_v3.SkillPublisher;
import com.akto.action.skill_validation_v3.SkillReferencedFile;
import com.akto.action.skill_validation_v3.SkillThreatReporterV3;
import com.akto.action.skill_validation_v3.SkillValidationEngine;
import com.akto.action.skill_validation_v3.SkillValidationEngine.Result;
import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Skill validation for the endpoint-shield agent (api/validateAndReportSkillV2). The validation is the
 * same as V3's (SkillValidationEngine); the difference is that the agent also sends the scripts the
 * skill runs (referencedFiles), which are validated with it, and that cyborg publishes everything:
 *   1. the agent collection, with the skill and the agent's login tags;
 *   2. the skill endpoint, ingested with its tags, skill_annotations and skill_verdict;
 *   3. the guardrail activity, when the skill is malicious and the agent did not turn it off.
 * A request without referencedFiles is validated exactly as V3 validates a stored skill.
 */
@Getter
@Setter
public class SkillValidationV2Action extends ActionSupport {

    private static final LoggerMaker logger = new LoggerMaker(SkillValidationV2Action.class, LogDb.DB_ABS);

    static final String NO_FILES = "(none — the skill runs no other files, or none were collected)";

    // Input fields, the agent's ValidateSkillRequest
    private String skillName;
    private String skillDescription;
    private String skillContent;
    // the scripts the skill runs, secrets redacted; validated with it, never ingested
    private List<SkillReferencedFile> referencedFiles;
    private String agentName;
    // the recognised agent for the collection's mcp-client / ai-agent tags; empty for orphan skills
    private String aiAgent;
    private List<SkillAgentLogin> agentLogins;
    private String localAnalysis;
    // The agent's ingest host for this skill, e.g. "<device>.ai-agent.claude".
    private String collectionName;
    private String filePath;
    // always ENDPOINT / AGENT_SKILL for skills; accepted so the request binds, not used
    private String contextSource;
    private String source;
    // null (an older agent) counts as true
    private Boolean reportThreat;

    // Output field
    private Map<String, Object> validationResult;

    public String validateAndReportSkillV2() {
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
        filePath = SkillValidationEngine.normalizePath(filePath);
        if (collectionName == null) collectionName = "";

        // Read up front: both are request-scoped and gone once publishing goes async.
        String authToken = SkillThreatReporterV3.requestAuthToken();
        int accountId = Context.accountId.get() == null ? 0 : Context.accountId.get();

        SkillPublisher.SkillRef skill = new SkillPublisher.SkillRef(skillName, skillDescription, skillContent,
                agentName, SkillPublisher.resolveAiAgent(aiAgent, agentName), filePath, collectionName, accountId,
                authToken);
        if (!collectionName.isEmpty()) SkillPublisher.ensureAgentCollection(skill, agentLogins);

        Result result;
        try {
            result = SkillValidationEngine.validate(new SkillValidationEngine.Request(skillName, skillDescription,
                    skillContent, filePath, referencedFiles, NO_FILES, localAnalysis,
                    "[SkillValidationV2] agent=" + agentName + " collection=" + collectionName));
        } catch (SkillValidationEngine.ValidationException e) {
            addActionError(e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        result.recordAudit(agentName);

        List<String> tagLabels = result.tagLabels();
        List<Map<String, Object>> annotations = result.annotations();
        Map<String, Object> verdict = result.verdict();

        // Publish off the request thread so the agent's validation call is not held up.
        if (authToken.isEmpty() || collectionName.isEmpty()) {
            logger.errorAndAddToDb("[SkillValidationV2] not publishing skill=" + skillName
                    + ": missing " + (authToken.isEmpty() ? "Authorization header" : "collectionName"), LogDb.DB_ABS);
        } else {
            SkillThreatReporterV3.Threat threat =
                    result.flagged && !Boolean.FALSE.equals(reportThreat) ? result.threat() : null;
            SkillThreatReporterV3.SkillRef threatRef = new SkillThreatReporterV3.SkillRef(skillName,
                    skillDescription, skillContent, agentName, collectionName, authToken);
            new Thread(() -> {
                Context.accountId.set(accountId);
                SkillPublisher.ingestSkill(skill, tagLabels, annotations, verdict);
                if (threat != null) SkillThreatReporterV3.reportThreat(threatRef, threat);
            }, "skill-publisher").start();
        }

        // Cyborg has published everything. An older agent raises threats and skill-tags from isMalicious /
        // maliciousEvents itself, so those stay empty to keep it from duplicating them; the result travels
        // in the same shape V3 returns it (tagLabels, annotations, verdict, riskScore).
        validationResult = new HashMap<>();
        validationResult.put("isMalicious", false);
        validationResult.put("maliciousMatchScore", 0.0);
        validationResult.put("toolNameDescriptionMatchScore", result.matchScore);
        validationResult.put("owaspCategories", new ArrayList<>());
        validationResult.put("maliciousEvents", new ArrayList<>());
        validationResult.put("tagLabels", tagLabels);
        validationResult.put("annotations", annotations);
        validationResult.put("verdict", verdict);
        validationResult.put("riskScore", result.riskScore);
        return Action.SUCCESS.toUpperCase();
    }
}
