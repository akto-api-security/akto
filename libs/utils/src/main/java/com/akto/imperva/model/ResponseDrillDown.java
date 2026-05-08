package com.akto.imperva.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponseDrillDown {
    private Map<String, DataTypeDto.ParameterDrillDown[]> contentTypeToResponseBody;
    private ErrorDto error;
    private HeaderDto[] headerList;
    private Boolean isError;
}