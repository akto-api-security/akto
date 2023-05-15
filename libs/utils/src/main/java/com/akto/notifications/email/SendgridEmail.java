package com.akto.notifications.email;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.*;

import java.io.IOException;

/*
  Invite Button > Invite API backend > sendEmail
*/

public class SendgridEmail{

  public static Mail buildInvitationEmail (
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
    return mail;
  }

  private static Object extractOrgName(String inviteFrom) {
    if (inviteFrom.indexOf('@')<0){
      return ("akto.io");
    }
    return (inviteFrom.split("@")[1]);
    
  }

  public static void send(final Mail mail) throws IOException {
    final SendGrid sg = new SendGrid(Constants.SENDGRID_TOKEN_KEY);
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
