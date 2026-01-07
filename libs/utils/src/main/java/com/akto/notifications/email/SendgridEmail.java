package com.akto.notifications.email;

import com.akto.dto.test_editor.YamlTemplate;
import com.akto.notifications.data.TestingAlertData;
import com.akto.onprem.Constants;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SendgridEmail {

    private static final Logger logger = LoggerFactory.getLogger(SendgridEmail.class);

    private SendgridEmail() {}
    private static final SendgridEmail sendgridEmail = new SendgridEmail();

    private void buildMailBasic(String inviteeName, String inviteeEmail, Mail mail, Personalization personalization, String subject, String templateId, boolean isHtml) {

        Email fromEmail = new Email();
        fromEmail.setName("Ankita");
        fromEmail.setEmail("ankita.gupta@akto.io");
        mail.setFrom(fromEmail);
        if(StringUtils.isNotBlank(subject)) {
            personalization.setSubject(subject);
        }
        Email to = new Email();
        if(StringUtils.isNotBlank(inviteeName)) {
            to.setName(inviteeName);
        }
        
        to.setEmail(inviteeEmail);
        personalization.addTo(to);
        mail.addPersonalization(personalization);
        mail.setTemplateId(templateId);

        Content content = new Content();
        if(!isHtml){
            content.setType("text/plain"); 
        }else {
            content.setType("text/html");
        }
        content.setValue("Hello,");
        mail.addContent(content);
    }

    public static SendgridEmail getInstance() {
        return sendgridEmail;
    }

    public Mail buildBillingEmail (
            String adminName,
            String adminEmail,
            int apis,
            int testRuns,
            int customTemplates,
            int accounts
    ) {
        Mail mail = new Mail();
        Personalization personalization = new Personalization();
        buildMailBasic(adminName, adminEmail, mail, personalization, null, "d-64cefe02855e48fa9b4dd0a618e38569", true);
        personalization.addDynamicTemplateData("apis",apis +"");
        personalization.addDynamicTemplateData("testRuns",testRuns + "");
        personalization.addDynamicTemplateData("customTemplates",customTemplates +"");
        personalization.addDynamicTemplateData("accounts",accounts +"");
        return mail;
    }

    public Mail buildInvitationEmail (
            String inviteeName,
            String inviteeEmail,
            String inviteFrom,
            String invitiationUrl
    ) {
        Mail mail = new Mail();
        Personalization personalization = new Personalization();
        buildMailBasic(inviteeName, inviteeEmail, mail, personalization, null, "d-ffe7d4ec96154b5d84e24816893161c7", true);
        personalization.addDynamicTemplateData("inviteFrom",inviteFrom);
        personalization.addDynamicTemplateData("inviteeName",inviteeName);
        personalization.addDynamicTemplateData("orgName",extractOrgName(inviteFrom));
        personalization.addDynamicTemplateData("aktoUrl",invitiationUrl);
        logger.info("Invitation url: "+invitiationUrl);
        return mail;
    }

    private void buildTemplateWithYamlTemplates(Personalization personalization, Map<String, YamlTemplate> yamlTemplates, Map<String, Integer> apisAffectedCount) {
        if (yamlTemplates == null || apisAffectedCount == null || yamlTemplates.isEmpty() || apisAffectedCount.isEmpty()) {
            personalization.addDynamicTemplateData("findings", new ArrayList<>());
            return;
        }

        List<Map<String, Object>> findings = new ArrayList<>();
        int serialNumber = 1;
        
        for(Map.Entry<String, Integer> entry : apisAffectedCount.entrySet()) {
            String template = entry.getKey();
            int apisAffected = entry.getValue();
            YamlTemplate yamlTemplate = yamlTemplates.get(template);
            
            if (yamlTemplate == null || yamlTemplate.getInfo() == null) {
                continue;
            }
            
            String issueName = yamlTemplate.getInfo().getName();
            if (StringUtils.isBlank(issueName)) {
                issueName = template;
            }
            
            String description = yamlTemplate.getInfo().getDescription();
            if (StringUtils.isBlank(description)) {
                description = "-";
            }
            
            String severity = yamlTemplate.getInfo().getSeverity();
            if (StringUtils.isBlank(severity)) {
                severity = "-";
            } else {
                severity = severity.toUpperCase();
            }
            
            String categoryName = "-";
            if (yamlTemplate.getInfo().getCategory() != null) {
                String displayName = yamlTemplate.getInfo().getCategory().getDisplayName();
                String shortName = yamlTemplate.getInfo().getCategory().getShortName();
                if (StringUtils.isNotBlank(displayName)) {
                    categoryName = displayName;
                    if (StringUtils.isNotBlank(shortName)) {
                        categoryName += " (" + shortName + ")";
                    }
                } else if (StringUtils.isNotBlank(yamlTemplate.getInfo().getCategory().getName())) {
                    categoryName = yamlTemplate.getInfo().getCategory().getName();
                }
            }
            
            Map<String, Object> finding = new HashMap<>();
            finding.put("sno", serialNumber);
            finding.put("issueName", issueName);
            finding.put("description", description);
            finding.put("apisAffected", apisAffected);
            finding.put("category", categoryName);
            finding.put("severity", severity);
            
            // Add boolean flags for severity to use in Handlebars conditionals
            finding.put("isCritical", "CRITICAL".equals(severity));
            finding.put("isHigh", "HIGH".equals(severity));
            finding.put("isMedium", "MEDIUM".equals(severity));
            finding.put("isLow", "LOW".equals(severity));
            finding.put("isUnknown", "-".equals(severity));
            
            findings.add(finding);
            serialNumber++;
        }
        
        personalization.addDynamicTemplateData("findings", findings);
    }

    public Mail buildTestingRunResultsEmail(TestingAlertData data, String email, String aktoUrl, String userName, Map<String, Integer> apisAffectedCount, Map<String, YamlTemplate> yamlTemplates) {
        Mail mail = new Mail(); 
        Personalization personalization = new Personalization();
        String templateId = "d-e6ec36c175564acf844c95a704a3051e";

        buildMailBasic(userName, email, mail, personalization, "Akto test results summary", templateId, true);
        personalization.addDynamicTemplateData("title", data.getTitle());
        personalization.addDynamicTemplateData("critical", String.valueOf(data.getCritical()));
        personalization.addDynamicTemplateData("high", String.valueOf(data.getHigh()));
        personalization.addDynamicTemplateData("medium", String.valueOf(data.getMedium()));
        personalization.addDynamicTemplateData("low", String.valueOf(data.getLow()));
        personalization.addDynamicTemplateData("newIssues", String.valueOf(data.getNewIssues()));
        personalization.addDynamicTemplateData("vulnerableApis", String.valueOf(data.getVulnerableApis()));
        personalization.addDynamicTemplateData("totalApis", String.valueOf(data.getTotalApis()));
        personalization.addDynamicTemplateData("collection", data.getCollection());
        personalization.addDynamicTemplateData("scanTimeInSeconds", String.valueOf(data.getScanTimeInSeconds()));
        personalization.addDynamicTemplateData("viewOnAktoURL", aktoUrl);
        
        // Build and add findings table
        buildTemplateWithYamlTemplates(personalization, yamlTemplates, apisAffectedCount);
        
        return mail;
    }

    public Mail buildPasswordResetEmail(String email, String passwordResetTokenUrl) {
        Mail mail = new Mail();
        Personalization personalization = new Personalization();
        buildMailBasic( null, email, mail, personalization, null, "d-266210cb361b4b659289a72aef04edfa", true);
        personalization.addDynamicTemplateData("aktoUrl", passwordResetTokenUrl);
        personalization.addDynamicTemplateData("supportEmail", "support@akto.io");

        return mail;
    }

    private Object extractOrgName(String inviteFrom) {
        if (inviteFrom.indexOf('@')<0){
            return ("akto.io");
        }
        return (inviteFrom.split("@")[1]);

    }

    public void send(final Mail mail) throws IOException {
        String secretKey = Constants.getSendgridConfig().getSendgridSecretKey();
        if (secretKey == null || secretKey.isEmpty()) {
            logger.info("No sendgrid config found. Skipping sending email");
            return;
        }
        final SendGrid sg = new SendGrid(Constants.getSendgridConfig().getSendgridSecretKey());
        final Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        final Response response = sg.api(request);
        logger.info(String.valueOf(response.getStatusCode()));
        logger.info(response.getBody());
        logger.info(response.getHeaders().toString());
    }

}
