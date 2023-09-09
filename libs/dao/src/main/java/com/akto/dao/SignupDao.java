package com.akto.dao;

import com.akto.dto.SignupInfo;
import com.akto.dto.SignupUserInfo;
import com.akto.dto.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Updates.set;

public class SignupDao extends CommonContextDao<SignupUserInfo> {

    public static final SignupDao instance = new SignupDao();

    @Override
    public String getCollName() {
        return "signup_info";
    }

    @Override
    public Class<SignupUserInfo> getClassT() {
        return SignupUserInfo.class;
    }

    public SignupUserInfo insertSignUp(String email, String name, SignupInfo info,  int invitationToAccount) {
        SignupUserInfo user = findOne("user.login", email);
        if (user == null) {
            insertOne(new SignupUserInfo(User.create(name, email, info, new HashMap<>()), null, null, null, new ArrayList<>(), invitationToAccount));
        } else {
            Map<String, SignupInfo> infoMap = user.getUser().getSignupInfoMap();
            if (infoMap == null) {
                infoMap = new HashMap<>();
            }
            infoMap.put(info.getKey(), info);
            this.getMCollection().updateOne(eq("user.login", email), set("user.signupInfoMap", infoMap));
        }

        return findOne("user.login", email);
    }
}
