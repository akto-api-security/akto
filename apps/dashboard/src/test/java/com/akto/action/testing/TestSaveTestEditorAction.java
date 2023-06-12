package com.akto.action.testing;

import com.akto.MongoBasedTest;
import com.akto.action.test_editor.SaveTestEditorAction;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.User;
import com.akto.dto.test_editor.YamlTemplate;
import com.mongodb.client.model.Filters;
import jdk.nashorn.internal.runtime.GlobalConstants;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class TestSaveTestEditorAction extends MongoBasedTest {

    @Test
    public void testSaveTestEditorFile() {
        String userEmail = "test@akto.io";
        User user = new User();

        user.setLogin(userEmail);
        SaveTestEditorAction action = new SaveTestEditorAction();
        Map<String, Object> session = new HashMap<>();
        session.put("user", user);
        action.setSession(session);
        action.setContent(content);
        action.saveTestEditorFile();
        YamlTemplate template = YamlTemplateDao.instance.findOne(Filters.eq("_id", "REMOVE_TOKENS"));
        assertTrue(template != null);
    }


    private final String content = "id: REMOVE_TOKENS\n" +
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
