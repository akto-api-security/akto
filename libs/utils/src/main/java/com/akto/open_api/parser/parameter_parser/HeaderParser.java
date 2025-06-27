package com.akto.open_api.parser.parameter_parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import io.swagger.oas.inflector.examples.ExampleBuilder;
import io.swagger.oas.inflector.examples.models.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.parameters.Parameter;

public class HeaderParser {

    private static final LoggerMaker loggerMaker = new LoggerMaker(HeaderParser.class, LogDb.DASHBOARD);

    public static Map<String, String> buildResponseHeaders(Map<String, Header> responseHeaders) {
        Map<String, String> headers = new HashMap<>();

        for (String key : responseHeaders.keySet()) {
            Header header = responseHeaders.get(key);
            String headerValue = "";
            if (header.getExample() != null) {
                headerValue = header.getExample().toString();
            } else if (header.getExamples() != null) {
                for (String exampleName : header.getExamples().keySet()) {
                    headerValue = header.getExamples().get(exampleName).getValue().toString();
                }
            } else {
                Example example = ExampleBuilder.fromSchema(header.getSchema(), null);
                headerValue = example.asString();
            }
            headers.put(key, headerValue);
        }
        return headers;
    }

    public static Map<String, String> buildHeaders(List<List<Parameter>> headersList) {
        Map<String, String> headersFinal = new HashMap<>();
        for (List<Parameter> headers : headersList) {
            if (headers != null) {
                for (Parameter header : headers) {
                    buildHeadersUtil(header, headersFinal);
                }
            }
        }
        return headersFinal;
    }

    private static void buildHeadersUtil(Parameter header, Map<String, String> headers) {
        if (header.getIn().equals("header")) {
            String headerName = header.getName();
            String headerValue = "";

            String existingExample = CommonParser.getExistingExample(header);
            if (existingExample != null) {
                headerValue = existingExample;
            } else if (header.getStyle().equals(Parameter.StyleEnum.SIMPLE) &&
                    header.getExplode().equals(false) &&
                    header.getSchema() != null) {
                Example example = ExampleBuilder.fromSchema(header.getSchema(), null);
                headerValue = example.asString();
            } else {
                loggerMaker.infoAndAddToDb("unable to handle header " + headerName + " with style "
                        + header.getStyle() + " and explode " + header.getExplode());
            }
            headers.put(headerName, headerValue);
        }
    }
}