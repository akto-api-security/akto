# DRIFT_REPORT — duplicated common files (Phase 1 finding)

Generated during Phase 1. Input to Phase 2/4 consolidation. Canonical picks:
- device identity: `github-cli-hooks/akto_machine_id.py` (most complete, 249 lines)
- observability entry: shared `run_observability_hook` (the 7 `akto-hooks.py` collapse to it)

## akto_machine_id.py — 9 copies

| md5 | lines | path | == canonical? |
|---|---|---|---|
| a97181596132a5c80513010975daac08 | 184 | amp-cli-hooks/akto_machine_id.py | drift |
| 013628da5601ea14e51a3d475e91e389 | 226 | claude-cli-hooks/akto_machine_id.py | drift |
| 48c123f54722ea52d1f022106e65ed88 | 223 | codex-cli-hooks/akto_machine_id.py | drift |
| c0f30766433c244ddd40ee2f7bd3ed87 | 225 | cursor-hooks/akto_machine_id.py | drift |
| 28f3d2a01acf7eae03e5916099e8ce7c | 229 | gemini-cli-hooks/akto_machine_id.py | drift |
| 992dd3ba3abdeca35d1cb618e10be969 | 249 | github-cli-hooks/akto_machine_id.py | **canonical** |
| c09417b8e7fd55b8e6fce4b73a2d0eb3 | 96 | hermes/akto_machine_id.py | drift |
| 28f3d2a01acf7eae03e5916099e8ce7c | 229 | kiro-cli-hooks/akto_machine_id.py | drift |
| a97181596132a5c80513010975daac08 | 184 | opencode/akto_machine_id.py | drift |

## akto-hooks.py — 7 copies (observability entry)

| md5 | lines | path |
|---|---|---|
| 84796335763adcccd45d0c4b57162ff9 | 23 | amp-cli-hooks/akto-hooks.py |
| 8982cae92803a62ea5a547ee59784adc | 20 | claude-cli-hooks/akto-hooks.py |
| 51455014d6ab6d4a9403116383028d3b | 21 | codex-cli-hooks/akto-hooks.py |
| 3e64a26941353ab4f8522966b6e36205 | 19 | cursor-hooks/akto-hooks.py |
| 736fca27ead27022d179b620049d3386 | 21 | gemini-cli-hooks/akto-hooks.py |
| 4c0210bf8870bdd66b8e79b627629d24 | 20 | github-cli-hooks/akto-hooks.py |
| 5d562efd640f524b4b2a218db036435a | 40 | kiro-cli-hooks/akto-hooks.py |
