package com.akto.action;

import java.util.List;
import java.util.Map;

import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dao.notifications.EventsMetricsDao;
import com.akto.dto.events.EventsMetrics;
import com.akto.util.IntercomEventsUtil;
import com.akto.util.http_request.CustomHttpRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

import io.intercom.api.Event;
import io.intercom.api.User;

public class EventMetricsAction extends UserAction {

    private static User findUserInIntercom(String userEmail) throws Exception{
        String url = "https://api.intercom.io/users?email=" + userEmail;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Map<String,Object> usersObject = CustomHttpRequest.getRequest(url, "Bearer " + System.getenv("INTERCOM_TOKEN"));  
            if(usersObject == null){
                return null;
            }
            User user = null;
            List<Object> userObj = (List) usersObject.get("users");
            user = objectMapper.convertValue(userObj.get(0), User.class);
            return user;
        } catch (Exception e) {
            throw new Exception("Error in getting users data from intercom: " + e.getMessage());
        }
        
    }

    public void sendEventsToAllUsersInAccount(EventsMetrics currentEventsMetrics) throws Exception{
        int timeNow = Context.now();
        Event event = new Event()
                            .setCreatedAt(timeNow)
                            .setEventName("account-metrics");

        boolean shouldSendEvent = IntercomEventsUtil.createAndSendMetaDataForEvent(event, currentEventsMetrics);
        // get all users for this account id
        if(shouldSendEvent){
            try {
                BasicDBList allUsers = UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get());
                for(Object obj: allUsers) {
                    BasicDBObject userObj = (BasicDBObject) obj;
                    String userEmail = userObj.getString("login");
                    String name = userObj.getString("name");
                    event.setEmail(userEmail);
                    Event.create(event);

                    User intercomUser = findUserInIntercom(userEmail);

                    if(intercomUser == null){
                        EventsMetrics.createUserInIntercom(userEmail, name, timeNow);
                    }else{
                        intercomUser.setUpdateLastRequestAt(true);
                        User.update(intercomUser);
                    }
                }
            } catch (Exception e) {
                throw new Exception(e.getMessage());
            }
        }
    }

    private Map<String, Object> eventsMetrics;

    public String sendEventToIntercomFromUI(){
        try {
            Event event = new Event();
            EventsMetrics lastEventMetrics = EventsMetricsDao.instance.findLatestOne(Filters.empty());
            boolean value = false;
            if(lastEventMetrics != null){
                value = IntercomEventsUtil.createAndSendMetaDataForEvent(event, lastEventMetrics);
            }
            if(value){
                this.eventsMetrics = event.getMetadata();
            }
            
        } catch (Exception e) {
            addActionError("Error in retrieving latest event metric " + e.getMessage());
            e.printStackTrace();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public Map<String, Object> getEventsMetrics() {
        return eventsMetrics;
    }
}
