package com.akto.action;

import com.akto.DaoInit;
import com.akto.MongoBasedTest;
import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomDataType;
import com.akto.dto.IgnoreData;
import com.akto.dto.User;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

public class TestCustomDataTypeAction extends MongoBasedTest {

    public static void main1(String[] args) throws Exception {
        DaoInit.init(new ConnectionString("mongodb://172.18.0.2:27017/admini"));
        Context.accountId.set(1_000_000);
        ArrayList<WriteModel<SampleData>> bulkUpdates = new ArrayList<>();
        String payload = "{\"method\":\"GET\",\"requestPayload\":\"{\\\"includeCustom\\\":\\\"false\\\",\\\"resourceType\\\":\\\"Event\\\"}\",\"responsePayload\":\"{\\\"status\\\": \\\"ok\\\", \\\"results\\\": [{\\\"id\\\": 0, \\\"name\\\": \\\"$browser\\\", \\\"displayName\\\": \\\"Browser\\\", \\\"exampleValue\\\": \\\"Chrome, Safari\\\", \\\"description\\\": \\\"The browser being used at the time the event was fired. (Not versioned)\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$browser_version\\\", \\\"displayName\\\": \\\"Browser Version\\\", \\\"exampleValue\\\": \\\"11, 11.1\\\", \\\"description\\\": \\\"The version number of the browser being used at the time the event was fired.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$current_url\\\", \\\"displayName\\\": \\\"Current URL\\\", \\\"exampleValue\\\": \\\"https://mixpanel.com/example/\\\", \\\"description\\\": \\\"The full URL of the webpage on which the event is triggered.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$device_id\\\", \\\"displayName\\\": \\\"Device ID\\\", \\\"exampleValue\\\": \\\"\\\", \\\"description\\\": \\\"A default Mixpanel property to track the device as determined by Mixpanel's client-side SDKs. Events tracked from the same device and same user but different applications may not have the same $device_id. The reset method will change the device_id.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$initial_referrer\\\", \\\"displayName\\\": \\\"Initial Referrer\\\", \\\"exampleValue\\\": \\\"$direct, https://www.google.com/\\\", \\\"description\\\": \\\"The referring URL at first arrival.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$initial_referring_domain\\\", \\\"displayName\\\": \\\"Initial Referring Domain\\\", \\\"exampleValue\\\": \\\"$direct, www.google.com\\\", \\\"description\\\": \\\"The referring domain at first arrival.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$insert_id\\\", \\\"displayName\\\": \\\"Insert ID\\\", \\\"exampleValue\\\": \\\"5d958f87-542d-4c10-9422-0ed75893dc81\\\", \\\"description\\\": \\\"A random 36 character string of hyphenated alphanumeric characters that is unique to an event. Used to deduplicate events.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": true, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$lib_version\\\", \\\"displayName\\\": \\\"Library Version\\\", \\\"exampleValue\\\": \\\"5.4.1\\\", \\\"description\\\": \\\"Version of the Mixpanel library used to send this data.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$mp_api_endpoint\\\", \\\"displayName\\\": \\\"API Endpoint\\\", \\\"exampleValue\\\": \\\"api.mixpanel.com\\\", \\\"description\\\": \\\"Internal Mixpanel property to record the API endpoint the data was sent to\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": true, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$os\\\", \\\"displayName\\\": \\\"OS\\\", \\\"exampleValue\\\": \\\"Mac OS X, Windows\\\", \\\"description\\\": \\\"The operating system of the event sender.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$screen_height\\\", \\\"displayName\\\": \\\"Screen Height\\\", \\\"exampleValue\\\": \\\"960\\\", \\\"description\\\": \\\"The height of a device's screen.\\\", \\\"type\\\": \\\"number\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$screen_width\\\", \\\"displayName\\\": \\\"Screen Width\\\", \\\"exampleValue\\\": \\\"360\\\", \\\"description\\\": \\\"The width of the screen of the device.\\\", \\\"type\\\": \\\"number\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"mp_country_code\\\", \\\"displayName\\\": \\\"Country\\\", \\\"exampleValue\\\": \\\"United States, Canada\\\", \\\"description\\\": \\\"The country of the event sender, parsed from IP.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"mp_lib\\\", \\\"displayName\\\": \\\"Mixpanel Library\\\", \\\"exampleValue\\\": \\\"Web, Android, iPhone\\\", \\\"description\\\": \\\"The name of the Mixpanel Library that sent the event.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"mp_processing_time_ms\\\", \\\"displayName\\\": \\\"Time Processed (UTC)\\\", \\\"exampleValue\\\": \\\"1543546310165\\\", \\\"description\\\": \\\"Time in milliseconds in Unix Time Stamp when an event was ingested by Mixpanel. While the \\\\\\\"Time\\\\\\\" property in Mixpanel is set in the time zone specified by the project, mp_processing_time_ms will always be in UTC (GMT) time.\\\", \\\"type\\\": \\\"number\\\", \\\"hidden\\\": true, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$event_name\\\", \\\"displayName\\\": \\\"Event Name\\\", \\\"exampleValue\\\": \\\"Song Played\\\", \\\"description\\\": \\\"Name of the event\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$region\\\", \\\"displayName\\\": \\\"Region\\\", \\\"exampleValue\\\": \\\"California, New York\\\", \\\"description\\\": \\\"The region (state or province) of the event sender that is parsed from IP.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3746, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$city\\\", \\\"displayName\\\": \\\"City\\\", \\\"exampleValue\\\": \\\"San Francisco, Los Angeles\\\", \\\"description\\\": \\\"The city of the event sender, parsed from IP.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3740, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 3}, {\\\"id\\\": 0, \\\"name\\\": \\\"$referrer\\\", \\\"displayName\\\": \\\"Referrer\\\", \\\"exampleValue\\\": \\\"https://mixpanel.com/example, https://mixpanel.com/example/segmentation\\\", \\\"description\\\": \\\"The referring URL, including your own domain.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 2443, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$referring_domain\\\", \\\"displayName\\\": \\\"Referring Domain\\\", \\\"exampleValue\\\": \\\"www.google.com, www.mixpanel.com\\\", \\\"description\\\": \\\"The referring domain, including your own domain.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 2443, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$search_engine\\\", \\\"displayName\\\": \\\"Search Engine\\\", \\\"exampleValue\\\": \\\"Google, Yahoo\\\", \\\"description\\\": \\\"The search engine that was used to arrive at your domain.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 5, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$device\\\", \\\"displayName\\\": \\\"Device\\\", \\\"exampleValue\\\": \\\"Android, iPhone\\\", \\\"description\\\": \\\"The name of the event sender's device, if they're on mobile web.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 1, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$duration_s\\\", \\\"displayName\\\": \\\"Session Duration (Seconds)\\\", \\\"exampleValue\\\": \\\"413\\\", \\\"description\\\": \\\"The duration between Session Start and Session End events in seconds.\\\", \\\"type\\\": \\\"number\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$event_count\\\", \\\"displayName\\\": \\\"Session Event Count\\\", \\\"exampleValue\\\": \\\"103\\\", \\\"description\\\": \\\"The number of events during a session. This does not include Excluded Events and Hidden Events in Lexicon.\\\", \\\"type\\\": \\\"number\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$origin_end\\\", \\\"displayName\\\": \\\"Session End Event Name\\\", \\\"exampleValue\\\": \\\"Sign Out, Complete Purchase,\\\", \\\"description\\\": \\\"The original event name that triggered Session End event.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$origin_start\\\", \\\"displayName\\\": \\\"Session Start Event Name\\\", \\\"exampleValue\\\": \\\"Sign In, View Pricing, Landing Page\\\", \\\"description\\\": \\\"The original event name that triggered Session Start event.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$distinct_id\\\", \\\"displayName\\\": \\\"Distinct ID\\\", \\\"exampleValue\\\": \\\"166424e227e7fb-0ff2f469b2f1fc-2d6a4f35\\\", \\\"description\\\": \\\"The way to uniquely identify your users. You can set this to any value using the identify method. Some Mixpanel libraries assign a random value by default.\\\", \\\"type\\\": \\\"string\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isSensitiveBlacklisted\\\": true, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}, {\\\"id\\\": 0, \\\"name\\\": \\\"$time\\\", \\\"displayName\\\": \\\"Time\\\", \\\"exampleValue\\\": \\\"2011-01T00:00:00Z\\\", \\\"description\\\": \\\"A unix time epoch that is used to determine the time of an event. If no time property is provided, we will use the time the event arrives at our servers.\\\", \\\"type\\\": \\\"datetime\\\", \\\"hidden\\\": false, \\\"dropped\\\": false, \\\"merged\\\": false, \\\"resourceType\\\": \\\"Event\\\", \\\"mergedPropertyId\\\": 0, \\\"status\\\": \\\"live\\\", \\\"sensitive\\\": false, \\\"required\\\": false, \\\"entityDefinitionId\\\": 0, \\\"isMixpanelDefinition\\\": true, \\\"count\\\": 3830, \\\"hasArbData\\\": true, \\\"uiUniqueQueryCount\\\": 0, \\\"uiQueryCount\\\": 0, \\\"apiQueryCount\\\": 0}]}\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"1648918173\",\"path\":\"https://mixpanel.com/api/app/workspaces/3138707/data-definitions/properties\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"mp__origin=invite; mp__origin_referrer=\\\\\\\"https://mixpanel.com/register/?company=Akto&name=&email=avneesh%40akto.io&nonce=c49093b12c5e7141a5ff13cca87c1a46c05fdf84a1d441d5c8f4e5a8fed2b995&next=%2Forganizations%2F2162460%2Faccept-invite%2F1edf5694b07504adc6f5fc8799d00cc790183d51d6b6d965fac00a93be70c248%2F1639393348%2Fc49093b12c5e7141a5ff13cca87c1a46c05fdf84a1d441d5c8f4e5a8fed2b995&invited=1&inviter=Ankita+Gupta\\\\\\\"; mixpanel_utm_medium=direct; mixpanel_utm_source=mixpanel; mixpanel_utm_campaign=https%3A//mixpanel.com/register/%3Fcompany%3DAkto%26name%3D%26email%3Davneesh%2540akto.io%26nonce%3Dc49093b12c5e7141a5ff13cca87c1a46c05fdf84a1d441d5c8f4e5a8fed2b995%26next%3D%252Forganizations%252F2162460%252Faccept-invite%252F1edf5694b07504adc6f5fc8799d00cc790183d51d6b6d965fac00a93be7; mixpanel_utm_content=https%3A//mixpanel.com; mixpanel_utm_term=na; g_state={\\\\\\\"i_l\\\\\\\":0}; mp_user=\\\\\\\"eyJpZCI6MjgzNTUwMSwibmFtZSI6IkF2bmVlc2ggSG90YSIsImVtYWlsIjoiYXZuZWVzaEBha3RvLmlvIn0=\\\\\\\"; csrftoken=k66J92L57AxjLwUNdIKrRFkscXTORuDCQ0DhgFN1hghPQfcf9Q0xf0Xu9naR9l40; sessionid=wo4icgm89xcwsjrrvmc8plsl424787uf; mp_distinct_id=2835501; mp_persistence=%7B%22project%22%3A2599843%2C%22workspace%22%3A3138707%2C%22report_path%22%3A%22dashboards%22%7D; mp_account_id=2811172\\\",\\\"Accept\\\":\\\"*/*\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:96.0) Gecko/20100101 Firefox/96.0\\\",\\\"Referer\\\":\\\"https://mixpanel.com/project/2599843/view/3138707/app/dashboards\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"mixpanel.com\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Pragma\\\":\\\"no-cache\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"Authorization\\\":\\\"Session\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Cache-Control\\\":\\\"no-cache\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Mon, 28 Feb 2022 06:37:02 GMT\\\",\\\"server\\\":\\\"nginx\\\",\\\"set-cookie\\\":\\\"mp_user=\\\\\\\"eyJpZCI6MjgzNTUwMSwibmFtZSI6IkF2bmVlc2ggSG90YSIsImVtYWlsIjoiYXZuZWVzaEBha3RvLmlvIn0=\\\\\\\"; expires=Mon, 14 Mar 2022 06:37:02 GMT; Max-Age=1209600; Path=/\\\",\\\"vary\\\":\\\"Authorization, Cookie\\\",\\\"x-frame-options\\\":\\\"SAMEORIGIN\\\",\\\"content-encoding\\\":\\\"gzip\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"x-server-elapsed\\\":\\\"5.420\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"strict-transport-security\\\":\\\"max-age=63072000; includeSubDomains; preload\\\",\\\"x-mp-request-id\\\":\\\"fc5639ae-93ff-4697-85ca-2398d0b59b08\\\"}\",\"time\":\"1646030216\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";


        for (int i=0; i < 10000; i++) {
            SampleData sampleData = new SampleData(
                    new Key(i,"url", URLMethods.Method.GET, 200, 0,0), Arrays.asList(payload, payload, payload, payload, payload, payload, payload, payload, payload, payload)
            );
            bulkUpdates.add(new InsertOneModel<>(sampleData));
        }

        SampleDataDao.instance.getMCollection().bulkWrite(bulkUpdates);
    }

