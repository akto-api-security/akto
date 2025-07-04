package com.akto.open_api.parser;

import java.util.List;

import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.servers.ServerVariables;

public class ServerParser {

    public static String addServer(String path, List<List<Server>> serverLists) {
        for (List<Server> servers : serverLists) {
            if (servers != null && !servers.isEmpty()) {
                replaceServerVariables(servers);
                // Use first valid server URL
                for (Server server : servers) {
                    if (server.getUrl() == null || server.getUrl().isEmpty()) {
                        continue;
                    }
                    String serverUrl = server.getUrl();

                    if (!serverUrl.endsWith("/")) {
                        serverUrl += "/";
                    }
                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }
                    return serverUrl + path;
                }
            }
        }
        return path;
    }

    private static void replaceServerVariables(List<Server> servers) {
        for (Server server : servers) {
            ServerVariables serverVariables = server.getVariables();
            if (serverVariables != null) {
                server.setUrl(replaceServerVariablesInUrl(server.getUrl(), serverVariables));
            }
        }
    }

    private static String replaceServerVariablesInUrl(String url, ServerVariables serverVariables) {

        for (String variableName : serverVariables.keySet()) {
            ServerVariable serverVariable = serverVariables.get(variableName);
            String defaultValue = serverVariable.getDefault();
            String variableNameInUrl = "{" + variableName + "}";
            url = url.replace(variableNameInUrl, defaultValue);
        }
        return url;
    }
}
