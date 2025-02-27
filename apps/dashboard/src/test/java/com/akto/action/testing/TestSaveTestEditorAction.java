package com.akto.action.testing;

import com.akto.MongoBasedTest;
import com.akto.action.test_editor.SaveTestEditorAction;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.User;
import com.akto.dto.test_editor.YamlTemplate;
import com.mongodb.client.model.Filters;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestSaveTestEditorAction extends MongoBasedTest {

    public static void saveTest(SaveTestEditorAction action){
        String userEmail = "test@akto.io";
        User user = new User();

        user.setLogin(userEmail);
        
        Map<String, Object> session = new HashMap<>();
        session.put("user", user);
        action.setSession(session);
        action.setContent(content);
        action.saveTestEditorFile();
    }


    @Test
    public void testSaveTestEditorFile() {
        SaveTestEditorAction action = new SaveTestEditorAction();
        saveTest(action);
        YamlTemplate template = YamlTemplateDao.instance.findOne(Filters.eq("_id", "REMOVE_TOKENS"));
        assertTrue(template != null);
    }

    @Test
    public void testSetTestInactive() {
        SaveTestEditorAction action = new SaveTestEditorAction();
        saveTest(action);
        YamlTemplate template = YamlTemplateDao.instance.findOne(Filters.eq("_id", "REMOVE_TOKENS"));
        assertEquals(false, template.getInactive());
        action.setOriginalTestId("REMOVE_TOKENS");
        action.setInactive(true);
        action.setTestInactive();
        template = YamlTemplateDao.instance.findOne(Filters.eq("_id", "REMOVE_TOKENS"));
        assertEquals(true, template.getInactive());
    }

    @Test
    public void testFetchTestContent() {
        SaveTestEditorAction action = new SaveTestEditorAction();
        saveTest(action);
        action.setOriginalTestId("REMOVE_TOKENS");
    
        String result = action.fetchTestContent();
        assertEquals("SUCCESS", result);
        assertEquals(outputContent, action.getContent());
    
        action.setOriginalTestId(null);
        assertEquals("ERROR", action.fetchTestContent());
    
        action.setOriginalTestId("NON_EXISTENT_TEST");
        assertEquals("ERROR", action.fetchTestContent());
    
        action.setOriginalTestId("");
        assertEquals("ERROR", action.fetchTestContent());
    }    

    private final String outputContent = "---\n" +
    "id: REMOVE_TOKENS\n" +
    "info:\n" +
    "  name: Broken Authentication by removing auth token\n" +
    "  description: API doesn't validate the authenticity of token. Attacker can remove the auth token and access the endpoint.\n" +
    "  details: |\n" +
    "    \"The endpoint appears to be vulnerable to broken authentication attack. The original request was replayed by removing victim's <b>auth</b> token. The server responded with 2XX success codes.<br>\" \"<b>Background:</b> Authentication is the process of attempting to verify the digital identity of the sender of a communication. Testing the authentication schema means understanding how the authentication process works and using that information to circumvent the authentication mechanism. While most applications require authentication to gain access to private information or to execute tasks, not every authentication method is able to provide adequate security. Negligence, ignorance, or simple understatement of security threats often result in authentication schemes that can be bypassed by simply skipping the log in page and directly calling an internal page that is supposed to be accessed only after authentication has been performed.\"\n" +
    "  impact: \"Broken User authentication is a serious vulnerability. Attackers can gain control to other users’ accounts in the system, read their personal data, and perform sensitive actions on their behalf, like money transactions and sending personal messages.\"\n" +
    "  category:\n" +
    "    name: NO_AUTH\n" +
    "    shortName: Broken Authentication\n" +
    "    displayName: Broken User Authentication (BUA)\n" +
    "  subCategory: REMOVE_TOKENS\n" +
    "  severity: HIGH\n" +
    "  tags:\n" +
    "  - Business logic\n" +
    "  - OWASP top 10\n" +
    "  - HackerOne top 10\n" +
    "  references:\n" +
    "  - https://owasp.org/www-project-web-security-testing-guide/v42/4-Web_Application_Security_Testing/\n" +
    "  - https://github.com/OWASP/API-Security/blob/master/2019/en/src/0xa2-broken-user-authentication.md\n" +
    "  - https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html\n" +
    "  - https://cwe.mitre.org/data/definitions/798.html\n" +
    "api_selection_filters:\n" +
    "  response_code:\n" +
    "    gte: 200\n" +
    "    lt: 300\n" +
    "execute:\n" +
    "  type: single\n" +
    "  requests:\n" +
    "  - req:\n" +
    "    - remove_auth_header: true\n" +
    "validate:\n" +
    "  response_code:\n" +
    "    gte: 200\n" +
    "    lt: 300\n";
    

    private final static String content = "id: REMOVE_TOKENS\n" +
            "info:\n" +
            "  name: \"Broken Authentication by removing auth token\"\n" +
            "  description: \"API doesn't validate the authenticity of token. Attacker can remove the auth token and access the endpoint.\"\n" +
            "  details: >\n" +
            "            \"The endpoint appears to be vulnerable to broken authentication attack. The original request was replayed by removing victim's <b>auth</b> token. The server responded with 2XX success codes.<br>\"\n" +
            "            \"<b>Background:</b> Authentication is the process of attempting to verify the digital identity of the sender of a communication. Testing the authentication schema means understanding how the authentication process works and using that information to\n" +
            "            circumvent the authentication mechanism. While most applications require authentication to gain access to private information or to execute tasks, not every authentication method is able to provide adequate security. Negligence, ignorance, or simple\n" +
            "            understatement of security threats often result in authentication schemes that can be bypassed by simply skipping the log in page and directly calling an internal page that is supposed to be accessed only after authentication has been performed.\"\n" +
            "  impact: \"Broken User authentication is a serious vulnerability. Attackers can gain control to other users’ accounts in the system, read their personal data, and perform sensitive actions on their behalf, like money transactions and sending personal messages.\"\n" +
            "  category:\n" +
            "    name: NO_AUTH\n" +
            "    shortName: Broken Authentication\n" +
            "    displayName: Broken User Authentication (BUA)\n" +
            "  subCategory: REMOVE_TOKENS\n" +
            "  severity: HIGH\n" +
            "  tags:\n" +
            "    - Business logic\n" +
            "    - OWASP top 10\n" +
            "    - HackerOne top 10\n" +
            "  references:\n" +
            "    - \"https://owasp.org/www-project-web-security-testing-guide/v42/4-Web_Application_Security_Testing/\"\n" +
            "    - \"https://github.com/OWASP/API-Security/blob/master/2019/en/src/0xa2-broken-user-authentication.md\"\n" +
            "    - \"https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html\"\n" +
            "    - \"https://cwe.mitre.org/data/definitions/798.html\"\n" +
            "\n" +
            "api_selection_filters:\n" +
            "  response_code:\n" +
            "    gte: 200\n" +
            "    lt: 300\n" +
            "\n" +
            "execute:\n" +
            "  type: single\n" +
            "  requests:\n" +
            "    - req:\n" +
            "        - remove_auth_header: true\n" +
            "\n" +
            "validate:\n" +
            "  response_code:\n" +
            "    gte: 200\n" +
            "    lt: 300\n";
}
