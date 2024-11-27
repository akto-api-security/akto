package com.akto.password_reset;

import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.security.SecureRandom;
import java.util.Base64;

public class PasswordResetUtils {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    public static String generateToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }

    public static String insertPasswordResetToken(String email, String websiteHostName) {
        Bson filters = Filters.eq(User.LOGIN, email);
        String passwordResetToken = generateToken();

        if(passwordResetToken == null || passwordResetToken.isEmpty()) {
            return null;
        }

        UsersDao.instance.updateOne(
                filters,
                Updates.combine(
                        Updates.set(User.PASSWORD_RESET_TOKEN, passwordResetToken),
                        Updates.set(User.LAST_PASSWORD_RESET_TOKEN, Context.now())
                )
        );

        String endpoint = "/login";
        return websiteHostName + endpoint + "?token=" + passwordResetToken;
    }
}
