 package com.akto.rules;

 import com.akto.dto.ApiInfo;
 import com.akto.dto.OriginalHttpRequest;
 import com.akto.dto.OriginalHttpResponse;
 import com.akto.dto.RawApi;
 import com.akto.dto.testing.TestResult;
 import com.akto.dto.type.RequestTemplate;
 import com.akto.log.LoggerMaker;
 import com.akto.store.SampleMessageStore;
 import com.akto.store.TestingUtil;
 import com.akto.util.HttpRequestResponseUtils;
 import com.akto.util.JSONUtils;
 import com.akto.util.modifier.NestedObjectModifier;
 import com.akto.util.modifier.SetValueModifier;
 import com.mongodb.BasicDBObject;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;

 import java.util.*;

 public class PageSizeDosTest extends TestPlugin {

     public static final String MODIFIED_COUNT = "AKTOREDIRECT";
     private final String testRunId;
     private final String testRunResultSummaryId;

     private static final Logger logger = LoggerFactory.getLogger(PageSizeDosTest.class);

     private final static String REDIRECT_KEYWORD = "{{redirect}}";
     private final static String REDIRECT_KEYWORD_TEMP = "AKTOREDIRECT";
     private static final String[] PAGINATED_KEYWORDS = {"limit", "size", "per_page", "perpage", "per-page",
     "page_size", "pagesize", "page-size", "page_limit", "pagelimit", "page-limit"};

     public PageSizeDosTest(String testRunId, String testRunResultSummaryId) {
         this.testRunId = testRunId;
         this.testRunResultSummaryId = testRunResultSummaryId;
     }
     @Override
     public Result start(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
         List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, testingUtil.getSampleMessages());
        if (messages.size() == 0) {
            logger.error("No messages found for apiInfoKey: " + apiInfoKey);
            return null;
        }
         for (RawApi message: messages) {
             RawApi rawApi = message.copy();
             OriginalHttpRequest req = rawApi.getRequest();

             String qp = req.getQueryParams();
             if (qp == null || qp.length() == 0) {
                 loggerMaker.infoAndAddToDb("No query params found for url: " + req.getUrl(), LoggerMaker.LogDb.TESTING);
                 continue;
             }

             String queryJson = HttpRequestResponseUtils.convertFormUrlEncodedToJson(req.getQueryParams());
             BasicDBObject queryParams = BasicDBObject.parse(queryJson);

             String paginatedKeyPresent = null;
             String paginationValue = "";
             for (String paginatedKey : PAGINATED_KEYWORDS) {
                 if (queryParams.containsKey(paginatedKey)) {
                     paginatedKeyPresent = paginatedKey;
                     paginationValue = queryParams.getString(paginatedKey);
                     queryParams.put(paginatedKey, MODIFIED_COUNT);
                     break;
                 }
             }

             if (paginatedKeyPresent == null) {
                 loggerMaker.infoAndAddToDb("No paginated keyword found for endpoint: " + req.getUrl() + " skipping this endpoint", LoggerMaker.LogDb.TESTING);
                 continue;
             }

             OriginalHttpResponse resp = rawApi.getResponse();
             int originalResponseLength = resp.getBody().length();

             //modify query param
             String modifiedQueryParamString = OriginalHttpRequest.getRawQueryFromJson(queryParams.toJson());
             if (modifiedQueryParamString != null) {
                 modifiedQueryParamString = modifiedQueryParamString.replaceAll(MODIFIED_COUNT, paginationValue+"0");
                 req.setQueryParams(modifiedQueryParamString);
             }
             logger.info("Modified query params: " + modifiedQueryParamString);

             String origTemplatePath = "https://raw.githubusercontent.com/Ankush12389/tests-library/master/Lack%20of%20Resources%20and%20Rate%20Limiting/resource-limiting/pagesize_dos.yaml";
             String testSourceConfigCategory = "";

             Map<String, Object> valuesMap = new HashMap<>();
             valuesMap.put("Method", apiInfoKey.method);
             String baseUrl;
             try {
                 baseUrl = req.getFullUrlWithParams();
             } catch (Exception e) {
                 loggerMaker.errorAndAddToDb("Error while getting full url including domain: " + e, LoggerMaker.LogDb.TESTING);
                 return addWithRequestError( rawApi.getOriginalMessage(), TestResult.TestError.FAILED_BUILDING_URL_WITH_DOMAIN,rawApi.getRequest(), null);
             }             

             int endIndexDomain = baseUrl.indexOf("/", 7);
             String domain = baseUrl.substring(0, endIndexDomain);
             String path = baseUrl.substring(endIndexDomain, baseUrl.length());

             valuesMap.put("MyPath", path.split("\\?")[0]);
             valuesMap.put("QueryParams", baseUrl.split("\\?")[1]);
             valuesMap.put("Body", rawApi.getRequest().getBody());
             
             req.setUrl(domain);
             rawApi.setRequest(req);
             FuzzingTest fuzzingTest = new FuzzingTest(
                     testRunId, testRunResultSummaryId, origTemplatePath,subTestName(), testSourceConfigCategory, valuesMap
             );
             try {
                 Result result = fuzzingTest.runNucleiTest(rawApi);
                 //analyze result
                 TestResult testResult = result.testResults.get(0);
                 int testResponseLength = testResult.getMessage().length();
                 if(testResponseLength >= 3 * originalResponseLength){
                     result.confidencePercentage = 100;
                     result.isVulnerable = true;
                     return result;
                 }
             } catch (Exception e ) {
                 return null;
             }

         }
         return null;
     }

     @Override
     public String superTestName() {
         return "RATE_LIMITING";
     }

     @Override
     public String subTestName() {
         return "PAGINATION_MISCONFIGURATION";
     }
 }