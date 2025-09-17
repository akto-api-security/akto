package com.akto.hybrid_runtime;

import com.akto.dto.McpServer;
import com.akto.dto.McpScanResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.JSONUtils;
import com.akto.util.McpConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class McpReconScanner {
    
    private static final LoggerMaker logger = new LoggerMaker(McpReconScanner.class, LogDb.RUNTIME);
    private static final ObjectMapper mapper = new ObjectMapper();
    
    // Performance settings
    private final int maxConcurrent;
    private final int timeout;
    private final int batchSize;
    private final ExecutorService executorService;
    private final Semaphore semaphore;
    private final CloseableHttpClient httpClient;
    
    // Cache for DNS lookups and failed IPs
    private final ConcurrentHashMap<String, Boolean> dnsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> failedIpCache = new ConcurrentHashMap<>();
    
    public McpReconScanner() {
        this(100, 2000, 500);
    }
    
    public McpReconScanner(int maxConcurrent, int timeout, int batchSize) {
        this.maxConcurrent = maxConcurrent;
        this.timeout = timeout;
        this.batchSize = batchSize;
        // Use cached thread pool for better thread reuse
        this.executorService = new ThreadPoolExecutor(
            maxConcurrent / 2,  // Core pool size
            maxConcurrent,      // Maximum pool size
            60L, TimeUnit.SECONDS,  // Keep alive time
            new LinkedBlockingQueue<>(maxConcurrent * 2),  // Bounded queue
            new ThreadPoolExecutor.CallerRunsPolicy()  // Handle overflow
        );
        this.semaphore = new Semaphore(maxConcurrent);
        
        // Optimized HTTP client with enhanced connection pooling
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxConcurrent * 2);  // Increase total connections
        connectionManager.setDefaultMaxPerRoute(50);  // Increase per-route connections
        connectionManager.setValidateAfterInactivity(2000);  // Validate idle connections
        
        RequestConfig requestConfig = RequestConfig.custom()
            .setSocketTimeout(timeout)
            .setConnectTimeout(timeout)
            .setConnectionRequestTimeout(timeout / 2)  // Reduce wait time for connection
            .setCircularRedirectsAllowed(false)
            .setMaxRedirects(2)  // Limit redirects
            .build();
            
        this.httpClient = HttpClientBuilder.create()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .setConnectionReuseStrategy((response, context) -> true)  // Always reuse connections
            .setKeepAliveStrategy((response, context) -> 30000)  // Keep connections alive for 30s
            .setMaxConnPerRoute(50)
            .setMaxConnTotal(maxConcurrent * 2)
            .build();
    }
    
    /**
     * Main scanning function - optimized for large IP ranges
     */
    public McpScanResult scanIpRange(String ipRange) {
        long startTime = System.currentTimeMillis();
        McpScanResult result = new McpScanResult();
        
        try {
            // Parse IP ranges
            List<String> allIps = parseIpRanges(ipRange);
            int totalIps = allIps.size();
            
            logger.info(String.format("Starting optimized scan of %d IPs", totalIps));
            
            List<McpServer> allServers = new ArrayList<>();
            AtomicInteger processedIps = new AtomicInteger(0);
            
            // Process in batches
            for (int batchStart = 0; batchStart < totalIps; batchStart += batchSize) {
                int batchEnd = Math.min(batchStart + batchSize, totalIps);
                List<String> batchIps = allIps.subList(batchStart, batchEnd);
                
                logger.info(String.format("Processing batch %d: IPs %d-%d of %d",
                    (batchStart / batchSize) + 1, batchStart, batchEnd, totalIps));
                
                // Create target list (ip, port) pairs
                List<IpPortPair> targets = new ArrayList<>();
                for (String ip : batchIps) {
                    for (Integer port : McpConstants.COMMON_MCP_PORTS) {
                        targets.add(new IpPortPair(ip, port));
                    }
                }
                
                // Step 1: Quick port scan to filter open ports
                logger.debug(String.format("Port scanning %d targets", targets.size()));
                List<IpPortPair> openTargets = batchPortScan(targets);
                
                logger.info(String.format("Found %d open ports in batch", openTargets.size()));
                
                // Step 2: Check only open ports for MCP
                if (!openTargets.isEmpty()) {
                    List<McpServer> batchServers = verifyMcpBatch(openTargets);
                    allServers.addAll(batchServers);
                }
                
                processedIps.addAndGet(batchIps.size());
                
                // Small delay between batches
                if (batchEnd < totalIps) {
                    Thread.sleep(100);
                }
            }
            
            double elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0;
            
            logger.info(String.format("Scan completed in %.2f seconds", elapsedTime));
            logger.info(String.format("Found %d MCP servers", allServers.size()));
            
            // Build result
            result.setScanCompleted(new Date().toString());
            result.setIpRange(ipRange);
            result.setIpsScanned(totalIps);
            result.setPortsChecked(McpConstants.COMMON_MCP_PORTS.size());
            result.setServersFound(allServers.size());
            result.setServers(allServers);
            result.setScanTimeSeconds(elapsedTime);
            
            Map<String, Object> performance = new HashMap<>();
            performance.put("ipsPerSecond", elapsedTime > 0 ? totalIps / elapsedTime : 0);
            performance.put("batchSize", batchSize);
            performance.put("maxConcurrent", maxConcurrent);
            result.setPerformance(performance);
            
        } catch (Exception e) {
            logger.error("Scan error", e);
        }
        
        return result;
    }
    
    /**
     * Parse IP ranges (comma-separated, CIDR, ranges) and return unique list of IPs
     */
    private List<String> parseIpRanges(String ipRange) {
        Set<String> allIps = new LinkedHashSet<>();
        String[] ranges = ipRange.split(",");
        
        for (String singleRange : ranges) {
            singleRange = singleRange.trim();
            if (StringUtils.isEmpty(singleRange)) {
                continue;
            }
            
            try {
                if (singleRange.contains("/")) {
                    // CIDR notation
                    allIps.addAll(parseCidr(singleRange));
                } else if (singleRange.contains("-")) {
                    // IP range
                    allIps.addAll(parseRange(singleRange));
                } else {
                    // Single IP or hostname
                    allIps.add(singleRange);
                }
            } catch (Exception e) {
                logger.error(String.format("Error parsing range %s: %s", singleRange, e.getMessage()));
            }
        }
        
        return new ArrayList<>(allIps);
    }
    
    private List<String> parseCidr(String cidr) throws UnknownHostException {
        List<String> ips = new ArrayList<>();
        String[] parts = cidr.split("/");
        InetAddress baseAddress = InetAddress.getByName(parts[0]);
        int prefixLength = Integer.parseInt(parts[1]);
        
        // Calculate network range
        byte[] addr = baseAddress.getAddress();
        int baseInt = bytesToInt(addr);
        int mask = (-1) << (32 - prefixLength);
        int start = baseInt & mask;
        int end = start | (~mask);
        
        // Limit to 10000 IPs
        int count = Math.min(end - start + 1, 10000);
        
        for (int i = 0; i < count; i++) {
            ips.add(intToIp(start + i));
        }
        
        return ips;
    }
    
    private List<String> parseRange(String range) throws UnknownHostException {
        List<String> ips = new ArrayList<>();
        String[] parts = range.split("-");
        
        InetAddress startAddr = InetAddress.getByName(parts[0].trim());
        InetAddress endAddr = InetAddress.getByName(parts[1].trim());
        
        int start = bytesToInt(startAddr.getAddress());
        int end = bytesToInt(endAddr.getAddress());
        
        // Limit range to 10000 IPs
        int count = Math.min(end - start + 1, 10000);
        
        for (int i = 0; i < count; i++) {
            ips.add(intToIp(start + i));
        }
        
        return ips;
    }
    
    private int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
               ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8) |
               (bytes[3] & 0xFF);
    }
    
    private String intToIp(int ip) {
        return String.format("%d.%d.%d.%d",
            (ip >> 24) & 0xFF,
            (ip >> 16) & 0xFF,
            (ip >> 8) & 0xFF,
            ip & 0xFF);
    }
    
    /**
     * Optimized batch scan ports using CompletableFuture for better async handling
     */
    private List<IpPortPair> batchPortScan(List<IpPortPair> targets) {
        // Use CompletableFuture for better async performance
        List<CompletableFuture<IpPortPair>> futures = new ArrayList<>(targets.size());
        
        for (IpPortPair target : targets) {
            CompletableFuture<IpPortPair> future = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // Acquire semaphore to limit concurrent connections
                        semaphore.acquire();
                        try {
                            if (isPortOpen(target.ip, target.port)) {
                                return target;
                            }
                        } finally {
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }, executorService);
            
            // Add timeout using completeOnTimeout (Java 8 compatible approach)
            final CompletableFuture<IpPortPair> timeoutFuture = new CompletableFuture<>();
            executorService.submit(() -> {
                try {
                    Thread.sleep(timeout + 500);
                    timeoutFuture.complete(null);
                } catch (InterruptedException e) {
                    // Ignore
                }
            });
            
            CompletableFuture<IpPortPair> resultFuture = future
                .applyToEither(timeoutFuture, result -> result)
                .exceptionally(ex -> null);
            
            futures.add(resultFuture);
        }
        
        // Wait for all futures and collect results
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            allFutures.get(timeout * 2, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Continue with completed futures
        }
        
        // Collect non-null results
        return futures.stream()
            .map(future -> future.getNow(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    private boolean isPortOpen(String ip, int port) {
        // Check cache for recently failed IPs
        String cacheKey = ip + ":" + port;
        Long failedTime = failedIpCache.get(cacheKey);
        if (failedTime != null && (System.currentTimeMillis() - failedTime) < McpConstants.FAILED_IP_CACHE_TIME_MS) {
            return false; // Skip recently failed IPs
        }
        
        try (Socket socket = new Socket()) {
            // Set socket options for faster connection
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(timeout);
            socket.setReuseAddress(true);
            
            socket.connect(new InetSocketAddress(ip, port), timeout);
            return true;
        } catch (Exception e) {
            // Cache failed connection
            failedIpCache.put(cacheKey, System.currentTimeMillis());
            // Clean old entries periodically
            if (failedIpCache.size() > 10000) {
                cleanFailedIpCache();
            }
            return false;
        }
    }
    
    private void cleanFailedIpCache() {
        long currentTime = System.currentTimeMillis();
        failedIpCache.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue()) > McpConstants.FAILED_IP_CACHE_TIME_MS);
    }
    
    /**
     * Optimized MCP verification using CompletableFuture with timeout management
     */
    private List<McpServer> verifyMcpBatch(List<IpPortPair> openTargets) {
        // Use CompletableFuture for better control
        List<CompletableFuture<McpServer>> futures = new ArrayList<>(openTargets.size());
        
        for (IpPortPair target : openTargets) {
            CompletableFuture<McpServer> future = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            return verifySingleMcp(target.ip, target.port);
                        } finally {
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }, executorService);
            
            // Add timeout using Java 8 compatible approach
            final CompletableFuture<McpServer> timeoutFuture = new CompletableFuture<>();
            executorService.submit(() -> {
                try {
                    Thread.sleep(timeout * 2);
                    timeoutFuture.complete(null);
                } catch (InterruptedException e) {
                    // Ignore
                }
            });
            
            CompletableFuture<McpServer> resultFuture = future
                .applyToEither(timeoutFuture, result -> result)
                .exceptionally(ex -> {
                    logger.debug("Verification timeout for " + target.ip + ":" + target.port);
                    return null;
                });
            
            futures.add(resultFuture);
        }
        
        // Wait for all completions or timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(timeout * 3, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Continue with completed futures
        }
        
        // Collect non-null results
        return futures.stream()
            .map(future -> future.getNow(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Comprehensive MCP verification for a single IP:port
     */
    private McpServer verifySingleMcp(String ip, int port) {
        String baseUrl = String.format("http://%s:%d", ip, port);
        
        try {
            // Method 1: Check SSE endpoints
            McpServer sseResult = checkSseEndpoint(baseUrl, ip, port);
            if (sseResult != null) {
                logger.info(String.format("MCP SSE server detected at %s", baseUrl));
                return sseResult;
            }
            
            // Method 2: Check JSON-RPC endpoints
            McpServer jsonRpcResult = checkJsonRpcEndpoint(baseUrl, ip, port);
            if (jsonRpcResult != null) {
                logger.info(String.format("MCP JSON-RPC server detected at %s", baseUrl));
                return jsonRpcResult;
            }
            
            // Method 3: HTTP pattern matching
            McpServer httpResult = checkHttpEndpoints(baseUrl, ip, port);
            if (httpResult != null) {
                logger.info(String.format("MCP HTTP server detected at %s", baseUrl));
                return httpResult;
            }
            
        } catch (Exception e) {
            logger.debug(String.format("Error checking %s: %s", baseUrl, e.getMessage()));
        }
        
        return null;
    }
    
    private McpServer checkSseEndpoint(String baseUrl, String ip, int port) {
        List<String> sseEndpoints = Arrays.asList("/sse", "/mcp/sse", "/mcp/stream", "/messages", "/events");
        
        for (String endpoint : sseEndpoints) {
            try {
                HttpGet request = new HttpGet(baseUrl + endpoint);
                request.setHeader("Accept", "text/event-stream");
                request.setHeader("Cache-Control", "no-cache");
                
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        String contentType = response.getFirstHeader("Content-Type") != null ? 
                            response.getFirstHeader("Content-Type").getValue() : "";
                        
                        if (contentType.contains("text/event-stream")) {
                            // Read sample data
                            HttpEntity entity = response.getEntity();
                            String content = EntityUtils.toString(entity).toLowerCase();
                            
                            // Check for MCP keywords
                            for (String indicator : McpConstants.MCP_INDICATORS) {
                                if (content.contains(indicator.toLowerCase())) {
                                    McpServer server = new McpServer();
                                    server.setIp(ip);
                                    server.setPort(port);
                                    server.setUrl(baseUrl);
                                    server.setVerified(true);
                                    server.setDetectionMethod("SSE");
                                    server.setTimestamp(new Date().toString());
                                    server.setType("SSE");
                                    server.setEndpoint(endpoint);
                                    return server;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Continue to next endpoint
            }
        }
        
        return null;
    }
    
    private McpServer checkJsonRpcEndpoint(String baseUrl, String ip, int port) {
        List<String> jsonRpcEndpoints = Arrays.asList("/mcp", "/mcp/v1", "/jsonrpc", "/rpc", "/api/mcp");
        
        Map<String, Object> initRequest = new HashMap<>();
        initRequest.put("jsonrpc", McpConstants.JSONRPC_VERSION);
        initRequest.put("id", 1);
        initRequest.put("method", McpConstants.JSONRPC_METHOD_INITIALIZE);
        
        Map<String, Object> params = new HashMap<>();
        params.put("protocolVersion", McpConstants.MCP_PROTOCOL_VERSION);
        
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("roots", Collections.singletonMap("listChanged", true));
        capabilities.put("sampling", new HashMap<>());
        params.put("capabilities", capabilities);
        
        Map<String, Object> clientInfo = new HashMap<>();
        clientInfo.put("name", "mcp-recon-scanner");
        clientInfo.put("version", "2.0.0");
        params.put("clientInfo", clientInfo);
        
        initRequest.put("params", params);
        
        for (String endpoint : jsonRpcEndpoints) {
            try {
                HttpPost request = new HttpPost(baseUrl + endpoint);
                request.setHeader("Content-Type", "application/json");
                request.setHeader("Accept", "application/json");
                request.setEntity(new StringEntity(Objects.requireNonNull(JSONUtils.getString(initRequest))));
                
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        HttpEntity entity = response.getEntity();
                        String content = EntityUtils.toString(entity);
                        Map<String, Object> jsonResponse = JSONUtils.getMap(content);
                        
                        if (jsonResponse != null && "2.0".equals(jsonResponse.get("jsonrpc"))) {
                            Map<String, Object> result = (Map<String, Object>) jsonResponse.get("result");
                            if (result != null && (result.containsKey("protocolVersion") || result.containsKey("serverInfo"))) {
                                McpServer server = new McpServer();
                                server.setIp(ip);
                                server.setPort(port);
                                server.setUrl(baseUrl);
                                server.setVerified(true);
                                server.setDetectionMethod("JSON-RPC");
                                server.setTimestamp(new Date().toString());
                                server.setType("JSON-RPC");
                                server.setEndpoint(endpoint);
                                if (result.get("protocolVersion") != null) {
                                    server.setProtocolVersion((String) result.get("protocolVersion"));
                                }
                                if (result.get("serverInfo") != null) {
                                    server.setServerInfo((Map<String, Object>) result.get("serverInfo"));
                                }
                                if (result.get("capabilities") != null) {
                                    server.setCapabilities((Map<String, Object>) result.get("capabilities"));
                                }
                                
                                // Try to get tools, resources, prompts
                                server.setTools(getToolsList(baseUrl + endpoint, null));
                                server.setResources(getResourcesList(baseUrl + endpoint, null));
                                server.setPrompts(getPromptsList(baseUrl + endpoint, null));
                                
                                return server;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Continue to next endpoint
            }
        }
        
        return null;
    }
    
    private McpServer checkHttpEndpoints(String baseUrl, String ip, int port) {
        for (String endpoint : McpConstants.MCP_ENDPOINTS.subList(0, Math.min(4, McpConstants.MCP_ENDPOINTS.size()))) {
            try {
                HttpGet request = new HttpGet(baseUrl + endpoint);
                
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    HttpEntity entity = response.getEntity();
                    String content = EntityUtils.toString(entity);
                    
                    if (isLikelyMcp(content)) {
                        McpServer server = new McpServer();
                        server.setIp(ip);
                        server.setPort(port);
                        server.setUrl(baseUrl);
                        server.setVerified(true);
                        server.setDetectionMethod("HTTP");
                        server.setTimestamp(new Date().toString());
                        server.setEndpoint(endpoint);
                        
                        List<String> detectedIndicators = getDetectedIndicators(content);
                        Map<String, Object> serverInfo = new HashMap<>();
                        serverInfo.put("detectedIndicators", detectedIndicators);
                        server.setServerInfo(serverInfo);
                        
                        return server;
                    }
                }
            } catch (Exception e) {
                // Continue to next endpoint
            }
        }
        
        return null;
    }
    
    private boolean isLikelyMcp(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        // Limit content size for performance
        String contentToCheck = content.length() > 5000 ? 
            content.substring(0, 5000) : content;
        String contentLower = contentToCheck.toLowerCase();
        
        // Quick check for common indicators first
        if (contentLower.contains("mcp") || contentLower.contains("jsonrpc") || 
            contentLower.contains("model") || contentLower.contains("context")) {
            
            // Detailed check only if quick check passes
            for (String indicator : McpConstants.MCP_INDICATORS) {
                if (contentLower.contains(indicator.toLowerCase())) {
                    return true;
                }
            }
            
            // Use pre-compiled patterns for regex matching
            for (Pattern pattern : McpConstants.MCP_PATTERNS) {
                if (pattern.matcher(contentToCheck).find()) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private List<String> getDetectedIndicators(String content) {
        List<String> detected = new ArrayList<>();
        String contentLower = content.toLowerCase();
        if (contentLower.length() > 1000) {
            contentLower = contentLower.substring(0, 1000);
        }
        
        for (String indicator : McpConstants.MCP_INDICATORS) {
            if (contentLower.contains(indicator.toLowerCase())) {
                detected.add(indicator);
                if (detected.size() >= 5) {
                    break;
                }
            }
        }
        
        return detected;
    }
    
    private List<Map<String, Object>> getToolsList(String url, Map<String, String> authHeaders) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", McpConstants.JSONRPC_VERSION);
            request.put("id", 2);
            request.put("method", McpConstants.JSONRPC_METHOD_TOOLS_LIST);
            request.put("params", new HashMap<>());
            
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");
            if (authHeaders != null) {
                authHeaders.forEach(httpPost::setHeader);
            }
            httpPost.setEntity(new StringEntity(JSONUtils.getString(request)));
            
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    String content = EntityUtils.toString(response.getEntity());
                    Map<String, Object> jsonResponse = JSONUtils.getMap(content);
                    if (jsonResponse != null && jsonResponse.containsKey("result")) {
                        Map<String, Object> result = (Map<String, Object>) jsonResponse.get("result");
                        return (List<Map<String, Object>>) result.get("tools");
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return new ArrayList<>();
    }
    
    private List<Map<String, Object>> getResourcesList(String url, Map<String, String> authHeaders) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", McpConstants.JSONRPC_VERSION);
            request.put("id", 3);
            request.put("method", McpConstants.JSONRPC_METHOD_RESOURCES_LIST);
            request.put("params", new HashMap<>());
            
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");
            if (authHeaders != null) {
                authHeaders.forEach(httpPost::setHeader);
            }
            httpPost.setEntity(new StringEntity(JSONUtils.getString(request)));
            
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    String content = EntityUtils.toString(response.getEntity());
                    Map<String, Object> jsonResponse = JSONUtils.getMap(content);
                    if (jsonResponse != null && jsonResponse.containsKey("result")) {
                        Map<String, Object> result = (Map<String, Object>) jsonResponse.get("result");
                        return (List<Map<String, Object>>) result.get("resources");
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return new ArrayList<>();
    }
    
    private List<Map<String, Object>> getPromptsList(String url, Map<String, String> authHeaders) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", McpConstants.JSONRPC_VERSION);
            request.put("id", 4);
            request.put("method", McpConstants.JSONRPC_METHOD_PROMPTS_LIST);
            request.put("params", new HashMap<>());
            
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");
            if (authHeaders != null) {
                authHeaders.forEach(httpPost::setHeader);
            }
            httpPost.setEntity(new StringEntity(JSONUtils.getString(request)));
            
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    String content = EntityUtils.toString(response.getEntity());
                    Map<String, Object> jsonResponse = JSONUtils.getMap(content);
                    if (jsonResponse != null && jsonResponse.containsKey("result")) {
                        Map<String, Object> result = (Map<String, Object>) jsonResponse.get("result");
                        return (List<Map<String, Object>>) result.get("prompts");
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return new ArrayList<>();
    }
    
    public void shutdown() {
        try {
            // Clear caches
            dnsCache.clear();
            failedIpCache.clear();
            
            // Shutdown executor service gracefully
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("Executor service did not terminate");
                }
            }
            
            // Close HTTP client
            httpClient.close();
        } catch (Exception e) {
            logger.error("Error shutting down scanner", e);
        }
    }
    
    private static class IpPortPair {
        final String ip;
        final int port;
        
        IpPortPair(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }
}