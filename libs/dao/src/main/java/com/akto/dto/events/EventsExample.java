package com.akto.dto.events;

import java.util.List;

import io.intercom.api.Event;

public class EventsExample {

    private int count;
    private List<String> examples;

    public EventsExample () {}

    public EventsExample (int count, List<String> examples){
        this.count = count;
        this.examples = examples;
    }

    public static void insertMetaDataFormat(EventsExample eventsExample, String key, Event event){
        event.putMetadata(key + "Count", eventsExample.getCount());
        List<String> apiExamples = eventsExample.getExamples();
        for(int i = 0 ; i < apiExamples.size(); i++){
            event.putMetadata(key + "Example " + i , apiExamples.get(i));
        }
       
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<String> getExamples() {
        return examples;
    }

    public void setExamples(List<String> examples) {
        this.examples = examples;
    }

}