    private CustomDataTypeAction generateCustomDataTypeAction(
            String name, String operator, List<CustomDataTypeAction.ConditionFromUser> keyConditions,
            List<CustomDataTypeAction.ConditionFromUser> valueConditions
    ) {
        CustomDataTypeAction customDataTypeAction = new CustomDataTypeAction();
        Map<String, Object> session = new HashMap<>();
        session.put("user", new User());
        customDataTypeAction.setSession(session);
        customDataTypeAction.setCreateNew(true);

        customDataTypeAction.setName(name);
        customDataTypeAction.setSensitiveAlways(true);
        customDataTypeAction.setOperator(operator);

        customDataTypeAction.setKeyOperator("OR");
        customDataTypeAction.setKeyConditionFromUsers(keyConditions);

        customDataTypeAction.setValueOperator("AND");
        customDataTypeAction.setValueConditionFromUsers(valueConditions);

        return customDataTypeAction;
    }


    @Test
    public void testGenerateCustomDataTypeHappy() {
        Context.accountId.set(1_000_000);
        CustomDataTypeDao.instance.getMCollection().drop();
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("value", "wencludeCustom");
        CustomDataTypeAction customDataTypeAction = generateCustomDataTypeAction(
                "test_1", "AND",
                Arrays.asList(new CustomDataTypeAction.ConditionFromUser(Predicate.Type.STARTS_WITH, valueMap), new CustomDataTypeAction.ConditionFromUser(Predicate.Type.ENDS_WITH, valueMap)),
                Collections.singletonList(new CustomDataTypeAction.ConditionFromUser(Predicate.Type.REGEX, valueMap)));

        customDataTypeAction.execute();
        assertNotNull(customDataTypeAction.getCustomDataType());

        CustomDataType customDataType = CustomDataTypeDao.instance.findOne(Filters.eq(CustomDataType.NAME, "TEST_1"));

        assertEquals(customDataType.getCreatorId(), 0);
        assertEquals(customDataType.getName(), "TEST_1");
        assertTrue(customDataType.isSensitiveAlways());
        assertEquals(customDataType.getOperator(), Conditions.Operator.AND);
        assertEquals(customDataType.getKeyConditions().getOperator(), Conditions.Operator.OR);
        assertEquals(customDataType.getKeyConditions().getPredicates().size(), 2);
        assertEquals(customDataType.getValueConditions().getOperator(), Conditions.Operator.AND);
        assertEquals(customDataType.getValueConditions().getPredicates().size(), 1);
        assertEquals(customDataType.getCreatorId(), 0);

        // updating
        customDataTypeAction.setActive(false);
        customDataTypeAction.setKeyOperator("AND");
        customDataTypeAction.setValueOperator("OR");
        customDataTypeAction.setKeyConditionFromUsers(Collections.emptyList());
        customDataTypeAction.setCreateNew(false);

        customDataTypeAction.execute();
        assertFalse(customDataTypeAction.getCustomDataType().isActive());

        customDataType = CustomDataTypeDao.instance.findOne(Filters.eq(CustomDataType.NAME, "TEST_1"));

        assertEquals(customDataType.getCreatorId(), 0);
        assertEquals(customDataType.getName(), "TEST_1");
        assertTrue(customDataType.isSensitiveAlways());
        assertFalse(customDataType.isActive());
        assertEquals(customDataType.getOperator(), Conditions.Operator.AND);
        assertNull(customDataType.getKeyConditions());
        assertEquals(customDataType.getValueConditions().getOperator(), Conditions.Operator.OR);
        assertEquals(customDataType.getValueConditions().getPredicates().size(), 1);
        assertEquals(customDataType.getCreatorId(), 0);


        // update invalid name
        customDataTypeAction.setName("RANDOM_SHIT");
        customDataTypeAction.execute();
        assertNull(customDataTypeAction.getCustomDataType());

    }


