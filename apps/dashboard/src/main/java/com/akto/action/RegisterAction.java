package com.akto.action;

import com.akto.dao.UsersDao;
import com.akto.dto.User;
import com.akto.utils.JWT;
import com.opensymphony.xwork2.Action;
import com.sendgrid.Method;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.Request;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class RegisterAction implements Action {
    @Override
    public String execute() {
//        System.out.println("Register Action Hit");
//
//        if (email == null | fullName == null | password == null) {
//            return "ERROR";
//        }
//
//        User user = UsersDao.addUser(email,fullName,password,false);
//
//        if (user == null) {
//            return "USER_EXISTS";
//        }
//        System.out.println("added user");
//
//        String emailValidationLink = createEmailValidationLink(this.email,user.getPassHash());
//
//        Email from = new Email("f20180355@goa.bits-pilani.ac.in");
//        String subject = "Click on this link to activate your account";
//        Email to = new Email(this.email);
//        Content content = new Content(
//                "text/plain",
//                "Welcome to AKTO\n" +
//                        "Click on this link to verify your account \n\n"
//                        + emailValidationLink
//        );
//        Mail mail = new Mail(from, subject, to, content);
//
//        SendGrid sg = new SendGrid(System.getenv("SENDGRID_API_KEY"));
//        System.out.println(System.getenv("SENDGRID_API_KEY"));
//        Request request = new Request();
//
//        try {
//            request.setMethod(Method.POST);
//            request.setEndpoint("mail/send");
//            request.setBody(mail.build());
//            System.out.println("Sending.... to " + this.email + "from" + from);
//            Response response = sg.api(request);
//        } catch (IOException ex) {
//            System.out.println(ex.toString());
//        }
//
//        System.out.println("done!!!!!!!");
//        result = "Done";

        return "SUCCESS";
    }

    private String email;
    private String fullName;
    private String password;
    private String result;

    public void setEmail(String email) {
        this.email = email;
    }
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getResult() {
        return result;
    }

    private static String createEmailValidationLink(String email, String passHash) {
        Calendar calendar = Calendar.getInstance();
        java.util.Date issueTime = calendar.getTime();

        Map<String,Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("passHash", passHash);

        try {
            String validationToken = JWT.createJWT(
                    "/home/avneesh/Desktop/akto/dashboard/private.pem",
                    claims,
                    "Akto",
                    "emailValidation",
                    Calendar.MINUTE,
                    15
            );
            return "localhost:8080/validate/" + validationToken;

        } catch(Exception err) {
            System.out.println(err.toString());
            return null;
        }

    }
}
