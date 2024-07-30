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
}
