package com.akto.notifications.slack;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.util.List;

public abstract class SlackAlerts {

    private final String markDownTextType = "mrkdwn";
    private final String plaintextType = "plain_text";
    private final SlackAlertType ALERT_TYPE;
    public SlackAlerts(SlackAlertType ALERT_TYPE) {
        this.ALERT_TYPE = ALERT_TYPE;
    }

    private BasicDBObject createText(String text, String textType) {
        return new BasicDBObject("type", textType).append("text", text+"\n");
    }

    BasicDBObject createDivider() {
        return new BasicDBObject("type", "divider");
    }

    BasicDBObject createHeader(String title) {
        BasicDBObject textObj = createText(title, plaintextType);
        return new BasicDBObject("type", "header").append("text", textObj);
    }

    BasicDBObject createFieldSection(List<FieldsModel> fieldsModelList) {
        BasicDBList elementsList = new BasicDBList();
        int count = 0;
        for(FieldsModel fieldModel : fieldsModelList) {
            boolean isLastTwo = (++count >= fieldsModelList.size() - 1);
            BasicDBObject fieldObj = createText(fieldModel.toCustomString(isLastTwo), markDownTextType);
            elementsList.add(fieldObj);
        }

        return new BasicDBObject("type", "section").append("fields", elementsList);
    }

     BasicDBObject createTextContext(String contextText) {
        BasicDBObject textObj = createText(contextText, markDownTextType);
        BasicDBList elementsList = new BasicDBList();
        elementsList.add(textObj);
        return new BasicDBObject("type", "context").append("elements", elementsList);
    }

    BasicDBObject createTextSection(String sectionText) {
        BasicDBObject textObj = createText(sectionText, markDownTextType);
        return new BasicDBObject("type", "section").append("text", textObj);
    }

    public BasicDBObject toAttachment(BasicDBObject blockObj) {
        BasicDBList attachmentsList = new BasicDBList();
        attachmentsList.add(blockObj);

        return new BasicDBObject("attachments", attachmentsList);
    }

    public abstract String toJson();

    public SlackAlertType getALERT_TYPE() {
        return ALERT_TYPE;
    }
}
