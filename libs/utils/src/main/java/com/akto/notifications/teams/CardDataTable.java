package com.akto.notifications.teams;

import java.util.List;

public class CardDataTable {
    public static String createTable(List<List<String>> data) {
        StringBuilder body = new StringBuilder();

        body.append("        {\n" +
                "            \"type\": \"Table\",\n" +
                "            \"columns\": [\n");

        int i = data.size();
        while (i > 0) {
            body.append("                {\"width\": 1},\n");
            i--;
        }
        body.append("            ],\n" +
                "            \"rows\": [\n");
        for (List<String> dataRow : data) {
            body.append(CardTableRow.createTableRow(dataRow));
        }

        body.append("                    ]\n" +
                "                },\n");

        return body.toString();

    }
}
