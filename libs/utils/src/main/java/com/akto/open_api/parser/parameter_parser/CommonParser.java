package com.akto.open_api.parser.parameter_parser;

import io.swagger.v3.oas.models.parameters.Parameter;

public class CommonParser {

    public static String getExistingExample(Parameter parameter) {
        String ret = null;
        if (parameter.getExample() != null) {
            ret = parameter.getExample().toString();
        } else if (parameter.getExamples() != null) {
            for (String exampleName : parameter.getExamples().keySet()) {
                ret = parameter.getExamples().get(exampleName).getValue().toString();
                break;
            }
        } else if (parameter.getSchema() != null && parameter.getSchema().getExample() != null) {
            ret = parameter.getSchema().getExample().toString();
        }
        return ret;
    }
}