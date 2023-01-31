package com.akto.dto;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Markov {
    private State current;
    private State next;
    private Map<String, Integer> countMap;
    Set<String> userIds = new HashSet<>();

    public Markov(State current, State next, Map<String, Integer> countMap, Set<String> userIds) {
        this.current = current;
        this.next = next;
        this.countMap = countMap;
        this.userIds = userIds;
    }

    public Markov() {
    }

    public int getTotalCount() {
        int count = 0;
        for (String key: countMap.keySet()) {
            count += countMap.get(key);
        }
        return count;
    }

    public void increaseCount(String key, String userId) {
        int count = countMap.getOrDefault(key, 0);
        count += 1;
        countMap.put(key, count);

        userIds.add(userId);
    }

    public static class State {
        private String url;
        private String method;

        public State(String url, String method) {
            this.url = url;
            this.method = method;
        }

        public State() {}

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;
            if (!(o instanceof State)) {
                return false;
            }
            State state = (State) o;
            return url.equals(state.url) && method.equals(state.method);
        }

        @Override
        public int hashCode() {
            return Objects.hash(url, method);
        }
    }

    public State getCurrent() {
        return current;
    }

    public void setCurrent(State current) {
        this.current = current;
    }

    public State getNext() {
        return next;
    }

    public void setNext(State next) {
        this.next = next;
    }

    public Map<String, Integer> getCountMap() {
        return countMap;
    }

    public void setCountMap(Map<String, Integer> countMap) {
        this.countMap = countMap;
    }

    public Set<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(Set<String> userIds) {
        this.userIds = userIds;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Markov)) {
            return false;
        }
        Markov state = (Markov) o;
        return current.equals(state.current) && next.equals(state.next);
    }

    @Override
    public int hashCode() {
        return Objects.hash(current, next);
    }
}
