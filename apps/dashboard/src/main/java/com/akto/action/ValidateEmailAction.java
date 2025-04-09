package com.akto.action;

import com.akto.dao.UsersDao;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.JWT;
import com.opensymphony.xwork2.Action;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class ValidateEmailAction implements Action{
    private static final LoggerMaker logger = new LoggerMaker(ValidateEmailAction.class, LogDb.DASHBOARD);

    @Override
    public String execute() {

        try {
            Jws<Claims> jws = JWT.parseJwt(emailToken, "/home/avneesh/Desktop/akto/dashboard/public.pem");
            String email= jws.getBody().get("email").toString();


            User user = UsersDao.validateEmail(email);

            if (user == null) {
                return "ERROR";
            }

        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
            logger.error(e.getMessage());
            return "ERROR";
        }

        return "SUCCESS";
    }

    private String emailToken;

    public void setEmailToken(String emailToken) {
        this.emailToken = emailToken;
    }
}
