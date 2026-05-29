"""
MCP (Model Context Protocol) utility functions for Hermes guardrails plugin.
Detects MCP tools and converts to JSON-RPC 2.0 format.
"""

import json
from typing import Any, Dict, Optional
from dataclasses import dataclass


@dataclass
class MCPTool:
    """Represents a parsed MCP tool."""
    is_mcp: bool
    server: str
    tool: str

    def __bool__(self) -> bool:
        """True if this is an MCP tool."""
        return self.is_mcp


def is_mcp_tool(tool_name: str) -> bool:
    """
    Detect if tool is MCP format.

    MCP tools have format: server_tool (e.g., calculator_add)
    Non-MCP tools: read, glob, write, etc.

    Args:
        tool_name: Name of tool

    Returns:
        True if tool is MCP format
    """
    if not tool_name or not isinstance(tool_name, str):
        return False

    # MCP tools have at least one underscore and server name before it
    parts = tool_name.split("_")

    # Must have at least 2 parts: server_tool
    if len(parts) < 2:
        return False

    # First part must be non-empty (server name)
    if not parts[0]:
        return False

    # Simple heuristic: if contains underscore and first part looks like identifier, it's MCP
    return True


def parse_mcp_tool(tool_name: str) -> MCPTool:
    """
    Parse MCP tool name into server and tool components.

    Format: server_tool (e.g., calculator_add -> server=calculator, tool=add)

    Args:
        tool_name: Tool name in MCP format

    Returns:
        MCPTool with parsed components
    """
    if not tool_name or not isinstance(tool_name, str):
        return MCPTool(is_mcp=False, server="", tool="")

    parts = tool_name.split("_")

    # Need at least server_tool format
    if len(parts) < 2:
        return MCPTool(is_mcp=False, server="", tool="")

    server = parts[0]
    tool = "_".join(parts[1:])  # In case tool name has underscores

    if not server or not tool:
        return MCPTool(is_mcp=False, server="", tool="")

    return MCPTool(is_mcp=True, server=server, tool=tool)


def build_jsonrpc_tool_call(
    tool_name: str,
    args: Dict[str, Any],
    mcp_tool: MCPTool
) -> str:
    """
    Build JSON-RPC 2.0 format tool call for MCP tools.

    Args:
        tool_name: Full tool name (e.g., calculator_add)
        args: Tool arguments
        mcp_tool: Parsed MCP tool info

    Returns:
        JSON-RPC 2.0 formatted string
    """
    if not mcp_tool.is_mcp:
        return json.dumps({})

    # JSON-RPC 2.0 format for MCP tools/call
    jsonrpc_call = {
        "jsonrpc": "2.0",
        "method": "tools/call",
        "params": {
            "name": mcp_tool.tool,
            "arguments": args
        }
    }

    return json.dumps(jsonrpc_call)


def build_jsonrpc_tool_response(
    tool_name: str,
    result: str,
    mcp_tool: MCPTool
) -> str:
    """
    Build JSON-RPC 2.0 format tool response for logging.

    Args:
        tool_name: Full tool name
        result: Tool output/result
        mcp_tool: Parsed MCP tool info

    Returns:
        JSON-RPC 2.0 formatted response string
    """
    if not mcp_tool.is_mcp:
        return json.dumps({})

    # JSON-RPC 2.0 format for tool response
    jsonrpc_response = {
        "jsonrpc": "2.0",
        "id": 1,
        "result": {
            "output": result
        }
    }

    return json.dumps(jsonrpc_response)
