package com.akto.notifications.slack;

import java.util.List;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class DailyUpdate {
    private static BasicDBObject createHeader(String title) {
        BasicDBObject textObj = new BasicDBObject("type", "mrkdwn").append("text", title+"\n");
        BasicDBObject ret = new BasicDBObject("type", "section").append("text", textObj);
        return ret;
    }

    private static BasicDBObject createNumberSection(String title, int number) {
        BasicDBList fieldsList = new BasicDBList();
        BasicDBObject ret = new BasicDBObject("type", "section").append("fields", fieldsList);

        fieldsList.add(new BasicDBObject("type", "mrkdwn").append("text", "*"+title+"*\n"+number));
        return ret;
    }

    private static BasicDBObject createNumberSection(String title1, int number1, String title2, int number2) {
        BasicDBList fieldsList = new BasicDBList();
        BasicDBObject ret = new BasicDBObject("type", "section").append("fields", fieldsList);

        BasicDBObject field1 = new BasicDBObject("type", "mrkdwn").append("text", "*"+title1+"*\n"+number1);
        BasicDBObject field2 = new BasicDBObject("type", "mrkdwn").append("text", "*"+title2+"*\n"+number2);

        fieldsList.add(field1);
        fieldsList.add(field2);

        return ret;
    }

    private static BasicDBList createApiListSection(List<String> mapEndpointToSubtypes, String dashboardLink) {
        BasicDBList ret = new BasicDBList();

        for(String endpointData: mapEndpointToSubtypes.subList(0, Math.min(4, mapEndpointToSubtypes.size()))) {
            ret.add(new BasicDBObject("type", "section").append("text", new BasicDBObject("type", "mrkdwn").append("text", ">" + endpointData)));
        }

        if (mapEndpointToSubtypes.size() > 4) {
            String text = ("> and "+ (mapEndpointToSubtypes.size() - 4)  +" more...");
            ret.add(new BasicDBObject("type", "section").append("text", new BasicDBObject("type", "mrkdwn").append("text", text)));

        }

        
        return ret;
    }


    private int totalSensitiveEndpoints;
    private int totalEndpoints;
    private int newSensitiveEndpoints;
    private int newEndpoints;
    private int newSensitiveParams;
    private List<String> mapEndpointToSubtypes;
    private String dashboardLink;
    


    public DailyUpdate(
        int totalSensitiveEndpoints, int totalEndpoints, int newSensitiveEndpoints, 
        int newEndpoints, int newSensitiveParams, List<String> mapEndpointToSubtypes, String dashboardLink
    ) {
        this.totalSensitiveEndpoints = totalSensitiveEndpoints;
        this.totalEndpoints = totalEndpoints;
        this.newSensitiveEndpoints = newSensitiveEndpoints;
        this.newEndpoints = newEndpoints;
        this.newSensitiveParams = newSensitiveParams;
        this.mapEndpointToSubtypes = mapEndpointToSubtypes;
        this.dashboardLink = dashboardLink;
    }


    public String toJSON() {
        BasicDBList sectionsList = new BasicDBList();
        BasicDBObject ret = new BasicDBObject("blocks", sectionsList);

        sectionsList.add(createHeader("Summary for today: "));        
        // sectionsList.add(createNumberSection("Total Sensitive Endpoints", totalSensitiveEndpoints, "Total Endpoints", totalEndpoints));
        sectionsList.add(createNumberSection("New Sensitive Endpoints", newSensitiveEndpoints, "New Endpoints", newEndpoints));
        sectionsList.add(createNumberSection("New Sensitive Parameters", newSensitiveParams));

        if (mapEndpointToSubtypes.size() > 0) {
            sectionsList.addAll(createApiListSection(mapEndpointToSubtypes, dashboardLink));
        }

        return ret.toJson();
    }
}
