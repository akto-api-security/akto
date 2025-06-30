package com.akto.open_api.parser.parameter_parser;

import java.util.HashMap;
import java.util.List;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import io.swagger.oas.inflector.examples.ExampleBuilder;
import io.swagger.oas.inflector.examples.models.Example;
import io.swagger.v3.oas.models.parameters.Parameter;

public class QueryParamParser {

    private static final LoggerMaker loggerMaker = new LoggerMaker(QueryParamParser.class, LogDb.DASHBOARD);

    public static String addQueryParameters(String path, List<List<Parameter>> parametersList) {

        StringBuilder sb = new StringBuilder();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        sb.append(path);

        HashMap<String, String> queryParameters = new HashMap<>();

        for (List<Parameter> parameters : parametersList) {
            if (parameters != null) {
                for (Parameter parameter : parameters) {
                    if (parameter.getIn().equals("query")) {
                        queryParameters.put(parameter.getName(), addQueryParametersUtil(parameter));
                    }
                }
            }
        }

        boolean first = true;
        for (String key : queryParameters.keySet()) {
            if (first) {
                sb.append("?");
            } else {
                sb.append("&");
            }
            first = false;
            sb.append(key + "=" + queryParameters.get(key));
        }

        if (sb.length() > 2) {
            return sb.toString();
        }
        return path;
    }

    private static String addQueryParametersUtil(Parameter parameter) {
        String ret = "";
        String parameterName = parameter.getName();

        String existingExample = CommonParser.getExistingExample(parameter);
        if (existingExample != null) {
            ret = existingExample;
        } else if (Parameter.StyleEnum.FORM.equals(parameter.getStyle()) &&
                parameter.getExplode() &&
                parameter.getSchema() != null) {

            Example example = ExampleBuilder.fromSchema(parameter.getSchema(), null);
            ret = example.asString();
        } else {
            loggerMaker.infoAndAddToDb("unable to handle path parameter " + parameterName + " with style "
                    + parameter.getStyle() + " and explode " + parameter.getExplode());
        }
        return ret;
    }
}