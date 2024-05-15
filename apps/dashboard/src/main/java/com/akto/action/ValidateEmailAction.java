package com.akto.action;
import com.akto.dao.UsersDao;
import com.akto.dto.User;
import com.akto.utils.JWT;
import com.opensymphony.xwork2.Action;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class ValidateEmailAction implements Action{
    private static final Logger logger = LoggerFactory.getLogger(ValidateEmailAction.class);
    @Override
    public String execute() {
        logger.info("ValidEmailAction Hit");
        logger.info(emailToken);

        try {
            Jws<Claims> jws = JWT.parseJwt(emailToken, "/home/avneesh/Desktop/akto/dashboard/public.pem");
            String email= jws.getBody().get("email").toString();

            logger.info("valid Token");

            User user = UsersDao.validateEmail(email);

            if (user == null) {
                return "ERROR";
            }

        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
            logger.error(e.getMessage());
            return "ERROR";
        }

        logger.info("Email Validation Done");
        return "SUCCESS";
    }

    private String emailToken;

    public void setEmailToken(String emailToken) {
        this.emailToken = emailToken;
    }
}
