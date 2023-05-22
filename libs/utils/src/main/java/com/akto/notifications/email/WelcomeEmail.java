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

public class WelcomeEmail{

  public static Mail buildWelcomeEmail (
    String welcomeName, 
    String welcomeEmail 
    ) {
    Mail mail = new Mail();

    Email fromEmail = new Email();
    fromEmail.setName("Ankita");
    fromEmail.setEmail("ankita.gupta@akto.io");
    mail.setFrom(fromEmail);

    //mail.setSubject("Welcome to Akto");

    Personalization personalization = new Personalization();
    Email to = new Email();
    to.setName(welcomeName);
    to.setEmail(welcomeEmail);
    personalization.addTo(to);
    //personalization.setSubject("Welcome to Akto");
    mail.addPersonalization(personalization);


    Content content = new Content();
    content.setType("text/html");
    content.setValue("Hello");
    mail.addContent(content);

    mail.setTemplateId("d-0e3d6462692d4a2196129f71b67c34a7");
    
        
    return mail;
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
