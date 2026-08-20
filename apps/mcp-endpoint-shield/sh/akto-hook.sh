#!/bin/bash
# akto-hook.sh — single entry point for every Akto shell hook.
#
#   akto-hook.sh <connector> <event>
#
# Reads the agent's hook JSON on stdin, evaluates it against Akto guardrails, and
# writes that agent's decision to stdout (and/or exits with the code the agent
# treats as a block). Replaces the per-agent Python validators.
#
# Every path fails OPEN: a missing config, an unreachable backend, a malformed
# response or an internal error allows the action. Guardrails must never wedge the
# agent they are protecting.

AKTO_HOOK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AKTO_LIB_DIR="$AKTO_HOOK_DIR/lib"

AKTO_CONNECTOR="${1:-$AKTO_CONNECTOR}"
AKTO_EVENT="${2:-}"

if [ -z "$AKTO_CONNECTOR" ] || [ -z "$AKTO_EVENT" ]; then
    echo "Usage: akto-hook.sh <connector> <event>" >&2
    exit 0
fi

# shellcheck source=lib/akto_core.sh
. "$AKTO_LIB_DIR/akto_core.sh"
# shellcheck source=lib/akto_adapters.sh
. "$AKTO_LIB_DIR/akto_adapters.sh"

akto_log_init "hook-executions"

# Live kill switch. The agent writes ENABLE_PROMPT_HOOKS_* / ENABLE_MCP_HOOKS_*
# into config.env, and setting either to "false" must disable the hook without
# uninstalling it. Prompt/response events read the PROMPT flag, tool events the
# MCP flag; both default to enabled.
akto_hooks_enabled() {
    local suffix flag
    case "$AKTO_CONNECTOR" in
        claude_code_cli) suffix=CLAUDE ;;
        cursor)          suffix=CURSOR ;;
        gemini_cli)      suffix=GEMINI ;;
        codex_cli)       suffix=CODEX ;;
        github)          suffix=GITHUB_CLI ;;
        vscode)          suffix=VSCODE_COPILOT ;;
        kiro_cli)        suffix=KIRO_CLI ;;
        opencode)        suffix=OPENCODE ;;
        *) return 0 ;;
    esac
    case "$1" in
        tool|tool_result) flag="ENABLE_MCP_HOOKS_$suffix" ;;
        *)                flag="ENABLE_PROMPT_HOOKS_$suffix" ;;
    esac
    [ "$(eval "printf '%s' \"\${$flag:-}\"")" = "false" ] && return 1
    return 0
}

AKTO_INPUT="$(cat)"
AKTO_DEVICE_LABEL="$(akto_device_label)"

log_info "=== $AKTO_CONNECTOR/$AKTO_EVENT started (mode=$MODE sync=$AKTO_SYNC_MODE) ==="

# An unparseable payload is not worth blocking a user's turn over.
if ! printf '%s' "$AKTO_INPUT" | awk -v mode=type -v path= -f "$AKTO_JSON_AWK" >/dev/null 2>&1; then
    log_error "stdin was not valid JSON; allowing"
    exit 0
fi

# ── Event classification ──────────────────────────────────────────────────────
# Maps each agent's event name onto one of: prompt | tool | tool_result |
# response | observe. Names taken from each vendor's hook reference.

akto_event_kind() {
    case "$1:$2" in
        claude_code_cli:UserPromptSubmit|codex_cli:UserPromptSubmit) echo prompt ;;
        claude_code_cli:PreToolUse|codex_cli:PreToolUse)             echo tool ;;
        claude_code_cli:PostToolUse|codex_cli:PostToolUse)           echo tool_result ;;
        claude_code_cli:Stop|codex_cli:Stop)                         echo response ;;

        cursor:beforeSubmitPrompt)    echo prompt ;;
        cursor:beforeMCPExecution)    echo tool ;;
        cursor:afterMCPExecution)     echo tool_result ;;
        cursor:afterAgentResponse)    echo response ;;

        gemini_cli:BeforeAgent|gemini_cli:UserPromptSubmit) echo prompt ;;
        gemini_cli:BeforeTool|gemini_cli:PreToolUse)        echo tool ;;
        gemini_cli:AfterTool|gemini_cli:PostToolUse)        echo tool_result ;;
        gemini_cli:AfterAgent|gemini_cli:Stop)              echo response ;;

        github:userPromptSubmitted|vscode:userPromptSubmitted) echo prompt ;;
        github:preToolUse|vscode:preToolUse)                   echo tool ;;
        github:postToolUse|vscode:postToolUse)                 echo tool_result ;;

        kiro_cli:userPromptSubmit) echo prompt ;;
        kiro_cli:preToolUse)       echo tool ;;
        kiro_cli:postToolUse)      echo tool_result ;;

        *) echo observe ;;
    esac
}

KIND="$(akto_event_kind "$AKTO_CONNECTOR" "$AKTO_EVENT")"

if ! akto_hooks_enabled "$KIND"; then
    log_info "Disabled by ENABLE_* flag for $AKTO_CONNECTOR/$KIND; allowing"
    exit 0
fi

