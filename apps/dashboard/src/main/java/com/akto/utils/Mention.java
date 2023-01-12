package com.akto.utils;

import com.akto.dao.UsersDao;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Mention {

    public static ArrayList<Integer> extractMentionedUsersID(String content) {
        ArrayList<Integer> mentioned_users = new ArrayList<>();

        Pattern pattern = Pattern.compile("data-mention-id=\"(\\d*)\"");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            try {
                int userId = Integer.parseInt(matcher.group(1));
                BasicDBObject mentionedUser = UsersDao.instance.getUserInfo(userId);
                if ( mentionedUser != null) {
                    mentioned_users.add(userId);
                }
            } catch (NumberFormatException e) {
                e.getMessage();
            }
        }

        return mentioned_users;
    }
}
