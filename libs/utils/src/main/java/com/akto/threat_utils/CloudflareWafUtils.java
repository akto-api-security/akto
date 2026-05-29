package com.akto.threat_utils;

import com.akto.dto.Config;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CloudflareWafUtils {

    private static final String CLOUDFLARE_WAF_BASE_URL = "https://api.cloudflare.com/client/v4";
    private static final LoggerMaker loggerMaker = new LoggerMaker(CloudflareWafUtils.class, LogDb.THREAT_DETECTION);

    /**
     * Blocks IPs by adding them to Cloudflare WAF lists
     * @param config CloudflareWafConfig with list information
     * @param ips List of actor IPs to block
     * @return true if successful, false otherwise
     */
    public static boolean blockActorIps(Config.CloudflareWafConfig config, List<String> ips) {
        if (config == null || ips == null || ips.isEmpty()) {
            loggerMaker.debugAndAddToDb("Invalid parameters for blocking IPs");
            return false;
        }

        return addIPsToList(config, ips);
    }

    /**
     * Unblocks IPs by removing them from Cloudflare WAF lists
     * @param config CloudflareWafConfig with list information
     * @param ips List of actor IPs to unblock
     * @return true if successful, false otherwise
     */
    public static boolean unblockActorIps(Config.CloudflareWafConfig config, List<String> ips) {
        if (config == null || ips == null || ips.isEmpty()) {
            loggerMaker.debugAndAddToDb("Invalid parameters for unblocking IPs");
            return false;
        }

        return removeIPsFromLists(config, ips);
    }

    /**
     * Adds IPs to the last list. If list is full, creates overflow list and retries.
     */
    private static boolean addIPsToList(Config.CloudflareWafConfig config, List<String> ips) {
        try {
            String currentListId = getLastListId(config);
            Map<String, List<String>> headers = getAuthHeaders(config);

            BasicDBList items = new BasicDBList();
            for (String ip : ips) {
                BasicDBObject ipItem = new BasicDBObject();
                ipItem.put("ip", ip);
                items.add(ipItem);
            }

            String url = CLOUDFLARE_WAF_BASE_URL + "/accounts/" + config.getAccountOrZoneId()
                    + "/rules/lists/" + currentListId + "/items";
            OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", items.toString(), headers, "");
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());

            if (response.getStatusCode() > 201 || response.getBody() == null) {
                String cfErr = extractCfError(response.getBody());

                // Check if list is at capacity — create overflow list and retry
                if (cfErr != null && cfErr.toLowerCase().contains("maximum")) {
                    loggerMaker.errorAndAddToDb("List at capacity, attempting to create overflow list");
                    return false;
                }

                loggerMaker.errorAndAddToDb("Failed to add IPs to list: " + (cfErr != null ? cfErr : "Unknown error"));
                return false;
            }
            return true;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Exception adding IPs to Cloudflare list: " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes IPs by searching across ALL lists and deleting from whichever contains them.
     */
    private static boolean removeIPsFromLists(Config.CloudflareWafConfig config, List<String> ips) {
        try {
            Map<String, List<String>> headers = getAuthHeaders(config);

            // Group items to delete by their list ID
            Map<String, BasicDBList> deleteByList = new HashMap<>();
            for (String ip : ips) {
                String[] found = findItemAcrossLists(ip, config);
                if (found != null) {
                    String foundListId = found[0];
                    String itemId = found[1];
                    deleteByList.computeIfAbsent(foundListId, k -> new BasicDBList());
                    BasicDBObject itemObj = new BasicDBObject();
                    itemObj.put("id", itemId);
                    deleteByList.get(foundListId).add(itemObj);
                }
            }

            if (deleteByList.isEmpty()) {
                loggerMaker.debugAndAddToDb("None of the IPs were found in the block list(s).");
                return false;
            }

            // Delete from each list in one call per list
            for (Map.Entry<String, BasicDBList> entry : deleteByList.entrySet()) {
                String url = CLOUDFLARE_WAF_BASE_URL + "/accounts/" + config.getAccountOrZoneId()
                        + "/rules/lists/" + entry.getKey() + "/items";
                BasicDBObject deletePayload = new BasicDBObject();
                deletePayload.put("items", entry.getValue());

                OriginalHttpRequest request = new OriginalHttpRequest(url, "", "DELETE", deletePayload.toString(), headers, "");
                OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());

                if (response.getStatusCode() > 201 || response.getBody() == null) {
                    String cfErr = extractCfError(response.getBody());
                    loggerMaker.errorAndAddToDb("Failed to remove IPs from list: " + (cfErr != null ? cfErr : "Unknown error"));
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Exception removing IPs from Cloudflare lists: " + e.getMessage());
            return false;
        }
    }

    /**
     * Searches for an IP across all lists. Returns [listId, itemId] or null.
     */
    private static String[] findItemAcrossLists(String actorIp, Config.CloudflareWafConfig config) {
        Map<String, List<String>> headers = getAuthHeaders(config);
        for (String listId : config.getListIds()) {
            try {
                String url = CLOUDFLARE_WAF_BASE_URL + "/accounts/" + config.getAccountOrZoneId()
                        + "/rules/lists/" + listId + "/items?search=" + actorIp;
                OriginalHttpRequest request = new OriginalHttpRequest(url, "", "GET", "", headers, "");
                OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());

                if (response.getStatusCode() > 201 || response.getBody() == null) continue;

                BasicDBList result = (BasicDBList) BasicDBObject.parse(response.getBody()).get("result");
                if (result != null && !result.isEmpty()) {
                    String itemId = ((BasicDBObject) result.get(0)).getString("id");
                    if (itemId != null) {
                        return new String[]{listId, itemId};
                    }
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error searching list " + listId + " for IP: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Gets the last list ID from the config
     */
    private static String getLastListId(Config.CloudflareWafConfig config) {
        List<String> listIds = config.getListIds();
        if (listIds == null || listIds.isEmpty()) {
            return null;
        }
        return listIds.get(listIds.size() - 1);
    }

    /**
     * Extracts error message from Cloudflare API response
     */
    public static String extractCfError(String body) {
        try {
            if (body == null) return null;
            BasicDBObject obj = BasicDBObject.parse(body);
            BasicDBList errors = (BasicDBList) obj.get("errors");
            if (errors != null && !errors.isEmpty()) {
                return ((BasicDBObject) errors.get(0)).getString("message");
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Builds authentication headers for Cloudflare API
     */
    public static Map<String, List<String>> getAuthHeaders(Config.CloudflareWafConfig config) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", java.util.Collections.singletonList("application/json"));
        String cfEmail = config.getEmail();
        if (cfEmail != null && !cfEmail.isEmpty()) {
            headers.put("X-Auth-Key", java.util.Collections.singletonList(config.getApiKey()));
            headers.put("X-Auth-Email", java.util.Collections.singletonList(cfEmail));
        } else {
            headers.put("Authorization", java.util.Collections.singletonList("Bearer " + config.getApiKey()));
        }
        return headers;
    }
}