SESSION_KEY="$(jstr "$(adapter_session_key_field "$AKTO_CONNECTOR")")"
[ -z "$SESSION_KEY" ] && SESSION_KEY="_latest"

# ── Message-turn correlation ──────────────────────────────────────────────────
# A prompt opens a new turn; later events in the same session reuse its id so the
# backend can stitch prompt -> tools -> response into one trace.

if [ "$KIND" = "prompt" ]; then
    MSG_ID="$(jstr generation_id)"
    [ -z "$MSG_ID" ] && MSG_ID="$SESSION_KEY:$(date +%s)"
    akto_session_save "$SESSION_KEY" current_message_id "$MSG_ID"
else
    MSG_ID="$(akto_session_load "$SESSION_KEY" current_message_id | awk -v mode=unquote -f "$AKTO_JSON_AWK" 2>/dev/null)"
fi

akto_headers() { # akto_headers <extra-json-fields-or-empty>
    printf '{"host":"%s","%s":"%s","content-type":"application/json"' \
        "$(jesc "$AKTO_HOST")" "$(jesc "$HOOK_HEADER")" "$(jesc "$AKTO_EVENT")"
    [ -n "$SESSION_KEY" ] && printf ',"x-akto-installer-akto_session_id":"%s"' "$(jesc "$SESSION_KEY")"
    [ -n "$MSG_ID" ] && printf ',"x-akto-installer-akto_message_id":"%s"' "$(jesc "$MSG_ID")"
    [ -n "$1" ] && printf '%s' "$1"
    printf '}'
}

# ── Blocking pipeline ─────────────────────────────────────────────────────────

block_reason_text() { # block_reason_text <kind-label> <escaped server reason>
    if _behaviour_is warn; then
        printf 'Warning!!, %s blocked, please review it. Send again to bypass. Reason for blocking: %s' "$1" "$2"
    else
        printf '%s blocked: %s' "$1" "$2"
    fi
}

run_blocking() { # run_blocking <emit-kind> <label> <state-name> <path> <req-payload> <tags> <is_mcp>
    local emit_kind="$1" label="$2" state="$3" path="$4" reqp="$5" tags="$6" is_mcp="$7"
    local vxlan="$AKTO_DEVICE_LABEL"
    [ "$is_mcp" = "1" ] && vxlan="0"

    local payload
    payload="$(akto_build_payload "$path" "$(akto_headers)" \
        "{\"$(jesc "$HOOK_HEADER")\":\"$(jesc "$AKTO_EVENT")\"}" \
        "$reqp" '{}' "$tags" "200" "$vxlan")"

    if [ "$AKTO_SYNC_MODE" != "true" ]; then
        akto_ingest "$payload" "$AKTO_EVENT"
        return 0
    fi

    akto_guardrails_eval "$payload" "$AKTO_EVENT"

    if [ "$GR_ALLOWED" != "false" ]; then
        # Guardrails may rewrite the request instead of refusing it (redaction).
        if [ "$GR_MODIFIED" = "true" ] && [ -n "$GR_MODIFIED_PAYLOAD" ] && [ "$emit_kind" = "tool" ]; then
            emit_modified "$is_mcp"
            return $?
        fi
        log_info "$label allowed"
        return 0
    fi

    local fp
    fp="$(sha256_of "$reqp")"
    if akto_apply_warn_flow "$state" "$fp"; then
        log_info "$label allowed after warn/alert flow"
        return 0
    fi

    log_warn "BLOCKING $label: $GR_REASON"
    local reason
    reason="$(block_reason_text "$label" "$GR_REASON")"

    # Record the block itself, with the 403 the caller would have seen.
    akto_ingest "$(akto_build_payload "$path" "$(akto_headers)" \
        "{\"$(jesc "$HOOK_HEADER")\":\"$(jesc "$AKTO_EVENT")\",\"x-blocked-by\":\"Akto Proxy\"}" \
        "$reqp" "{\"body\":{\"x-blocked-by\":\"Akto Proxy\",\"reason\":\"$GR_REASON\"}}" \
        "$tags" "403" "$vxlan")" "$AKTO_EVENT"

    adapter_emit_deny "$AKTO_CONNECTOR" "$emit_kind" "$reason"
    return $?
}

emit_modified() { # emit_modified <is_mcp>
    local new_input
    if [ "$1" = "1" ]; then
        new_input="$(jget_from "$GR_MODIFIED_PAYLOAD" "params.arguments")"
    else
        new_input="$(jget_from "$GR_MODIFIED_PAYLOAD" "body")"
    fi
    if [ -z "$new_input" ]; then
        log_warn "ModifiedPayload had no usable arguments; keeping original input"
        return 0
    fi
    log_info "Applying guardrail-modified tool input"
    case "$AKTO_CONNECTOR" in
        claude_code_cli|codex_cli) emit_allow_claude_tool_modified "$new_input" ;;
        *) return 0 ;;
    esac
    return $?
}

# ── Per-kind entry ────────────────────────────────────────────────────────────

