package com.akto.test_editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.akto.dao.context.Context;
import com.akto.test_editor.execution.VariableResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class TestUtils {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testStrSubstituteThreeParams() {

        String message = "{\"request\": {\"url\": \"https://juiceshop.akto.io/rest/captcha//metrics\", \"method\": \"GET\", \"type\": \"HTTP/1.1\", \"queryParams\": null, \"body\": \"\", \"headers\": \"{\\\"sec-fetch-mode\\\":\\\"cors\\\",\\\"referer\\\":\\\"https://juiceshop.akto.io/\\\",\\\"sec-fetch-site\\\":\\\"same-origin\\\",\\\"accept-language\\\":\\\"en-GB,en-US;q=0.9,en;q=0.8\\\",\\\"x-akto-ignore\\\":\\\"0\\\",\\\"accept\\\":\\\"application/json, text/plain, */*\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Not A(Brand\\\\\\\";v=\\\\\\\"24\\\\\\\", \\\\\\\"Chromium\\\\\\\";v=\\\\\\\"110\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"macOS\\\\\\\"\\\",\\\"host\\\":\\\"juiceshop.akto.io\\\",\\\"connection\\\":\\\"close\\\",\\\"accept-encoding\\\":\\\"gzip, deflate\\\",\\\"user-agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.5481.178 Safari/537.36\\\",\\\"sec-fetch-dest\\\":\\\"empty\\\"}\"}, \"response\": {\"statusCode\": 500, \"body\": \"{\\n" + //
                "  \\\"error\\\": {\\n" + //
                "    \\\"message\\\": \\\"Unexpected path: /rest/captcha/metrics\\\",\\n" + //
                "    \\\"stack\\\": \\\"Error: Unexpected path: /rest/captcha/metrics\\\\n" + //
                "    at /juice-shop/build/routes/angular.js:15:18\\\\n" + //
                "    at Layer.handle [as handle_request] (/juice-shop/node_modules/express/lib/router/layer.js:95:5)\\\\n" + //
                "    at trim_prefix (/juice-shop/node_modules/express/lib/router/index.js:328:13)\\\\n" + //
                "    at /juice-shop/node_modules/express/lib/router/index.js:286:9\\\\n" + //
                "    at Function.process_params (/juice-shop/node_modules/express/lib/router/index.js:346:12)\\\\n" + //
                "    at next (/juice-shop/node_modules/express/lib/router/index.js:280:10)\\\\n" + //
                "    at /juice-shop/build/routes/verify.js:135:5\\\\n" + //
                "    at Layer.handle [as handle_request] (/juice-shop/node_modules/express/lib/router/layer.js:95:5)\\\\n" + //
                "    at trim_prefix (/juice-shop/node_modules/express/lib/router/index.js:328:13)\\\\n" + //
                "    at /juice-shop/node_modules/express/lib/router/index.js:286:9\\\\n" + //
                "    at Function.process_params (/juice-shop/node_modules/express/lib/router/index.js:346:12)\\\\n" + //
                "    at next (/juice-shop/node_modules/express/lib/router/index.js:280:10)\\\\n" + //
                "    at /juice-shop/build/routes/verify.js:71:5\\\\n" + //
                "    at Layer.handle [as handle_request] (/juice-shop/node_modules/express/lib/router/layer.js:95:5)\\\\n" + //
                "    at trim_prefix (/juice-shop/node_modules/express/lib/router/index.js:328:13)\\\\n" + //
                "    at /juice-shop/node_modules/express/lib/router/index.js:286:9\\\\n" + //
                "    at Function.process_params (/juice-shop/node_modules/express/lib/router/index.js:346:12)\\\\n" + //
                "    at next (/juice-shop/node_modules/express/lib/router/index.js:280:10)\\\\n" + //
                "    at logger (/juice-shop/node_modules/morgan/index.js:144:5)\\\\n" + //
                "    at Layer.handle [as handle_request] (/juice-shop/node_modules/express/lib/router/layer.js:95:5)\\\\n" + //
                "    at trim_prefix (/juice-shop/node_modules/express/lib/router/index.js:328:13)\\\\n" + //
                "    at /juice-shop/node_modules/express/lib/router/index.js:286:9\\\"\\n" + //
                "  }\\n" + //
                "}\", \"headers\": \"{\\\"date\\\":\\\"Wed, 07 Feb 2024 05:55:11 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"x-content-type-options\\\":\\\"nosniff\\\",\\\"x-recruiting\\\":\\\"/#/jobs\\\",\\\"vary\\\":\\\"Accept-Encoding\\\",\\\"x-frame-options\\\":\\\"SAMEORIGIN\\\",\\\"content-type\\\":\\\"application/json; charset=utf-8\\\",\\\"feature-policy\\\":\\\"payment 'self'\\\"}\"}}";

            String harPayload;
        
            try {
                harPayload = Utils.convertToHarPayload(message, 1000000, Context.now(), "", "HAR");
            } catch (Exception e) {
                harPayload = null;
            }
            
            Map<String, Object> config = new HashMap<>();
            try {
                config = mapper.readValue(harPayload, Map.class);
            } catch (Exception e) {
                // TODO: handle exception
            }
            
            assertEquals("GET", config.get("method"));
            assertEquals("HAR", config.get("source"));
            assertEquals("https://juiceshop.akto.io/rest/captcha//metrics", config.get("path"));
            assertNotNull(config.get("requestHeaders"));
            assertNotNull(config.get("responseHeaders"));
            

            System.out.println(harPayload);

    }

}

