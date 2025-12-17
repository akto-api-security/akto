"""
Model Router Service
Routes scan requests to appropriate model services based on scanner type
Maintains backward compatibility with existing /scan endpoint
"""
import logging
import time
from typing import Dict, Any, List, Optional
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel
import httpx
from config import (
    SCANNER_SERVICE_MAP,
    SERVICE_URLS,
    REQUEST_TIMEOUT,
    MAX_RETRIES,
    RETRY_DELAY,
)

logging.basicConfig(
    level=logging.INFO,
    format='%(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = FastAPI(title="Agent Guard Model Router", version="1.0.0")

# HTTP client for making requests to model services
http_client = httpx.AsyncClient(timeout=REQUEST_TIMEOUT)

# Service health status cache
service_health: Dict[str, Dict[str, Any]] = {}


class ScanRequest(BaseModel):
    scanner_type: str
    scanner_name: str
    text: str
    config: Dict[str, Any] = {}


class ScanResponse(BaseModel):
    scanner_name: str
    is_valid: bool
    risk_score: float
    sanitized_text: str
    details: Dict[str, Any] = {}


class HealthStatus(BaseModel):
    status: str
    service: str
    services: Dict[str, str] = {}


@app.on_event("startup")
async def startup_event():
    """Initialize router on startup"""
    logger.info("Model Router Service starting...")
    logger.info(f"Configured services: {list(SERVICE_URLS.keys())}")
    for service_name, url in SERVICE_URLS.items():
        logger.info(f"  {service_name}: {url}")

    # Perform initial health checks
    await check_all_services_health()


@app.on_event("shutdown")
async def shutdown_event():
    """Cleanup on shutdown"""
    logger.info("Model Router Service shutting down...")
    await http_client.aclose()


async def check_service_health(service_name: str, service_url: str) -> bool:
    """Check health of a specific service"""
    try:
        health_url = f"{service_url}/health"
        response = await http_client.get(health_url, timeout=5.0)
        is_healthy = response.status_code == 200

        service_health[service_name] = {
            "status": "healthy" if is_healthy else "unhealthy",
            "last_check": time.time(),
            "url": service_url
        }

        return is_healthy
    except Exception as e:
        logger.warning(f"Health check failed for {service_name}: {e}")
        service_health[service_name] = {
            "status": "unhealthy",
            "last_check": time.time(),
            "error": str(e),
            "url": service_url
        }
        return False


async def check_all_services_health():
    """Check health of all configured services"""
    logger.info("Checking health of all services...")
    for service_name, service_url in SERVICE_URLS.items():
        is_healthy = await check_service_health(service_name, service_url)
        status = "✓" if is_healthy else "✗"
        logger.info(f"  {status} {service_name}: {service_health[service_name]['status']}")


def get_service_for_scanner(scanner_name: str) -> Optional[str]:
    """Get the service name for a given scanner"""
    return SCANNER_SERVICE_MAP.get(scanner_name)


def get_service_url_for_scanner(scanner_name: str) -> Optional[str]:
    """Get the service URL for a given scanner"""
    service_name = get_service_for_scanner(scanner_name)
    if not service_name:
        return None
    return SERVICE_URLS.get(service_name)


async def forward_scan_request(
    service_url: str,
    scanner_name: str,
    request: ScanRequest,
    retry_count: int = 0
) -> ScanResponse:
    """Forward scan request to appropriate model service with retry logic"""

    try:
        # Build target URL
        target_url = f"{service_url}/scan"

        # Prepare request payload
        payload = {
            "scanner_type": request.scanner_type,
            "scanner_name": request.scanner_name,
            "text": request.text,
            "config": request.config,
        }

        logger.debug(f"Forwarding request to {target_url}: {scanner_name}")
        start_time = time.time()

        # Make request to model service
        response = await http_client.post(
            target_url,
            json=payload,
            timeout=REQUEST_TIMEOUT
        )

        duration = (time.time() - start_time) * 1000

        if response.status_code == 200:
            result = response.json()
            logger.info(f"Scan completed via {service_url}: {scanner_name}, time={duration:.2f}ms")
            return ScanResponse(**result)
        else:
            logger.error(f"Service returned error {response.status_code}: {response.text}")
            raise HTTPException(
                status_code=response.status_code,
                detail=f"Service error: {response.text}"
            )

    except httpx.TimeoutException as e:
        logger.error(f"Request timeout for {scanner_name} at {service_url}")

        # Retry logic
        if retry_count < MAX_RETRIES:
            logger.info(f"Retrying request (attempt {retry_count + 1}/{MAX_RETRIES})...")
            await asyncio.sleep(RETRY_DELAY)
            return await forward_scan_request(service_url, scanner_name, request, retry_count + 1)

        raise HTTPException(
            status_code=504,
            detail=f"Request timeout after {retry_count + 1} attempts"
        )

    except Exception as e:
        logger.error(f"Error forwarding request to {service_url}: {e}")

        # Retry logic for network errors
        if retry_count < MAX_RETRIES:
            logger.info(f"Retrying request (attempt {retry_count + 1}/{MAX_RETRIES})...")
            await asyncio.sleep(RETRY_DELAY)
            return await forward_scan_request(service_url, scanner_name, request, retry_count + 1)

        raise HTTPException(
            status_code=500,
            detail=f"Error forwarding request: {str(e)}"
        )


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    # Check if we can reach at least one service
    healthy_services = [
        name for name, health in service_health.items()
        if health.get("status") == "healthy"
    ]

    if not healthy_services:
        # Perform fresh health check
        await check_all_services_health()
        healthy_services = [
            name for name, health in service_health.items()
            if health.get("status") == "healthy"
        ]

    services_status = {
        name: health.get("status", "unknown")
        for name, health in service_health.items()
    }

    return {
        "status": "healthy" if healthy_services else "unhealthy",
        "service": "agent-guard-model-router",
        "services": services_status,
        "healthy_count": len(healthy_services),
        "total_count": len(SERVICE_URLS)
    }


@app.get("/services")
async def list_services():
    """List all configured services and their health status"""
    return {
        "services": service_health,
        "scanner_mapping": SCANNER_SERVICE_MAP
    }


@app.post("/scan", response_model=ScanResponse)
async def scan_text(request: ScanRequest):
    """
    Scan text using appropriate model service
    Maintains backward compatibility with existing executor service
    """
    start_time = time.time()

    try:
        logger.info(
            f"Routing scan request: scanner={request.scanner_name}, "
            f"type={request.scanner_type}, text_length={len(request.text)}"
        )

        # Determine which service to route to
        service_url = get_service_url_for_scanner(request.scanner_name)

        if not service_url:
            logger.error(f"No service configured for scanner: {request.scanner_name}")
            raise HTTPException(
                status_code=400,
                detail=f"Unknown scanner: {request.scanner_name}"
            )

        # Check service health
        service_name = get_service_for_scanner(request.scanner_name)
        if service_name in service_health:
            if service_health[service_name].get("status") == "unhealthy":
                logger.warning(f"Routing to unhealthy service: {service_name}")

        # Forward request to model service
        result = await forward_scan_request(service_url, request.scanner_name, request)

        total_duration = (time.time() - start_time) * 1000
        logger.info(
            f"Routing completed: scanner={request.scanner_name}, "
            f"total_time={total_duration:.2f}ms"
        )

        return result

    except HTTPException:
        raise
    except Exception as e:
        total_duration = (time.time() - start_time) * 1000
        logger.error(
            f"Routing failed: scanner={request.scanner_name}, "
            f"error={str(e)}, time={total_duration:.2f}ms"
        )
        raise HTTPException(status_code=500, detail=f"Routing error: {str(e)}")


@app.post("/scan/batch", response_model=List[ScanResponse])
async def scan_batch(requests: List[ScanRequest]):
    """
    Batch scan endpoint - processes multiple scan requests
    Maintains backward compatibility with existing executor service
    """
    import asyncio

    logger.info(f"Processing batch scan: {len(requests)} requests")
    start_time = time.time()

    # Group requests by service for efficient routing
    service_requests: Dict[str, List[tuple[int, ScanRequest]]] = {}

    for idx, req in enumerate(requests):
        service_url = get_service_url_for_scanner(req.scanner_name)
        if service_url:
            if service_url not in service_requests:
                service_requests[service_url] = []
            service_requests[service_url].append((idx, req))

    # Process all requests in parallel
    async def process_request(idx: int, req: ScanRequest) -> tuple[int, ScanResponse]:
        try:
            result = await scan_text(req)
            return (idx, result)
        except HTTPException as e:
            # Return error as a scan response
            return (idx, ScanResponse(
                scanner_name=req.scanner_name,
                is_valid=False,
                risk_score=1.0,
                sanitized_text=req.text,
                details={"error": str(e.detail)}
            ))

    # Execute all requests in parallel
    tasks = [process_request(idx, req) for idx, req in enumerate(requests)]
    results = await asyncio.gather(*tasks)

    # Sort results by original index
    sorted_results = sorted(results, key=lambda x: x[0])
    final_results = [result for _, result in sorted_results]

    total_duration = (time.time() - start_time) * 1000
    logger.info(f"Batch scan completed: {len(requests)} requests, time={total_duration:.2f}ms")

    return final_results


if __name__ == "__main__":
    import uvicorn
    import os
    import asyncio

    port = int(os.getenv("PORT", 8090))
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")