    @Test
    public void testGenerateCustomDataTypeNewInvalidName() {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("value", "wencludeCustom");
        CustomDataTypeAction customDataTypeAction = generateCustomDataTypeAction(
                "", "AND",
                Arrays.asList(new CustomDataTypeAction.ConditionFromUser(Predicate.Type.STARTS_WITH, valueMap), new CustomDataTypeAction.ConditionFromUser(Predicate.Type.ENDS_WITH, valueMap)),
                Collections.singletonList(new CustomDataTypeAction.ConditionFromUser(Predicate.Type.REGEX, valueMap)));

        customDataTypeAction.execute();
        assertNull(customDataTypeAction.getCustomDataType());

        customDataTypeAction.setName("Avneesh.hota");
        customDataTypeAction.execute();
        assertNull(customDataTypeAction.getCustomDataType());
    }

    @Test
    public void testGenerateCustomDataTypeNewInvalidOperator() {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("value", "wencludeCustom");
        CustomDataTypeAction customDataTypeAction = generateCustomDataTypeAction(
                "first", "something",
                Arrays.asList(new CustomDataTypeAction.ConditionFromUser(Predicate.Type.STARTS_WITH, valueMap), new CustomDataTypeAction.ConditionFromUser(Predicate.Type.ENDS_WITH, valueMap)),
                Collections.singletonList(new CustomDataTypeAction.ConditionFromUser(Predicate.Type.REGEX, valueMap)));

        customDataTypeAction.execute();

        assertNull(customDataTypeAction.getCustomDataType());
    }

