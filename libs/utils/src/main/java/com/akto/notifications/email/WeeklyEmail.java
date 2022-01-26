package com.akto.notifications.email;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
  Invite Button > Invite API backend > sendEmail
*/

public class WeeklyEmail{

  public static Mail buildWeeklyEmail (
    String allSensitive, 
    String newLastWeek, 
    String newLastMonth,
    String sendTo,
    List <String> newEpList,
    String seeMoreNew,
    List <String> sEpList,
    String seeMoreSensitive 
    ) {
    Mail mail = new Mail();

    Email fromEmail = new Email();
    fromEmail.setName("Ankita");
    fromEmail.setEmail("ankita@akto.io");
    mail.setFrom(fromEmail);

    //mail.setSubject("Welcome to Akto");

    Personalization personalization = new Personalization();
    Email to = new Email();
    to.setEmail(sendTo);
    to.setName(sendTo);
    personalization.addTo(to);
    //personalization.setSubject("Welcome to Akto");
    mail.addPersonalization(personalization);


    Content content = new Content();
    content.setType("text/html");
    content.setValue("Hello");
    mail.addContent(content);

    mail.setTemplateId("d-1d45b42dde424b039d156dba5778d119");
    
        
    personalization.addDynamicTemplateData("allSensitive",allSensitive);
    personalization.addDynamicTemplateData("newLastWeek",newLastWeek);
    personalization.addDynamicTemplateData("newLastMonth",newLastMonth);
    personalization.addDynamicTemplateData("newEpList",newEpList);
    personalization.addDynamicTemplateData("sEpList",sEpList);
    personalization.addDynamicTemplateData("seeMoreNew",seeMoreNew);
    personalization.addDynamicTemplateData("seeMoreSensitive", seeMoreSensitive);
    
    return mail;
  }



  public static void send(final Mail mail) throws IOException {
    final SendGrid sg = new SendGrid("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");
    final Request request = new Request();
    request.setMethod(Method.POST);
    request.setEndpoint("mail/send");
    request.setBody(mail.build());

    final Response response = sg.api(request);
    System.out.println(response.getStatusCode());
    System.out.println(response.getBody());
    System.out.println(response.getHeaders());
  }

 
  public static void main (String [] arguments) {
    
  //  User user = UserAction.getSUser();


    String allSensitive = "5";
    String newLastWeek = "10";
    String newLastMonth = "20";
    String sendTo ="ankita@akto.io";
    List <String> newEpList = new ArrayList<>();
    List <String> sEpList = new ArrayList<>();
    String seeMoreNew ="www.akto.io";
    String seeMoreSensitive ="www.akto.io";

     newEpList.add("1. Hey");
     newEpList.add("2. Hey");
     sEpList.add("1. Hey");
     sEpList.add("2. Hey");

   // String sendTo = getSUser().getName(user)

    Mail email = WeeklyEmail.buildWeeklyEmail(allSensitive, newLastWeek, newLastMonth, sendTo, newEpList, seeMoreNew,sEpList, seeMoreSensitive);
    
    try {
            WeeklyEmail.send(email);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    
  }
  
}

