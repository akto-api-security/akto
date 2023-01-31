package com.akto.notifications.slack;

import java.util.List;
import java.util.Map;

import com.akto.dao.context.Context;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class DailyUpdate {
    public static BasicDBObject createHeader(String title) {
        BasicDBObject textObj = new BasicDBObject("type", "plain_text").append("text", title+"\n");
        return new BasicDBObject("type", "header").append("text", textObj);
    }

    public static BasicDBObject createSimpleBlockText(String text) {
        BasicDBObject textObj = new BasicDBObject("type", "mrkdwn").append("text",text+"\n");
        return new BasicDBObject("type", "section").append("text", textObj);
    }


    private static BasicDBObject createNumberSection(String title, int number, String link) {
        BasicDBList fieldsList = new BasicDBList();
        BasicDBObject ret = new BasicDBObject("type", "section").append("fields", fieldsList);

        fieldsList.add(new BasicDBObject("type", "mrkdwn").append("text", "*"+title+"*\n<"+link+"|"+number+">"));
        return ret;
    }

    public static BasicDBObject createNumberSection(String title1, int number1, String link1, String title2, int number2, String link2) {
        BasicDBList fieldsList = new BasicDBList();
        BasicDBObject ret = new BasicDBObject("type", "section").append("fields", fieldsList);

        BasicDBObject field1 = new BasicDBObject("type", "mrkdwn").append("text", "*"+title1+"*\n<"+link1+"|"+number1+">");
        BasicDBObject field2 = new BasicDBObject("type", "mrkdwn").append("text", "*"+title2+"*\n<"+link2+"|"+number2+">");

        fieldsList.add(field1);
        fieldsList.add(field2);

        return ret;
    }

    public static class LinkWithDescription {
        String header;
        String link;
        String description;

        public LinkWithDescription(String header, String link, String description) {
            this.header = header;
            this.link = link;
            this.description = description;
        }
    }

    public static BasicDBList createLinksSection(List<LinkWithDescription> linkWithDescriptionList) {
        BasicDBList ret = new BasicDBList();

        int counter = 0;
        for(LinkWithDescription linkWithDescription: linkWithDescriptionList) {
            counter ++ ;
            if (counter == 5) {
                break;
            }
            String completeText = "><"+linkWithDescription.link+"|"+linkWithDescription.header+">";
            if (linkWithDescription.description != null) completeText += "\n>"+linkWithDescription.description;
            ret.add(new BasicDBObject("type", "section").append("text", new BasicDBObject("type", "mrkdwn").append("text", completeText)));
        }

        if (linkWithDescriptionList.size() > 4) {
            String text = ("> and "+ (linkWithDescriptionList.size() - 4)  +" more...");
            ret.add(new BasicDBObject("type", "section").append("text", new BasicDBObject("type", "mrkdwn").append("text", text)));

        }


        return ret;
    }

    public static BasicDBList createApiListSection(Map<String, String> mapEndpointToSubtypes, String dashboardLink) {
        BasicDBList ret = new BasicDBList();

        int counter = 0;
        for(String endpointData: mapEndpointToSubtypes.keySet()) {
            counter ++ ;
            if (counter == 5) {
                break;
            }
            String link = dashboardLink + mapEndpointToSubtypes.get(endpointData);
            String text = "<"+link+"|"+endpointData+">";
            ret.add(new BasicDBObject("type", "section").append("text", new BasicDBObject("type", "mrkdwn").append("text", ">" + text)));
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
    private int newParams;
    private Map<String, String> mapEndpointToSubtypes;
    private String dashboardLink;
    private int startTimestamp;
    private int endTimestamp;

    public DailyUpdate(
        int totalSensitiveEndpoints, int totalEndpoints, 
        int newSensitiveEndpoints, int newEndpoints, 
        int newSensitiveParams, int newParams,
        int startTimestamp,int endTimestamp,
        Map<String, String> mapEndpointToSubtypes, String dashboardLink
    ) {
        this.totalSensitiveEndpoints = totalSensitiveEndpoints;
        this.totalEndpoints = totalEndpoints;
        this.newSensitiveEndpoints = newSensitiveEndpoints;
        this.newEndpoints = newEndpoints;
        this.newSensitiveParams = newSensitiveParams;
        this.newParams=newParams;
        this.startTimestamp=startTimestamp;
        this.endTimestamp=endTimestamp;
        this.mapEndpointToSubtypes = mapEndpointToSubtypes;
        this.dashboardLink = dashboardLink;
    }


    public String toJSON() {
        BasicDBList sectionsList = new BasicDBList();
        BasicDBObject ret = new BasicDBObject("blocks", sectionsList);

        sectionsList.add(createHeader("API Inventory Summary For Today :ledger: :"));        
        // sectionsList.add(createNumberSection("Total Sensitive Endpoints", totalSensitiveEndpoints, "Total Endpoints", totalEndpoints));

        // int end = Context.now();
        // int start = end - 24 * 60 * 60;

        String linkNewEndpoints = dashboardLink + "/dashboard/observe/changes?tab=endpoints&start="+startTimestamp+"&end="+endTimestamp;
        BasicDBObject topNumberSection = createNumberSection(
            "New Sensitive Endpoints", 
            newSensitiveEndpoints, 
            linkNewEndpoints, 
            "New Endpoints", 
            newEndpoints, 
            linkNewEndpoints
        );
        sectionsList.add(topNumberSection);

        String linkSensitiveParams = dashboardLink + "/dashboard/observe/changes?tab=parameters&start="+startTimestamp+"&end="+endTimestamp;
        BasicDBObject bottomNumberSection = createNumberSection(
            "New Sensitive Parameters",
            newSensitiveParams, 
            linkSensitiveParams,
            "New Parameters",
            newParams,
            linkSensitiveParams
        );
        sectionsList.add(bottomNumberSection);

        if (mapEndpointToSubtypes.size() > 0) {
            sectionsList.addAll(createApiListSection(mapEndpointToSubtypes, dashboardLink));
        }

        return ret.toJson();
    }
}
