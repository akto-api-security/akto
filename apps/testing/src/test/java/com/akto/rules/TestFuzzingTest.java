package com.akto.rules;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.testing.NucleiExecutor;
import com.akto.util.Pair;
import com.mongodb.BasicDBObject;
import kotlin.text.Charsets;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TestFuzzingTest {

    @Rule
    public TemporaryFolder templatesFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder utilsFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder nucleiOutputFolder = new TemporaryFolder();

    private File[] testDownloadLinkMain(String value, String templateName) throws Exception {
        File file = templatesFolder.newFile(templateName);

        FileUtils.writeStringToFile(file, value, Charsets.UTF_8);
        String outputDirectory = utilsFolder.getRoot().getPath();

        FuzzingTest.downloadLinks(file.getPath(),outputDirectory);

        return new File(outputDirectory).listFiles();
    }

//     @Test
//     public void testDownloadLinksSwagger() throws Exception {
//         // contains a rawGithubLink in payloads so should generate payloadFile
//         File[] files = testDownloadLinkMain(generateSwaggerFileDetectionText(), "swagger_file_detection.yaml");
//         assertEquals(1, files.length);
//         assertEquals("swagger_pathlist.txt", files[0].getName());
//     }

    @Test
    public void testDownloadLinksWordpress() throws Exception {
        // contains a local file link in payloads so should NOT generate payloadFile
        File[] files = testDownloadLinkMain(generateWordpressPluginsDetect(), "wordpress-plugins-detect.yaml");
        assertEquals(0, files.length);
    }

    @Test
    public void testDownloadLinksGmail() throws Exception {
        // contains a local file link in payloads so should NOT generate payloadFile
        File[] files = testDownloadLinkMain(generateValidGmailCheck(), "valid-gmail-check.yaml");
        assertEquals(0, files.length);
    }

    @Test
    public void testDownloadLinksPathTraversalFull() throws Exception {
        // contains a local file link in payloads so should NOT generate payloadFile
        File[] files = testDownloadLinkMain(generatePathTraversalFullText(), "path_traversal_full.yaml");
         assertEquals("dotdotpwn.txt", files[0].getName());

         List<String> lines = FileUtils.readLines(files[0], Charsets.UTF_8);
         assertEquals(FuzzingTest.payloadLineLimit, lines.size());
    }

    @Test
    public void testReadMetaData() throws IOException {
        File file = nucleiOutputFolder.newFile("main.txt");
        FileUtils.writeStringToFile(file, "", Charsets.UTF_8);

        List<BasicDBObject> metaData = NucleiExecutor.readMetaData(nucleiOutputFolder.getRoot().getPath());
        assertEquals(0, metaData.size());

        boolean deleteSuccessful = file.delete();
        assertTrue(deleteSuccessful);

        file = nucleiOutputFolder.newFile("main.txt");
        FileUtils.writeStringToFile(file, generateMetaFile(), Charsets.UTF_8);

        metaData = NucleiExecutor.readMetaData(nucleiOutputFolder.getRoot().getPath());
        assertEquals(4, metaData.size());
    }


    @Test
    public void testReadResponses() throws IOException {
        String path = nucleiOutputFolder.getRoot()+"/calls/http/random.txt";
        File file = new File(path);
        FileUtils.writeStringToFile(file, generateRequestResponseText(), Charsets.UTF_8);

        ArrayList<Pair<OriginalHttpRequest, OriginalHttpResponse>> responses = NucleiExecutor.readResponses(nucleiOutputFolder.getRoot().getPath()+"/calls");
        assertEquals(4, responses.size());
    }

    private String generateRequestResponseText() {
        return "[swagger-version] Dumped HTTP request for https://petstore.swagger.io/v2/pet/findByStatus/swagger-ui/swagger-ui.js\n" +
                "\n" +
                "\n" +
                "GET /v2/pet/findByStatus/swagger-ui/swagger-ui.js HTTP/1.1\n" +
                "Host: petstore.swagger.io\n" +
                "User-Agent: Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\n" +
                "Connection: close\n" +
                "Accept: application/json\n" +
                "Accept-Encoding: gzip, deflate, br\n" +
                "Accept-Language: en-US,en;q=0.5\n" +
                "Connection: keep-alive\n" +
                "Referer: https://petstore.swagger.io/\n" +
                "Sec-Fetch-Dest: empty\n" +
                "Sec-Fetch-Mode: cors\n" +
                "Sec-Fetch-Site: same-origin\n" +
                "Te: trailers\n" +
                "\n" +
                "\n" +
                "[swagger-version] Dumped HTTP response https://petstore.swagger.io/v2/pet/findByStatus/swagger-ui/swagger-ui.js\n" +
                "\n" +
                "HTTP/1.1 404 Not Found\n" +
                "Transfer-Encoding: chunked\n" +
                "Access-Control-Allow-Headers: Content-Type, api_key, Authorization\n" +
                "Access-Control-Allow-Methods: GET, POST, DELETE, PUT\n" +
                "Access-Control-Allow-Origin: *\n" +
                "Connection: keep-alive\n" +
                "Content-Type: application/json\n" +
                "Date: Thu, 05 Jan 2023 04:20:13 GMT\n" +
                "Server: Jetty(9.2.9.v20150224)\n" +
                "\n" +
                "{\"code\":404,\"type\":\"unknown\",\"message\":\"null for uri: http://petstore.swagger.io/v2/pet/findByStatus/swagger-ui/swagger-ui.js\"}\n" +
                "[swagger-version] Dumped HTTP request for https://petstore.swagger.io/v2/pet/findByStatus/swagger/swagger-ui.js\n" +
                "\n" +
                "\n" +
                "GET /v2/pet/findByStatus/swagger/swagger-ui.js HTTP/1.1\n" +
                "Host: petstore.swagger.io\n" +
                "User-Agent: Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\n" +
                "Connection: close\n" +
                "Accept: application/json\n" +
                "Accept-Encoding: gzip, deflate, br\n" +
                "Accept-Language: en-US,en;q=0.5\n" +
                "Connection: keep-alive\n" +
                "Referer: https://petstore.swagger.io/\n" +
                "Sec-Fetch-Dest: empty\n" +
                "Sec-Fetch-Mode: cors\n" +
                "Sec-Fetch-Site: same-origin\n" +
                "Te: trailers\n" +
                "\n" +
                "\n" +
                "[swagger-version] Dumped HTTP response https://petstore.swagger.io/v2/pet/findByStatus/swagger/swagger-ui.js\n" +
                "\n" +
                "HTTP/1.1 404 Not Found\n" +
                "Transfer-Encoding: chunked\n" +
                "Access-Control-Allow-Headers: Content-Type, api_key, Authorization\n" +
                "Access-Control-Allow-Methods: GET, POST, DELETE, PUT\n" +
                "Access-Control-Allow-Origin: *\n" +
                "Connection: keep-alive\n" +
                "Content-Type: application/json\n" +
                "Date: Thu, 05 Jan 2023 04:20:13 GMT\n" +
                "Server: Jetty(9.2.9.v20150224)\n" +
                "\n" +
                "{\"code\":404,\"type\":\"unknown\",\"message\":\"null for uri: http://petstore.swagger.io/v2/pet/findByStatus/swagger/swagger-ui.js\"}\n" +
                "[swagger-version] Dumped HTTP request for https://petstore.swagger.io/v2/pet/findByStatus/swagger-ui.js\n" +
                "\n" +
                "\n" +
                "GET /v2/pet/findByStatus/swagger-ui.js HTTP/1.1\n" +
                "Host: petstore.swagger.io\n" +
                "User-Agent: Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\n" +
                "Connection: close\n" +
                "Accept: application/json\n" +
                "Accept-Encoding: gzip, deflate, br\n" +
                "Accept-Language: en-US,en;q=0.5\n" +
                "Connection: keep-alive\n" +
                "Referer: https://petstore.swagger.io/\n" +
                "Sec-Fetch-Dest: empty\n" +
                "Sec-Fetch-Mode: cors\n" +
                "Sec-Fetch-Site: same-origin\n" +
                "Te: trailers\n" +
                "\n" +
                "\n" +
                "[swagger-version] Dumped HTTP response https://petstore.swagger.io/v2/pet/findByStatus/swagger-ui.js\n" +
                "\n" +
                "HTTP/1.1 404 Not Found\n" +
                "Transfer-Encoding: chunked\n" +
                "Access-Control-Allow-Headers: Content-Type, api_key, Authorization\n" +
                "Access-Control-Allow-Methods: GET, POST, DELETE, PUT\n" +
                "Access-Control-Allow-Origin: *\n" +
                "Connection: keep-alive\n" +
                "Content-Type: application/json\n" +
                "Date: Thu, 05 Jan 2023 04:20:13 GMT\n" +
                "Server: Jetty(9.2.9.v20150224)\n" +
                "\n" +
                "{\"code\":404,\"type\":\"unknown\",\"message\":\"null for uri: http://petstore.swagger.io/v2/pet/findByStatus/swagger-ui.js\"}\n" +
                "[swagger-version] Dumped HTTP request for https://petstore.swagger.io/v2/pet/findByStatus/swagger/ui/swagger-ui.js\n" +
                "\n" +
                "\n" +
                "GET /v2/pet/findByStatus/swagger/ui/swagger-ui.js HTTP/1.1\n" +
                "Host: petstore.swagger.io\n" +
                "User-Agent: Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\n" +
                "Connection: close\n" +
                "Accept: application/json\n" +
                "Accept-Encoding: gzip, deflate, br\n" +
                "Accept-Language: en-US,en;q=0.5\n" +
                "Connection: keep-alive\n" +
                "Referer: https://petstore.swagger.io/\n" +
                "Sec-Fetch-Dest: empty\n" +
                "Sec-Fetch-Mode: cors\n" +
                "Sec-Fetch-Site: same-origin\n" +
                "Te: trailers\n" +
                "\n" +
                "\n" +
                "[swagger-version] Dumped HTTP response https://petstore.swagger.io/v2/pet/findByStatus/swagger/ui/swagger-ui.js\n" +
                "\n" +
                "HTTP/1.1 404 Not Found\n" +
                "Transfer-Encoding: chunked\n" +
                "Access-Control-Allow-Headers: Content-Type, api_key, Authorization\n" +
                "Access-Control-Allow-Methods: GET, POST, DELETE, PUT\n" +
                "Access-Control-Allow-Origin: *\n" +
                "Connection: keep-alive\n" +
                "Content-Type: application/json\n" +
                "Date: Thu, 05 Jan 2023 04:20:13 GMT\n" +
                "Server: Jetty(9.2.9.v20150224)\n" +
                "\n" +
                "{\"code\":404,\"type\":\"unknown\",\"message\":\"null for uri: http://petstore.swagger.io/v2/pet/findByStatus/swagger/ui/swagger-ui.js\"}";
    }

    private String generateMetaFile() {
        return "{\"template-id\":\"swagger-version\",\"info\":{\"name\":\"Swagger 2.x Version Detection\",\"author\":[\"c-sh0\"],\"tags\":[\"tech\",\"swagger\",\"api\"],\"description\":\"Obtain Swagger Version\",\"reference\":[\"swagger ui 2.x and under\",\"https://github.com/swagger-api/swagger-ui/blob/master/docs/usage/version-detection.md\"],\"severity\":\"info\"},\"type\":\"http\",\"host\":\"https://petstore.swagger.io/v2/pet/findByStatus\",\"timestamp\":\"2023-01-05T04:20:13.328093384Z\",\"matcher-status\":false,\"matched-line\":null}\n" +
                "{\"template-id\":\"swagger-version\",\"info\":{\"name\":\"Swagger 2.x Version Detection\",\"author\":[\"c-sh0\"],\"tags\":[\"tech\",\"swagger\",\"api\"],\"description\":\"Obtain Swagger Version\",\"reference\":[\"swagger ui 2.x and under\",\"https://github.com/swagger-api/swagger-ui/blob/master/docs/usage/version-detection.md\"],\"severity\":\"info\"},\"type\":\"http\",\"host\":\"https://petstore.swagger.io/v2/pet/findByStatus\",\"timestamp\":\"2023-01-05T04:20:13.378927488Z\",\"matcher-status\":false,\"matched-line\":null}\n" +
                "{\"template-id\":\"swagger-version\",\"info\":{\"name\":\"Swagger 2.x Version Detection\",\"author\":[\"c-sh0\"],\"tags\":[\"tech\",\"swagger\",\"api\"],\"description\":\"Obtain Swagger Version\",\"reference\":[\"swagger ui 2.x and under\",\"https://github.com/swagger-api/swagger-ui/blob/master/docs/usage/version-detection.md\"],\"severity\":\"info\"},\"type\":\"http\",\"host\":\"https://petstore.swagger.io/v2/pet/findByStatus\",\"timestamp\":\"2023-01-05T04:20:13.429506909Z\",\"matcher-status\":false,\"matched-line\":null}\n" +
                "{\"template-id\":\"swagger-version\",\"info\":{\"name\":\"Swagger 2.x Version Detection\",\"author\":[\"c-sh0\"],\"tags\":[\"tech\",\"swagger\",\"api\"],\"description\":\"Obtain Swagger Version\",\"reference\":[\"swagger ui 2.x and under\",\"https://github.com/swagger-api/swagger-ui/blob/master/docs/usage/version-detection.md\"],\"severity\":\"info\"},\"type\":\"http\",\"host\":\"https://petstore.swagger.io/v2/pet/findByStatus\",\"timestamp\":\"2023-01-05T04:20:13.48052973Z\",\"matcher-status\":false,\"matched-line\":null}";
    }

    private String generateValidGmailCheck() {
        return "id: valid-gmail-checker\n" +
                "\n" +
                "info:\n" +
                "  name: Valid Google Mail Checker\n" +
                "  author: dievus,dwisiswant0\n" +
                "  severity: info\n" +
                "  reference:\n" +
                "    - https://github.com/dievus/geeMailUserFinder\n" +
                "\n" +
                "self-contained: true\n" +
                "requests:\n" +
                "  - method: HEAD\n" +
                "    path:\n" +
                "      - \"https://mail.google.com/mail/gxlu?email={{email}}\"\n" +
                "\n" +
                "    matchers:\n" +
                "      - type: word\n" +
                "        part: header\n" +
                "        words:\n" +
                "          - \"COMPASS\"";
    }

    private String generateWordpressPluginsDetect() {
        return "id: wordpress-plugins-detect\n" +
                "\n" +
                "info:\n" +
                "  name: WordPress Plugins Detection\n" +
                "  author: 0xcrypto\n" +
                "  severity: info\n" +
                "  tags: fuzz,wordpress\n" +
                "\n" +
                "requests:\n" +
                "  - raw:\n" +
                "      - |\n" +
                "        GET /wp-content/plugins/{{pluginSlug}}/readme.txt HTTP/1.1\n" +
                "        Host: {{Hostname}}\n" +
                "\n" +
                "    threads: 50\n" +
                "    payloads:\n" +
                "      pluginSlug: helpers/wordlists/wordpress-plugins.txt\n" +
                "\n" +
                "    matchers-condition: and\n" +
                "    matchers:\n" +
                "      - type: status\n" +
                "        status:\n" +
                "          - 200\n" +
                "\n" +
                "      - type: word\n" +
                "        words:\n" +
                "          - \"== Description ==\"\n" +
                "\n" +
                "    extractors:\n" +
                "      - type: regex\n" +
                "        part: body\n" +
                "        group: 1\n" +
                "        regex:\n" +
                "          - \"===\\\\s(.*)\\\\s===\" # extract the plugin name\n" +
                "          - \"(?m)Stable tag: ([0-9.]+)\" # extract the plugin version";
    }

    private String generatePathTraversalFullText() {
        return "id: path-traversal\n" +
                "  \n" +
                "info:\n" +
                "  name: Path traversal full\n" +
                "  author: akto\n" +
                "  reference:\n" +
                "    - From OWASP site - https://owasp.org/www-project-web-security-testing-guide/latest/4-Web_Application_Security_Testing/05-Authorization_Testing/01-Testing_Directory_Traversal_File_Include\n" +
                "    - https://github.com/swisskyrepo/PayloadsAllTheThings/tree/master/Directory%20Traversal\n" +
                "  severity: info\n" +
                "\n" +
                "requests:\n" +
                "  - method: GET\n" +
                "    path:\n" +
                "      - \"{{BaseURL}}/{{locations}}\"\n" +
                "\n" +
                "    payloads:\n" +
                "      locations: https://raw.githubusercontent.com/swisskyrepo/PayloadsAllTheThings/master/Directory%20Traversal/Intruder/dotdotpwn.txt\n" +
                "    matchers-condition: and\n" +
                "    matchers:\n" +
                "     - type: status\n" +
                "       status:\n" +
                "         - 200\n" +
                "\n" +
                "    extractors:\n" +
                "     - type: regex\n" +
                "       part: body\n" +
                "       group: 1\n" +
                "       regex:\n" +
                "         - \" @version (v[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3})\"";
    }



//     private String generateSwaggerFileDetectionText() {
//         return "id: swagger-version\n" +
//                 "  \n" +
//                 "info:\n" +
//                 "  name: Swagger 2.x Version Detection\n" +
//                 "  author: c-sh0\n" +
//                 "  reference:\n" +
//                 "     - Swagger UI 2.x and under\n" +
//                 "     - https://github.com/swagger-api/swagger-ui/blob/master/docs/usage/version-detection.md\n" +
//                 "  severity: info\n" +
//                 "  description: Obtain Swagger Version\n" +
//                 "  tags: tech,swagger,api\n" +
//                 "\n" +
//                 "requests:\n" +
//                 "  - method: GET\n" +
//                 "    path:\n" +
//                 "      - \"{{BaseURL}}/{{locations}}\"\n" +
//                 "    payloads:\n" +
//                 "       locations: https://github.com/akto-api-security/testing_sources/blob/master/Misconfiguration/swagger-detection/wordlists/swagger_pathlist.txt\n" +
//                 "\n" +
//                 "    matchers-condition: and\n" +
//                 "    matchers:\n" +
//                 "     - type: status\n" +
//                 "       status:\n" +
//                 "         - 200\n" +
//                 "\n" +
//                 "    extractors:\n" +
//                 "     - type: regex\n" +
//                 "       part: body\n" +
//                 "       group: 1\n" +
//                 "       regex:\n" +
//                 "         - \" @version (v[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3})\"";
//     }

}
