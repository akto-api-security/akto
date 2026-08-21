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
        if (!data.isEmpty()) {
            body.setLength(body.length() - 2); // drop the ",\n" left by the last column
            body.append("\n");
        }
        body.append("            ],\n" +
                "            \"rows\": [\n");
        boolean firstRow = true;
        for (List<String> dataRow : data) {
            if (!firstRow) body.append(",\n");
            body.append(CardTableRow.createTableRow(dataRow));
            firstRow = false;
        }

        body.append("\n                    ]\n" +
                "                }");

        return body.toString();

    }
}
