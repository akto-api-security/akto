package com.akto.notifications.teams;

import java.util.List;

public class CardTableRow {
    public static String createTableRow(List<String> data) {
        StringBuilder body = new StringBuilder();

        body.append(
                "                {\n" +
                        "                    \"type\": \"TableRow\",\n" +
                        "                    \"cells\": [\n");

        for (String key : data) {
            body.append("                        {\n" +
                    "                            \"type\": \"TableCell\",\n" +
                    "                            \"items\": [\n" +
                    "                                {\n" +
                    "                                    \"type\": \"TextBlock\",\n" +
                    "                                    \"text\": \"" + key + "\",\n" +
                    "                                    \"wrap\": true\n" +
                    "                                }\n" +
                    "                            ]\n" +
                    "                        },\n");
        }
        if (!data.isEmpty()) {
            body.setLength(body.length() - 2); // drop the ",\n" left by the last cell
            body.append("\n");
        }
        body.append("                    ]\n" +
                "                }");

        return body.toString();

    }

}
