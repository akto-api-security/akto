package com.akto;

import com.akto.dao.context.Context;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.graphql.GraphQLUtils;
import com.akto.util.grpc.ProtoBufUtils;
import com.akto.har.HAR;
import com.akto.util.HttpRequestResponseUtils;
import com.google.gson.Gson;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mortbay.util.ajax.JSON;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestGraphQLUtils{

    private static final Gson gson = new Gson();


    @Test
    public void testGraphQLParser() throws Exception {
        HAR har = new HAR();
        List<String> requests = har.getMessages(harString, 0, 1_000_000, HttpResponseParams.Source.HAR);
        //Even with 2 har entries, we get 10 endpoints

        for (String request : requests) {
            HttpResponseParams responseParams = parseKafkaMessage(request);
            List<HttpResponseParams> list = GraphQLUtils.getUtils().parseGraphqlResponseParam(responseParams);
            Assert.assertTrue(responseParams.getRequestParams().url.contains("graphql") != list.isEmpty());
        }
    }

    @Test
    public void testAddGraphqlField() throws Exception {
        HAR har = new HAR();
        List<String> requests = har.getMessages(harString, 0, 1_000_000, HttpResponseParams.Source.HAR);

        for (String request : requests) {
            HttpResponseParams responseParams = parseKafkaMessage(request);
            String payload = responseParams.getRequestParams().getPayload();
            if (payload.contains("query")) {
                String modifiedPayload = GraphQLUtils.getUtils().addGraphqlField(payload, "types", "email");
                Assert.assertTrue(modifiedPayload.contains("email"));
            }
        }
    }

    @Test
    public void testDeleteGraphqlField() throws Exception {
        HAR har = new HAR();
        List<String> requests = har.getMessages(harString, 0, 1_000_000, HttpResponseParams.Source.HAR);

        for (String request : requests) {
            HttpResponseParams responseParams = parseKafkaMessage(request);
            String payload = responseParams.getRequestParams().getPayload();
            if (payload.contains("query")) {
                String modifiedPayload = GraphQLUtils.getUtils().deleteGraphqlField(payload, "types");
                Assert.assertTrue(!modifiedPayload.contains("types"));
            }
        }
    }

    @Test
    public void testModifyGraphqlField() throws Exception {
        HAR har = new HAR();
        List<String> requests = har.getMessages(harString, 0, 1_000_000, HttpResponseParams.Source.HAR);

        for (String request : requests) {
            HttpResponseParams responseParams = parseKafkaMessage(request);
            String payload = responseParams.getRequestParams().getPayload();
            if (payload.contains("query")) {
                String modifiedPayload = GraphQLUtils.getUtils().modifyGraphqlField(payload, "types", "email");
                Assert.assertTrue(modifiedPayload.contains("email") && !modifiedPayload.contains("types") );
            }
        }
    }

    @Test
    public void testAddUniqueGraphqlField() throws Exception {
        HAR har = new HAR();
        List<String> requests = har.getMessages(harString, 0, 1_000_000, HttpResponseParams.Source.HAR);

        for (String request : requests) {
            HttpResponseParams responseParams = parseKafkaMessage(request);
            String payload = responseParams.getRequestParams().getPayload();
            if (payload.contains("query")) {
                String modifiedPayload = GraphQLUtils.getUtils().addUniqueGraphqlField(payload, "types", "email");
                Assert.assertTrue(modifiedPayload.contains("email"));
            }
        }
    }

    private static HttpResponseParams parseKafkaMessage(String message) throws Exception {

        //convert java object to JSON format
        Map<String, Object> json = gson.fromJson(message, Map.class);

        String method = (String) json.get("method");
        String url = (String) json.get("path");
        String type = (String) json.get("type");
        Map<String,List<String>> requestHeaders = OriginalHttpRequest.buildHeadersMap(json, "requestHeaders");

        String rawRequestPayload = (String) json.get("requestPayload");
        String requestPayload = HttpRequestResponseUtils.rawToJsonString(rawRequestPayload,requestHeaders);



        String apiCollectionIdStr = json.getOrDefault("akto_vxlan_id", "0").toString();
        int apiCollectionId = 0;
        if (NumberUtils.isDigits(apiCollectionIdStr)) {
            apiCollectionId = NumberUtils.toInt(apiCollectionIdStr, 0);
        }

        HttpRequestParams requestParams = new HttpRequestParams(
                method,url,type, requestHeaders, requestPayload, apiCollectionId
        );

        int statusCode = Integer.parseInt(json.get("statusCode").toString());
        String status = (String) json.get("status");
        Map<String,List<String>> responseHeaders = OriginalHttpRequest.buildHeadersMap(json, "responseHeaders");
        String payload = (String) json.get("responsePayload");
        int time = Integer.parseInt(json.get("time").toString());
        String accountId = (String) json.get("akto_account_id");
        String sourceIP = (String) json.get("ip");

        String isPendingStr = (String) json.getOrDefault("is_pending", "false");
        boolean isPending = !isPendingStr.toLowerCase().equals("false");
        String sourceStr = (String) json.getOrDefault("source", HttpResponseParams.Source.OTHER.name());
        HttpResponseParams.Source source = HttpResponseParams.Source.valueOf(sourceStr);

        return new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, message, sourceIP
        );
    }

    public static final String harString = "{\n" +
            "  \"log\": {\n" +
            "    \"entries\": [\n" +
            "      {\n" +
            "        \"request\": {\n" +
            "          \"method\": \"POST\",\n" +
            "          \"url\": \"http://localhost:8081/graphql\",\n" +
            "          \"httpVersion\": \"HTTP/1.1\",\n" +
            "          \"headers\": [\n" +
            "            {\n" +
            "              \"name\": \"Accept-Encoding\",\n" +
            "              \"value\": \"gzip, deflate, br\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Accept-Language\",\n" +
            "              \"value\": \"en-GB,en-US;q=0.9,en;q=0.8\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Connection\",\n" +
            "              \"value\": \"keep-alive\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Content-Length\",\n" +
            "              \"value\": \"472\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Host\",\n" +
            "              \"value\": \"localhost:8081\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Origin\",\n" +
            "              \"value\": \"http://localhost:8081\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Referer\",\n" +
            "              \"value\": \"http://localhost:8081/graphiql?path=/graphql\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Sec-Fetch-Dest\",\n" +
            "              \"value\": \"empty\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Sec-Fetch-Mode\",\n" +
            "              \"value\": \"cors\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Sec-Fetch-Site\",\n" +
            "              \"value\": \"same-origin\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"User-Agent\",\n" +
            "              \"value\": \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"accept\",\n" +
            "              \"value\": \"application/json, multipart/mixed\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"content-type\",\n" +
            "              \"value\": \"application/json\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"sec-ch-ua\",\n" +
            "              \"value\": \"\\\"Not_A Brand\\\";v=\\\"99\\\", \\\"Google Chrome\\\";v=\\\"109\\\", \\\"Chromium\\\";v=\\\"109\\\"\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"sec-ch-ua-mobile\",\n" +
            "              \"value\": \"?0\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"sec-ch-ua-platform\",\n" +
            "              \"value\": \"\\\"macOS\\\"\"\n" +
            "            }\n" +
            "          ],\n" +
            "          \"queryString\": [],\n" +
            "          \"headersSize\": 1650,\n" +
            "          \"bodySize\": 472,\n" +
            "          \"postData\": {\n" +
            "            \"mimeType\": \"application/json\",\n" +
            "            \"text\": \"{\\\"query\\\":\\\"{\\\\n  __schema {\\\\n    types {\\\\n      name\\\\n    }\\\\n  }\\\\n  shivamBook : bookById(id: \\\\\\\"book-1\\\\\\\") {\\\\n    id\\\\n    name\\\\n  }\\\\n shivamBook : bookById(id: 3.5) {\\\\n    id\\\\n    name\\\\n  }\\\\n shivamBook : bookById(id: true) {\\\\n    id\\\\n    name\\\\n  }\\\\n  allBooks{\\\\n    name\\\\n    author {\\\\n      firstName\\\\n    }\\\\n  }\\\\n  \\\\nbookById(id: 3) {\\\\n  id\\\\n    name\\\\n  }\\\\n  allBooks{\\\\n    name\\\\n    author {\\\\n      firstName\\\\n    }\\\\n  }  \\\\n  allAuthors {\\\\n    id\\\\n    firstName\\\\n    lastName\\\\n  }\\\\n  authorById(id: \\\\\\\"author-1\\\\\\\") {\\\\n    lastName\\\\n    bookList {\\\\n      name\\\\n    }\\\\n    \\\\n  }\\\\n}\\\"}\"\n" +
            "          }\n" +
            "        },\n" +
            "        \"response\": {\n" +
            "          \"status\": 200,\n" +
            "          \"statusText\": \"\",\n" +
            "          \"httpVersion\": \"HTTP/1.1\",\n" +
            "          \"headers\": [\n" +
            "            {\n" +
            "              \"name\": \"Connection\",\n" +
            "              \"value\": \"keep-alive\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Content-Type\",\n" +
            "              \"value\": \"application/json\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Date\",\n" +
            "              \"value\": \"Tue, 07 Feb 2023 12:56:33 GMT\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Keep-Alive\",\n" +
            "              \"value\": \"timeout=60\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Transfer-Encoding\",\n" +
            "              \"value\": \"chunked\"\n" +
            "            }\n" +
            "          ],\n" +
            "          \"cookies\": [],\n" +
            "          \"content\": {\n" +
            "            \"size\": 1224,\n" +
            "            \"mimeType\": \"application/json\",\n" +
            "            \"compression\": -13,\n" +
            "            \"text\": \"{\\\"data\\\":{\\\"__schema\\\":{\\\"types\\\":[{\\\"name\\\":\\\"Author\\\"},{\\\"name\\\":\\\"Book\\\"},{\\\"name\\\":\\\"Boolean\\\"},{\\\"name\\\":\\\"ID\\\"},{\\\"name\\\":\\\"Int\\\"},{\\\"name\\\":\\\"Mutation\\\"},{\\\"name\\\":\\\"Query\\\"},{\\\"name\\\":\\\"String\\\"},{\\\"name\\\":\\\"__Directive\\\"},{\\\"name\\\":\\\"__DirectiveLocation\\\"},{\\\"name\\\":\\\"__EnumValue\\\"},{\\\"name\\\":\\\"__Field\\\"},{\\\"name\\\":\\\"__InputValue\\\"},{\\\"name\\\":\\\"__Schema\\\"},{\\\"name\\\":\\\"__Type\\\"},{\\\"name\\\":\\\"__TypeKind\\\"}]},\\\"shivamBook\\\":{\\\"id\\\":\\\"book-1\\\",\\\"name\\\":\\\"Harry Potter and the Philosopher's Stone\\\"},\\\"allBooks\\\":[{\\\"name\\\":\\\"Harry Potter and the Philosopher's Stone\\\",\\\"author\\\":{\\\"firstName\\\":\\\"Joanne\\\"}},{\\\"name\\\":\\\"Moby Dick\\\",\\\"author\\\":{\\\"firstName\\\":\\\"Herman\\\"}},{\\\"name\\\":\\\"To Kill a Mockingbird\\\",\\\"author\\\":{\\\"firstName\\\":\\\"Harper\\\"}},{\\\"name\\\":\\\"Interview with the vampire\\\",\\\"author\\\":{\\\"firstName\\\":\\\"Anne\\\"}}],\\\"bookById\\\":{\\\"id\\\":\\\"book-2\\\",\\\"name\\\":\\\"Moby Dick\\\"},\\\"allAuthors\\\":[{\\\"id\\\":\\\"author-1\\\",\\\"firstName\\\":\\\"Joanne\\\",\\\"lastName\\\":\\\"Rowling\\\"},{\\\"id\\\":\\\"author-2\\\",\\\"firstName\\\":\\\"Herman\\\",\\\"lastName\\\":\\\"Melville\\\"},{\\\"id\\\":\\\"author-4\\\",\\\"firstName\\\":\\\"Harper\\\",\\\"lastName\\\":\\\"Lee\\\"},{\\\"id\\\":\\\"author-3\\\",\\\"firstName\\\":\\\"Anne\\\",\\\"lastName\\\":\\\"Rice\\\"},{\\\"id\\\":\\\"author-15\\\",\\\"firstName\\\":\\\"Shivam\\\",\\\"lastName\\\":\\\"Rawat\\\"},{\\\"id\\\":\\\"author-15\\\",\\\"firstName\\\":\\\"Shivam\\\",\\\"lastName\\\":\\\"Rawat\\\"}],\\\"authorById\\\":{\\\"lastName\\\":\\\"Rowling\\\",\\\"bookList\\\":[{\\\"name\\\":\\\"Harry Potter and the Philosopher's Stone\\\"}]}}}\"\n" +
            "          },\n" +
            "          \"redirectURL\": \"\",\n" +
            "          \"headersSize\": 161,\n" +
            "          \"bodySize\": 1237,\n" +
            "          \"_transferSize\": 1398,\n" +
            "          \"_error\": null\n" +
            "        },\n" +
            "        \"serverIPAddress\": \"[::1]\",\n" +
            "        \"startedDateTime\": \"2023-02-07T12:56:33.908Z\",\n" +
            "        \"time\": 13.571000017691404,\n" +
            "        \"timings\": {\n" +
            "          \"blocked\": 0.5270000217072666,\n" +
            "          \"dns\": -1,\n" +
            "          \"ssl\": -1,\n" +
            "          \"connect\": -1,\n" +
            "          \"send\": 0.089,\n" +
            "          \"wait\": 12.821000023379922,\n" +
            "          \"receive\": 0.13399997260421515,\n" +
            "          \"_blocked_queueing\": 0.40800002170726657\n" +
            "        }\n" +
            "      },\n" +
            "      {\n" +
            "        \"request\": {\n" +
            "          \"method\": \"POST\",\n" +
            "          \"url\": \"https://www.notion.so/api/v3/teV1\",\n" +
            "          \"httpVersion\": \"http/2.0\",\n" +
            "          \"headers\": [\n" +
            "            {\n" +
            "              \"name\": \"sec-ch-ua\",\n" +
            "              \"value\": \"\\\"Not_A Brand\\\";v=\\\"99\\\", \\\"Google Chrome\\\";v=\\\"109\\\", \\\"Chromium\\\";v=\\\"109\\\"\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"sec-ch-ua-platform\",\n" +
            "              \"value\": \"\\\"macOS\\\"\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Referer\",\n" +
            "              \"value\": \"https://www.notion.so/1-Health-Insurance-and-Employee-Wellness-6e949bd72cee468fa7b0312810f618a6\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"sec-ch-ua-mobile\",\n" +
            "              \"value\": \"?0\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"User-Agent\",\n" +
            "              \"value\": \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"Content-Type\",\n" +
            "              \"value\": \"application/x-www-form-urlencoded; charset=UTF-8\"\n" +
            "            }\n" +
            "          ],\n" +
            "          \"queryString\": [],\n" +
            "          \"cookies\": [],\n" +
            "          \"headersSize\": -1,\n" +
            "          \"bodySize\": 16757,\n" +
            "          \"postData\": {\n" +
            "            \"mimeType\": \"application/x-www-form-urlencoded; charset=UTF-8\",\n" +
            "            \"text\": \"checksum=dd400d834918ee905d84e222053c51dd&client=af43d4b535912f7751949bfb061d8659&e=%5B%7B%22device_id%22%3A%226795e392d94a4178b903a5550d183832%22%2C%22user_id%22%3A%221ee218f7fef247c8a655c358ca3fd589%22%2C%22timestamp%22%3A1676540063384%2C%22event_id%22%3A1693%2C%22session_id%22%3A1676540063384%2C%22event_type%22%3A%22open_team_browser%22%2C%22version_name%22%3A%222022051601%22%2C%22platform%22%3A%22Web%22%2C%22os_name%22%3A%22Chrome%22%2C%22os_version%22%3A%22109%22%2C%22device_model%22%3A%22Mac%20OS%22%2C%22device_manufacturer%22%3Anull%2C%22language%22%3A%22en-GB%22%2C%22api_properties%22%3A%7B%7D%2C%22event_properties%22%3A%7B%22from%22%3A%22sidebar_browse_team_item%22%2C%22notify_desktop%22%3Atrue%2C%22notify_email%22%3Atrue%2C%22notify_mobile%22%3Atrue%2C%22app_version%22%3A%2223.12.0.7%22%2C%22is_desktop_app_or_browser%22%3Atrue%2C%22is_desktop%22%3Afalse%2C%22is_mobile%22%3Afalse%2C%22is_tablet%22%3Afalse%2C%22is_react_native%22%3Afalse%2C%22is_ios%22%3Afalse%2C%22is_logged_in%22%3Atrue%2C%22deviceType%22%3A%22mac%22%2C%22device_type%22%3A%22web-desktop%22%2C%22device_os%22%3A%22mac%22%2C%22platform%22%3A%22browser%22%2C%22browser%22%3A%22Chrome%22%2C%22browser_version%22%3A%22109.0.0.0%22%2C%22dnt%22%3Afalse%2C%22device_id%22%3A%226795e392d94a4178b903a5550d183832%22%2C%22host%22%3A%22www.notion.so%22%2C%22route_name%22%3A%22page%22%2C%22notion_top_level_domain%22%3A%22so%22%2C%22referrer%22%3A%22none%22%2C%22accept_language_raw%22%3A%22en-GB%2Cen-US%3Bq%3D0.9%2Cen%3Bq%3D0.8%22%2C%22accept_language_preference%22%3A%22v2%2Fen-US%22%2C%22query%22%3A%22%22%2C%22space_analytics_data_type%22%3A%22space_member_data%22%2C%22subscription_tier%22%3A%22team%22%2C%22page_id%22%3A%226e949bd7-2cee-468f-a7b0-312810f618a6%22%2C%22space_id%22%3A%2212d8c890689c463e813daf75d7675cf4%22%2C%22space_is_subscribed%22%3Atrue%2C%22space_visible_total%22%3A14%2C%22space_visible_members%22%3A12%2C%22space_visible_guests%22%3A2%2C%22space_block_usage%22%3A0%2C%22space_block_usage_over_limit%22%3Afalse%2C%22space_role%22%3A%22read_and_write%22%2C%22space_block_usage_percent%22%3A0%2C%22space_timeline_view_usage%22%3A0%2C%22space_created_time%22%3A1646646871875%2C%22available_credit%22%3A500%2C%22space_domain%22%3A%22autumn-tracker-f27%22%2C%22space_email_domains%22%3A%5B%22akto.io%22%5D%2C%22space_has_paid_nonzero%22%3Afalse%2C%22space_plan_type%22%3A%22team%22%2C%22space_invite_link_enabled%22%3Atrue%2C%22space_created_by%22%3A%22c4e08e3b39164385861bae2837a0c97d%22%2C%22teams_is_enabled%22%3Atrue%2C%22page_public_role%22%3A%22not_defined%22%2C%22page_space_role%22%3A%22not_defined%22%2C%22page_all_collaborator_count%22%3A1%2C%22page_editor_collaborator_count%22%3A1%2C%22page_read_only_collaborator_count%22%3A0%2C%22page_content_count%22%3A11%2C%22page_current_user_role%22%3A%22comment_only%22%2C%22page_is_peek%22%3Afalse%2C%22page_copied_from%22%3A%22fc8ee540ec214b1082cc8ad6ffc66c0b%22%2C%22page_is_notion_template%22%3Afalse%2C%22page_is_template%22%3Afalse%2C%22page_is_prepopulated%22%3Atrue%2C%22page_is_database%22%3Afalse%2C%22page_user_has_explicit_access%22%3Afalse%2C%22preferred_locale%22%3A%22en-US%22%2C%22signup_time%22%3A1665422408361%2C%22user_session_count%22%3A45%2C%22user_id%22%3A%221ee218f7fef247c8a655c358ca3fd589%22%2C%22user_email%22%3A%22shivam%40akto.io%22%2C%22email%22%3A%22shivam%40akto.io%22%2C%22name%22%3A%22Shivam%20Rawat%22%2C%22experiments%22%3A%7B%22saml%22%3A%22control%22%2C%22student%22%3A%22preview%22%2C%22student-marketing%22%3A%22preview%22%2C%22startup-landing%22%3A%22has_startup_landing%22%2C%22password%22%3A%22control%22%2C%22auth-redirect%22%3A%22browser%22%2C%22inline-emojis%22%3A%22on%22%2C%22case-studies%22%3A%22has_case_studies%22%2C%22remote-landing%22%3A%22has_remote_landing%22%2C%22better-mobile%22%3A%22has_better_mobile%22%2C%22inline-equations%22%3A%22on%22%2C%22always-send-email%22%3A%22control%22%2C%22content-classification-block%22%3A%22has-content-classification-block%22%2C%22better-notification-triage%22%3A%22on%22%2C%22personal-onboarding%22%3A%22control%22%2C%22columns-full-width%22%3A%22on%22%2C%22pistachio%22%3A%22email%22%2C%22save-transactions-indexdb%22%3A%22use_save_api%22%2C%22save-transactions-legacy%22%3A%22use_save_api%22%2C%22save-transactions-memory%22%3A%22use_save_api%22%2C%22macadamia-nut%22%3A%22on%22%2C%22nested-db-filters%22%3A%22on%22%2C%22twitter-emoji-sprites%22%3A%22on%22%2C%22google-emoji-sprites%22%3A%22on%22%2C%22move-api%22%3A%22control%22%2C%22product-page-length%22%3A%22no-demo%22%2C%22iterative-duplicate-block%22%3A%22on%22%2C%22rename-work-nav-item%22%3A%22on%22%2C%22desktop-team-create-page%22%3A%22control%22%2C%22product-h1%22%3A%22truth%22%2C%22hazelnut%22%3A%22control%22%2C%22sign-in-with-apple%22%3A%22on%22%2C%22tiger-tamer%22%3A%22on%22%2C%22multi-account%22%3A%22has_multi_account%22%2C%22login-signup-code-strings%22%3A%22on%22%2C%22login-signup-code-strings-v2%22%3A%22on%22%2C%22better-upsells%22%3A%22control%22%2C%22tools-and-craft%22%3A%22control%22%2C%22sharing-show-ancestors%22%3A%22control%22%2C%22new-upsell-modal%22%3A%22on%22%2C%22notion-learn%22%3A%22on%22%2C%22front-events%22%3A%22on%22%2C%22product-redirect-login%22%3A%22control%22%2C%22front-api-beta%22%3A%22on%22%2C%22inline-page-creation%22%3A%22on%22%2C%22link-hover-preview%22%3A%22on%22%2C%22backlinks%22%3A%22on%22%2C%22definitely-not-timeline%22%3A%22on%22%2C%22collect-use-case%22%3A%22control%22%2C%22enterprise-contact-us-form%22%3A%22on%22%2C%22apple-silicon%22%3A%22on%22%2C%22lucy-in-the-sky-with-tab-blocks%22%3A%22control%22%2C%22collect-use-case-new-step%22%3A%22control%22%2C%22supernatural-perfect%22%3A%22on%22%2C%22visitors%22%3A%22on%22%2C%22confluence-import%22%3A%22on%22%2C%22page-customization%22%3A%22on%22%2C%22front-confluence%22%3A%22on%22%2C%22button-focus-rings%22%3A%22control%22%2C%22dark-mode-settings%22%3A%22on%22%2C%22single-name%22%3A%22on%22%2C%22ios-iap%22%3A%22control%22%2C%22use-google-webrisk-caching-server%22%3A%22on%22%2C%22front-static-site%22%3A%22on%22%2C%22collapse-client-operations%22%3A%22on%22%2C%22move-to-space%22%3A%22on%22%2C%22rc%22%3A%22on%22%2C%22silent-releases%22%3A%22on%22%2C%22sidebar-enhancements%22%3A%22on%22%2C%22desktop-sqlite%22%3A%22on%22%2C%22front-education-page%22%3A%22on%22%2C%22oauth-page-search%22%3A%22on%22%2C%22paul-mockapetris%22%3A%22on%22%2C%22mobile-action-bar%22%3A%22on%22%2C%22mobile-fixed-webview-size%22%3A%22on%22%2C%22stripe-elements%22%3A%22on%22%2C%22create-database%22%3A%22on%22%2C%22billing-interval-ui%22%3A%22control%22%2C%22recursive-sqlite-query-3%22%3A%22on%22%2C%22turbo-toggles%22%3A%22on%22%2C%22recursive-sqlite-query-2%22%3A%22control%22%2C%22invoice-limit%22%3A%22on%22%2C%22enable-report-page-button%22%3A%22on%22%2C%22recursive-sqlite-query%22%3A%22control%22%2C%22messagestore-android-v2%22%3A%22on%22%2C%22teams-settings-members%22%3A%22on%22%2C%22indent-algo-v2%22%3A%22on%22%2C%22messagestore-android%22%3A%22control%22%2C%22front-api-updates%22%3A%22on%22%2C%22front-contentful-nav-items%22%3A%22control%22%2C%22ted-nelson%22%3A%22on%22%2C%22aloha%22%3A%22control%22%2C%22refresh-enterprise-landing-page%22%3A%22on%22%2C%22update-database%22%3A%22on%22%2C%22api-update-block%22%3A%22on%22%2C%22preceding-space-slash-menu%22%3A%22control%22%2C%22nav-refresh%22%3A%22control%22%2C%22paul-mockapetris-cta%22%3A%22control%22%2C%22messagestore-ios%22%3A%22control%22%2C%22onboarding-bulk-invite%22%3A%22on%22%2C%22mobile-image-lightbox%22%3A%22on%22%2C%22onboarding-checklist%22%3A%22control%22%2C%22refresh-teams-landing-page%22%3A%22on%22%2C%22default-new-page-property-to-hide-when-empty%22%3A%22control%22%2C%22colossus%22%3A%22on%22%2C%22set-homepage%22%3A%22on%22%2C%22public-page-cta%22%3A%22control%22%2C%22temp-onboarding-app-download%22%3A%22control%22%2C%22lion-tamer%22%3A%22on%22%2C%22txnqueue-ios%22%3A%22control%22%2C%22message-port-based-bridge%22%3A%22on%22%2C%22homepage-refresh%22%3A%22control%22%2C%22media-attachment-in-comments%22%3A%22on%22%2C%22multi-select-android%22%3A%22control%22%2C%22user-session-auth%22%3A%22on%22%2C%22no-cjk-selection-formatting%22%3A%22control%22%2C%22txnqueue-android%22%3A%22control%22%2C%22hangul-day-celebration%22%3A%22control%22%2C%22web-pinch-to-zoom%22%3A%22control%22%2C%22ios-json-bridge%22%3A%22control%22%2C%22nav-refresh-split%22%3A%22download%22%2C%22workspace-user-sort-and-filter%22%3A%22on%22%2C%22pied-piper-launch%22%3A%22on%22%2C%22pied-piper%22%3A%22on%22%2C%22mobile-bottom-bar%22%3A%22control%22%2C%22messagestore-ios-debug-logging%22%3A%22control%22%2C%22no-cjk-selection-formatting-2%22%3A%22control%22%2C%22comments-v2%22%3A%22on%22%2C%22homepage-refresh-v2%22%3A%22on%22%2C%22refresh-security-landing-page%22%3A%22on%22%2C%22new-releases-page%22%3A%22on%22%2C%22startups-landing-page%22%3A%22on%22%2C%22migrations-android%22%3A%22control%22%2C%22native-web-error-modal%22%3A%22control%22%2C%22skip-collection-reset-sidebar%22%3A%22control%22%2C%22getting-started-templates%22%3A%22control%22%2C%22comments-v2-unread-state%22%3A%22on%22%2C%22coupon-link%22%3A%22on%22%2C%22onboarding-background-image%22%3A%22control%22%2C%22pricing-refresh%22%3A%22control%22%2C%22android-web-managed-tab-navigation%22%3A%22control%22%2C%22multi-select-eol%22%3A%22control%22%2C%22contact-sales-page%22%3A%22on%22%2C%22messagestore-ios-v2%22%3A%22control%22%2C%22url-embeds%22%3A%22on%22%2C%22margin-comments%22%3A%22on%22%2C%22simple-table-drop-blocks%22%3A%22control%22%2C%22multi-select-firefox%22%3A%22control%22%2C%22formatted-linked-db%22%3A%22on%22%2C%22simple-table-drop-blocks-multiple-column-insert%22%3A%22control%22%2C%22simple-tables%22%3A%22on%22%2C%22mobile-bottom-bar-android%22%3A%22on%22%2C%22nav-refresh-status%22%3A%22control%22%2C%22dbg%22%3A%22on%22%2C%22comments-v2-mobile%22%3A%22on%22%2C%22better-space-integration-settings%22%3A%22on%22%2C%22deprecate-space-level-oauthed-bots%22%3A%22on%22%2C%22public-page-cta-destination%22%3A%22signup-link%22%2C%22site-first-redirect%22%3A%22control%22%2C%22ios-native-experiment-store%22%3A%22on%22%2C%22pricing-refresh-v2%22%3A%22on%22%2C%22ios-webview-resizing-on-selection%22%3A%22on%22%2C%22txnqueue-ios-v4%22%3A%22control%22%2C%22scim_bot_v2%22%3A%22control%22%2C%22migrations-android-v2%22%3A%22on%22%2C%22messagestore-ios-single-connection%22%3A%22control%22%2C%22fast-and-furious%22%3A%22on%22%2C%22multi-select-other%22%3A%22control%22%2C%22migrations-ios%22%3A%22on%22%2C%22multi-select-eol-markers%22%3A%22control%22%2C%22ux-gift-relative-time%22%3A%22on%22%2C%22dual-tab-share-menu%22%3A%22control%22%2C%22txnqueue-ios-v2%22%3A%22control%22%2C%22txnqueue-ios-v3%22%3A%22control%22%2C%22ios-json-bridge-v2%22%3A%22on%22%2C%22messagestore-ios-v3%22%3A%22on%22%2C%22txnqueue-android-v2%22%3A%22control%22%2C%22more-toggleable-blocks%22%3A%22on%22%2C%22eoi%22%3A%22on%22%2C%22front-user-provider%22%3A%22on%22%2C%22refresh-legacy-download-menu%22%3A%22on%22%2C%22template-gallery-live-previews%22%3A%22control%22%2C%22txnqueue-android-v4%22%3A%22control%22%2C%22onboarding-emails%22%3A%22generic%22%2C%22mobile-ads-signup%22%3A%22control%22%2C%22txnqueue-ios-v5%22%3A%22control%22%2C%22txnqueue-ios-v6%22%3A%22on%22%2C%222-fast-2-furious%22%3A%22on%22%2C%22snap-resizer%22%3A%22control%22%2C%22txnqueue-android-v5%22%3A%22on%22%2C%22simple-table-colors%22%3A%22on%22%2C%22space-private-pages-no-more%22%3A%22control%22%2C%22consolidate-settings%22%3A%22control%22%2C%22mobile-uxgift-2021q4%22%3A%22on%22%2C%22invoice-redesign%22%3A%22on%22%2C%22mermaid%22%3A%22on%22%2C%22mobile-bottom-bar-ios%22%3A%22control%22%2C%22multi-select-ios%22%3A%22on%22%2C%22cookie-consent%22%3A%22on%22%2C%22multi-select-android-v2%22%3A%22on%22%2C%22multi-select%22%3A%22control%22%2C%22deeper-dark-mode%22%3A%22on%22%2C%22multi-select-electron%22%3A%22on%22%2C%22multi-select-safari%22%3A%22on%22%2C%22multi-select-chrome%22%3A%22on%22%2C%22android-background-bridge-message-parsing%22%3A%22on%22%2C%22newer-primus%22%3A%22control%22%2C%22content-only-editor%22%3A%22on%22%2C%22help-center-guides-migration%22%3A%22on%22%2C%22hex-deepnote-embeds%22%3A%22on%22%2C%22mobile-bottom-bar-ios-v2%22%3A%22control%22%2C%22enable-invalid-id-source-track%22%3A%22control%22%2C%22multi-select-new-page-view-block-layout%22%3A%22on%22%2C%22marketing-dark-mode%22%3A%22control%22%2C%22integration-settings%22%3A%22has-integration-settings%22%2C%22newer-mathjs%22%3A%22on%22%2C%22marketing-site-french-launch%22%3A%22on%22%2C%22vs2%22%3A%22control%22%2C%22business-plan%22%3A%22control%22%2C%22integration-approvals%22%3A%22on%22%2C%22teams-template%22%3A%22control%22%2C%22search-single-char-index%22%3A%22control%22%2C%22teams-sidebar-overflow%22%3A%22control%22%2C%22writing-granular-capabilities-on-blocks%22%3A%22on%22%2C%22granular-bot-capabilities%22%3A%22on%22%2C%22private-page-in-space-view%22%3A%22on%22%2C%22restrict%22%3A%22control%22%2C%22show-business-plan%22%3A%22control%22%2C%22data-loss-log-unsaved-transaction-errors%22%3A%22on%22%2C%22indexeddb-transaction-timeout-v2%22%3A%22on%22%2C%22home-android-tablet%22%3A%22control%22%2C%22chrome-99-idb-fallback-check%22%3A%22control%22%2C%22vs2b%22%3A%22control%22%2C%22emoji-apple-spritesheets%22%3A%22control%22%2C%22db-sync%22%3A%22control%22%2C%22ios-web-managed-tab-navigation%22%3A%22control%22%2C%22teams-workspace-settings%22%3A%22on%22%2C%22connection-error-indicator%22%3A%22on%22%2C%22disable-enterprise-monthly-billing%22%3A%22treatment%22%2C%22mobile-bottom-bar-ios-v3%22%3A%22control%22%2C%22alpha-api%22%3A%22has-alpha-api%22%2C%22home-android-v2%22%3A%22on%22%2C%22inverse-relations-space%22%3A%22control%22%2C%22ios-restore-purchases%22%3A%22control%22%2C%22mobile-bottom-bar-ios-v4%22%3A%22on%22%2C%22ios-restore-purchases-v2%22%3A%22on%22%2C%22ios-internal-settings%22%3A%22control%22%2C%22inverse-relations%22%3A%22on%22%2C%22home-ios%22%3A%22control%22%2C%22messagestore-ios-single-connection-v2%22%3A%22on%22%2C%22growth-exclusive-experiments%22%3A%22c%22%2C%22request-access%22%3A%22on%22%2C%22home-android%22%3A%22control%22%2C%22mobile-delete-account%22%3A%22on%22%2C%22enforce-root-redirect-on-root-only%22%3A%22on%22%2C%22sync-get-experiment%22%3A%22control%22%2C%22myspace%22%3A%22control%22%2C%22home-android-beta%22%3A%22on%22%2C%22content-reprovisioning%22%3A%22on%22%2C%22home-ios-beta%22%3A%22on%22%2C%22login-ntn-so%22%3A%22on%22%2C%22janus%22%3A%22control%22%2C%22home-ios-v2%22%3A%22on%22%2C%22home-reordering-ios%22%3A%22on%22%2C%22home-ipad%22%3A%22control%22%2C%22ios-web-bridge-profiling%22%3A%22control%22%2C%22csat%22%3A%22control%22%2C%22polyglot%22%3A%22control%22%2C%22user-data-consent%22%3A%22on%22%2C%22beta-developers-external%22%3A%22control%22%2C%22notion-mentions-to-slack%22%3A%22control%22%2C%22notion-to-slack%22%3A%22control%22%2C%22statsig-client-performance-profiler%22%3Afalse%2C%22statsig-messagestore_remote_version_update_check%22%3A%22on%22%2C%22statsig-messagestore_remove_from_queue_on_unsubscribe%22%3A%22on%22%2C%22statsig-sort_indexeddb_tasks_by_index%22%3A%22on%22%2C%22statsig-sprig%22%3Afalse%2C%22statsig-enable_sudo_mode_permission_update_ui%22%3Atrue%2C%22statsig-teams_pricing_updates%22%3Atrue%2C%22statsig-completions%22%3Afalse%2C%22statsig-completions-space%22%3Afalse%2C%22statsig-onboarding_checklist_gate%22%3Atrue%2C%22statsig-request_members_from_teamspaces_and_sidebar%22%3A%22control%22%2C%22statsig-enable_get_private_page_info_endpoint%22%3Atrue%2C%22statsig-button_block%22%3Afalse%2C%22statsig-button_property%22%3Afalse%2C%22statsig-wiki_database%22%3Afalse%2C%22statsig-meeting_notes_contextual_sharing%22%3Afalse%2C%22statsig-efficient_editable_store%22%3Afalse%2C%22statsig-link_preview_resizing%22%3A%22on%22%2C%22statsig-block_attribution%22%3Afalse%2C%22statsig-ai_onboarding%22%3Atrue%2C%22statsig-luxon_parsing%22%3Atrue%2C%22statsig-persona_collection%22%3A%22on%22%2C%22statsig-text_selection_highlight%22%3Afalse%7D%2C%22experimentStoreLoaded%22%3Atrue%2C%22current_space_view_count%22%3A1%2C%22appSource%22%3A%22client%22%2C%22windowWidth%22%3A1440%2C%22windowHeight%22%3A486%2C%22unique_event_marker%22%3Atrue%2C%22isActive%22%3Atrue%2C%22sharedUUID%22%3A%221072174a-37f3-4031-9fec-4d108730a6a6%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22uuid%22%3A%223f7e893d-4dce-4f36-811b-ebab5af5857e%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-js%22%2C%22version%22%3A%228.6.0%22%7D%2C%22sequence_number%22%3A2233%2C%22groups%22%3A%7B%22workspace%22%3A%2212d8c890689c463e813daf75d7675cf4%22%7D%2C%22group_properties%22%3A%7B%7D%2C%22user_agent%22%3A%22Mozilla%2F5.0%20%28Macintosh%3B%20Intel%20Mac%20OS%20X%2010_15_7%29%20AppleWebKit%2F537.36%20%28KHTML%2C%20like%20Gecko%29%20Chrome%2F109.0.0.0%20Safari%2F537.36%22%7D%5D&upload_time=1676540063385&v=2\",\n" +
            "            \"params\": [\n" +
            "              {\n" +
            "                \"name\": \"checksum\",\n" +
            "                \"value\": \"dd400d834918ee905d84e222053c51dd\"\n" +
            "              },\n" +
            "              {\n" +
            "                \"name\": \"client\",\n" +
            "                \"value\": \"af43d4b535912f7751949bfb061d8659\"\n" +
            "              },\n" +
            "              {\n" +
            "                \"name\": \"e\",\n" +
            "                \"value\": \"%5B%7B%22device_id%22%3A%226795e392d94a4178b903a5550d183832%22%2C%22user_id%22%3A%221ee218f7fef247c8a655c358ca3fd589%22%2C%22timestamp%22%3A1676540063384%2C%22event_id%22%3A1693%2C%22session_id%22%3A1676540063384%2C%22event_type%22%3A%22open_team_browser%22%2C%22version_name%22%3A%222022051601%22%2C%22platform%22%3A%22Web%22%2C%22os_name%22%3A%22Chrome%22%2C%22os_version%22%3A%22109%22%2C%22device_model%22%3A%22Mac%20OS%22%2C%22device_manufacturer%22%3Anull%2C%22language%22%3A%22en-GB%22%2C%22api_properties%22%3A%7B%7D%2C%22event_properties%22%3A%7B%22from%22%3A%22sidebar_browse_team_item%22%2C%22notify_desktop%22%3Atrue%2C%22notify_email%22%3Atrue%2C%22notify_mobile%22%3Atrue%2C%22app_version%22%3A%2223.12.0.7%22%2C%22is_desktop_app_or_browser%22%3Atrue%2C%22is_desktop%22%3Afalse%2C%22is_mobile%22%3Afalse%2C%22is_tablet%22%3Afalse%2C%22is_react_native%22%3Afalse%2C%22is_ios%22%3Afalse%2C%22is_logged_in%22%3Atrue%2C%22deviceType%22%3A%22mac%22%2C%22device_type%22%3A%22web-desktop%22%2C%22device_os%22%3A%22mac%22%2C%22platform%22%3A%22browser%22%2C%22browser%22%3A%22Chrome%22%2C%22browser_version%22%3A%22109.0.0.0%22%2C%22dnt%22%3Afalse%2C%22device_id%22%3A%226795e392d94a4178b903a5550d183832%22%2C%22host%22%3A%22www.notion.so%22%2C%22route_name%22%3A%22page%22%2C%22notion_top_level_domain%22%3A%22so%22%2C%22referrer%22%3A%22none%22%2C%22accept_language_raw%22%3A%22en-GB%2Cen-US%3Bq%3D0.9%2Cen%3Bq%3D0.8%22%2C%22accept_language_preference%22%3A%22v2%2Fen-US%22%2C%22query%22%3A%22%22%2C%22space_analytics_data_type%22%3A%22space_member_data%22%2C%22subscription_tier%22%3A%22team%22%2C%22page_id%22%3A%226e949bd7-2cee-468f-a7b0-312810f618a6%22%2C%22space_id%22%3A%2212d8c890689c463e813daf75d7675cf4%22%2C%22space_is_subscribed%22%3Atrue%2C%22space_visible_total%22%3A14%2C%22space_visible_members%22%3A12%2C%22space_visible_guests%22%3A2%2C%22space_block_usage%22%3A0%2C%22space_block_usage_over_limit%22%3Afalse%2C%22space_role%22%3A%22read_and_write%22%2C%22space_block_usage_percent%22%3A0%2C%22space_timeline_view_usage%22%3A0%2C%22space_created_time%22%3A1646646871875%2C%22available_credit%22%3A500%2C%22space_domain%22%3A%22autumn-tracker-f27%22%2C%22space_email_domains%22%3A%5B%22akto.io%22%5D%2C%22space_has_paid_nonzero%22%3Afalse%2C%22space_plan_type%22%3A%22team%22%2C%22space_invite_link_enabled%22%3Atrue%2C%22space_created_by%22%3A%22c4e08e3b39164385861bae2837a0c97d%22%2C%22teams_is_enabled%22%3Atrue%2C%22page_public_role%22%3A%22not_defined%22%2C%22page_space_role%22%3A%22not_defined%22%2C%22page_all_collaborator_count%22%3A1%2C%22page_editor_collaborator_count%22%3A1%2C%22page_read_only_collaborator_count%22%3A0%2C%22page_content_count%22%3A11%2C%22page_current_user_role%22%3A%22comment_only%22%2C%22page_is_peek%22%3Afalse%2C%22page_copied_from%22%3A%22fc8ee540ec214b1082cc8ad6ffc66c0b%22%2C%22page_is_notion_template%22%3Afalse%2C%22page_is_template%22%3Afalse%2C%22page_is_prepopulated%22%3Atrue%2C%22page_is_database%22%3Afalse%2C%22page_user_has_explicit_access%22%3Afalse%2C%22preferred_locale%22%3A%22en-US%22%2C%22signup_time%22%3A1665422408361%2C%22user_session_count%22%3A45%2C%22user_id%22%3A%221ee218f7fef247c8a655c358ca3fd589%22%2C%22user_email%22%3A%22shivam%40akto.io%22%2C%22email%22%3A%22shivam%40akto.io%22%2C%22name%22%3A%22Shivam%20Rawat%22%2C%22experiments%22%3A%7B%22saml%22%3A%22control%22%2C%22student%22%3A%22preview%22%2C%22student-marketing%22%3A%22preview%22%2C%22startup-landing%22%3A%22has_startup_landing%22%2C%22password%22%3A%22control%22%2C%22auth-redirect%22%3A%22browser%22%2C%22inline-emojis%22%3A%22on%22%2C%22case-studies%22%3A%22has_case_studies%22%2C%22remote-landing%22%3A%22has_remote_landing%22%2C%22better-mobile%22%3A%22has_better_mobile%22%2C%22inline-equations%22%3A%22on%22%2C%22always-send-email%22%3A%22control%22%2C%22content-classification-block%22%3A%22has-content-classification-block%22%2C%22better-notification-triage%22%3A%22on%22%2C%22personal-onboarding%22%3A%22control%22%2C%22columns-full-width%22%3A%22on%22%2C%22pistachio%22%3A%22email%22%2C%22save-transactions-indexdb%22%3A%22use_save_api%22%2C%22save-transactions-legacy%22%3A%22use_save_api%22%2C%22save-transactions-memory%22%3A%22use_save_api%22%2C%22macadamia-nut%22%3A%22on%22%2C%22nested-db-filters%22%3A%22on%22%2C%22twitter-emoji-sprites%22%3A%22on%22%2C%22google-emoji-sprites%22%3A%22on%22%2C%22move-api%22%3A%22control%22%2C%22product-page-length%22%3A%22no-demo%22%2C%22iterative-duplicate-block%22%3A%22on%22%2C%22rename-work-nav-item%22%3A%22on%22%2C%22desktop-team-create-page%22%3A%22control%22%2C%22product-h1%22%3A%22truth%22%2C%22hazelnut%22%3A%22control%22%2C%22sign-in-with-apple%22%3A%22on%22%2C%22tiger-tamer%22%3A%22on%22%2C%22multi-account%22%3A%22has_multi_account%22%2C%22login-signup-code-strings%22%3A%22on%22%2C%22login-signup-code-strings-v2%22%3A%22on%22%2C%22better-upsells%22%3A%22control%22%2C%22tools-and-craft%22%3A%22control%22%2C%22sharing-show-ancestors%22%3A%22control%22%2C%22new-upsell-modal%22%3A%22on%22%2C%22notion-learn%22%3A%22on%22%2C%22front-events%22%3A%22on%22%2C%22product-redirect-login%22%3A%22control%22%2C%22front-api-beta%22%3A%22on%22%2C%22inline-page-creation%22%3A%22on%22%2C%22link-hover-preview%22%3A%22on%22%2C%22backlinks%22%3A%22on%22%2C%22definitely-not-timeline%22%3A%22on%22%2C%22collect-use-case%22%3A%22control%22%2C%22enterprise-contact-us-form%22%3A%22on%22%2C%22apple-silicon%22%3A%22on%22%2C%22lucy-in-the-sky-with-tab-blocks%22%3A%22control%22%2C%22collect-use-case-new-step%22%3A%22control%22%2C%22supernatural-perfect%22%3A%22on%22%2C%22visitors%22%3A%22on%22%2C%22confluence-import%22%3A%22on%22%2C%22page-customization%22%3A%22on%22%2C%22front-confluence%22%3A%22on%22%2C%22button-focus-rings%22%3A%22control%22%2C%22dark-mode-settings%22%3A%22on%22%2C%22single-name%22%3A%22on%22%2C%22ios-iap%22%3A%22control%22%2C%22use-google-webrisk-caching-server%22%3A%22on%22%2C%22front-static-site%22%3A%22on%22%2C%22collapse-client-operations%22%3A%22on%22%2C%22move-to-space%22%3A%22on%22%2C%22rc%22%3A%22on%22%2C%22silent-releases%22%3A%22on%22%2C%22sidebar-enhancements%22%3A%22on%22%2C%22desktop-sqlite%22%3A%22on%22%2C%22front-education-page%22%3A%22on%22%2C%22oauth-page-search%22%3A%22on%22%2C%22paul-mockapetris%22%3A%22on%22%2C%22mobile-action-bar%22%3A%22on%22%2C%22mobile-fixed-webview-size%22%3A%22on%22%2C%22stripe-elements%22%3A%22on%22%2C%22create-database%22%3A%22on%22%2C%22billing-interval-ui%22%3A%22control%22%2C%22recursive-sqlite-query-3%22%3A%22on%22%2C%22turbo-toggles%22%3A%22on%22%2C%22recursive-sqlite-query-2%22%3A%22control%22%2C%22invoice-limit%22%3A%22on%22%2C%22enable-report-page-button%22%3A%22on%22%2C%22recursive-sqlite-query%22%3A%22control%22%2C%22messagestore-android-v2%22%3A%22on%22%2C%22teams-settings-members%22%3A%22on%22%2C%22indent-algo-v2%22%3A%22on%22%2C%22messagestore-android%22%3A%22control%22%2C%22front-api-updates%22%3A%22on%22%2C%22front-contentful-nav-items%22%3A%22control%22%2C%22ted-nelson%22%3A%22on%22%2C%22aloha%22%3A%22control%22%2C%22refresh-enterprise-landing-page%22%3A%22on%22%2C%22update-database%22%3A%22on%22%2C%22api-update-block%22%3A%22on%22%2C%22preceding-space-slash-menu%22%3A%22control%22%2C%22nav-refresh%22%3A%22control%22%2C%22paul-mockapetris-cta%22%3A%22control%22%2C%22messagestore-ios%22%3A%22control%22%2C%22onboarding-bulk-invite%22%3A%22on%22%2C%22mobile-image-lightbox%22%3A%22on%22%2C%22onboarding-checklist%22%3A%22control%22%2C%22refresh-teams-landing-page%22%3A%22on%22%2C%22default-new-page-property-to-hide-when-empty%22%3A%22control%22%2C%22colossus%22%3A%22on%22%2C%22set-homepage%22%3A%22on%22%2C%22public-page-cta%22%3A%22control%22%2C%22temp-onboarding-app-download%22%3A%22control%22%2C%22lion-tamer%22%3A%22on%22%2C%22txnqueue-ios%22%3A%22control%22%2C%22message-port-based-bridge%22%3A%22on%22%2C%22homepage-refresh%22%3A%22control%22%2C%22media-attachment-in-comments%22%3A%22on%22%2C%22multi-select-android%22%3A%22control%22%2C%22user-session-auth%22%3A%22on%22%2C%22no-cjk-selection-formatting%22%3A%22control%22%2C%22txnqueue-android%22%3A%22control%22%2C%22hangul-day-celebration%22%3A%22control%22%2C%22web-pinch-to-zoom%22%3A%22control%22%2C%22ios-json-bridge%22%3A%22control%22%2C%22nav-refresh-split%22%3A%22download%22%2C%22workspace-user-sort-and-filter%22%3A%22on%22%2C%22pied-piper-launch%22%3A%22on%22%2C%22pied-piper%22%3A%22on%22%2C%22mobile-bottom-bar%22%3A%22control%22%2C%22messagestore-ios-debug-logging%22%3A%22control%22%2C%22no-cjk-selection-formatting-2%22%3A%22control%22%2C%22comments-v2%22%3A%22on%22%2C%22homepage-refresh-v2%22%3A%22on%22%2C%22refresh-security-landing-page%22%3A%22on%22%2C%22new-releases-page%22%3A%22on%22%2C%22startups-landing-page%22%3A%22on%22%2C%22migrations-android%22%3A%22control%22%2C%22native-web-error-modal%22%3A%22control%22%2C%22skip-collection-reset-sidebar%22%3A%22control%22%2C%22getting-started-templates%22%3A%22control%22%2C%22comments-v2-unread-state%22%3A%22on%22%2C%22coupon-link%22%3A%22on%22%2C%22onboarding-background-image%22%3A%22control%22%2C%22pricing-refresh%22%3A%22control%22%2C%22android-web-managed-tab-navigation%22%3A%22control%22%2C%22multi-select-eol%22%3A%22control%22%2C%22contact-sales-page%22%3A%22on%22%2C%22messagestore-ios-v2%22%3A%22control%22%2C%22url-embeds%22%3A%22on%22%2C%22margin-comments%22%3A%22on%22%2C%22simple-table-drop-blocks%22%3A%22control%22%2C%22multi-select-firefox%22%3A%22control%22%2C%22formatted-linked-db%22%3A%22on%22%2C%22simple-table-drop-blocks-multiple-column-insert%22%3A%22control%22%2C%22simple-tables%22%3A%22on%22%2C%22mobile-bottom-bar-android%22%3A%22on%22%2C%22nav-refresh-status%22%3A%22control%22%2C%22dbg%22%3A%22on%22%2C%22comments-v2-mobile%22%3A%22on%22%2C%22better-space-integration-settings%22%3A%22on%22%2C%22deprecate-space-level-oauthed-bots%22%3A%22on%22%2C%22public-page-cta-destination%22%3A%22signup-link%22%2C%22site-first-redirect%22%3A%22control%22%2C%22ios-native-experiment-store%22%3A%22on%22%2C%22pricing-refresh-v2%22%3A%22on%22%2C%22ios-webview-resizing-on-selection%22%3A%22on%22%2C%22txnqueue-ios-v4%22%3A%22control%22%2C%22scim_bot_v2%22%3A%22control%22%2C%22migrations-android-v2%22%3A%22on%22%2C%22messagestore-ios-single-connection%22%3A%22control%22%2C%22fast-and-furious%22%3A%22on%22%2C%22multi-select-other%22%3A%22control%22%2C%22migrations-ios%22%3A%22on%22%2C%22multi-select-eol-markers%22%3A%22control%22%2C%22ux-gift-relative-time%22%3A%22on%22%2C%22dual-tab-share-menu%22%3A%22control%22%2C%22txnqueue-ios-v2%22%3A%22control%22%2C%22txnqueue-ios-v3%22%3A%22control%22%2C%22ios-json-bridge-v2%22%3A%22on%22%2C%22messagestore-ios-v3%22%3A%22on%22%2C%22txnqueue-android-v2%22%3A%22control%22%2C%22more-toggleable-blocks%22%3A%22on%22%2C%22eoi%22%3A%22on%22%2C%22front-user-provider%22%3A%22on%22%2C%22refresh-legacy-download-menu%22%3A%22on%22%2C%22template-gallery-live-previews%22%3A%22control%22%2C%22txnqueue-android-v4%22%3A%22control%22%2C%22onboarding-emails%22%3A%22generic%22%2C%22mobile-ads-signup%22%3A%22control%22%2C%22txnqueue-ios-v5%22%3A%22control%22%2C%22txnqueue-ios-v6%22%3A%22on%22%2C%222-fast-2-furious%22%3A%22on%22%2C%22snap-resizer%22%3A%22control%22%2C%22txnqueue-android-v5%22%3A%22on%22%2C%22simple-table-colors%22%3A%22on%22%2C%22space-private-pages-no-more%22%3A%22control%22%2C%22consolidate-settings%22%3A%22control%22%2C%22mobile-uxgift-2021q4%22%3A%22on%22%2C%22invoice-redesign%22%3A%22on%22%2C%22mermaid%22%3A%22on%22%2C%22mobile-bottom-bar-ios%22%3A%22control%22%2C%22multi-select-ios%22%3A%22on%22%2C%22cookie-consent%22%3A%22on%22%2C%22multi-select-android-v2%22%3A%22on%22%2C%22multi-select%22%3A%22control%22%2C%22deeper-dark-mode%22%3A%22on%22%2C%22multi-select-electron%22%3A%22on%22%2C%22multi-select-safari%22%3A%22on%22%2C%22multi-select-chrome%22%3A%22on%22%2C%22android-background-bridge-message-parsing%22%3A%22on%22%2C%22newer-primus%22%3A%22control%22%2C%22content-only-editor%22%3A%22on%22%2C%22help-center-guides-migration%22%3A%22on%22%2C%22hex-deepnote-embeds%22%3A%22on%22%2C%22mobile-bottom-bar-ios-v2%22%3A%22control%22%2C%22enable-invalid-id-source-track%22%3A%22control%22%2C%22multi-select-new-page-view-block-layout%22%3A%22on%22%2C%22marketing-dark-mode%22%3A%22control%22%2C%22integration-settings%22%3A%22has-integration-settings%22%2C%22newer-mathjs%22%3A%22on%22%2C%22marketing-site-french-launch%22%3A%22on%22%2C%22vs2%22%3A%22control%22%2C%22business-plan%22%3A%22control%22%2C%22integration-approvals%22%3A%22on%22%2C%22teams-template%22%3A%22control%22%2C%22search-single-char-index%22%3A%22control%22%2C%22teams-sidebar-overflow%22%3A%22control%22%2C%22writing-granular-capabilities-on-blocks%22%3A%22on%22%2C%22granular-bot-capabilities%22%3A%22on%22%2C%22private-page-in-space-view%22%3A%22on%22%2C%22restrict%22%3A%22control%22%2C%22show-business-plan%22%3A%22control%22%2C%22data-loss-log-unsaved-transaction-errors%22%3A%22on%22%2C%22indexeddb-transaction-timeout-v2%22%3A%22on%22%2C%22home-android-tablet%22%3A%22control%22%2C%22chrome-99-idb-fallback-check%22%3A%22control%22%2C%22vs2b%22%3A%22control%22%2C%22emoji-apple-spritesheets%22%3A%22control%22%2C%22db-sync%22%3A%22control%22%2C%22ios-web-managed-tab-navigation%22%3A%22control%22%2C%22teams-workspace-settings%22%3A%22on%22%2C%22connection-error-indicator%22%3A%22on%22%2C%22disable-enterprise-monthly-billing%22%3A%22treatment%22%2C%22mobile-bottom-bar-ios-v3%22%3A%22control%22%2C%22alpha-api%22%3A%22has-alpha-api%22%2C%22home-android-v2%22%3A%22on%22%2C%22inverse-relations-space%22%3A%22control%22%2C%22ios-restore-purchases%22%3A%22control%22%2C%22mobile-bottom-bar-ios-v4%22%3A%22on%22%2C%22ios-restore-purchases-v2%22%3A%22on%22%2C%22ios-internal-settings%22%3A%22control%22%2C%22inverse-relations%22%3A%22on%22%2C%22home-ios%22%3A%22control%22%2C%22messagestore-ios-single-connection-v2%22%3A%22on%22%2C%22growth-exclusive-experiments%22%3A%22c%22%2C%22request-access%22%3A%22on%22%2C%22home-android%22%3A%22control%22%2C%22mobile-delete-account%22%3A%22on%22%2C%22enforce-root-redirect-on-root-only%22%3A%22on%22%2C%22sync-get-experiment%22%3A%22control%22%2C%22myspace%22%3A%22control%22%2C%22home-android-beta%22%3A%22on%22%2C%22content-reprovisioning%22%3A%22on%22%2C%22home-ios-beta%22%3A%22on%22%2C%22login-ntn-so%22%3A%22on%22%2C%22janus%22%3A%22control%22%2C%22home-ios-v2%22%3A%22on%22%2C%22home-reordering-ios%22%3A%22on%22%2C%22home-ipad%22%3A%22control%22%2C%22ios-web-bridge-profiling%22%3A%22control%22%2C%22csat%22%3A%22control%22%2C%22polyglot%22%3A%22control%22%2C%22user-data-consent%22%3A%22on%22%2C%22beta-developers-external%22%3A%22control%22%2C%22notion-mentions-to-slack%22%3A%22control%22%2C%22notion-to-slack%22%3A%22control%22%2C%22statsig-client-performance-profiler%22%3Afalse%2C%22statsig-messagestore_remote_version_update_check%22%3A%22on%22%2C%22statsig-messagestore_remove_from_queue_on_unsubscribe%22%3A%22on%22%2C%22statsig-sort_indexeddb_tasks_by_index%22%3A%22on%22%2C%22statsig-sprig%22%3Afalse%2C%22statsig-enable_sudo_mode_permission_update_ui%22%3Atrue%2C%22statsig-teams_pricing_updates%22%3Atrue%2C%22statsig-completions%22%3Afalse%2C%22statsig-completions-space%22%3Afalse%2C%22statsig-onboarding_checklist_gate%22%3Atrue%2C%22statsig-request_members_from_teamspaces_and_sidebar%22%3A%22control%22%2C%22statsig-enable_get_private_page_info_endpoint%22%3Atrue%2C%22statsig-button_block%22%3Afalse%2C%22statsig-button_property%22%3Afalse%2C%22statsig-wiki_database%22%3Afalse%2C%22statsig-meeting_notes_contextual_sharing%22%3Afalse%2C%22statsig-efficient_editable_store%22%3Afalse%2C%22statsig-link_preview_resizing%22%3A%22on%22%2C%22statsig-block_attribution%22%3Afalse%2C%22statsig-ai_onboarding%22%3Atrue%2C%22statsig-luxon_parsing%22%3Atrue%2C%22statsig-persona_collection%22%3A%22on%22%2C%22statsig-text_selection_highlight%22%3Afalse%7D%2C%22experimentStoreLoaded%22%3Atrue%2C%22current_space_view_count%22%3A1%2C%22appSource%22%3A%22client%22%2C%22windowWidth%22%3A1440%2C%22windowHeight%22%3A486%2C%22unique_event_marker%22%3Atrue%2C%22isActive%22%3Atrue%2C%22sharedUUID%22%3A%221072174a-37f3-4031-9fec-4d108730a6a6%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22uuid%22%3A%223f7e893d-4dce-4f36-811b-ebab5af5857e%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-js%22%2C%22version%22%3A%228.6.0%22%7D%2C%22sequence_number%22%3A2233%2C%22groups%22%3A%7B%22workspace%22%3A%2212d8c890689c463e813daf75d7675cf4%22%7D%2C%22group_properties%22%3A%7B%7D%2C%22user_agent%22%3A%22Mozilla%2F5.0%20%28Macintosh%3B%20Intel%20Mac%20OS%20X%2010_15_7%29%20AppleWebKit%2F537.36%20%28KHTML%2C%20like%20Gecko%29%20Chrome%2F109.0.0.0%20Safari%2F537.36%22%7D%5D\"\n" +
            "              },\n" +
            "              {\n" +
            "                \"name\": \"upload_time\",\n" +
            "                \"value\": \"1676540063385\"\n" +
            "              },\n" +
            "              {\n" +
            "                \"name\": \"v\",\n" +
            "                \"value\": \"2\"\n" +
            "              }\n" +
            "            ]\n" +
            "          }\n" +
            "        },\n" +
            "        \"response\": {\n" +
            "          \"status\": 200,\n" +
            "          \"statusText\": \"\",\n" +
            "          \"httpVersion\": \"http/2.0\",\n" +
            "          \"headers\": [\n" +
            "            {\n" +
            "              \"name\": \"date\",\n" +
            "              \"value\": \"Thu, 16 Feb 2023 09:34:24 GMT\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"content-security-policy\",\n" +
            "              \"value\": \"default-src 'none'\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"x-content-type-options\",\n" +
            "              \"value\": \"nosniff\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"strict-transport-security\",\n" +
            "              \"value\": \"max-age=5184000; includeSubDomains\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"cf-cache-status\",\n" +
            "              \"value\": \"DYNAMIC\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"x-permitted-cross-domain-policies\",\n" +
            "              \"value\": \"none\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"x-dns-prefetch-control\",\n" +
            "              \"value\": \"off\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"content-length\",\n" +
            "              \"value\": \"7\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"x-xss-protection\",\n" +
            "              \"value\": \"0\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"referrer-policy\",\n" +
            "              \"value\": \"strict-origin-when-cross-origin\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"server\",\n" +
            "              \"value\": \"cloudflare\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"etag\",\n" +
            "              \"value\": \"W/\\\"7-U6VofLJtxB8qtAM+l+E63v03QNY\\\"\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"x-download-options\",\n" +
            "              \"value\": \"noopen\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"x-frame-options\",\n" +
            "              \"value\": \"SAMEORIGIN\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"vary\",\n" +
            "              \"value\": \"Accept-Encoding\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"content-type\",\n" +
            "              \"value\": \"text/html; charset=utf-8\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"access-control-allow-origin\",\n" +
            "              \"value\": \"*\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"cf-ray\",\n" +
            "              \"value\": \"79a54989ad0de338-DEL\"\n" +
            "            }\n" +
            "          ],\n" +
            "          \"cookies\": [],\n" +
            "          \"content\": {\n" +
            "            \"size\": 7,\n" +
            "            \"mimeType\": \"text/html\",\n" +
            "            \"text\": \"success\"\n" +
            "          },\n" +
            "          \"redirectURL\": \"\",\n" +
            "          \"headersSize\": -1,\n" +
            "          \"bodySize\": -1,\n" +
            "          \"_transferSize\": 611,\n" +
            "          \"_error\": null\n" +
            "        },\n" +
            "        \"startedDateTime\": \"2023-02-16T09:34:23.388Z\",\n" +
            "        \"time\": 1277.2410000125915,\n" +
            "        \"timings\": {\n" +
            "          \"blocked\": 2.576000021953136,\n" +
            "          \"dns\": 130.34599999999998,\n" +
            "          \"ssl\": 137.733,\n" +
            "          \"connect\": 400.527,\n" +
            "          \"send\": 0.7280000000000086,\n" +
            "          \"wait\": 742.6230000086912,\n" +
            "          \"receive\": 0.4409999819472432,\n" +
            "          \"_blocked_queueing\": 2.4720000219531357\n" +
            "        }\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}";
}
