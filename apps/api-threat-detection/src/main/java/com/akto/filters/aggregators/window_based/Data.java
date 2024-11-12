package com.akto.filters.aggregators.window_based;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Data {
    @JsonProperty("ln")
    public long lastNotifiedAt = 0;

    @JsonProperty("rq")
    public List<Request> requests = new ArrayList<>();

    public static class Request {
        private long receivedAt;

        public Request() {
        }

        public Request(long receivedAt) {
            this.receivedAt = receivedAt;
        }

        public long getReceivedAt() {
            return receivedAt;
        }

        public void setReceivedAt(long receivedAt) {
            this.receivedAt = receivedAt;
        }
    }

    public Data() {
    }

    public long getLastNotifiedAt() {
        return lastNotifiedAt;
    }

    public void setLastNotifiedAt(long lastNotifiedAt) {
        this.lastNotifiedAt = lastNotifiedAt;
    }

    public List<Request> getRequests() {
        return requests;
    }

    public void setRequests(List<Request> requests) {
        this.requests = requests;
    }
}
