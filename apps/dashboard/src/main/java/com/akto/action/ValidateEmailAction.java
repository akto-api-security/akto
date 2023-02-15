package com.akto.action;
import com.akto.dao.UsersDao;
import com.akto.dto.User;
import com.akto.utils.JWT;
import com.opensymphony.xwork2.Action;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class ValidateEmailAction implements Action{

    @Override
    public String execute() {
        System.out.println("ValidEmailAction Hit");
        System.out.println(emailToken);

        try {
            Jws<Claims> jws = JWT.parseJwt(emailToken, "/home/avneesh/Desktop/akto/dashboard/public.pem");
            String email= jws.getBody().get("email").toString();

            System.out.println("valid Token");

            User user = UsersDao.validateEmail(email);

            if (user == null) {
                return "ERROR";
            }

        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
            System.out.println(e);
            return "ERROR";
        }

        System.out.println("Email Validation Done");
        return "SUCCESS";
    }

    private String emailToken;

    public void setEmailToken(String emailToken) {
        this.emailToken = emailToken;
    }
}
