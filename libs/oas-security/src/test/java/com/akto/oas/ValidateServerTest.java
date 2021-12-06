package com.akto.oas;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.servers.ServerVariables;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.akto.oas.Issue;
import com.akto.oas.OpenAPIValidator;
import com.akto.oas.PathComponent;

import static org.junit.jupiter.api.Assertions.*;

class ValidateServerTest {
    @Test
    public void testNullGlobalServers() {
        List<String> path = new ArrayList<>();
        List<Issue> issues = OpenAPIValidator.validateServers(null,true, path);
        assertEquals(issues.size(), 1);
        assertEquals(issues.get(0).getMessage(), "Global server field is null/empty");
        assertEquals(issues.get(0).getPath(), path);
    }

    @Test
    public void testNullLocalServers() {
        List<Issue> issues = OpenAPIValidator.validateServers(null,false, new ArrayList<>());
        assertEquals(issues.size(), 0);
    }

    @Test
    public void testGenerateSimpleURL() {
        String url = "www.google.com/{path1}/something/{path2}";
        ServerVariables serverVariables = new ServerVariables();
        ServerVariable serverVariable1 = new ServerVariable();
        serverVariable1.setDefault("p1");
        ServerVariable serverVariable2 = new ServerVariable();
        serverVariable2.setDefault("p2");
        ServerVariable serverVariable3 = new ServerVariable();
        serverVariable3.setDefault("p3");
        serverVariables.put("path1", serverVariable1);
        serverVariables.put("path2", serverVariable2);
        serverVariables.put("path3", serverVariable3);
        String finalUrl = OpenAPIValidator.generateURL(url, serverVariables);
        assertEquals(finalUrl,"www.google.com/p1/something/p2");
    }

    @Test
    public void testGenerateNullURL() {
        String url = null;
        ServerVariables serverVariables = new ServerVariables();
        ServerVariable serverVariable1 = new ServerVariable();
        serverVariable1.setDefault("p1");
        serverVariables.put("path1", serverVariable1);
        String finalUrl = OpenAPIValidator.generateURL(url, serverVariables);
        assertNull(finalUrl);
    }

    @Test
    public void testGenerateIncompleteURL() {
        String url = "www.google.com/{path1}/something/{path2}";
        ServerVariables serverVariables = new ServerVariables();
        ServerVariable serverVariable1 = new ServerVariable();
        serverVariable1.setDefault("p1");
        serverVariables.put("path1", serverVariable1);
        String finalUrl = OpenAPIValidator.generateURL(url, serverVariables);
        assertEquals(finalUrl,"www.google.com/p1/something/{path2}");
    }

    @Test
    public void testGenerateWithoutDefaultServerVariable() {
        String url = "www.google.com/{path1}/something/";
        ServerVariables serverVariables = new ServerVariables();
        ServerVariable serverVariable1 = new ServerVariable();
        serverVariable1.setDefault(null);
        serverVariables.put("path1", serverVariable1);
        String finalUrl = OpenAPIValidator.generateURL(url, serverVariables);
        assertEquals(finalUrl,"www.google.com/{path1}/something/");
    }

    @Test
    public void testValidateServersNoIssue() {
        String url = "https://www.google.com/";
        Server server = new Server();
        server.setUrl(url);
        List<Server> servers = Collections.singletonList(server);
        List<Issue> issues = OpenAPIValidator.validateServers(servers,true,new ArrayList<>());
        assertEquals(issues.size(),0);
    }

    @Test
    public void testValidateServersHttp() {
        String url = "http://www.google.com/";
        List<String> path = new ArrayList<>();
        Server server = new Server();
        server.setUrl(url);
        List<Server> servers = Collections.singletonList(server);
        List<Issue> global_issues = OpenAPIValidator.validateServers(servers,true,path);
        assertEquals(global_issues.size(),1);
        assertEquals(global_issues.get(0).getMessage(), "Use https");
        assertEquals(global_issues.get(0).getPath(), Arrays.asList(PathComponent.SERVERS,"0",PathComponent.URL));

        List<Issue> local_issues= OpenAPIValidator.validateServers(servers,false,path);
        assertEquals(local_issues.size(),1);
        assertEquals(local_issues.get(0).getMessage(), "Use https");
        assertEquals(local_issues.get(0).getPath(), Arrays.asList(PathComponent.SERVERS,"0",PathComponent.URL));
    }

    @Test
    public void testValidateServerAbsoluteURL() {
        String url = "www.google.com";
        List<String> path = new ArrayList<>();
        Server server = new Server();
        server.setUrl(url);
        List<Server> servers = Collections.singletonList(server);
        List<Issue> global_issues = OpenAPIValidator.validateServers(servers,true,path);
        assertEquals(global_issues.size(),1);
        assertEquals(global_issues.get(0).getMessage(), "Use absolute url");
        assertEquals(global_issues.get(0).getPath(), Arrays.asList(PathComponent.SERVERS,"0",PathComponent.URL));

        List<Issue> local_issues= OpenAPIValidator.validateServers(servers,false,path);
        assertEquals(local_issues.size(),0);
    }

    @Test
    public void testMultipleServerHttpIssuesSingle() {
        String url1 = "https://www.google.com";
        String url2 = "http://www.google.com";
        List<String> path = new ArrayList<>();
        Server server1 = new Server();
        server1.setUrl(url1);
        Server server2 = new Server();
        server2.setUrl(url2);
        List<Server> servers = Arrays.asList(server1, server2);
        List<Issue> global_issues = OpenAPIValidator.validateServers(servers,true,path);
        assertEquals(global_issues.size(),1);
        assertEquals(global_issues.get(0).getMessage(), "Use https");
        assertEquals(global_issues.get(0).getPath(), Arrays.asList(PathComponent.SERVERS,"1",PathComponent.URL));
    }

    @Test
    public void testMissingParameterInUrl() {
        String url1 = "https://www.google.com/{path1}/end/{path2}";
        List<String> path = new ArrayList<>();
        Server server1 = new Server();
        server1.setUrl(url1);
        ServerVariables serverVariables = new ServerVariables();
        ServerVariable serverVariable = new ServerVariable();
        serverVariable.setDefault("default");
        serverVariables.put("path2", serverVariable);
        server1.setVariables(serverVariables);
        List<Server> servers = Collections.singletonList(server1);
        List<Issue> global_issues = OpenAPIValidator.validateServers(servers,true,path);
        assertEquals(global_issues.size(),1);
        assertEquals(global_issues.get(0).getMessage(), "Missing parameter path1 in server url");
        assertEquals(global_issues.get(0).getPath(), Arrays.asList(PathComponent.SERVERS,"0",PathComponent.URL));
    }
}