case "$KIND" in
prompt)
    AKTO_HOST="$(akto_atlas_host)"
    PROMPT="$(jget "$(adapter_prompt_field "$AKTO_CONNECTOR")")"
    if [ -z "$PROMPT" ] || [ "$PROMPT" = '""' ]; then
        log_info "Empty prompt, allowing"
        exit 0
    fi
    run_blocking prompt "Prompt" "prompt" "/v1/messages" \
        "{\"body\":$PROMPT}" "$(akto_tags 0)" 0
    exit $?
    ;;

tool)
    TOOL_NAME="$(jstr "$(adapter_tool_name_field "$AKTO_CONNECTOR")")"
    TOOL_INPUT="$(jget "$(adapter_tool_input_field "$AKTO_CONNECTOR")")"
    [ -z "$TOOL_INPUT" ] && TOOL_INPUT='{}'

    adapter_parse_tool "$AKTO_CONNECTOR" "$TOOL_NAME"
    # Cursor routes MCP through its own event and names the server explicitly.
    if [ "$AKTO_CONNECTOR" = "cursor" ] && [ "$AKTO_EVENT" = "beforeMCPExecution" ]; then
        MCP_IS=1
        MCP_SERVER="$(jstr server_name)"
        MCP_TOOL="$TOOL_NAME"
    fi

    if [ "$MCP_IS" = "1" ]; then
        AKTO_HOST="$AKTO_DEVICE_LABEL.$AKTO_CONNECTOR_VALUE.$MCP_SERVER"
        REQP="{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"$(jesc "$MCP_TOOL")\",\"arguments\":$TOOL_INPUT},\"id\":1}"
        PATH_="$MCP_INGEST_PATH"
    else
        AKTO_HOST="$(akto_atlas_host)"
        REQP="{\"body\":$TOOL_INPUT,\"toolName\":\"$(jesc "$TOOL_NAME")\"}"
        PATH_="$(adapter_non_mcp_path "$TOOL_NAME")"
    fi

    run_blocking tool "Tool request" "pretool" "$PATH_" "$REQP" "$(akto_tags "$MCP_IS")" "$MCP_IS"
    exit $?
    ;;

tool_result)
    TOOL_NAME="$(jstr "$(adapter_tool_name_field "$AKTO_CONNECTOR")")"
    TOOL_RESP="$(jget tool_response)"
    [ -z "$TOOL_RESP" ] && TOOL_RESP="$(jget tool_result)"
    [ -z "$TOOL_RESP" ] && TOOL_RESP='{}'

    adapter_parse_tool "$AKTO_CONNECTOR" "$TOOL_NAME"
    if [ "$AKTO_CONNECTOR" = "cursor" ] && [ "$AKTO_EVENT" = "afterMCPExecution" ]; then
        MCP_IS=1
        MCP_SERVER="$(jstr server_name)"
        MCP_TOOL="$TOOL_NAME"
    fi

    if [ "$MCP_IS" = "1" ]; then
        AKTO_HOST="$AKTO_DEVICE_LABEL.$AKTO_CONNECTOR_VALUE.$MCP_SERVER"
        REQP="{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"$(jesc "$MCP_TOOL")\",\"arguments\":{}},\"id\":1}"
        PATH_="$MCP_INGEST_PATH"
    else
        AKTO_HOST="$(akto_atlas_host)"
        REQP="{\"body\":$TOOL_RESP,\"toolName\":\"$(jesc "$TOOL_NAME")\"}"
        PATH_="$(adapter_non_mcp_path "$TOOL_NAME")"
    fi

    run_blocking tool "Tool response" "posttool" "$PATH_" "$REQP" "$(akto_tags "$MCP_IS")" "$MCP_IS"
    exit $?
    ;;

response)
    AKTO_HOST="$(akto_atlas_host)"
    # Claude re-fires Stop after a Stop-hook block; honouring it again would loop.
    if [ "$(jget stop_hook_active)" = "true" ]; then
        log_info "stop_hook_active=true: skipping guardrails to avoid a Stop loop"
        exit 0
    fi
    RESP="$(jget last_assistant_message)"
    [ -z "$RESP" ] && RESP="$(jget response)"
    if [ -z "$RESP" ] || [ "$RESP" = '""' ]; then
        log_info "No assistant response on this event, allowing"
        exit 0
    fi
    run_blocking response "Response" "response" "/v1/messages" \
        "{\"body\":$RESP}" "$(akto_tags 0)" 0
    exit $?
    ;;

observe)
    # Fire-and-forget: mirror the event, never block.
    AKTO_HOST="$(akto_atlas_host)"
    akto_ingest "$(akto_build_payload "/v1/hooks/$AKTO_EVENT" "$(akto_headers)" \
        "{\"$(jesc "$HOOK_HEADER")\":\"$(jesc "$AKTO_EVENT")\"}" \
        "{\"body\":$AKTO_INPUT}" '{}' "$(akto_tags 0)" "200" "$AKTO_DEVICE_LABEL")" "$AKTO_EVENT"
    log_info "=== $AKTO_CONNECTOR/$AKTO_EVENT completed (observe) ==="
    exit 0
    ;;
esac

exit 0
