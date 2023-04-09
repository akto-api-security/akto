package com.akto.dao.test_editor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    public static Boolean checkIfContainsMatch(String text, String keyword) {
        Pattern pattern = Pattern.compile(keyword);
        Matcher matcher = pattern.matcher(text);
        String match = null;
        if (matcher.find()) {
            match = matcher.group(0);
        }

        return match != null;
    }

}