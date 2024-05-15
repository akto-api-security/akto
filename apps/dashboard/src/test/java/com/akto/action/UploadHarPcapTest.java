// package com.akto.action;
// import static org.junit.Assert.assertEquals;
// import static org.junit.Assert.assertNotNull;
// import static org.junit.Assert.assertNull;
// import static org.junit.Assert.assertTrue;

// import java.io.File;
// import java.io.IOException;
// import java.util.HashMap;
// import java.util.HashSet;
// import java.util.List;
// import java.util.Map;

// import com.akto.MongoBasedTest;
// import com.akto.dao.ApiCollectionsDao;
// import com.akto.dao.SingleTypeInfoDao;
// import com.akto.dao.TrafficInfoDao;
// import com.akto.dto.ApiCollection;
// import com.akto.dto.User;
// import com.akto.dto.messaging.Message;
// import com.akto.dto.traffic.TrafficInfo;
// import com.akto.dto.type.SingleTypeInfo;
// import com.mongodb.BasicDBList;
// import com.mongodb.BasicDBObject;

// import org.apache.commons.io.FileUtils;
// import org.junit.BeforeClass;
// import org.junit.Test;

// import io.swagger.parser.OpenAPIParser;
// import io.swagger.v3.oas.models.OpenAPI;
// import io.swagger.v3.oas.models.Operation;

// public class UploadHarPcapTest extends MongoBasedTest {

//     private static final int apiCollectionId = 1234;
//     private static final String apiKey = "";
//     private static String workspaceId = null;

//     @BeforeClass
//     public static void setup() throws IOException {
//         ClassLoader classLoader = UploadHarPcapTest.class.getClassLoader();
//         File file = new File(classLoader.getResource("restapi_81.har").getFile());
        
//         HarAction action = new HarAction();
        
//         action.setApiCollectionId(apiCollectionId);
//         action.setSkipKafka(true);

//         String harString = FileUtils.readFileToString(file);
//         assertEquals(157426, harString.length());

//         action.setHarString(harString);
        
//         action.execute();

//         com.akto.postman.Main main = new com.akto.postman.Main(apiKey);
//         workspaceId = main.createWorkspace();

//     }

//     @Test
//     public void testRestAPIHar() throws IOException {
//         ApiCollection apiCollection = ApiCollectionsDao.instance.findOne("_id", apiCollectionId);
//         assertEquals(2, apiCollection.getUrls().size());

//         List<SingleTypeInfo> singleTypeInfos = SingleTypeInfoDao.instance.findAll("apiCollectionId", apiCollectionId);
//         assertEquals(56, singleTypeInfos.size());

//         List<TrafficInfo> trafficInfos = TrafficInfoDao.instance.findAll("_id.apiCollectionId", apiCollectionId);
//         int sum = 0;
//         for(TrafficInfo info: trafficInfos) {
//             sum += info.mapHoursToCount.values().stream().reduce(0, (z, e) -> z + e);
//         }

//         assertEquals(162, sum);
//     }    

//     private void validateOpenAPI(Operation operation) {
//         Map carsPostReqProps = operation.getRequestBody().getContent().get("application/json").getSchema().getProperties();
//         assertTrue(carsPostReqProps.containsKey("p"));
//         assertTrue(carsPostReqProps.containsKey("q"));
//         assertEquals(2, carsPostReqProps.size());

        
//         Map carsPostResProps = operation.getResponses().get("201").getContent().get("application/json").getSchema().getProperties();
//         assertTrue(carsPostResProps.containsKey("author"));
//         assertTrue(carsPostResProps.containsKey("title"));
//         assertTrue(carsPostResProps.containsKey("isbn"));
//         assertTrue(carsPostResProps.containsKey("timestamp"));
//         assertTrue(carsPostResProps.containsKey("id"));
//         assertEquals(5, carsPostResProps.size());
//     }

//     @Test
//     public void testDownloadAPISpec() throws IOException {
//         OpenApiAction openApiAction = new OpenApiAction();
//         openApiAction.setApiCollectionId(apiCollectionId);

//         openApiAction.execute();
//         String openAPIString = openApiAction.getOpenAPIString();
//         OpenAPI openAPI = new OpenAPIParser().readContents(openAPIString, null, null).getOpenAPI();

//         assertEquals(2, openAPI.getPaths().size());
//         validateOpenAPI(openAPI.getPaths().get("/api/cars").getPost());
//         validateOpenAPI(openAPI.getPaths().get("/api/books").getGet());
//         assertEquals(2, openAPI.getPaths().size());
//     }    

//     @Test
//     public void testDuplicateCollectionNames() {
//         ApiCollectionsAction action = new ApiCollectionsAction();
//         String testName = "test1";
//         ApiCollectionsDao.instance.insertOne(new ApiCollection(0, "Default", 0, new HashSet<>(),null,0));
//         action.setCollectionName(testName);

//         action.createCollection();
//         ApiCollection newCollection = ApiCollectionsDao.instance.findByName(testName);
//         assertEquals(testName, newCollection.getName());
        
//         String result = action.createCollection();
//         assertEquals("ERROR", result);

//         action.setApiCollectionId(0);
//         action.deleteCollection();
//         assertNotNull(ApiCollectionsDao.instance.findOne("_id", 0));

//         action.setApiCollectionId(newCollection.getId());
//         action.deleteCollection();
//         assertNull(ApiCollectionsDao.instance.findOne("_id", newCollection.getId()));

//     }

//     @Test
//     public void testUploadToPostman() throws Exception {
//         PostmanAction postmanAction = new PostmanAction();
//         postmanAction.setApi_key(apiKey);
//         postmanAction.setWorkspace_id(workspaceId);

//         Map<String, Object> session = new HashMap<>();
//         User user = new User("test-user", "test-user@test.com", new HashMap<>(), new HashMap<>(), Message.Mode.EMAIL);
//         session.put("user", user);
//         postmanAction.setSession(session);

//         postmanAction.addOrUpdateApiKey();
//         postmanAction.fetchPostmanCred();
//         postmanAction.getPostmanCred().get("api_key").equals(apiKey);
//         postmanAction.getPostmanCred().get("workspace_id").equals(workspaceId);
//         postmanAction.setApiCollectionId(apiCollectionId);
        
//         String result = postmanAction.createPostmanApi();
//         assertEquals("SUCCESS", result);
//         com.akto.postman.Main main = new com.akto.postman.Main(apiKey);

//         String apiStr = main.fetchOneApiFromWorkspace(workspaceId);
//         BasicDBObject json = BasicDBObject.parse(apiStr);
//         Object versionsObj = ((BasicDBObject) json.get("api")).get("versions");
//         BasicDBList versions = (BasicDBList) versionsObj;
//         assertEquals(3, versions.size());

//         for(Object versionObj: versions) {
//             BasicDBObject version = (BasicDBObject) versionObj;
//             BasicDBList schemas = (BasicDBList) (version.get("schemas"));
//             if (schemas == null) {
//                 continue; // version = Draft
//             }
//             assertEquals(1, schemas.size());
//             BasicDBObject schema = (BasicDBObject) (schemas.get(0));
//             switch(version.getString("name")) {
//                 case "Senstive": 
//                     assertEquals(0, new OpenAPIParser().readContents(schema.getString("content"), null, null).getOpenAPI().getPaths().size());
//                     break;
//                 case "All":
//                     assertEquals(2, new OpenAPIParser().readContents(schema.getString("content"), null, null).getOpenAPI().getPaths().size());
//                     break;
//             }
//         }

//         main.deleteWorkspace(workspaceId);
//     }

// }