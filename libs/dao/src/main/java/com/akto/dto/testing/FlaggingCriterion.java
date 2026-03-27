package com.akto.dto.testing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Optional probe criterion that was satisfied when flagging.
 * JSON: { "id": "...", "name": "...", "met": true }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FlaggingCriterion {

    private String id;
    private String name;
    private boolean met;
}
