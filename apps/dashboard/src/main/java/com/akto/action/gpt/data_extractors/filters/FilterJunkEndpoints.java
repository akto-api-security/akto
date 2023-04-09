package com.akto.action.gpt.data_extractors.filters;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;

public class FilterJunkEndpoints implements Filter<String>{

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(FilterJunkEndpoints.class);
    private final static Pattern urlPattern = Pattern.compile("^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))(.*))$", Pattern.CASE_INSENSITIVE);

    private static int countSwitches(String endpoint) {
        boolean lastCharWasAlphabet = true;
        int countSwitches = 0;
        for (int i = 0; i < endpoint.length(); i++) {
            char c = endpoint.charAt(i);
            boolean thisCharIsAlphabet = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');

            if (thisCharIsAlphabet ^ lastCharWasAlphabet) {
                countSwitches++;
            }
        }

        return countSwitches;
    }

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

            if (urlPattern.matcher(endpoint).matches()) {
                try {
                    URI uri = new URI(endpoint);
                    endpoint = uri.getPath();
                } catch (URISyntaxException e) {
                    skipEndpoint = true;
                }
            }

            skipEndpoint = countSwitches(endpoint) > 20;

            if(!skipEndpoint){
                result.add(endpoint);
            } else {
                logger.info("skipping: " + endpoint);
            }

            if (result.size() > 100) {
                logger.info("skipping remaining: " + (result.size() - 100));
                break;
            }
        }
        return result;
    }

    public static void main(String[] args) {
        String e = "/questions/70067185/css-only-typewriter-animation-over-multiple-lines";
        
        System.out.println(countSwitches(e));
    }
}
