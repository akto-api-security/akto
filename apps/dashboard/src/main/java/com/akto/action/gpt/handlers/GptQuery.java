package com.akto.action.gpt.handlers;

public enum GptQuery {
    LIST_APIS_BY_TYPE("list_apis_by_type"),
    GROUP_APIS_BY_FUNCTIONALITY("group_apis_by_functionality");

    private final String name;

    GptQuery(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static GptQuery getQuery(String name){
        for (GptQuery query : GptQuery.values()) {
            if (query.getName().equalsIgnoreCase(name)) {
                return query;
            }
        }
        throw new IllegalArgumentException("No such query supported");
    }
}
