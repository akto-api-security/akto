package com.akto.notifications.teams;

public class CardTextBlock {

    /*
     * For reference of values: https://adaptivecards.io/explorer/TextBlock.html
     */
    public static String createTextBlock(String text, boolean wrap, String weight) {
        return createTextBlock(text, wrap, weight, "default");
    }

    public static String createTextBlock(String text, boolean wrap, String weight, String color) {
        String body = "        {\n" +
                "            \"type\":\"TextBlock\",\n" +
                "            \"text\":\"" + text + "\",\n" +
                "            \"weight\": \"" + weight + "\",\n" +
                "            \"color\": \"" + color + "\",\n" +
                "            \"wrap\": " + wrap + "\n" +
                "        },";
        return body;
    }

}
