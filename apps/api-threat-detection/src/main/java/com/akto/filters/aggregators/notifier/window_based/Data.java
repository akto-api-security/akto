package com.akto.filters.aggregators.notifier.window_based;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Data {
    @JsonProperty("ln")
    public long lastNotifiedAt;

    @JsonProperty("rq")
    public List<Request> requests;

    public static class Request {
        private final long receivedAt;
        private final String path;

        public Request(long receivedAt, String path) {
            this.receivedAt = receivedAt;
            this.path = path;
        }

        public long getReceivedAt() {
            return receivedAt;
        }

        public String getPath() {
            return path;
        }
    }

    public Data() {
        this.requests = new ArrayList<>();
        this.lastNotifiedAt = 0;
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
