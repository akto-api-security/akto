package com.akto.notifications.email;

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
import org.springframework.util.StringUtils;

import java.io.IOException;

public class SendgridEmail {

    private static final Logger logger = LoggerFactory.getLogger(SendgridEmail.class);

    private SendgridEmail() {}
    private static final SendgridEmail sendgridEmail = new SendgridEmail();

    private void buildMailBasic(String inviteeName, String inviteeEmail, Mail mail, Personalization personalization, String subject, String templateId, boolean isHtml) {

        Email fromEmail = new Email();
        fromEmail.setName("Ankita");
        fromEmail.setEmail("ankita.gupta@akto.io");
        mail.setFrom(fromEmail);
        if(StringUtils.hasText(subject)) {
            personalization.setSubject(subject);
        }
        Email to = new Email();
        if(StringUtils.hasText(inviteeName)) {
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

    public Mail buildTestingRunResultsEmail(TestingAlertData data, String email, String aktoUrl, String userName) {
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
