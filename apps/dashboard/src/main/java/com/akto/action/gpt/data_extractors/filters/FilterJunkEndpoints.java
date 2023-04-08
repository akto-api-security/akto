package com.akto.action.gpt.data_extractors.filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FilterJunkEndpoints implements Filter<String>{

    private static final List<String> KEYWORDS_TO_AVOID = Arrays.asList("css", "js", "html", "scss", "xml");
    @Override
    public List<String> filterData(List<String> data) {
        List<String> result = new ArrayList<>();
        for(String endpoint: data){
            boolean skipEndpoint = false;
            for(String keyword: KEYWORDS_TO_AVOID){
                if(endpoint.contains(keyword)){
                    skipEndpoint = true;
                    break;
                }
            }
            if(!skipEndpoint){
                result.add(endpoint);
            }
        }
        return result;
    }
}