    @Test
    public void testGenerateCustomDataTypeNewInvalidValueMap() {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("valude", "wencludeCustom");
        CustomDataTypeAction customDataTypeAction = generateCustomDataTypeAction(
                "first", "AND",
                Arrays.asList(new CustomDataTypeAction.ConditionFromUser(Predicate.Type.STARTS_WITH, valueMap), new CustomDataTypeAction.ConditionFromUser(Predicate.Type.ENDS_WITH, valueMap)),
                Collections.singletonList(new CustomDataTypeAction.ConditionFromUser(Predicate.Type.REGEX, valueMap)));

        customDataTypeAction.execute();

        assertNull(customDataTypeAction.getCustomDataType());
    }


    @Test
    public void testFetchDataTypes() {
        IgnoreData ignoreData = new IgnoreData(new HashMap<>(), new HashSet<>());
        CustomDataType customDataType1 = new CustomDataType("name1", true, Collections.emptyList(), 1, true, null,null, Conditions.Operator.AND,ignoreData, false, true);
        User user1 = new User();
        user1.setId(1);
        user1.setName("user1");

        CustomDataType customDataType2 = new CustomDataType("name2", true, Collections.emptyList(), 2,false, null,null, Conditions.Operator.AND,ignoreData, false, true);
        User user2 = new User();
        user2.setId(2);
        user2.setName("user1");

        CustomDataType customDataType3 = new CustomDataType("name3", true, Collections.emptyList(), 3, true, null,null, Conditions.Operator.AND,ignoreData, false, true);
        User user3 = new User();
        user3.setId(3);
        user3.setName("user1");

        User user4 = new User();
        user4.setId(4);
        user4.setName("user4");

        User user5 = new User();
        user5.setId(5);
        user5.setName("user5");

        UsersDao.instance.getMCollection().drop();
        CustomDataTypeDao.instance.getMCollection().drop();

        UsersDao.instance.insertMany(Arrays.asList(user1, user2,user3, user4, user5));
        CustomDataTypeDao.instance.insertMany(Arrays.asList(customDataType1, customDataType2, customDataType3));

        CustomDataTypeAction customDataTypeAction = new CustomDataTypeAction();
        Map<String, Object> session = new HashMap<>();
        session.put("user", user4);
        customDataTypeAction.setSession(session);
        customDataTypeAction.fetchDataTypesForSettings();

        BasicDBObject dataTypes = customDataTypeAction.getDataTypes();

        assertEquals(((List) dataTypes.get("customDataTypes")).size(), 3);
        assertEquals(((Map) dataTypes.get("usersMap")).size(), 4);

    }

    @Test
    public void testToggleDataTypeActiveParam() {
        Context.accountId.set(1_000_000);
        CustomDataTypeDao.instance.getMCollection().drop();
        IgnoreData ignoreData = new IgnoreData(new HashMap<>(), new HashSet<>());
        CustomDataType customDataType = new CustomDataType("NAME1", true, Collections.emptyList(), 1, true, null,null, Conditions.Operator.AND,ignoreData, false, true);
        CustomDataTypeDao.instance.insertOne(customDataType);

        CustomDataTypeAction customDataTypeAction = new CustomDataTypeAction();
        Map<String, Object> session = new HashMap<>();
        session.put("user", new User());
        customDataTypeAction.setSession(session);
        customDataTypeAction.setActive(false);
        customDataTypeAction.setName("NAME1");

        customDataTypeAction.toggleDataTypeActiveParam();
        assertFalse(customDataTypeAction.getCustomDataType().isActive());

        CustomDataType customDataTypeFromDb = CustomDataTypeDao.instance.findOne(CustomDataType.NAME, "NAME1");
        assertFalse(customDataTypeFromDb.isActive());
    }

}
