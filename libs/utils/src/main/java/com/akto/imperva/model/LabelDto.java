package com.akto.imperva.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LabelDto {
    private Integer confidence;
    private String labelProgressStatus;
    private String name;
    private Boolean sensitive;
    private String status;
}