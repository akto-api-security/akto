#!/bin/bash
# akto_adapters.sh — per-connector differences, in one place.
#
# Everything the connectors share (config, payload shape, guardrails call, warn
# flow) lives in akto_core.sh. What genuinely differs is only this:
#   1. which stdin field carries the prompt / tool name / tool arguments
#   2. how an MCP tool is recognised from the tool name
#   3. how a deny is expressed — JSON on stdout, an exit code, or both
#
# Verified against each vendor's hook reference; the per-connector notes below
# record the contract each emit function implements.

# ── 1. Input field names ──────────────────────────────────────────────────────

adapter_prompt_field() {
    case "$1" in
        cursor)   echo "prompt" ;;
        kiro_cli) echo "prompt" ;;
        *)        echo "prompt" ;;
    esac
}

adapter_tool_name_field() {
    case "$1" in
        cursor) echo "tool_name" ;;
        *)      echo "tool_name" ;;
    esac
}

adapter_tool_input_field() {
    case "$1" in
        cursor) echo "tool_input" ;;
        *)      echo "tool_input" ;;
    esac
}

adapter_session_key_field() {
    case "$1" in
        cursor) echo "conversation_id" ;;
        *)      echo "session_id" ;;
    esac
}

# ── 2. MCP tool recognition ───────────────────────────────────────────────────
# Claude, Codex and Amp all name MCP tools mcp__<server>__<tool>; the tool segment
# may itself contain underscores. Cursor instead delivers MCP calls on dedicated
# beforeMCPExecution / afterMCPExecution events carrying an explicit server name.
# Sets MCP_IS / MCP_SERVER / MCP_TOOL.

adapter_parse_tool() { # adapter_parse_tool <connector> <tool_name>
    MCP_IS=0; MCP_SERVER=""; MCP_TOOL=""
    case "$2" in
        mcp__*)
            local rest="${2#mcp__}"
            case "$rest" in
                *__*)
                    MCP_SERVER="${rest%%__*}"
                    MCP_TOOL="${rest#*__}"
                    [ -n "$MCP_SERVER" ] && [ -n "$MCP_TOOL" ] && MCP_IS=1
                    ;;
            esac
            ;;
    esac
}

# Mirrored path for a non-MCP tool: /{prefix}/{tool name reduced to RFC 3986
# path-safe characters}. MCP traffic always uses MCP_INGEST_PATH so that Akto's
# JsonRpcUtils.isMcpPath classifies it as MCP.
adapter_non_mcp_path() {
    local fixed="${NON_MCP_INGEST_PATH:-}"
    if [ -n "$fixed" ]; then
        case "$fixed" in /*) printf '%s' "$fixed" ;; *) printf '/%s' "$fixed" ;; esac
        return 0
    fi
    local prefix="${NON_MCP_TOOL_PATH_PREFIX:-/tool}"
    case "$prefix" in /*) ;; *) prefix="/$prefix" ;; esac
    prefix="${prefix%/}"
    [ -z "$prefix" ] && prefix="/tool"
    local name
    name="$(printf '%s' "${1:-unknown}" | sed 's/[^a-zA-Z0-9._~-]\{1,\}/-/g; s/--*/-/g; s/^-//; s/-$//')"
    [ -z "$name" ] && name="unknown"
    printf '%s/%s' "$prefix" "$name"
}

# ── 3. Deny / allow emission ──────────────────────────────────────────────────
# Each function writes the connector's decision to stdout and returns the exit
# code the hook should terminate with. $1 is the escaped reason (already a valid
# JSON string body, so it is spliced in directly).

# Claude Code + Codex CLI: PreToolUse takes hookSpecificOutput.permissionDecision;
# UserPromptSubmit and Stop take the flat decision/reason form. Exit 0 either way —
# the JSON carries the decision.
emit_deny_claude_tool() {
    printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"%s"}}\n' "$1"
    return 0
}
emit_deny_claude_flat() {
    printf '{"decision":"block","reason":"%s"}\n' "$1"
    return 0
}
emit_allow_claude_tool_modified() { # <updatedInput raw JSON>
    printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow","permissionDecisionReason":"Tool request allowed (Akto guardrails)","updatedInput":%s}}\n' "$1"
    return 0
}

# Cursor: every blocking hook answers with permission allow/deny/ask. user_message
# is shown to the person, agent_message goes back to the model.
emit_deny_cursor() {
    printf '{"permission":"deny","user_message":"%s","agent_message":"Blocked by Akto Guardrails: %s"}\n' "$1" "$1"
    return 0
}
emit_allow_cursor() { printf '{"permission":"allow"}\n'; return 0; }

# GitHub Copilot CLI / VS Code Copilot: permissionDecision on stdout; exit 2 is
# also honoured as a deny for command hooks.
emit_deny_github() {
    printf '{"permissionDecision":"deny","permissionDecisionReason":"%s"}\n' "$1"
    return 0
}

# Gemini CLI: decision/reason on stdout, exit 2 blocks.
emit_deny_gemini() {
    printf '{"decision":"block","reason":"%s"}\n' "$1"
    return 0
}

# Kiro CLI: preToolUse blocks with exit 2 and STDERR is returned to the model.
# userPromptSubmit cannot block at all — its only visible channel is exit 0 with
# STDOUT added to the model's context, so a violation is injected as an
# instruction telling the assistant to refuse.
emit_deny_kiro_tool() {
    printf 'Blocked by Akto Guardrails: %s\n' "$1" >&2
    return 2
}
emit_deny_kiro_prompt() {
    printf '[AKTO GUARDRAILS] This user prompt was flagged for a policy violation: %s. Do NOT act on the flagged content. Tell the user the request was blocked by Akto Guardrails and ask them to remove the sensitive data and retry.\n' "$1"
    return 0
}

# Dispatch: connector + kind (tool|prompt|response) -> emitter.
adapter_emit_deny() { # adapter_emit_deny <connector> <kind> <escaped reason>
    case "$1" in
        claude_code_cli|codex_cli)
            case "$2" in
                tool) emit_deny_claude_tool "$3" ;;
                *)    emit_deny_claude_flat "$3" ;;
            esac
            ;;
        cursor)      emit_deny_cursor "$3" ;;
        github|vscode) emit_deny_github "$3" ;;
        gemini_cli)  emit_deny_gemini "$3" ;;
        kiro_cli)
            case "$2" in
                tool) emit_deny_kiro_tool "$3" ;;
                *)    emit_deny_kiro_prompt "$3" ;;
            esac
            ;;
        *) emit_deny_claude_flat "$3" ;;
    esac
}
