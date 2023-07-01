package com.akto.notifications.email;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.mongodb.client.model.Filters;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;

import java.io.IOException;

public class SendgridEmail {

    private SendgridEmail() {}
    private static final SendgridEmail sendgridEmail = new SendgridEmail();

    public static SendgridEmail getInstance() {
        return sendgridEmail;
    }
    private static Config.SendgridConfig sendgridConfig;
    private int lastRefreshTs = 0;
    public Config.SendgridConfig getSendgridConfig() {
        int now = Context.now();
        if (sendgridConfig == null || now - lastRefreshTs > 1 * 60) {
            synchronized (this) {
                if (sendgridConfig == null) {
                    Config config = ConfigsDao.instance.findOne(Filters.eq(ConfigsDao.ID, Config.SendgridConfig.CONFIG_ID));
                    if (config == null) {
                        config = new Config.SendgridConfig();
                    }
                    sendgridConfig = (Config.SendgridConfig) config;
                    lastRefreshTs = now;
                }
            }
        }
        return sendgridConfig;
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
        fromEmail.setEmail("ankita@akto.io");
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
        return mail;
    }

    private Object extractOrgName(String inviteFrom) {
        if (inviteFrom.indexOf('@')<0){
            return ("akto.io");
        }
        return (inviteFrom.split("@")[1]);

    }

    public void send(final Mail mail) throws IOException {
        String secretKey = this.getSendgridConfig().getSendgridSecretKey();
        if (secretKey == null || secretKey.isEmpty()) {
            return;
        }
        final SendGrid sg = new SendGrid(this.getSendgridConfig().getSendgridSecretKey());
        final Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        final Response response = sg.api(request);
        System.out.println(response.getStatusCode());
        System.out.println(response.getBody());
        System.out.println(response.getHeaders());
    }

}
