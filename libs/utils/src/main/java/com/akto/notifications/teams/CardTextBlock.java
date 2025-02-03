package com.akto.notifications.teams;

public class CardTextBlock {
    public static String createTextBlock(String text, boolean wrap, String weight) {
        String body = "        {\n" +
                "            \"type\":\"TextBlock\",\n" +
                "            \"text\":\"" + text + "\",\n" +
                "            \"weight\": \"" + weight + "\",\n" +
                "            \"wrap\": " + wrap + "\n" +
                "        },";
        return body;
    }
}
