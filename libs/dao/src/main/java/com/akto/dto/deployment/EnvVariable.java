package com.akto.dto.deployment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnvVariable {
    private String key;
    private String value;
    private boolean editable;
}



