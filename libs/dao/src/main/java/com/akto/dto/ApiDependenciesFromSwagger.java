package com.akto.dto;

import java.util.List;

import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor

public class ApiDependenciesFromSwagger {
    @Getter
    @Setter
    @NoArgsConstructor
    public static class APIIdentifier {
        private String url;
        private String method;

        public APIIdentifier(String url, String method) {
            this.url = url;
            this.method = method;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            APIIdentifier that = (APIIdentifier) o;
            return url.equals(that.url) && method.equals(that.method);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Dependency {
        private APIIdentifier id;
        private int order;
        private String param;

        public Dependency(APIIdentifier id, int order, String param) {
            this.id = id;
            this.order = order;
            this.param = param;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Dependency that = (Dependency) o;
            return id.equals(that.id) && order == that.order && param.equals(that.param);
        }
    }

    private APIIdentifier apiIdentifier;
    private List<Dependency> dependencies;
    private int lastSeen;
    private int apiCollectionId;

    public ApiDependenciesFromSwagger(APIIdentifier apiIdentifier, List<Dependency> dependencies, int lastSeen, int apiCollectionId) {
        this.apiIdentifier = apiIdentifier;
        this.dependencies = dependencies;
        this.lastSeen = lastSeen;
        this.apiCollectionId = apiCollectionId;
    }
}
