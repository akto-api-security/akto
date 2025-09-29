package com.akto.threat.detection.tasks;

// import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ApiDistributionDataRequestPayload;
// import com.akto.threat.detection.cache.CounterCache;
// import com.akto.threat.detection.constants.RedisKeyInfo;
// import com.akto.threat.detection.ip_api_counter.DistributionCalculator;
// import com.akto.threat.detection.ip_api_counter.DistributionDataForwardLayer;

// import io.lettuce.core.RedisClient;

// import org.apache.http.client.methods.HttpPost;
// TODO: do not use CloseableHttpClient
// import org.apache.http.impl.client.CloseableHttpClient;
// import org.junit.Before;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.mockito.*;

// import java.lang.reflect.Field;
// import java.util.*;

// import static org.mockito.Matchers.any;
// import static org.mockito.Matchers.anyInt;
// import static org.mockito.Matchers.anyLong;
// import static org.mockito.Matchers.anyString;
// import static org.mockito.Matchers.eq;
// import static org.mockito.Mockito.*;
// import java.lang.reflect.Field;
// import java.util.*;

// import static org.mockito.Matchers.*;
// import static org.mockito.Mockito.*;


// public class DistributionDataForwardLayerTest {

//     private DistributionCalculator mockCalculator;
//     private CounterCache mockCache;
//     private CloseableHttpClient mockHttpClient;
//     private DistributionDataForwardLayer forwardLayer;

//     @BeforeEach
//     public void setup() throws Exception {
//         mockCalculator = mock(DistributionCalculator.class);
//         mockCache = mock(CounterCache.class);
//         mockHttpClient = mock(CloseableHttpClient.class);

//         System.setProperty("AKTO_THREAT_PROTECTION_BACKEND_URL", "http://mock-url");
//         System.setProperty("AKTO_THREAT_PROTECTION_BACKEND_TOKEN", "mock-token");

//         forwardLayer = new DistributionDataForwardLayer(null, mockCalculator);

//         // Inject mockCache and mockHttpClient using reflection
//         Field cacheField = DistributionDataForwardLayer.class.getDeclaredField("cache");
//         cacheField.setAccessible(true);
//         cacheField.set(null, mockCache);

//         Field clientField = DistributionDataForwardLayer.class.getDeclaredField("httpClient");
//         clientField.setAccessible(true);
//         clientField.set(forwardLayer, mockHttpClient);
//     }

//     @Test
//     public void testBuildPayloadAndForwardData() throws Exception {
//         long currentEpochMin = System.currentTimeMillis() / 60000;
//         int windowSize = 5;
//         long lastSent = currentEpochMin - 10;
//         long windowStart = currentEpochMin - 5;

//         String redisKey = "api_distribution_data_last_sent:" + windowSize;

//         // Java 8 compatible map creation
//         Map<String, Integer> distMap = new HashMap<>();
//         distMap.put("b1", 2);

//         Map<String, Map<String, Integer>> outerMap = new HashMap<>();
//         outerMap.put("GET:/api", distMap);

//         when(mockCache.get(eq(redisKey))).thenReturn(lastSent);
//         when(mockCalculator.getBucketDistribution(eq(windowSize), anyLong())).thenReturn(outerMap);

//         forwardLayer.buildPayloadAndForwardData();

//         verify(mockHttpClient, atLeastOnce()).execute(any(HttpPost.class));
//         verify(mockCache, atLeastOnce()).set(eq(redisKey), anyLong());
//     }

//     @Test
//     public void testEmptyDistributionDoesNotSend() {
//         long currentEpochMin = System.currentTimeMillis() / 60000;
//         int windowSize = 5;
//         long lastSent = currentEpochMin - 10;

//         String redisKey = "api_distribution_data_last_sent:" + windowSize;

//         when(mockCache.get(eq(redisKey))).thenReturn(lastSent);
//         when(mockCalculator.getBucketDistribution(eq(windowSize), anyLong())).thenReturn(Collections.emptyMap());

//         forwardLayer.buildPayloadAndForwardData();

//         try {
//             verify(mockHttpClient, never()).execute(any(HttpPost.class));            
//         } catch (Exception e) {
//             // TODO: handle exception
//         }
//     }
// }