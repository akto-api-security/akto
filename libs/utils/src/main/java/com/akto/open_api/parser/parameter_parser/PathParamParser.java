package com.akto.open_api.parser.parameter_parser;

import java.util.List;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

public class PathParamParser {

    public static String replacePathParameter(String path, List<List<Parameter>> parameterLists) {
        for (List<Parameter> parameters : parameterLists) {
            if (parameters != null) {
                for (Parameter parameter : parameters) {
                    path = replacePathParameterUtil(path, parameter);
                }
            }
        }
        return path;
    }

    private static String replacePathParameterUtil(String path, Parameter parameter) {
        if (parameter.getIn().equals("path")) {
            String parameterName = parameter.getName();
            String parameterNameInUrl = "{" + parameterName + "}";
            String replacement = parameterNameInUrl;

            /*
             * If the URL is a template URL,
             * then the examples should not be used
             * for the URL to be treated as a template URL.
             */

            if (parameter.getSchema() != null) {
                Schema<?> schema = parameter.getSchema();
                String type = schema.getType();
                String format = schema.getFormat();

                String example = schema.getExample() != null ?  String.valueOf(schema.getExample()) : parameter.getExample() != null ? String.valueOf(parameter.getExample()): null;
                if(example != null) {
                    replacement = example;
                }
                else if ("integer".equalsIgnoreCase(type)) {
                    replacement = "INTEGER";
                } else if ("string".equalsIgnoreCase(type)) {
                    if ("uuid".equalsIgnoreCase(format)) {
                        replacement = "UUID";
                    } else {
                        replacement = "STRING";
                    }
                }
            }

            if (replacement.equals(parameterNameInUrl)) {
                replacement = "STRING";
            }

            path = path.replace(parameterNameInUrl, replacement);
        }
        return path;
    }
}