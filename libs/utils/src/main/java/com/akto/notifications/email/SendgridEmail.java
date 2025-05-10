package com.akto.notifications.email;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.onprem.Constants;
import com.mongodb.client.model.Filters;
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

import java.io.IOException;

public class SendgridEmail {

    private static final Logger logger = LoggerFactory.getLogger(SendgridEmail.class);

    private SendgridEmail() {}
    private static final SendgridEmail sendgridEmail = new SendgridEmail();

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

        Email fromEmail = new Email();
        fromEmail.setName("Ankita");
        fromEmail.setEmail("ankita.gupta@akto.io");
        mail.setFrom(fromEmail);

        Personalization personalization = new Personalization();
        Email to = new Email();
        to.setName(adminName);
        to.setEmail(adminEmail);
        personalization.addTo(to);
        //personalization.setSubject("Welcome to Akto");
        mail.addPersonalization(personalization);

        Content content = new Content();
        content.setType("text/html");
        content.setValue("Hello");
        mail.addContent(content);

        mail.setTemplateId("d-64cefe02855e48fa9b4dd0a618e38569");


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

        Email fromEmail = new Email();
        fromEmail.setName("Ankita");
        fromEmail.setEmail("ankita.gupta@akto.io");
        mail.setFrom(fromEmail);

        //mail.setSubject("Welcome to Akto");

        Personalization personalization = new Personalization();
        Email to = new Email();
        to.setName(inviteeName);
        to.setEmail(inviteeEmail);
        personalization.addTo(to);
        //personalization.setSubject("Welcome to Akto");
        mail.addPersonalization(personalization);

        Content content = new Content();
        content.setType("text/html");
        content.setValue("Hello");
        mail.addContent(content);

        mail.setTemplateId("d-ffe7d4ec96154b5d84e24816893161c7");


        personalization.addDynamicTemplateData("inviteFrom",inviteFrom);
        personalization.addDynamicTemplateData("inviteeName",inviteeName);
        personalization.addDynamicTemplateData("orgName",extractOrgName(inviteFrom));
        personalization.addDynamicTemplateData("aktoUrl",invitiationUrl);
        logger.info("Invitation url: "+invitiationUrl);
        return mail;
    }

    public Mail buildPasswordResetEmail(String email, String passwordResetTokenUrl) {
        Mail mail = new Mail();

        Email fromEmail = new Email();
        fromEmail.setName("Ankita");
        fromEmail.setEmail("ankita.gupta@akto.io");
        mail.setFrom(fromEmail);

        Personalization personalization = new Personalization();
        Email to = new Email();
        to.setEmail(email);
        personalization.addTo(to);
        mail.addPersonalization(personalization);

        Content content = new Content();
        content.setType("text/html");
        content.setValue("Hello,");
        mail.addContent(content);

        mail.setTemplateId("d-266210cb361b4b659289a72aef04edfa");

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
