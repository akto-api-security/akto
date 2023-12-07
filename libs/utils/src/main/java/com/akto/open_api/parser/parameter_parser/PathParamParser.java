package com.akto.open_api.parser.parameter_parser;

import java.util.List;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import io.swagger.oas.inflector.examples.ExampleBuilder;
import io.swagger.oas.inflector.examples.models.Example;
import io.swagger.v3.oas.models.parameters.Parameter;

public class PathParamParser {

    private static final LoggerMaker loggerMaker = new LoggerMaker(PathParamParser.class, LogDb.DASHBOARD);

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
             * TODO: If it is a template endpoint, parse it such that it can be captured as
             * a template url in runtime, in some cases.
             */

            String existingExample = CommonParser.getExistingExample(parameter);
            if (existingExample != null) {
                replacement = existingExample;
            } else if (parameter.getStyle().equals(Parameter.StyleEnum.SIMPLE) &&
                    parameter.getExplode().equals(false) &&
                    parameter.getSchema() != null) {
                Example example = ExampleBuilder.fromSchema(parameter.getSchema(), null);
                replacement = example.asString();
            } else {
                loggerMaker.infoAndAddToDb("unable to handle path parameter " + parameterName + " with style "
                        + parameter.getStyle() + " and explode " + parameter.getExplode());
            }
            path = path.replace(parameterNameInUrl, replacement);
        }
        return path;
    }
}