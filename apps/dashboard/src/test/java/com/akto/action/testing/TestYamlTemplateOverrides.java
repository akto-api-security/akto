package com.akto.action.testing;

import com.akto.MongoBasedTest;
import com.akto.dao.AccountsDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.test_editor.YamlTemplateOverrideDao;
import com.akto.dto.Account;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.listener.InitializerListener;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class TestYamlTemplateOverrides extends MongoBasedTest {

    private static final String TEMPLATE_ID = "OVERRIDE_MERGE_TEST";

    private static String yamlFor(String id, String name) {
        return "id: " + id + "\n" +
                "info:\n" +
                "  name: \"" + name + "\"\n" +
                "  description: \"desc\"\n" +
                "  details: \"details\"\n" +
                "  impact: \"impact\"\n" +
                "  category:\n" +
                "    name: NO_AUTH\n" +
                "    shortName: Broken Authentication\n" +
                "    displayName: Broken User Authentication (BUA)\n" +
                "  subCategory: " + id + "\n" +
                "  severity: HIGH\n" +
                "  tags: []\n" +
                "  references: []\n" +
                "api_selection_filters:\n" +
                "  response_code:\n" +
                "    gte: 200\n" +
                "    lt: 300\n" +
                "execute:\n" +
                "  type: single\n" +
                "  requests:\n" +
                "    - req:\n" +
                "        - remove_auth_header: true\n" +
                "validate:\n" +
                "  response_code:\n" +
                "    gte: 200\n" +
                "    lt: 300\n";
    }

    private void resetState() throws Exception {
        YamlTemplateDao.instance.deleteAll(Filters.eq(Constants.ID, TEMPLATE_ID));
        YamlTemplateOverrideDao.instance.deleteAll(Filters.eq(Constants.ID, TEMPLATE_ID));
        YamlTemplateOverrideDao.instance.deleteAll(Filters.eq(Constants.ID, "UNKNOWN_OVERRIDE_ID"));
        AccountsDao.instance.deleteAll(Filters.eq(Constants.ID, ACCOUNT_ID));
        Account account = new Account(ACCOUNT_ID, "test");
        AccountsDao.instance.insertOne(account);
        Context.accountId.set(ACCOUNT_ID);
    }

    private YamlTemplate insertSystemTemplate(String content) throws Exception {
        TestConfig config = TestConfigYamlParser.parseTemplate(content);
        YamlTemplate template = new YamlTemplate(TEMPLATE_ID, Context.now(), Constants._AKTO, Context.now(), content, config.getInfo(), null);
        YamlTemplateDao.instance.insertOne(template);
        return template;
    }

    @Test
    public void fetchUsesOverrideForActiveAccount() throws Exception {
        resetState();
        String systemYaml = yamlFor(TEMPLATE_ID, "System name");
        String overrideYaml = yamlFor(TEMPLATE_ID, "Override name");
        insertSystemTemplate(systemYaml);

        TestConfig overrideConfig = TestConfigYamlParser.parseTemplate(overrideYaml);
        YamlTemplate override = new YamlTemplate(TEMPLATE_ID, Context.now(), "user", Context.now(), overrideYaml, overrideConfig.getInfo(), null);
        YamlTemplateOverrideDao.instance.insertOne(override);

        Map<String, TestConfig> configMap = YamlTemplateDao.instance.fetchTestConfigMap(true, false, 0, 10, Filters.eq(Constants.ID, TEMPLATE_ID));
        assertEquals("Override name", configMap.get(TEMPLATE_ID).getInfo().getName());
        assertEquals(overrideYaml, YamlTemplateDao.instance.findEffectiveOne(TEMPLATE_ID, null).getContent());
        assertEquals("Override name", YamlTemplateDao.instance.fetchTestInfoMap(Filters.eq(Constants.ID, TEMPLATE_ID)).get(TEMPLATE_ID).getName());
    }

    @Test
    public void fetchIgnoresOverrideForInactiveAccount() throws Exception {
        resetState();
        String systemYaml = yamlFor(TEMPLATE_ID, "System name");
        String overrideYaml = yamlFor(TEMPLATE_ID, "Override name");
        insertSystemTemplate(systemYaml);

        TestConfig overrideConfig = TestConfigYamlParser.parseTemplate(overrideYaml);
        YamlTemplate override = new YamlTemplate(TEMPLATE_ID, Context.now(), "user", Context.now(), overrideYaml, overrideConfig.getInfo(), null);
        YamlTemplateOverrideDao.instance.insertOne(override);

        AccountsDao.instance.updateOne(Filters.eq(Constants.ID, ACCOUNT_ID), Updates.set(Account.INACTIVE_STR, true));

        Map<String, TestConfig> configMap = YamlTemplateDao.instance.fetchTestConfigMap(true, false, 0, 10, Filters.eq(Constants.ID, TEMPLATE_ID));
        assertEquals("System name", configMap.get(TEMPLATE_ID).getInfo().getName());
        assertEquals(systemYaml, YamlTemplateDao.instance.findEffectiveOne(TEMPLATE_ID, null).getContent());
    }

    @Test
    public void overrideZipReplacesMatchingAktoIdsOnly() throws Exception {
        resetState();
        String systemYaml = yamlFor(TEMPLATE_ID, "System name");
        insertSystemTemplate(systemYaml);

        String overrideYaml = yamlFor(TEMPLATE_ID, "Override from zip");
        String unknownYaml = yamlFor("UNKNOWN_OVERRIDE_ID", "Unknown test");
        byte[] zip = zipOf(new String[][] {
                {"tests/" + TEMPLATE_ID + ".yaml", overrideYaml},
                {"tests/UNKNOWN_OVERRIDE_ID.yaml", unknownYaml}
        });

        InitializerListener.processTemplateFilesZip(zip, "user", YamlTemplateSource.CUSTOM.toString(), "http://override.zip", true);

        YamlTemplate storedOverride = YamlTemplateOverrideDao.instance.findOne(Filters.eq(Constants.ID, TEMPLATE_ID));
        assertNotNull(storedOverride);
        assertEquals("Override from zip", storedOverride.getInfo().getName());
        assertEquals("http://override.zip", storedOverride.getRepositoryUrl());
        assertNull(YamlTemplateOverrideDao.instance.findOne(Filters.eq(Constants.ID, "UNKNOWN_OVERRIDE_ID")));

        YamlTemplate system = YamlTemplateDao.instance.findOne(Filters.eq(Constants.ID, TEMPLATE_ID));
        assertEquals("System name", system.getInfo().getName());
    }

    private byte[] zipOf(String[][] files) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (String[] file : files) {
                zipOutputStream.putNextEntry(new ZipEntry(file[0]));
                zipOutputStream.write(file[1].getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }
}
