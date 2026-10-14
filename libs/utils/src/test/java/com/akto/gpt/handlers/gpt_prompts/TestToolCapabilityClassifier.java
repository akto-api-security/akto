package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.junit.Test;

import javax.validation.ValidationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestToolCapabilityClassifier {

    private final ToolCapabilityClassifier classifier = new ToolCapabilityClassifier();

    private BasicDBObject process(String raw) {
        return classifier.processResponse(raw);
    }

    private static void assertFailed(BasicDBObject r) {
        assertTrue("expected an error verdict, got " + r, r.containsField("error"));
        assertFalse("a failed verdict must not carry a capability", r.containsField(ToolCapabilityClassifier.CAPABILITY));
    }

    private static void assertVerdict(BasicDBObject r, String capability, boolean dangerous) {
        assertFalse("expected a real verdict, got " + r, r.containsField("error"));
        assertEquals(capability, r.getString(ToolCapabilityClassifier.CAPABILITY));
        assertEquals(dangerous, r.getBoolean(ToolCapabilityClassifier.DANGEROUS));
    }

    @Test
    public void dangerousCapabilitiesAreRecognised() {
        assertVerdict(process("{\"dangerous\":true,\"capability\":\"RESOURCE_DELETE\"}"),
                ToolCapabilityClassifier.RESOURCE_DELETE, true);
        assertVerdict(process("{\"dangerous\":true,\"capability\":\"FILE_WRITE\"}"),
                ToolCapabilityClassifier.FILE_WRITE, true);
        assertVerdict(process("{\"dangerous\":true,\"capability\":\"CREDENTIAL_OR_PII_READ\"}"),
                ToolCapabilityClassifier.CREDENTIAL_OR_PII_READ, true);
        assertVerdict(process("{\"dangerous\":true,\"capability\":\"CRITICAL_RESOURCE_WRITE\"}"),
                ToolCapabilityClassifier.CRITICAL_RESOURCE_WRITE, true);
    }

    @Test
    public void safeVerdictIsARealVerdictNotAFailure() {
        assertVerdict(process("{\"dangerous\":false,\"capability\":\"SAFE\"}"),
                ToolCapabilityClassifier.SAFE, false);
    }

    @Test
    public void capabilityIsNormalisedForCaseAndWhitespace() {
        assertVerdict(process("{\"capability\":\"  resource_delete  \"}"),
                ToolCapabilityClassifier.RESOURCE_DELETE, true);
    }

    @Test
    public void dangerousFlagWinsWhenCapabilityIsUnrecognised() {
        assertVerdict(process("{\"dangerous\":true,\"capability\":\"SOMETHING_ELSE\"}"),
                ToolCapabilityClassifier.SAFE, true);
    }

    @Test
    public void capabilityWinsOverAContradictingDangerousFlag() {
        assertVerdict(process("{\"dangerous\":false,\"capability\":\"RESOURCE_DELETE\"}"),
                ToolCapabilityClassifier.RESOURCE_DELETE, true);
    }

    @Test
    public void missingFieldsFallBackToSafe() {
        assertVerdict(process("{}"), ToolCapabilityClassifier.SAFE, false);
    }

    @Test
    public void proseResponseIsAFailureNotSafe() {
        assertFailed(process("This tool deletes a table, so it is dangerous."));
    }

    @Test
    public void emptyResponseIsAFailureNotSafe() {
        assertFailed(process(""));
    }

    @Test
    public void nullResponseIsAFailureNotSafe() {
        assertFailed(process(null));
    }

    @Test
    public void notFoundResponseIsAFailureNotSafe() {
        assertFailed(process("NOT_FOUND"));
        assertFailed(process("not_found"));
    }

    @Test
    public void truncatedJsonIsAFailureNotSafe() {
        assertFailed(process("{\"dangerous\":true,\"capability\":\"RESOURCE_DEL"));
    }

    @Test
    public void blankToolNameIsRejectedBeforeAnyLlmCall() {
        BasicDBObject input = new BasicDBObject(ToolCapabilityClassifier.TOOL_NAME, "   ")
                .append(ToolCapabilityClassifier.SAMPLE_DATA, "{}");
        assertTrue(classifier.handle(input).containsField("error"));
    }

    @Test(expected = ValidationException.class)
    public void missingToolNameFailsValidation() throws ValidationException {
        classifier.validate(new BasicDBObject(ToolCapabilityClassifier.SAMPLE_DATA, "{}"));
    }

    @Test
    public void promptCarriesToolNameAndSample() {
        String prompt = classifier.getPrompt(new BasicDBObject(ToolCapabilityClassifier.TOOL_NAME, "drop_table_mysql")
                .append(ToolCapabilityClassifier.SAMPLE_DATA, "{\"requestPayload\":\"{}\"}"));
        assertTrue(prompt.contains("drop_table_mysql"));
        assertTrue(prompt.contains("requestPayload"));
    }

    @Test
    public void oversizedSampleIsTruncatedIntoThePrompt() {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 20000; i++) huge.append('x');
        String prompt = classifier.getPrompt(new BasicDBObject(ToolCapabilityClassifier.TOOL_NAME, "t")
                .append(ToolCapabilityClassifier.SAMPLE_DATA, huge.toString()));
        assertTrue(prompt.length() < huge.length());
    }

    @Test
    public void responseFormatPinsJsonObject() throws Exception {
        assertEquals("json_object", classifier.getResponseFormat().getString("type"));
    }

    @Test
    public void markdownFencedJsonSurvivesCleaning() {
        String fenced = "```json\n{\"dangerous\":true,\"capability\":\"FILE_WRITE\"}\n```";
        assertVerdict(process(AzureOpenAIPromptHandler.cleanJSON(fenced)),
                ToolCapabilityClassifier.FILE_WRITE, true);
    }

    @Test
    public void proseAroundJsonIsStripped() {
        String chatty = "Sure! Here is the answer:\n{\"capability\":\"RESOURCE_DELETE\"}\nHope that helps.";
        assertVerdict(process(AzureOpenAIPromptHandler.cleanJSON(chatty)),
                ToolCapabilityClassifier.RESOURCE_DELETE, true);
    }

    @Test
    public void pureProseWithNoBracesEndsAsAFailure() {
        assertFailed(process(AzureOpenAIPromptHandler.cleanJSON("This tool looks dangerous to me.")));
    }

    @Test
    public void emptyModelOutputBecomesNotFoundAndThenAFailure() {
        assertEquals("NOT_FOUND", AzureOpenAIPromptHandler.cleanJSON(""));
        assertFailed(process(AzureOpenAIPromptHandler.cleanJSON("")));
    }

    @Test
    public void unknownExtraFieldsAreIgnored() {
        assertVerdict(process("{\"capability\":\"FILE_WRITE\",\"dangerous\":true,\"reason\":\"writes\",\"score\":0.9}"),
                ToolCapabilityClassifier.FILE_WRITE, true);
    }

    @Test
    public void leadingWhitespaceIsTolerated() {
        assertVerdict(process("   \n\t{\"capability\":\"SAFE\"}"), ToolCapabilityClassifier.SAFE, false);
    }

    @Test
    public void jsonArrayResponseIsAFailure() {
        assertFailed(process("[{\"capability\":\"FILE_WRITE\"}]"));
    }

    @Test
    public void capabilityOfWrongTypeIsAFailureOrSafeNotACrash() {
        BasicDBObject r = process("{\"capability\":123,\"dangerous\":true}");
        assertTrue(r.containsField("error") || ToolCapabilityClassifier.SAFE.equals(r.getString(ToolCapabilityClassifier.CAPABILITY)));
    }
}
