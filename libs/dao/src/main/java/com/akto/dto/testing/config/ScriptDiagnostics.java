package com.akto.dto.testing.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What the pre and post scripts did during one validation replay. Filled by the dashboard module today;
 * shaped so mini-testing can fill it on its playground document later. A null phase had no script.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScriptDiagnostics {
    private ScriptPhaseDiagnostics pre;
    private ScriptPhaseDiagnostics post;
}
