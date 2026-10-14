package com.akto.utils.crons;

import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiInfo;
import com.akto.gpt.handlers.gpt_prompts.ToolCapabilityClassifier;
import com.akto.service.insights.InsightClassificationHelper;
import com.akto.dto.type.URLMethods;
import com.mongodb.client.model.WriteModel;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestToolClassificationCronClassify {

    private static final int ACCOUNT = 1_000_000;
    private static final int COLLECTION = 42;
    private static final String TOOL_URL = "/mcp/tools/call/drop_table";
    private static final String SAMPLE = "{\"requestPayload\":\"{}\",\"responsePayload\":\"{}\"}";

    private static ApiInfo toolRow(String url, URLMethods.Method method) {
        return new ApiInfo(new ApiInfo.ApiInfoKey(COLLECTION, url, method));
    }

    private List<WriteModel<ApiInfo>> runClassify(String sampleReturned,
                                                  InsightClassificationHelper.ToolDangerVerdict verdict,
                                                  ApiInfo row) {
        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
        try (MockedStatic<SampleDataDao> sample = Mockito.mockStatic(SampleDataDao.class);
             MockedStatic<InsightClassificationHelper> helper =
                     Mockito.mockStatic(InsightClassificationHelper.class)) {

            sample.when(() -> SampleDataDao.getLatestSampleData(Mockito.anyInt(), Mockito.anyString(),
                    Mockito.anyString())).thenReturn(sampleReturned);
            helper.when(() -> InsightClassificationHelper.classifyToolDanger(Mockito.anyString(),
                    Mockito.anyString())).thenReturn(verdict);

            new ToolClassificationCron().classify(ACCOUNT, row, updates);
        }
        return updates;
    }

    @Test
    public void dangerousVerdictIsPersisted() {
        List<WriteModel<ApiInfo>> updates = runClassify(SAMPLE,
                new InsightClassificationHelper.ToolDangerVerdict(true, ToolCapabilityClassifier.RESOURCE_DELETE),
                toolRow(TOOL_URL, URLMethods.Method.POST));
        assertEquals(1, updates.size());
    }

    @Test
    public void safeVerdictIsAlsoPersisted() {
        List<WriteModel<ApiInfo>> updates = runClassify(SAMPLE,
                new InsightClassificationHelper.ToolDangerVerdict(false, ToolCapabilityClassifier.SAFE),
                toolRow(TOOL_URL, URLMethods.Method.POST));
        assertEquals("a real SAFE determination must be stored", 1, updates.size());
    }

    @Test
    public void failedClassificationIsNotPersisted() {
        List<WriteModel<ApiInfo>> updates = runClassify(SAMPLE, null,
                toolRow(TOOL_URL, URLMethods.Method.POST));
        assertTrue("a failed classification must not be written, so the next run retries it",
                updates.isEmpty());
    }

    @Test
    public void missingSampleDataIsNotPersisted() {
        List<WriteModel<ApiInfo>> updates = runClassify(null,
                new InsightClassificationHelper.ToolDangerVerdict(true, ToolCapabilityClassifier.RESOURCE_DELETE),
                toolRow(TOOL_URL, URLMethods.Method.POST));
        assertTrue(updates.isEmpty());
    }

    @Test
    public void unparseableSampleDataIsNotPersisted() {
        List<WriteModel<ApiInfo>> updates = runClassify("not json",
                new InsightClassificationHelper.ToolDangerVerdict(true, ToolCapabilityClassifier.RESOURCE_DELETE),
                toolRow(TOOL_URL, URLMethods.Method.POST));
        assertTrue(updates.isEmpty());
    }

    @Test
    public void rowWithNullKeyIsSkipped() {
        List<WriteModel<ApiInfo>> updates = runClassify(SAMPLE,
                new InsightClassificationHelper.ToolDangerVerdict(true, ToolCapabilityClassifier.RESOURCE_DELETE),
                new ApiInfo());
        assertTrue(updates.isEmpty());
    }

    @Test
    public void rowWithNullMethodIsSkipped() {
        List<WriteModel<ApiInfo>> updates = runClassify(SAMPLE,
                new InsightClassificationHelper.ToolDangerVerdict(true, ToolCapabilityClassifier.RESOURCE_DELETE),
                toolRow(TOOL_URL, null));
        assertTrue(updates.isEmpty());
    }

    @Test
    public void rowWithNullUrlIsSkipped() {
        List<WriteModel<ApiInfo>> updates = runClassify(SAMPLE,
                new InsightClassificationHelper.ToolDangerVerdict(true, ToolCapabilityClassifier.RESOURCE_DELETE),
                toolRow(null, URLMethods.Method.POST));
        assertTrue(updates.isEmpty());
    }

    @Test
    public void headersAreStrippedBeforeReachingTheClassifier() {
        String withSecrets = "{\"requestHeaders\":\"{\\\"authorization\\\":\\\"Bearer sk-secret\\\"}\","
                + "\"requestPayload\":\"{}\"}";
        final String[] seen = new String[1];
        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
        try (MockedStatic<SampleDataDao> sample = Mockito.mockStatic(SampleDataDao.class);
             MockedStatic<InsightClassificationHelper> helper =
                     Mockito.mockStatic(InsightClassificationHelper.class)) {

            sample.when(() -> SampleDataDao.getLatestSampleData(Mockito.anyInt(), Mockito.anyString(),
                    Mockito.anyString())).thenReturn(withSecrets);
            helper.when(() -> InsightClassificationHelper.classifyToolDanger(Mockito.anyString(),
                    Mockito.anyString())).thenAnswer(inv -> {
                        seen[0] = inv.getArgument(1);
                        return new InsightClassificationHelper.ToolDangerVerdict(false, ToolCapabilityClassifier.SAFE);
                    });

            new ToolClassificationCron().classify(ACCOUNT, toolRow(TOOL_URL, URLMethods.Method.POST), updates);
        }
        assertTrue(seen[0] != null && !seen[0].contains("sk-secret"));
    }

    @Test
    public void toolNamePassedToClassifierIsTheLastPathSegment() {
        final String[] seen = new String[1];
        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
        try (MockedStatic<SampleDataDao> sample = Mockito.mockStatic(SampleDataDao.class);
             MockedStatic<InsightClassificationHelper> helper =
                     Mockito.mockStatic(InsightClassificationHelper.class)) {

            sample.when(() -> SampleDataDao.getLatestSampleData(Mockito.anyInt(), Mockito.anyString(),
                    Mockito.anyString())).thenReturn(SAMPLE);
            helper.when(() -> InsightClassificationHelper.classifyToolDanger(Mockito.anyString(),
                    Mockito.anyString())).thenAnswer(inv -> {
                        seen[0] = inv.getArgument(0);
                        return new InsightClassificationHelper.ToolDangerVerdict(false, ToolCapabilityClassifier.SAFE);
                    });

            new ToolClassificationCron().classify(ACCOUNT, toolRow(TOOL_URL, URLMethods.Method.POST), updates);
        }
        assertEquals("drop_table", seen[0]);
    }

    @Test
    public void classifierExceptionIsNotPersisted() {
        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
        try (MockedStatic<SampleDataDao> sample = Mockito.mockStatic(SampleDataDao.class);
             MockedStatic<InsightClassificationHelper> helper =
                     Mockito.mockStatic(InsightClassificationHelper.class)) {

            sample.when(() -> SampleDataDao.getLatestSampleData(Mockito.anyInt(), Mockito.anyString(),
                    Mockito.anyString())).thenReturn(SAMPLE);
            helper.when(() -> InsightClassificationHelper.classifyToolDanger(Mockito.anyString(),
                    Mockito.anyString())).thenThrow(new RuntimeException("azure 503"));

            new ToolClassificationCron().classify(ACCOUNT, toolRow(TOOL_URL, URLMethods.Method.POST), updates);
        }
        assertTrue("an exception must not be written as a verdict", updates.isEmpty());
    }

    @Test
    public void headerOnlySampleStillReachesTheClassifier() {
        final String[] seen = new String[1];
        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
        try (MockedStatic<SampleDataDao> sample = Mockito.mockStatic(SampleDataDao.class);
             MockedStatic<InsightClassificationHelper> helper =
                     Mockito.mockStatic(InsightClassificationHelper.class)) {
            sample.when(() -> SampleDataDao.getLatestSampleData(Mockito.anyInt(), Mockito.anyString(),
                    Mockito.anyString())).thenReturn("{\"requestHeaders\":\"{}\",\"responseHeaders\":\"{}\"}");
            helper.when(() -> InsightClassificationHelper.classifyToolDanger(Mockito.anyString(),
                    Mockito.anyString())).thenAnswer(inv -> {
                        seen[0] = inv.getArgument(1);
                        return new InsightClassificationHelper.ToolDangerVerdict(false, ToolCapabilityClassifier.SAFE);
                    });
            new ToolClassificationCron().classify(ACCOUNT, toolRow(TOOL_URL, URLMethods.Method.POST), updates);
        }
        assertEquals("{}", seen[0]);
        assertEquals(1, updates.size());
    }

    @Test
    public void jsonArraySampleIsTreatedAsUnparseable() {
        List<WriteModel<ApiInfo>> updates = runClassify("[{\"a\":1}]",
                new InsightClassificationHelper.ToolDangerVerdict(true, ToolCapabilityClassifier.RESOURCE_DELETE),
                toolRow(TOOL_URL, URLMethods.Method.POST));
        assertTrue(updates.isEmpty());
    }

    @Test
    public void emptySampleStringIsTreatedAsUnparseable() {
        List<WriteModel<ApiInfo>> updates = runClassify("",
                new InsightClassificationHelper.ToolDangerVerdict(true, ToolCapabilityClassifier.RESOURCE_DELETE),
                toolRow(TOOL_URL, URLMethods.Method.POST));
        assertTrue(updates.isEmpty());
    }
}
