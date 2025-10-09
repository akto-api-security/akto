package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MissingUrlResult {
    private String url;
    private Integer collectionId;
    private String name;
    private boolean apiInfo;
    private boolean singleTypeInfo;
}
