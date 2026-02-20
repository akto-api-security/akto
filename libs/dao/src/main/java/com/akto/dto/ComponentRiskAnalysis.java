package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComponentRiskAnalysis {

    private boolean isComponentNameRisky;
    private boolean isComponentMalicious;
    private String evidence;
}
