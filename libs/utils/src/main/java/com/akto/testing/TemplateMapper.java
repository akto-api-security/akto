package com.akto.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.AktoDataType;
import com.akto.dto.CustomDataType;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.data_types.Conditions.Operator;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;

public class TemplateMapper {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TemplateMapper.class, LogDb.DASHBOARD);
    public void createTestTemplateForCustomDataType(CustomDataType dataType) {

        if (dataType == null) {
            return;
        }

        /*
         * If a user has explicitly marked the data type
         * to be skipped from test template creation.
         */
        if (dataType.isSkipDataTypeTestTemplateMapping()) {
            return;
        }

        Set<SingleTypeInfo.Position> positions = getSensitivePositions(dataType.isSensitiveAlways(),
                dataType.getSensitivePosition());

        boolean inactive = getInactivity(!dataType.isActive(), dataType.isSensitiveAlways(),
                dataType.getSensitivePosition());
        String filter = "";
        String currentDepthTabs = TAB;
        String name = dataType.getName();
        name = name.replace(" ", "_");
        boolean keyConditionsExist = dataType.getKeyConditions() != null;
        boolean valueConditionsExist = dataType.getValueConditions() != null;
        Conditions.Operator operator = dataType.getOperator();

        filter = createFilterForPositions(name, operator, keyConditionsExist, valueConditionsExist, positions,
                currentDepthTabs);
        String severity = TestConfig.DYNAMIC_SEVERITY;
        prepareAndInsertTemplate(name, filter, inactive, severity);
    }

    public void createTestTemplateForAktoDataType(AktoDataType dataType) {
        Set<SingleTypeInfo.Position> positions = getSensitivePositions(dataType.isSensitiveAlways(),
                dataType.getSensitivePosition());
                
        boolean inactive = getInactivity(false, dataType.isSensitiveAlways(),
                dataType.getSensitivePosition());
        String filter = "";
        String currentDepthTabs = TAB;
        String name = dataType.getName();
        name = name.replace(" ", "_");

        filter = createFilterForPositions(name, Conditions.Operator.AND, false, true, positions, currentDepthTabs);
        String severity = TestConfig.DYNAMIC_SEVERITY;
        prepareAndInsertTemplate(name, filter, inactive, severity);
    }

    private void prepareAndInsertTemplate(String name, String filter, boolean inactive, String severity) {
        String template = TEST_TEMPLATE_FORMAT;

        template = template.replace("${DATA_TYPE_ID}", name);
        name = name.replace("_", " ");
        template = template.replace("${DATA_TYPE}", name);
        template = template.replace("${FILTERS}", filter);
        template = template.replace("${INACTIVE_TYPE}", String.valueOf(inactive));
        template = template.replace("${SEVERITY_TAG}", severity);
        // TODO: Use severity from data types, once severity for data types is released.
        template = template.replace("${SEVERITY_TAG_1}", "LOW");
        template = template.replace("${SEVERITY_TAG_2}", "HIGH");
        template = template.replace("${SEVERITY_TAG_3}", "MEDIUM");


        TestConfig testConfig = null;
        try {
            testConfig = TestConfigYamlParser.parseTemplate(template);

            Bson proj = Projections.include(YamlTemplate.HASH);
            List<YamlTemplate> hashValueTemplates = YamlTemplateDao.instance.findAll(Filters.empty(), proj);

            Map<String, List<YamlTemplate>> mapIdToHash = hashValueTemplates.stream()
                    .collect(Collectors.groupingBy(YamlTemplate::getId));
            String testConfigId = testConfig.getId();
            List<YamlTemplate> existingTemplatesInDb = mapIdToHash.get(testConfigId);
            if (existingTemplatesInDb != null && existingTemplatesInDb.size() == 1) {
                int existingTemplateHash = existingTemplatesInDb.get(0).getHash();
                if (existingTemplateHash == template.hashCode()) {
                    return;
                } else {
                    loggerMaker.infoAndAddToDb("Updating test yaml for data type: " + testConfigId, LogDb.DASHBOARD);
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error parsing template for data type " + name);
            return;
        }

        int now = Context.now();
        List<Bson> updates = new ArrayList<>(
                Arrays.asList(
                        Updates.setOnInsert(YamlTemplate.CREATED_AT, now),
                        Updates.setOnInsert(YamlTemplate.AUTHOR, Constants._AKTO),
                        Updates.set(YamlTemplate.UPDATED_AT, now),
                        Updates.set(YamlTemplate.HASH, template.hashCode()),
                        Updates.set(YamlTemplate.CONTENT, template),
                        Updates.set(YamlTemplate.INFO, testConfig.getInfo())));
        updates.add(
                Updates.setOnInsert(YamlTemplate.SOURCE, YamlTemplateSource.AKTO_TEMPLATES.toString()));

        try {
            Object inactiveObject = TestConfigYamlParser.getFieldIfExists(template,
                    YamlTemplate.INACTIVE);
            if (inactiveObject != null && inactiveObject instanceof Boolean) {
                inactive = (boolean) inactiveObject;
                updates.add(Updates.set(YamlTemplate.INACTIVE, inactive));
            }
        } catch (Exception e) {
        }

        String id = testConfig.getId();
        YamlTemplateDao.instance.updateOne(
                Filters.eq(Constants.ID, id),
                Updates.combine(updates));
    }

    private String createFilterForPositions(String dataType, Conditions.Operator operator, boolean keyConditionsExist, boolean valueConditionsExist, Set<SingleTypeInfo.Position> positions, String currentDepthTabs) {
        String filter = "";
        filter += currentDepthTabs + Operator.OR.name().toLowerCase() + COLON + NEW_LINE;
        currentDepthTabs += TAB;
        for (SingleTypeInfo.Position position : positions) {
            String positionKey = getPositionKey(position);
            String tempCurrentDepth = new String(currentDepthTabs);
            filter += currentDepthTabs + DASH + SPACE + positionKey + COLON + NEW_LINE;
            currentDepthTabs += TAB + TAB;
            filter += createFilterForPosition(dataType, operator, keyConditionsExist, valueConditionsExist,
                    currentDepthTabs);
            currentDepthTabs = tempCurrentDepth;
        }
        return filter;
    }

    private String createFilterForPosition(String datatype, Conditions.Operator operator, boolean keyConditionsExist, boolean valueConditionsExist, String currentDepthTabs) {
        String filter = "";
        switch (operator) {
            case AND:
                filter += currentDepthTabs + "for_one" + COLON + NEW_LINE;
                currentDepthTabs += TAB;
                if (keyConditionsExist) {
                    filter += createSingleFilter(datatype, "key", currentDepthTabs);
                }
                if (valueConditionsExist) {
                    filter += createSingleFilter(datatype, "value", currentDepthTabs);
                }
                break;
            case OR:
                filter += currentDepthTabs + "or" + COLON + NEW_LINE;
                if (keyConditionsExist) {
                    String tempCurrentDepth = new String(currentDepthTabs);
                    filter += currentDepthTabs + DASH + SPACE + "for_one" + COLON + NEW_LINE;
                    currentDepthTabs += TAB + TAB;
                    filter += createSingleFilter(datatype, "key", currentDepthTabs);
                    currentDepthTabs = tempCurrentDepth;
                }
                if (valueConditionsExist) {
                    String tempCurrentDepth = new String(currentDepthTabs);
                    filter += currentDepthTabs + DASH + SPACE + "for_one" + COLON + NEW_LINE;
                    currentDepthTabs += TAB + TAB;
                    filter += createSingleFilter(datatype, "value", currentDepthTabs);
                    currentDepthTabs = tempCurrentDepth;
                }
                break;
            default:
                break;
        }
        return filter;
    }

    private String createSingleFilter(String dataType, String position, String currentDepthTabs) {
        String tempCurrentDepth = new String(currentDepthTabs);
        String filter = "";
        filter += currentDepthTabs + position + COLON + NEW_LINE;
        currentDepthTabs += TAB;
        filter += currentDepthTabs + "datatype" + COLON + SPACE + dataType + PERIOD + position + NEW_LINE;
        currentDepthTabs = tempCurrentDepth;
        return filter;
    }

    private String getPositionKey(SingleTypeInfo.Position position) {
        String positionKey = "";
        switch (position) {
            case REQUEST_HEADER:
                positionKey = "request_headers";
                break;
            case RESPONSE_HEADER:
                positionKey = "response_headers";
                break;
            case REQUEST_PAYLOAD:
                positionKey = "request_payload";
                break;
            case RESPONSE_PAYLOAD:
                positionKey = "response_payload";
                break;
            default:
                break;
        }
        return positionKey;
    }

    private boolean getInactivity(boolean defaultInactivity, boolean isSensitiveAlways,
            List<SingleTypeInfo.Position> sensitivePositions) {

        /*
         * If not sensitive anywhere, mark as inactive.
         */
        if (!isSensitiveAlways && (sensitivePositions == null || sensitivePositions.isEmpty())) {
            return true;
        }

        /*
         * If sensitive only in request , mark as inactive,
         * since only sensitive in response is in scope for vulnerability.
         */
        if (!isSensitiveAlways) {
            List<SingleTypeInfo.Position> temp = new ArrayList<>();
            for (SingleTypeInfo.Position position : sensitivePositions) {
                /*
                 * For checking sensitive data vulnerabilities,
                 * only response is in scope.
                 */
                if (SingleTypeInfo.Position.REQUEST_HEADER.equals(position) ||
                        SingleTypeInfo.Position.REQUEST_PAYLOAD.equals(position)) {
                            temp.add(position);
                }
            }
            if(temp.size() == sensitivePositions.size()){
                return true;
            }
        }

        return defaultInactivity;
    }

    private Set<SingleTypeInfo.Position> getSensitivePositions(boolean isSensitiveAlways,
            List<SingleTypeInfo.Position> sensitivePositions) {
        Set<SingleTypeInfo.Position> positions = new HashSet<>();
        if (isSensitiveAlways) {
            positions.add(SingleTypeInfo.Position.RESPONSE_HEADER);
            positions.add(SingleTypeInfo.Position.RESPONSE_PAYLOAD);
        }
        if (sensitivePositions != null && !sensitivePositions.isEmpty()) {
            for (SingleTypeInfo.Position position : sensitivePositions) {
                /*
                 * For checking sensitive data vulnerabilities,
                 * only response is in scope.
                 */
                if (SingleTypeInfo.Position.REQUEST_HEADER.equals(position) ||
                        SingleTypeInfo.Position.REQUEST_PAYLOAD.equals(position)) {
                    continue;
                }
                positions.add(position);
            }
        }
        if (positions == null || positions.isEmpty()) {
            /*
             * In case a datatype is not defined as sensitive,
             * Then create a test with all positions but mark as inactive.
             */
            positions = new HashSet<>();
            positions.add(SingleTypeInfo.Position.RESPONSE_HEADER);
            positions.add(SingleTypeInfo.Position.RESPONSE_PAYLOAD);
        }
        return positions;
    }

    final String TAB = "  ";
    final String COLON = ":";
    final String DASH = "-";
    final String NEW_LINE = "\n";
    final String SPACE = " ";
    final String PERIOD = ".";

    final String TEST_TEMPLATE_FORMAT = "id: SENSITIVE_DATA_EXPOSURE_${DATA_TYPE_ID}\n" +
            "info:\n" +
            "  name: \"Sensitive data exposure for ${DATA_TYPE}\"\n" +
            "  description: |\n" +
            "    This issue detects the exposure of sensitive data of type ${DATA_TYPE} within the API.\n" +
            "  details: >\n" +
            "    The API endpoint exposes ${DATA_TYPE}. Such exposure can lead to unauthorized access or misuse of sensitive data, resulting in data breaches or violations of compliance policies.\n"
            +
            "  impact: >\n" +
            "    The exposure of ${DATA_TYPE} can lead to privacy violations, data theft, and potential exploitation. It can compromise user data security and may result in financial losses, reputation damage, or legal and compliance issues for the organization.\n"
            +
            "  category:\n" +
            "    name: EDE\n" +
            "    shortName: Sensitive Data Exposure\n" +
            "    displayName: Excessive Data Exposure (EDE)\n" +
            "  subCategory: SENSITIVE_DATA_EXPOSURE_${DATA_TYPE_ID}\n" +
            "  severity: ${SEVERITY_TAG}\n" +
            "  tags:\n" +
            "    - Business logic\n" +
            "    - OWASP top 10\n" +
            "    - HackerOne top 10\n" +
            "  references:\n" +
            "    - \"https://owasp.org/API-Security/editions/2019/en/0xa3-excessive-data-exposure/\"\n"
            +
            "    - \"https://owasp.org/API-Security/editions/2023/en/0xa3-broken-object-property-level-authorization/\"\n"
            +
            "    - \"https://owasp.org/www-project-top-ten/2017/A3_2017-Sensitive_Data_Exposure\"\n" +
            "    - \"https://cwe.mitre.org/data/definitions/213.html\"\n" +
            "  cwe:\n" +
            "    - CWE-213\n" +
            "    - CWE-319\n" +
            "  cve:\n" +
            "    - CVE-2022-0235\n" +
            "settings:\n" +
            "  plan: PRO\n" +
            "  nature: NON_INTRUSIVE\n" +
            "  duration: FAST\n" +
            "\n" + 
            "dynamic_severity:\n" +
            "- check:\n" +
            "    api_access_type:\n" +
            "      eq: private\n" +
            "  return: ${SEVERITY_TAG_1}\n" +
            "- check:\n" +
            "    api_access_type:\n" +
            "      eq: public\n" +
            "  return: ${SEVERITY_TAG_2}\n" +
            "- return: ${SEVERITY_TAG_3}\n" +
            "\n" + 
            "api_selection_filters:\n" +
            "${FILTERS}" +
            "\n" +
            "execute:\n" +
            "  type: passive\n" +
            "  requests:\n" +
            "    - req: []\n" +
            "\n" +
            "validate:\n" +
            "  response_code:\n" +
            "    gte: 200\n" +
            "    lt: 400\n" +
            "\n" +
            "inactive: ${INACTIVE_TYPE}" +
            "\n\n";

}