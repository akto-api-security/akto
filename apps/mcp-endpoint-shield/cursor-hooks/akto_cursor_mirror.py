"""
Shared HTTP mirror ingestion helpers for Cursor hooks (MCP after, postToolUse file reads, etc.).
"""
import json
import logging
import os
import ssl
import time
import urllib.request
from typing import Any, Dict, Union

from akto_machine_id import get_machine_id, get_username


def create_cursor_hook_ssl_context(logger: logging.Logger) -> ssl.SSLContext:
    ssl_verify = os.getenv("SSL_VERIFY", "true").lower() == "true"
    ssl_cert_path = os.getenv("SSL_CERT_PATH")

    if not ssl_verify:
        logger.warning("SSL verification disabled via SSL_VERIFY=false - INSECURE!")
        return ssl._create_unverified_context()

    if ssl_cert_path:
        try:
            context = ssl.create_default_context(cafile=ssl_cert_path)
            logger.info(f"Using custom SSL certificate: {ssl_cert_path}")
            return context
        except Exception as e:
            logger.warning(f"Failed to load custom SSL certificate from {ssl_cert_path}: {e}")

    try:
        context = ssl.create_default_context()
        logger.debug("Using system default SSL context")
        return context
    except Exception as e:
        logger.warning(f"Failed to create default SSL context: {e}")

    try:
        import certifi
        context = ssl.create_default_context(cafile=certifi.where())
        logger.info("Using Python certifi SSL bundle")
        return context
    except ImportError:
        logger.debug("certifi package not available")
    except Exception as e:
        logger.warning(f"Failed to create SSL context with certifi: {e}")

    logger.error("WARNING: All SSL verification methods failed! Falling back to UNVERIFIED context - INSECURE!")
    return ssl._create_unverified_context()


def build_mirror_http_proxy_url(
    akto_data_ingestion_url: str,
    akto_connector: str,
    *,
    guardrails: bool,
    ingest_data: bool,
) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={akto_connector}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{akto_data_ingestion_url.rstrip('/')}/api/http-proxy?{'&'.join(params)}"


def generate_curl_command(url: str, payload: Dict[str, Any], headers: Dict[str, str]) -> str:
    payload_json = json.dumps(payload)
    headers_str = " ".join([f"-H '{k}: {v}'" for k, v in headers.items()])
    payload_escaped = payload_json.replace("'", "'\\''")
    return f"curl -X POST {headers_str} -d '{payload_escaped}' '{url}'"


def post_json_to_akto_proxy(
    url: str,
    payload: Dict[str, Any],
    logger: logging.Logger,
    *,
    timeout: float,
    log_payloads: bool,
) -> Union[Dict[str, Any], str]:
    logger.info(f"API CALL: POST {url}")
    if log_payloads:
        logger.debug(f"Request payload: {json.dumps(payload)[:1000]}...")

    headers = {"Content-Type": "application/json"}
    curl_cmd = generate_curl_command(url, payload, headers)
    logger.debug(f"CURL EQUIVALENT:\n{curl_cmd}")

    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )

    start_time = time.time()
    try:
        ssl_context = create_cursor_hook_ssl_context(logger)
        with urllib.request.urlopen(request, context=ssl_context, timeout=timeout) as response:
            duration_ms = int((time.time() - start_time) * 1000)
            status_code = response.getcode()
            raw = response.read().decode("utf-8")

            logger.info(f"API RESPONSE: Status {status_code}, Duration: {duration_ms}ms, Size: {len(raw)} bytes")

            if log_payloads:
                logger.debug(f"Response body: {raw[:1000]}...")

            try:
                return json.loads(raw)
            except json.JSONDecodeError:
                return raw
    except Exception as e:
        duration_ms = int((time.time() - start_time) * 1000)
        logger.error(f"API CALL FAILED after {duration_ms}ms: {e}")
        raise


def build_mirror_ingestion_payload(
    *,
    x_cursor_hook: str,
    tool_input_str: str,
    response_body_str: str,
    server_tag: str,
    mode: str,
    api_url: str,
    context_source: str,
    device_id: str,
    username: str,
) -> Dict[str, Any]:
    tags: Dict[str, str] = {"gen-ai": "Gen AI"}
    if mode == "atlas":
        tags["ai-agent"] = "cursor"
        tags["source"] = context_source

    tags["mcp_server_name"] = server_tag

    host = api_url.replace("https://", "").replace("http://", "")

    request_headers = json.dumps({
        "host": host,
        "x-cursor-hook": x_cursor_hook,
        "content-type": "application/json",
    })

    response_headers = json.dumps({
        "x-cursor-hook": x_cursor_hook,
        "content-type": "application/json",
    })

    request_payload = json.dumps({"body": tool_input_str})
    response_payload = json.dumps({"body": response_body_str})

    return {
        "path": "/v1/messages",
        "requestHeaders": request_headers,
        "responseHeaders": response_headers,
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": username,
        "destIp": "127.0.0.1",
        "time": str(int(time.time() * 1000)),
        "statusCode": "200",
        "type": None,
        "status": "200",
        "akto_account_id": "1000000",
        "akto_vxlan_id": device_id,
        "is_pending": "false",
        "source": "MIRRORING",
        "direction": None,
        "process_id": None,
        "socket_id": None,
        "daemonset_id": None,
        "enabled_graph": None,
        "tag": json.dumps(tags),
        "metadata": json.dumps(tags),
        "contextSource": context_source,
    }


def send_mirror_ingestion(
    *,
    x_cursor_hook: str,
    tool_input_str: str,
    response_body_str: str,
    server_tag: str,
    mode: str,
    api_url: str,
    context_source: str,
    akto_connector: str,
    akto_data_ingestion_url: str,
    akto_timeout: float,
    akto_sync_mode: bool,
    log_payloads: bool,
    logger: logging.Logger,
) -> None:
    if not tool_input_str.strip() or not response_body_str.strip():
        return

    logger.info(f"Ingesting mirror event for tag: {server_tag} (hook: {x_cursor_hook})")
    if log_payloads:
        logger.debug(f"Tool input: {tool_input_str[:500]}...")
        logger.debug(f"Result: {response_body_str[:500]}...")

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    username = get_username()

    request_body = build_mirror_ingestion_payload(
        x_cursor_hook=x_cursor_hook,
        tool_input_str=tool_input_str,
        response_body_str=response_body_str,
        server_tag=server_tag,
        mode=mode,
        api_url=api_url,
        context_source=context_source,
        device_id=device_id,
        username=username,
    )

    proxy_url = build_mirror_http_proxy_url(
        akto_data_ingestion_url,
        akto_connector,
        guardrails=not akto_sync_mode,
        ingest_data=True,
    )

    post_json_to_akto_proxy(
        proxy_url,
        request_body,
        logger,
        timeout=akto_timeout,
        log_payloads=log_payloads,
    )
    logger.info(f"Data ingestion successful for {server_tag}")
