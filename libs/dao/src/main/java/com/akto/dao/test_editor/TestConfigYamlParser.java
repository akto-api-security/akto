package com.akto.dao.test_editor;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import com.akto.dao.test_editor.api_filters.ApiFilter;
import com.akto.dao.test_editor.api_filters.ApiTypeFilter;
import com.akto.dao.test_editor.api_filters.ContainsParamFilter;
import com.akto.dao.test_editor.api_filters.ExcludeMethodsFilter;
import com.akto.dao.test_editor.api_filters.MustContainHeaderFilter;
import com.akto.dao.test_editor.api_filters.MustContainKeysFilter;
import com.akto.dao.test_editor.api_filters.PaginationFilter;
import com.akto.dao.test_editor.api_filters.ResponseStatusFilter;
import com.akto.dao.test_editor.api_filters.UrlContainsFilter;
import com.akto.dao.test_editor.api_filters.VersionRegexFilter;
import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class TestConfigYamlParser {

    private List<ApiFilter> filters = Arrays.asList(new ApiTypeFilter(), new ExcludeMethodsFilter(), 
        new MustContainKeysFilter(), new PaginationFilter(), new ResponseStatusFilter(), 
        new UrlContainsFilter(), new VersionRegexFilter(), new ContainsParamFilter());

    public TestConfigYamlParser() { }

    public TestConfig parseTemplate(String fileName) {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try {
            //String filePath = new File("").getAbsolutePath() + "/libs/dao/src/main/java/com/akto/dto/test_editor/" + fileName + ".yaml";
            String filePath = "/Users/admin/akto_code/akto/libs/dao/src/main/java/com/akto/dto/test_editor/inbuilt_test_yaml_files/" + fileName + ".yaml";
            TestConfig config = mapper.readValue(new File(filePath), TestConfig.class);
            return config;
        } catch (Exception e) {
            System.out.print(e);
            return null;
        }
    }

    public Boolean validateAgainstTemplate(TestConfig testConfig, RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) {
        
        Boolean isValid;
        if (rawApi == null) {
            return false;
        }
        for (ApiFilter filter: this.filters) {
            isValid = filter.isValid(testConfig, rawApi, apiInfoKey);
            if (!isValid) {
                return false;
            }
        }

        return true;

    }

}
