package com.akto.util;

import java.util.List;

public class CookieTransformer {

    public static Boolean isKeyPresentInCookie(List<String> cookieList, String key) {

        if(cookieList==null)return false;
        for (String cookieValues : cookieList) {
            String[] cookies = cookieValues.split(";");
            for (String cookie : cookies) {
                cookie=cookie.trim();
                String[] cookieFields = cookie.split("=");
                if (cookieFields.length == 2) {
                    String cookieKey = cookieFields[0].toLowerCase();
                    if(cookieKey.equals(key.toLowerCase())){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void modifyCookie(List<String> cookieList, String key, String value) {

        if(cookieList==null)return;

        int i = 0;
        for (String cookieValues : cookieList) {
            String[] cookies = cookieValues.split(";");
            int index = 0;
            for (String cookie : cookies) {
                cookie=cookie.trim();
                String[] cookieFields = cookie.split("=");

                if (cookieFields.length == 2) {
                    String cookieKey = cookieFields[0].toLowerCase();
                    if(cookieKey.equals(key.toLowerCase())){
                        cookies[index] = String.join("=", cookieFields[0], value);
                    }
                }
                index++;
            }

            String modifiedCookieValue = String.join(";", cookies);
            cookieList.set(i, modifiedCookieValue);
            i++;
        }
    }

    public static void deleteCookie(List<String> cookieList, String key, String value) {

        if(cookieList==null)return;

        int i = 0;
        for (String cookieValues : cookieList) {
            String[] cookies = cookieValues.split(";");
            String[] modifiedCookies = new String[cookies.length];
            int index = 0;
            for (String cookie : cookies) {
                cookie=cookie.trim();
                String[] cookieFields = cookie.split("=");

                if (cookieFields.length == 2) {
                    String cookieKey = cookieFields[0].toLowerCase();
                    if(!cookieKey.equals(key.toLowerCase())){
                        modifiedCookies[index] = String.join("=", cookieFields[0], cookieFields[1]);
                    }
                }
                index++;
            }

            String modifiedCookieValue = String.join(";", modifiedCookies);
            cookieList.set(i, modifiedCookieValue);
            i++;
        }
    }

}